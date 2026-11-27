import logging
import os
import uuid
from datetime import datetime, timedelta
from decimal import ROUND_DOWN, Decimal, InvalidOperation

from django.contrib import messages
from django.core.exceptions import ValidationError
from django.core.validators import URLValidator, validate_email
from django.core.files.storage import default_storage
from django.db.models import Avg, Count, Max, Q, Sum
from django.http import Http404, JsonResponse
from django.shortcuts import render, redirect, get_object_or_404
from django.urls import reverse
from django.utils import timezone
from django.utils.text import get_valid_filename

from .models import (
    Usuario, EstadoCuenta, Administrador, Asistente, AsistenteConsorcio, Consorcio, TipoChat, Chat, MiembroChat, MensajeChat,
    CuentaMarketplace, ImgCuentaMarketplace, Resena, Deuda, Factura, AgendaEvento, TareaAsistente,
    TituloAdministrador, Votacion, VotacionUser, VotacionContrato, DocVotacion,
    GoogleCuenta, Reunion, ReunionParticipante, DocReunion,
    TipoDeuda, FormaPagoContrato, ContratoProveedor, HistorialConsorcio,
)
from . import google_meet
from .templatetags.consora_tags import moneda as _moneda

logger = logging.getLogger(__name__)


def index(request):
    return render(request, 'index.html')


def login_view(request):
    error = None

    if request.method == 'POST':
        email = request.POST.get('email', '').strip()
        password = request.POST.get('password', '')

        usuario = Usuario.objects.filter(mail__iexact=email).first()

        if usuario is None or not usuario.check_password(password):
            error = 'Email o contraseña incorrectos.'
        elif usuario.estado_cuenta and usuario.estado_cuenta.estado != 'Activa':
            error = 'Tu cuenta no está activa. Contactá al soporte.'
        else:
            request.session['usuario_id'] = usuario.pk
            # Cada rol entra a su propio inicio
            return redirect('dashboard')

    return render(request, 'login.html', {'error': error})


def logout_view(request):
    request.session.flush()
    return redirect('login')


def _get_logged_usuario(request):
    """Devuelve el Usuario logueado (con rol/estado_cuenta) o None si no hay sesión válida."""
    usuario_id = request.session.get('usuario_id')

    if not usuario_id:
        return None

    usuario = Usuario.objects.select_related('rol', 'estado_cuenta').filter(pk=usuario_id).first()

    if usuario is None:
        request.session.flush()
        return None

    return usuario


# Roles que pueden usar el panel web. El resto (propietarios, proveedores,
# asistentes) ve la pantalla "en construcción" hasta que tengan su propio panel.
ROLES_ADMIN = ('Administrador/a', 'Administrador del sistema')


ASISTENTE = 'Asistente'
PROVEEDOR = 'Proveedor'
PROPIETARIO = 'Propietario/Residente'


def _es_admin(usuario):
    return usuario.rol.rol in ROLES_ADMIN


def _rol_permitido(usuario, *roles):
    """El administrador entra a todo; el resto, solo a las secciones de su rol."""
    return _es_admin(usuario) or usuario.rol.rol in roles


def en_construccion(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if _es_admin(usuario):
        return redirect('dashboard')

    return render(request, 'en_construccion.html', {'usuario': usuario})


def dashboard(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Cada rol tiene su propio inicio
    rol = usuario.rol.rol
    if rol == ASISTENTE:
        return _inicio_asistente(request, usuario)
    if rol == PROVEEDOR:
        return _inicio_proveedor(request, usuario)
    if rol == PROPIETARIO:
        return _inicio_propietario(request, usuario)

    return render(request, 'admin/dashboard.html', {
        'page_title': 'Dashboard',
        'page_subtitle': 'Resumen general de tus consorcios',
        'usuario': usuario,
    })


def _proximos_eventos(eventos, cantidad=5):
    return list(eventos.filter(fecha__gte=timezone.localdate()).select_related('consorcio').distinct()[:cantidad])


def _inicio_asistente(request, usuario):
    """Inicio del asistente: sus tareas, los edificios habilitados, votaciones y agenda."""
    asistente = getattr(usuario, 'asistente', None)
    mis_consorcios = _consorcios_del_usuario(usuario)

    # Tocando una tarea se marca como completada (o vuelve a en proceso)
    if request.method == 'POST' and request.POST.get('accion') == 'cambiar_tarea':
        tarea = usuario.tareas.filter(pk=request.POST.get('id_tarea')).first()
        if tarea:
            tarea.estado = TareaAsistente.EN_PROCESO if tarea.completada else TareaAsistente.COMPLETADO
            tarea.save(update_fields=['estado'])
        return redirect('dashboard')

    tareas = sorted(usuario.tareas.all(), key=lambda t: t.completada)
    consorcios = (
        mis_consorcios
        .annotate(cant_facturas=Count('facturas', distinct=True),
                  cant_pendientes=Count('deudas', filter=Q(deudas__factura__isnull=True), distinct=True))
        .order_by('name_consorcio')
    )
    votaciones_abiertas = list(
        Votacion.objects.filter(consorcio__in=mis_consorcios, fecha_limite__gt=timezone.now())
        .select_related('consorcio').order_by('fecha_limite')
    )
    return render(request, 'inicio/asistente.html', {
        'page_title': f'Hola, {usuario.name_surname.split()[0]}',
        'page_subtitle': f'Asistente de {asistente.admin.name_surname}' if asistente else 'Asistente',
        'usuario': usuario,
        'asistente': asistente,
        'tareas': tareas,
        'en_proceso': sum(1 for t in tareas if not t.completada),
        'consorcios': consorcios,
        'votaciones_abiertas': votaciones_abiertas,
        'eventos': _proximos_eventos(AgendaEvento.objects.filter(Q(usuario=usuario) | Q(consorcio__in=mis_consorcios))),
        'reuniones': _proximas_reuniones(usuario),
    })


def _inicio_proveedor(request, usuario):
    """Inicio del proveedor: invitaciones a contrataciones, su perfil del Marketplace y reseñas."""
    cuenta = CuentaMarketplace.objects.filter(dueno=usuario).first()
    invitaciones = list(
        VotacionContrato.objects.filter(proveedor=usuario)
        .select_related('votacion__consorcio__representante').order_by('-votacion__fecha')
    )
    for inv in invitaciones:
        mensaje = inv.mensajes.first()
        inv.chat_id = mensaje.chat_id if mensaje else None
    resenas = list(cuenta.resenas.select_related('autor')[:3]) if cuenta else []
    promedio = cuenta.resenas.aggregate(p=Avg('estrellas'))['p'] if cuenta else None
    # Reuniones a las que lo invitaron: las pendientes se responden desde el chat
    reuniones = [p for p in ReunionParticipante.objects.filter(usuario=usuario)
                 .exclude(respuesta=ReunionParticipante.RECHAZA)
                 .select_related('reunion__consorcio').order_by('reunion__fecha_inicio') if p.reunion.activa]
    for p in reuniones:
        mensaje = p.mensajes.first()
        p.chat_id = mensaje.chat_id if mensaje else None

    return render(request, 'inicio/proveedor.html', {
        'page_title': usuario.name_surname,
        'page_subtitle': 'Tu panel de proveedor',
        'usuario': usuario,
        'cuenta': cuenta,
        'pendientes': [i for i in invitaciones if i.aceptacion is None and i.votacion.abierta],
        'participando': [i for i in invitaciones if i.aceptacion],
        'contratos': sum(1 for i in invitaciones if i.contratado),
        'publicaciones': cuenta.publicaciones.count() if cuenta else 0,
        'cant_resenas': cuenta.resenas.count() if cuenta else 0,
        'promedio': _formato_estrellas(promedio) if promedio else None,
        'resenas': resenas,
        'reuniones_pendientes': [p for p in reuniones if p.respuesta == ReunionParticipante.PENDIENTE],
        'invitaciones_por_responder': sum(1 for i in invitaciones if i.aceptacion is None and i.votacion.abierta)
                                      + sum(1 for p in reuniones if p.respuesta == ReunionParticipante.PENDIENTE),
        'reuniones_confirmadas': [p for p in reuniones if p.acepto],
    })


def _inicio_propietario(request, usuario):
    """Inicio del propietario: su unidad, votaciones para votar, expensas y agenda del edificio."""
    unidades = list(usuario.unidades.select_related('consorcio__representante'))
    mis_consorcios = _consorcios_del_usuario(usuario)
    votaciones_abiertas = list(
        Votacion.objects.filter(consorcio__in=mis_consorcios, fecha_limite__gt=timezone.now())
        .select_related('consorcio').order_by('fecha_limite')
    )
    mis_votos = set(VotacionUser.objects.filter(usuario=usuario, votacion__in=votaciones_abiertas)
                    .values_list('votacion_id', flat=True))
    for v in votaciones_abiertas:
        v.ya_vote = v.pk in mis_votos

    return render(request, 'inicio/propietario.html', {
        'page_title': f'Hola, {usuario.name_surname.split()[0]}',
        'page_subtitle': 'Tu consorcio en un vistazo',
        'usuario': usuario,
        'unidades': unidades,
        'votaciones_abiertas': votaciones_abiertas,
        'sin_votar': sum(1 for v in votaciones_abiertas if not v.ya_vote and v.lanzada),
        'deudas': Deuda.objects.filter(consorcio__in=mis_consorcios, factura__isnull=True).select_related('tipo_deuda', 'consorcio')[:5],
        'facturas': Factura.objects.filter(consorcio__in=mis_consorcios).select_related('deuda__tipo_deuda')[:5],
        'eventos': _proximos_eventos(AgendaEvento.objects.filter(consorcio__in=mis_consorcios)),
        'reuniones': _proximas_reuniones(usuario),
    })


def asistentes(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _es_admin(usuario):
        return redirect('dashboard')

    if request.method == 'POST':
        _accion_asistentes(request, usuario)
        # Se vuelve a abrir el detalle del asistente que se estaba editando
        return redirect(f"{reverse('asistentes')}?abrir={request.POST.get('id_asistente', '')}")

    lista_asistentes = list(
        Asistente.objects
        .select_related('usuario', 'usuario__estado_cuenta')
        .filter(admin=usuario)
        .prefetch_related('consorcios_asignados__consorcio')
        .order_by('usuario__name_surname')
    )

    ahora = timezone.now()

    # Para cada asistente, traemos su historial (más reciente primero) y le
    # calculamos "hace cuántos días" pasó, así el filtro de período del
    # modal (Últimos 7/30 días) puede compararlo del lado del cliente.
    for asistente in lista_asistentes:
        eventos = list(
            asistente.usuario.historial_generado
            .select_related('motivo', 'usuario_involucrado')
            .prefetch_related('documentos')
            .order_by('-creado_en')[:15]
        )
        for evento in eventos:
            evento.dias_ago = (ahora - evento.creado_en).days
        asistente.historial_list = eventos
        # Tareas en proceso primero, después las completadas
        asistente.tareas_list = sorted(asistente.usuario.tareas.all(), key=lambda t: t.completada)
        asistente.tareas_en_proceso = sum(1 for t in asistente.tareas_list if not t.completada)

    total_asistentes = len(lista_asistentes)
    asistentes_activos = sum(1 for a in lista_asistentes if a.habilitado)
    consorcios_delegados = (
        Consorcio.objects
        .filter(asistentes_asignados__asistente__admin=usuario)
        .distinct()
        .count()
    )

    return render(request, 'admin/asistentes.html', {
        'page_title': 'Asistentes',
        'page_subtitle': 'Gestioná las cuentas de tu equipo y sus permisos',
        'usuario': usuario,
        'lista_asistentes': lista_asistentes,
        'total_asistentes': total_asistentes,
        'asistentes_activos': asistentes_activos,
        'consorcios_delegados': consorcios_delegados,
        'abrir': request.GET.get('abrir', ''),
        'mis_consorcios': Consorcio.objects.filter(representante=usuario).order_by('name_consorcio'),
    })


def _accion_asistentes(request, usuario):
    """Acciones del administrador sobre su equipo (como en el perfil de asistente de la app mobile)."""
    asistente = Asistente.objects.filter(admin=usuario, pk=request.POST.get('id_asistente')).first()
    if asistente is None:
        return
    accion = request.POST.get('accion')

    if accion == 'habilitar_asistente':
        asistente.habilitado = not asistente.habilitado
        asistente.save(update_fields=['habilitado'])

    # Tocando el edificio se habilita o deshabilita al asistente en ese consorcio
    elif accion == 'habilitar_consorcio':
        asignacion = asistente.consorcios_asignados.filter(pk=request.POST.get('id_asignacion')).first()
        if asignacion:
            asignacion.habilitado = not asignacion.habilitado
            asignacion.save(update_fields=['habilitado'])

    elif accion == 'asignar_consorcio':
        consorcio = Consorcio.objects.filter(representante=usuario, pk=request.POST.get('id_consorcio')).first()
        if consorcio:
            AsistenteConsorcio.objects.get_or_create(asistente=asistente, consorcio=consorcio)

    elif accion == 'nueva_tarea':
        descripcion = request.POST.get('descripcion', '').strip()
        if descripcion:
            TareaAsistente.objects.create(usuario=asistente.usuario, descripcion=descripcion)

    # Tocando la tarea se marca como completada (o vuelve a en proceso)
    elif accion == 'cambiar_tarea':
        tarea = asistente.usuario.tareas.filter(pk=request.POST.get('id_tarea')).first()
        if tarea:
            tarea.estado = TareaAsistente.EN_PROCESO if tarea.completada else TareaAsistente.COMPLETADO
            tarea.save(update_fields=['estado'])

    elif accion == 'borrar_tarea':
        asistente.usuario.tareas.filter(pk=request.POST.get('id_tarea')).delete()


# Archivos que se pueden mandar por chat: PDF, Word, Excel y fotos
EXTENSIONES_CHAT = {'.pdf', '.doc', '.docx', '.xls', '.xlsx', '.csv', '.jpg', '.jpeg', '.png', '.gif', '.webp'}
TAMANO_MAXIMO_CHAT = 15 * 1024 * 1024  # 15 MB


def _validar_adjunto_chat(archivo):
    """Devuelve el mensaje de error, o None si el archivo se puede enviar."""
    if os.path.splitext(archivo.name)[1].lower() not in EXTENSIONES_CHAT:
        return 'Solo se pueden enviar PDF, Word, Excel o fotos.'
    if archivo.size > TAMANO_MAXIMO_CHAT:
        return 'El archivo no puede pesar más de 15 MB.'
    return None


def chat(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Lo usan todos los roles (a los proveedores les llegan ahí las invitaciones a votaciones)

    # Enviar un mensaje (texto, archivo o ambos) a una conversación de la que el usuario es miembro
    if request.method == 'POST':
        chat_destino = Chat.objects.filter(pk=request.POST.get('chat_id'), miembros__usuario=usuario).first()
        texto = request.POST.get('mensaje', '').strip()
        adjunto = request.FILES.get('archivo')
        if chat_destino and adjunto:
            error = _validar_adjunto_chat(adjunto)
            if error:
                messages.error(request, error)
                adjunto = None
                texto = ''  # no se manda el texto solo: el usuario corrige el archivo y vuelve a enviar
        if chat_destino and (texto or adjunto):
            MensajeChat.objects.create(
                chat=chat_destino, usuario=usuario, mensaje=texto,
                # Cada conversación tiene su carpeta: docs/chats/chat_<id>/
                archivo=_guardar_archivo(adjunto, f'docs/chats/chat_{chat_destino.pk}') if adjunto else None,
                nombre_archivo=os.path.basename(adjunto.name)[:255] if adjunto else None,
            )
        return redirect(f"{reverse('chat')}?chat_id={request.POST.get('chat_id', '')}")

    # Todos los chats individuales/grupales donde participa este usuario,
    # con el último mensaje de cada uno para armar la lista de la izquierda.
    chats_usuario = (
        Chat.objects
        .filter(miembros__usuario=usuario)
        .prefetch_related('miembros__usuario', 'mensajes')
        .distinct()
    )

    # Mensajes sin leer por conversación (para el numerito de cada una en la lista)
    sin_leer_por_chat = dict(
        MensajeChat.sin_leer_para(usuario).values_list('chat_id').annotate(n=Count('pk')).order_by()
    )

    conversaciones = []
    for c in chats_usuario:
        otro_miembro = next(
            (m.usuario for m in c.miembros.all() if m.usuario_id != usuario.pk),
            None,
        )
        ultimo_mensaje = c.mensajes.order_by('-enviado_en').first()

        conversaciones.append({
            'chat': c,
            'otro_usuario': otro_miembro,
            'ultimo_mensaje': ultimo_mensaje,
            'sin_leer': sin_leer_por_chat.get(c.pk, 0),
        })

    # Más reciente primero. Las conversaciones sin mensajes (recién creadas, por ejemplo
    # desde un perfil del Marketplace) van arriba de todo, como si fueran de ahora.
    ahora = timezone.now()
    conversaciones.sort(
        key=lambda x: x['ultimo_mensaje'].enviado_en if x['ultimo_mensaje'] else ahora,
        reverse=True,
    )

    # ¿Qué conversación mostrar del lado derecho? La que venga por
    # ?chat_id=, o si no la más reciente de la lista.
    chat_id_param = request.GET.get('chat_id')
    chat_activo = None
    otro_usuario_activo = None
    mensajes_activos = []

    if conversaciones:
        seleccionada = None
        if chat_id_param:
            seleccionada = next(
                (conv for conv in conversaciones if str(conv['chat'].id_chat) == chat_id_param),
                None,
            )
        if seleccionada is None:
            seleccionada = conversaciones[0]

        chat_activo = seleccionada['chat']
        otro_usuario_activo = seleccionada['otro_usuario']
        # Abrir la conversación la marca como leída
        MiembroChat.objects.filter(chat=chat_activo, usuario=usuario).update(ultimo_leido=timezone.now())
        seleccionada['sin_leer'] = 0
        mensajes_activos = list(
            MensajeChat.objects.filter(chat=chat_activo)
            .select_related('usuario', 'invitacion__votacion__consorcio', 'invitacion_reunion__reunion__consorcio').order_by('enviado_en')
        )

    return render(request, 'chat.html', {
        'page_title': 'Chat',
        'page_subtitle': 'Comunicate con propietarios, proveedores y tu equipo',
        'usuario': usuario,
        'conversaciones': conversaciones,
        'chat_activo': chat_activo,
        'otro_usuario_activo': otro_usuario_activo,
        'mensajes_activos': mensajes_activos,
    })

def chat_estado(request):
    """Marca de los chats del usuario: cambia cuando llega o se borra un mensaje.
    La página del chat la consulta cada pocos segundos y, si cambió, se actualiza sola."""
    usuario = _get_logged_usuario(request)
    if usuario is None:
        return JsonResponse({'error': 'no autenticado'}, status=401)

    resumen = MensajeChat.objects.filter(chat__miembros__usuario=usuario).aggregate(
        ultimo=Max('pk'), cantidad=Count('pk'),
    )
    return JsonResponse({
        'version': f"{resumen['ultimo'] or 0}-{resumen['cantidad']}",
        'sin_leer': MensajeChat.sin_leer_para(usuario).count(),
    })


# Colores de las tarjetas del Marketplace cuando el proveedor no tiene fotos
TEMAS_MARKETPLACE = ['blue', 'amber', 'teal', 'rose', 'indigo', 'green', 'sky', 'orange', 'slate', 'cyan', 'red', 'lime']


def marketplace(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Lo usan todos los roles

    # Cuentas del Marketplace de usuarios activos, con su promedio de estrellas
    # y cantidad de reseñas (igual que la lista de proveedores de la app mobile)
    q = request.GET.get('q', '').strip()
    cuentas = (
        CuentaMarketplace.objects
        .select_related('dueno')
        .filter(Q(dueno__estado_cuenta__isnull=True) | Q(dueno__estado_cuenta__estado='Activa'))
        # Los administradores no se muestran entre los proveedores: su perfil es para los propietarios
        .exclude(dueno__rol__rol__in=ROLES_ADMIN)
        # Un proveedor no se ve a sí mismo en el listado (su perfil lo tiene en "Mi perfil del Marketplace")
        .exclude(dueno=usuario)
        .annotate(promedio=Avg('resenas__estrellas'), cant_resenas=Count('resenas', distinct=True))
        .prefetch_related('publicaciones')
        .order_by('dueno__name_surname')
    )
    if q:
        cuentas = cuentas.filter(Q(dueno__name_surname__icontains=q) | Q(descripcion__icontains=q))

    publicaciones = []
    for cuenta in cuentas:
        ultima = next(iter(cuenta.publicaciones.all()), None)  # ya vienen ordenadas, la más nueva primero
        publicaciones.append(dict(
            autor=cuenta.dueno.name_surname,
            autor_img=cuenta.dueno.img,
            etiqueta=(f"{len(cuenta.publicaciones.all())} publicaci{'ón' if len(cuenta.publicaciones.all()) == 1 else 'ones'}"
                      if cuenta.publicaciones.all() else None),
            img=ultima.img if ultima else None,
            iniciales=cuenta.dueno.iniciales,
            tema=TEMAS_MARKETPLACE[cuenta.dueno.id_user % len(TEMAS_MARKETPLACE)],
            tipo='album' if len(cuenta.publicaciones.all()) > 1 else 'foto',
            texto=cuenta.descripcion or '',
            stats=[
                ('bi-star-fill', _formato_estrellas(cuenta.promedio) if cuenta.promedio else 'Sin reseñas'),
                ('bi-chat-fill', cuenta.cant_resenas),
            ],
            url=reverse('proveedor_perfil', args=[cuenta.dueno.id_user]),
        ))

    return render(request, 'admin/marketplace.html', {
        'page_title': 'Marketplace',
        'page_subtitle': 'Encontrá proveedores para tus consorcios',
        'usuario': usuario,
        'buscador_placeholder': 'Buscar proveedores por nombre o servicio',
        'busqueda': q,
        'publicaciones': publicaciones,
    })


def _formato_estrellas(promedio):
    """4.666 -> "4,7" """
    return f"{promedio:.1f}".replace('.', ',')


# Edificios de ejemplo que buscan administrador (solo visual). Cuando esté la tabla de búsquedas
# de consorcios, se reemplaza por la consulta. stats: unidades y cantidad de postulaciones recibidas.
EDIFICIOS_BUSCAN_ADMINISTRADOR = [
    ('Edificio Mirador', 'Palermo, CABA', 'bi-building', 'blue', 'album',
     'Buscamos administración con experiencia en edificios con amenities', 64, 12),
    ('Torre Alvear', 'Recoleta, CABA', 'bi-buildings', 'indigo', 'foto',
     'Cambio de administración: priorizamos rendiciones claras y online', 120, 23),
    ('Complejo Los Álamos', 'Nordelta, Tigre', 'bi-houses', 'green', 'video',
     'Barrio cerrado de 3 torres busca administrador matriculado', 210, 31),
    ('Edificio Rivadavia 7800', 'Flores, CABA', 'bi-building', 'slate', 'foto',
     'Edificio antiguo, necesitamos ordenar deudas y mantenimiento', 28, 7),
    ('Residencias del Parque', 'Caballito, CABA', 'bi-tree', 'teal', 'album',
     'Consorcio nuevo, primera administración del edificio', 48, 15),
    ('Torres del Río', 'Rosario, Santa Fe', 'bi-water', 'sky', 'video',
     'Dos torres frente al río, buscamos atención presencial semanal', 156, 19),
    ('Edificio Cabildo', 'Belgrano, CABA', 'bi-building', 'amber', 'foto',
     'Buscamos administrador con buena gestión de proveedores', 72, 10),
    ('Altos de Nueva Córdoba', 'Córdoba Capital', 'bi-buildings', 'orange', 'album',
     'Edificio de estudiantes, alta rotación de inquilinos', 96, 14),
    ('Edificio Sarmiento', 'Centro, Mendoza', 'bi-building', 'rose', 'foto',
     'Asamblea aprobó buscar nueva administración este mes', 36, 5),
    ('Condominio La Plata', 'La Plata, Bs. As.', 'bi-houses', 'lime', 'video',
     'Condominio de casas con espacios comunes y pileta', 40, 9),
    ('Torre Libertador', 'Núñez, CABA', 'bi-buildings', 'cyan', 'album',
     'Torre con seguridad 24 h y cocheras, buscamos administración integral', 180, 27),
    ('Edificio San Telmo', 'San Telmo, CABA', 'bi-building', 'red', 'foto',
     'Edificio histórico, buscamos experiencia en obras de fachada', 22, 4),
]


def marketplace_consorcios(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _es_admin(usuario):
        return redirect('dashboard')

    # Edificios de ejemplo que buscan administrador (solo visual). Cuando
    # esté la tabla de búsquedas de consorcios, se reemplaza por la consulta.
    # stats: unidades del edificio y cantidad de postulaciones recibidas
    edificios = EDIFICIOS_BUSCAN_ADMINISTRADOR

    publicaciones = [
        dict(autor=nombre, etiqueta=zona, icono=icono, tema=tema, tipo=tipo, texto=texto,
             stats=[('bi-door-closed-fill', f'{unidades} u.'), ('bi-person-fill', postulaciones)])
        for nombre, zona, icono, tema, tipo, texto, unidades, postulaciones in edificios
    ]

    return render(request, 'admin/marketplace.html', {
        'page_title': 'Marketplace de consorcios',
        'page_subtitle': 'Edificios que están buscando administrador',
        'usuario': usuario,
        'buscador_placeholder': 'Buscar edificios por nombre, barrio o ciudad',
        'publicaciones': publicaciones,
    })


def _guardar_archivo(archivo, carpeta):
    """Guarda un archivo subido en MEDIA_ROOT/<carpeta>/ con un nombre único y devuelve su ruta.
    Las carpetas son las mismas que usa la app mobile (img/publicaciones, docs/facturas...)."""
    extension = os.path.splitext(archivo.name)[1].lower()
    return default_storage.save(f"{carpeta}/{uuid.uuid4().hex}{extension}", archivo)


def proveedor_perfil(request, id_user):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Lo usan todos los roles

    dueno = get_object_or_404(Usuario.objects.select_related('rol'), pk=id_user)
    cuenta = CuentaMarketplace.objects.filter(dueno=dueno).first()
    es_propio = dueno.pk == usuario.pk

    # El perfil de un administrador solo lo ven los propietarios de consorcios (y él mismo)
    if _es_admin(dueno) and not es_propio and usuario.rol.rol != 'Propietario/Residente':
        raise Http404('Perfil no disponible')

    if request.method == 'POST' and cuenta:
        accion = request.POST.get('accion')

        # Reseña: cada usuario deja una sola por cuenta (si ya tenía, se edita)
        if accion == 'resena' and not es_propio:
            try:
                estrellas = int(request.POST.get('estrellas', 0))
            except ValueError:
                estrellas = 0
            if 1 <= estrellas <= 5:
                Resena.objects.update_or_create(
                    cuenta=cuenta, autor=usuario,
                    defaults={'estrellas': estrellas,
                              'descripcion': request.POST.get('descripcion', '').strip() or None,
                              'creation': timezone.now()},
                )
                messages.success(request, 'Reseña guardada.')
            else:
                messages.error(request, 'Elegí de 1 a 5 estrellas.')

        # Publicaciones: solo el dueño puede subir y borrar fotos
        elif accion == 'publicar' and es_propio:
            foto = request.FILES.get('foto')
            if foto and (foto.content_type or '').startswith('image/'):
                ImgCuentaMarketplace.objects.create(
                    cuenta=cuenta,
                    img=_guardar_archivo(foto, 'img/publicaciones'),
                    descripcion=request.POST.get('descripcion', '').strip() or None,
                )
                messages.success(request, 'Foto publicada.')
            else:
                messages.error(request, 'Elegí una imagen para publicar.')

        elif accion == 'borrar_publicacion' and es_propio:
            ImgCuentaMarketplace.objects.filter(cuenta=cuenta, pk=request.POST.get('id_publicacion')).delete()
            messages.success(request, 'Foto eliminada.')

        return redirect('proveedor_perfil', id_user=dueno.pk)

    publicaciones = list(cuenta.publicaciones.all()) if cuenta else []
    resenas = list(cuenta.resenas.select_related('autor')) if cuenta else []
    promedio = sum(r.estrellas for r in resenas) / len(resenas) if resenas else None
    mi_resena = next((r for r in resenas if r.autor_id == usuario.pk), None)

    return render(request, 'admin/proveedor_perfil.html', {
        'page_title': dueno.name_surname,
        'page_subtitle': 'Perfil del Marketplace',
        'usuario': usuario,
        'dueno': dueno,
        'cuenta': cuenta,
        'es_propio': es_propio,
        'publicaciones': publicaciones,
        'resenas': resenas,
        'promedio': _formato_estrellas(promedio) if promedio else None,
        'mi_resena': mi_resena,
    })


def chat_con(request, id_user):
    """Abre (o crea) el chat individual con otro usuario, por ejemplo desde un perfil del Marketplace."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Lo usan todos los roles

    otro = get_object_or_404(Usuario, pk=id_user)
    if otro.pk == usuario.pk:
        return redirect('chat')

    return redirect(f"{reverse('chat')}?chat_id={_chat_entre(usuario, otro).id_chat}")


def _chat_entre(usuario, otro):
    """Chat individual entre dos usuarios (lo crea si todavía no existe)."""
    tipo_individual, _ = TipoChat.objects.get_or_create(name_chat='Individual')
    chat_existente = (
        Chat.objects
        .filter(tipo_chat=tipo_individual, miembros__usuario=usuario)
        .filter(miembros__usuario=otro)
        .first()
    )
    if chat_existente is None:
        chat_existente = Chat.objects.create(tipo_chat=tipo_individual)
        MiembroChat.objects.create(chat=chat_existente, usuario=usuario)
        MiembroChat.objects.create(chat=chat_existente, usuario=otro)
    return chat_existente


def _consorcios_del_usuario(usuario):
    """Consorcios que ve el usuario: los que representa (administrador), los que su
    administrador le habilitó (asistente) o en los que tiene una unidad (propietario)."""
    if hasattr(usuario, 'asistente'):
        return Consorcio.objects.filter(
            asistentes_asignados__asistente=usuario.asistente,
            asistentes_asignados__habilitado=True,
        )
    if usuario.rol.rol == PROPIETARIO:
        return Consorcio.objects.filter(propietarios__usuario=usuario)
    return Consorcio.objects.filter(representante=usuario)


def consorcios(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    # Cambiar la foto de un consorcio (solo el administrador)
    if request.method == 'POST' and _es_admin(usuario):
        consorcio = get_object_or_404(_consorcios_del_usuario(usuario), pk=request.POST.get('id_consorcio'))
        foto = request.FILES.get('foto')
        if foto and (foto.content_type or '').startswith('image/'):
            consorcio.img_consorcio = _guardar_archivo(foto, 'img/consorcios')
            consorcio.save(update_fields=['img_consorcio'])
            messages.success(request, f'Foto de {consorcio.name_consorcio} actualizada.')
        else:
            messages.error(request, 'Elegí una imagen.')
        return redirect('consorcios')

    q = request.GET.get('q', '').strip()
    lista = (
        _consorcios_del_usuario(usuario)
        .annotate(
            cant_facturas=Count('facturas', distinct=True),
            cant_pendientes=Count('deudas', filter=Q(deudas__factura__isnull=True), distinct=True),
        )
        .order_by('name_consorcio')
    )
    if q:
        lista = lista.filter(Q(name_consorcio__icontains=q) | Q(direccion__icontains=q))

    return render(request, 'admin/consorcios.html', {
        'puede_gestionar': _es_admin(usuario),
        'page_title': 'Consorcios' if usuario.rol.rol != PROPIETARIO else 'Mi consorcio',
        'page_subtitle': 'Tus edificios, sus facturas y deudas pendientes',
        'usuario': usuario,
        'consorcios': lista,
        'busqueda': q,
    })


def consorcio_facturas(request, id_consorcio):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    consorcio = get_object_or_404(_consorcios_del_usuario(usuario), pk=id_consorcio)
    pendientes = list(consorcio.deudas.filter(factura__isnull=True).select_related('tipo_deuda'))

    puede_cargar = _rol_permitido(usuario, ASISTENTE)

    # Cargar una factura: se asocia a una deuda pendiente del consorcio
    if request.method == 'POST' and puede_cargar:
        datos = request.POST
        errores = []
        deuda = next((d for d in pendientes if str(d.pk) == datos.get('id_deuda')), None)
        if deuda is None:
            errores.append('Elegí la deuda a la que corresponde la factura.')
        proveedor = datos.get('proveedor', '').strip()
        nro = datos.get('nro_comprobante', '').strip()
        if not proveedor or not nro:
            errores.append('Completá el proveedor y el número de comprobante.')
        try:
            fecha = datetime.strptime(datos.get('fecha', ''), '%Y-%m-%d').date()
        except ValueError:
            fecha = None
            errores.append('La fecha no es válida.')
        try:
            importe = Decimal(datos.get('importe', '').replace(',', '.'))
            if importe <= 0:
                raise InvalidOperation
        except InvalidOperation:
            importe = None
            errores.append('El importe tiene que ser un número mayor a cero.')

        if errores:
            for e in errores:
                messages.error(request, e)
        else:
            archivo = request.FILES.get('archivo')
            Factura.objects.create(
                consorcio=consorcio, deuda=deuda, usuario=usuario,
                proveedor=proveedor, nro_comprobante=nro, fecha=fecha, importe=importe,
                archivo=_guardar_archivo(archivo, 'docs/facturas') if archivo else None,
            )
            _registrar_historial(consorcio, HistorialConsorcio.PAGO,
                                 f'Se pagó "{deuda.descripcion}" a {proveedor} ({_moneda(importe)}).', usuario)
            if deuda.contrato_id:
                _actualizar_contrato(deuda.contrato, usuario)
            messages.success(request, 'Factura cargada.')
        return redirect('consorcio_facturas', id_consorcio=consorcio.pk)

    return render(request, 'admin/consorcio_facturas.html', {
        'page_title': consorcio.name_consorcio,
        'page_subtitle': consorcio.direccion,
        'usuario': usuario,
        'consorcio': consorcio,
        'facturas': consorcio.facturas.select_related('deuda__tipo_deuda'),
        'pendientes': pendientes,
        'hoy': timezone.localdate(),
        'puede_cargar': puede_cargar,
    })


def agenda(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    hoy = timezone.localdate()
    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')

    puede_agendar = _rol_permitido(usuario, ASISTENTE)

    if request.method == 'POST' and puede_agendar:
        if request.POST.get('accion') == 'eliminar':
            AgendaEvento.objects.filter(usuario=usuario, pk=request.POST.get('id_evento')).delete()
            messages.success(request, 'Evento eliminado.')
        else:
            titulo = request.POST.get('titulo', '').strip()
            try:
                fecha = datetime.strptime(request.POST.get('fecha', ''), '%Y-%m-%d').date()
            except ValueError:
                fecha = None
            try:
                hora = datetime.strptime(request.POST['hora'], '%H:%M').time() if request.POST.get('hora') else None
            except ValueError:
                hora = None
            consorcio = mis_consorcios.filter(pk=request.POST.get('id_consorcio') or None).first()

            # Solo fechas de hoy en adelante, como en la app
            if not titulo or fecha is None or fecha < hoy:
                messages.error(request, 'Completá el título y una fecha de hoy en adelante.')
            else:
                AgendaEvento.objects.create(
                    usuario=usuario, consorcio=consorcio, titulo=titulo,
                    descripcion=request.POST.get('descripcion', '').strip() or None,
                    fecha=fecha, hora=hora,
                )
                messages.success(request, 'Evento agendado.')
        return redirect('agenda')

    # Eventos próximos agrupados por día (ya vienen ordenados por fecha y hora)
    dias = []
    if _es_admin(usuario):
        eventos = AgendaEvento.objects.filter(usuario=usuario)
    elif usuario.rol.rol == PROPIETARIO:
        eventos = AgendaEvento.objects.filter(consorcio__in=mis_consorcios)
    else:
        eventos = AgendaEvento.objects.filter(Q(usuario=usuario) | Q(consorcio__in=mis_consorcios))
    for evento in eventos.filter(fecha__gte=hoy).select_related('consorcio').distinct():
        if not dias or dias[-1]['fecha'] != evento.fecha:
            dias.append({'fecha': evento.fecha, 'eventos': []})
        dias[-1]['eventos'].append(evento)

    return render(request, 'admin/agenda.html', {
        'page_title': 'Agenda',
        'page_subtitle': 'Tus próximos eventos y vencimientos',
        'usuario': usuario,
        'dias': dias,
        'hoy': hoy,
        'manana': hoy + timedelta(days=1),
        'mis_consorcios': mis_consorcios,
        'puede_agendar': puede_agendar,
    })


# Planes según el documento de la tesis (2.8), igual que en la app mobile.
# TODO: por ahora son datos armados en código; más adelante saldrán de tablas en la base.
PRECIO_ASISTENTE = Decimal('8000')
IVA = Decimal('0.21')
PRECIOS_ADMINISTRACION = {'Bronce': Decimal('25000'), 'Oro': Decimal('45000')}
PRECIOS_PROVEEDOR = {'Bronce': Decimal('15000'), 'Oro': Decimal('30000')}
MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio',
         'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']


def _suscripcion(usuario, pagada):
    """Lo que el usuario le paga a CONSORA este mes, o None si su rol no paga (propietarios, asistentes)."""
    if hasattr(usuario, 'administrador'):
        plan, precio, asistentes = 'Oro', PRECIOS_ADMINISTRACION['Oro'], usuario.asistentes_a_cargo.count()
    elif usuario.rol.rol == 'Proveedor':
        plan, precio, asistentes = 'Bronce', PRECIOS_PROVEEDOR['Bronce'], 0
    else:
        return None

    lineas = [{'concepto': f'Plan {plan}', 'detalle': 'Mensual', 'importe': precio}]
    if asistentes:
        lineas.append({'concepto': 'Asistentes adicionales', 'detalle': f'{asistentes} × $ 8.000',
                       'importe': asistentes * PRECIO_ASISTENTE})
    subtotal = sum(l['importe'] for l in lineas)
    lineas.append({'concepto': 'IVA', 'detalle': '21 %', 'importe': (subtotal * IVA).quantize(Decimal('0.01'))})

    hoy = timezone.localdate()
    # Vence el día 10 del mes siguiente
    vencimiento = (hoy.replace(day=1) + timedelta(days=32)).replace(day=10)
    return {
        'plan': plan,
        'lineas': lineas,
        'total': sum(l['importe'] for l in lineas),
        'vencimiento': vencimiento,
        'periodo': f'{MESES[hoy.month - 1]} {hoy.year}',
        'pagada': pagada,
    }


def _accion_cuenta(request, usuario):
    """Cambios que el usuario hace sobre su propio perfil desde la página Cuenta."""
    datos = request.POST
    accion = datos.get('accion')

    # Datos personales (y de la matrícula, si es administrador)
    if accion == 'perfil':
        errores = []
        nombre = datos.get('name_surname', '').strip()
        mail = datos.get('mail', '').strip().lower()
        dni = datos.get('dni', '').strip() or None
        if not nombre:
            errores.append('El nombre no puede quedar vacío.')
        try:
            validate_email(mail)
        except ValidationError:
            errores.append('El email no es válido.')
        if Usuario.objects.filter(mail__iexact=mail).exclude(pk=usuario.pk).exists():
            errores.append('Ese email ya lo usa otra cuenta.')
        if dni and Usuario.objects.filter(dni=dni).exclude(pk=usuario.pk).exists():
            errores.append('Ese DNI ya lo usa otra cuenta.')
        nacimiento = _fecha_o_none(datos.get('date_nacimiento'))
        if datos.get('date_nacimiento') and (nacimiento is None or nacimiento >= timezone.localdate()):
            errores.append('La fecha de nacimiento no es válida.')

        administrador = getattr(usuario, 'administrador', None)
        matricula = datos.get('matricula', '').strip()
        if administrador:
            if not matricula:
                errores.append('La matrícula no puede quedar vacía.')
            elif Administrador.objects.filter(matricula=matricula).exclude(pk=administrador.pk).exists():
                errores.append('Esa matrícula ya está registrada.')

        if errores:
            for e in errores:
                messages.error(request, e)
            return

        usuario.name_surname = nombre
        usuario.mail = mail
        usuario.dni = dni
        usuario.phone = datos.get('phone', '').strip() or None
        usuario.date_nacimiento = nacimiento
        usuario.save(update_fields=['name_surname', 'mail', 'dni', 'phone', 'date_nacimiento'])
        if administrador:
            administrador.matricula = matricula
            administrador.fecha_alta_matricula = _fecha_o_none(datos.get('fecha_alta_matricula'))
            administrador.ente_otorgante = datos.get('ente_otorgante', '').strip() or None
            administrador.save(update_fields=['matricula', 'fecha_alta_matricula', 'ente_otorgante'])
        messages.success(request, 'Tus datos se guardaron.')

    # Foto de perfil (se guarda en img/usuarios, como en la app)
    elif accion == 'foto':
        foto = request.FILES.get('foto')
        if foto and (foto.content_type or '').startswith('image/'):
            usuario.img = _guardar_archivo(foto, 'img/usuarios')
            usuario.save(update_fields=['img'])
            messages.success(request, 'Foto de perfil actualizada.')
        else:
            messages.error(request, 'Elegí una imagen.')

    elif accion == 'quitar_foto':
        usuario.img = None
        usuario.save(update_fields=['img'])
        messages.success(request, 'Quitaste tu foto de perfil.')

    elif accion == 'password':
        actual, nueva, repetida = datos.get('actual', ''), datos.get('nueva', ''), datos.get('repetida', '')
        if not usuario.check_password(actual):
            messages.error(request, 'La contraseña actual no es correcta.')
        elif len(nueva) < 6:
            messages.error(request, 'La contraseña nueva tiene que tener al menos 6 caracteres.')
        elif nueva != repetida:
            messages.error(request, 'Las contraseñas nuevas no coinciden.')
        else:
            usuario.set_password(nueva)
            usuario.save(update_fields=['password'])
            messages.success(request, 'Cambiaste tu contraseña.')

    # Título del administrador: PDF guardado en docs/titulos
    elif accion == 'subir_titulo':
        archivo = request.FILES.get('titulo')
        if archivo is None:
            messages.error(request, 'Elegí el PDF del título.')
        elif not archivo.name.lower().endswith('.pdf') or archivo.read(5) != b'%PDF-':
            messages.error(request, 'El título tiene que ser un archivo PDF.')
        elif archivo.size > TAMANO_MAXIMO_TITULO:
            messages.error(request, 'El PDF no puede pesar más de 10 MB.')
        else:
            archivo.seek(0)
            TituloAdministrador.objects.create(usuario=usuario, titulo=_guardar_archivo(archivo, 'docs/titulos'))
            messages.success(request, 'Título subido.')

    elif accion == 'borrar_titulo':
        titulo = usuario.titulos.filter(pk=datos.get('id_titulo')).first()
        if titulo:
            titulo.delete()
            # En Windows no se puede borrar un archivo abierto (por ejemplo, mientras se ve la vista previa):
            # en ese caso el título igual se elimina y el PDF queda huérfano en la carpeta.
            try:
                default_storage.delete(titulo.titulo)
            except OSError:
                pass
            messages.success(request, 'Título eliminado.')

    # Perfil del Marketplace: descripción y web (se crea si todavía no tenía)
    elif accion == 'marketplace':
        link = datos.get('link', '').strip() or None
        if link and not link.startswith(('http://', 'https://')):
            link = 'https://' + link
        if link:
            try:
                URLValidator()(link)
            except ValidationError:
                messages.error(request, 'El link no es una dirección web válida.')
                return
        CuentaMarketplace.objects.update_or_create(
            dueno=usuario,
            defaults={'descripcion': datos.get('descripcion', '').strip() or None, 'link': link},
        )
        messages.success(request, 'Tu perfil del Marketplace se guardó.')

    # Baja de la cuenta: no se borra (quedan sus facturas, chats...), pasa a estado "Baja"
    elif accion == 'baja':
        # No puede quedar un consorcio sin representante
        if usuario.consorcios_administrados.exists():
            messages.error(request, 'Para dar de baja tu cuenta tenés que desasociarte de los consorcios.')
            return
        if not usuario.check_password(datos.get('confirmar_password', '')):
            messages.error(request, 'La contraseña no es correcta; la cuenta no se dio de baja.')
            return
        usuario.estado_cuenta, _ = EstadoCuenta.objects.get_or_create(
            estado='Baja', defaults={'motivo': 'Solicitada por el usuario'},
        )
        usuario.save(update_fields=['estado_cuenta'])
        return 'baja'


TAMANO_MAXIMO_TITULO = 10 * 1024 * 1024  # 10 MB


def _fecha_o_none(texto):
    try:
        return datetime.strptime(texto or '', '%Y-%m-%d').date()
    except ValueError:
        return None


def cuenta(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # Lo usan todos los roles

    # Pago simulado (Mercado Pago o MODO): queda guardado solo en la sesión, para la demo
    periodo_actual = timezone.localdate().strftime('%Y-%m')
    if request.method == 'POST' and request.POST.get('medio') in ('Mercado Pago', 'MODO'):
        request.session['pago_suscripcion'] = periodo_actual
        messages.success(request, f"Pago realizado con {request.POST['medio']}.")
        return redirect('cuenta')

    # Edición del perfil (datos, foto, contraseña, Marketplace, baja)
    if request.method == 'POST':
        if _accion_cuenta(request, usuario) == 'baja':
            request.session.flush()
            return redirect('login')
        return redirect('cuenta')

    return render(request, 'admin/cuenta.html', {
        'page_title': 'Cuenta',
        'page_subtitle': 'Tus datos y la facturación de CONSORA',
        'usuario': usuario,
        'suscripcion': _suscripcion(usuario, request.session.get('pago_suscripcion') == periodo_actual),
        'consorcios_a_cargo': usuario.consorcios_administrados.order_by('name_consorcio'),
        'google_cuenta': GoogleCuenta.objects.filter(usuario=usuario).first(),
        'google_disponible': google_meet.disponible(),
        'titulos': list(usuario.titulos.all()),
    })


NINGUNO = 'ninguno'  # voto de contratación: no contratar a ninguno de los proveedores


def _guardar_doc_votacion(votacion, archivo):
    """Guarda un documento en docs/votaciones/votacion_<id>/ como "<código>_<nombre original>"
    (el código evita pisar archivos con el mismo nombre) y lo registra en doc_votacion."""
    nombre = get_valid_filename(os.path.basename(archivo.name))[:150]
    ruta = default_storage.save(f'docs/votaciones/votacion_{votacion.pk}/{uuid.uuid4().hex[:8]}_{nombre}', archivo)
    return DocVotacion.objects.create(votacion=votacion, doc=ruta)


def _invitar_proveedor(votacion, proveedor, admin):
    """Invita a un proveedor a una votación de contratación: le llega por chat y responde ahí."""
    invitacion, creada = VotacionContrato.objects.get_or_create(votacion=votacion, proveedor=proveedor)
    if creada:
        MensajeChat.objects.create(
            chat=_chat_entre(admin, proveedor), usuario=admin, invitacion=invitacion,
            mensaje=(f'Te invitamos a participar en una votación de contratación de '
                     f'{votacion.consorcio.name_consorcio}: {votacion.detalle}'),
        )
    return invitacion


def _votantes(votacion):
    """Quiénes votan: los propietarios del consorcio y su administrador."""
    ids = set(votacion.consorcio.propietarios.values_list('usuario_id', flat=True))
    ids.add(votacion.consorcio.representante_id)
    return ids


def _resultados(votacion):
    """Opciones con su cantidad de votos y porcentaje, y la participación."""
    votos = list(votacion.votos.all())
    if votacion.tipo == Votacion.CONTRATACION:
        opciones = [(str(p.pk), p.proveedor.name_surname, p)
                    for p in votacion.propuestas.filter(aceptacion=True).select_related('proveedor')]
        opciones.append((NINGUNO, 'No contratar a ninguno', None))
    else:
        opciones = [(valor, texto, None) for valor, texto in VotacionUser.OPCIONES_COMUN]

    total = len(votos)
    resultados = []
    for valor, texto, propuesta in opciones:
        cantidad = sum(1 for v in votos if v.voto == valor)
        resultados.append({
            'valor': valor, 'texto': texto, 'propuesta': propuesta, 'cantidad': cantidad,
            'porcentaje': round(cantidad * 100 / total) if total else 0,
        })
    maximo = max((r['cantidad'] for r in resultados), default=0)
    for r in resultados:
        r['gana'] = maximo > 0 and r['cantidad'] == maximo

    habilitados = len(_votantes(votacion))
    return resultados, {
        'votos': total, 'habilitados': habilitados,
        'porcentaje': round(total * 100 / habilitados) if habilitados else 0,
    }


def votaciones(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')

    puede_gestionar = _es_admin(usuario)

    # Nueva votación (solo el administrador)
    if request.method == 'POST' and puede_gestionar:
        consorcio = mis_consorcios.filter(pk=request.POST.get('id_consorcio') or None).first()
        tipo = request.POST.get('tipo')
        detalle = request.POST.get('detalle', '').strip()
        try:
            limite = timezone.make_aware(datetime.strptime(request.POST.get('fecha_limite', ''), '%Y-%m-%dT%H:%M'))
        except ValueError:
            limite = None

        proveedores = list(Usuario.objects.filter(pk__in=request.POST.getlist('proveedores'), rol__rol='Proveedor'))
        documentos = request.FILES.getlist('docs')
        error_docs = next((e for e in map(_validar_adjunto_chat, documentos) if e), None)

        if consorcio is None or tipo not in dict(Votacion.TIPOS) or not detalle:
            messages.error(request, 'Elegí el consorcio, el tipo y escribí el detalle de la votación.')
        elif limite is None or limite <= timezone.now():
            messages.error(request, 'La fecha límite tiene que ser posterior a ahora.')
        elif tipo == Votacion.CONTRATACION and not proveedores:
            messages.error(request, 'Elegí al menos un proveedor para invitar a la contratación.')
        elif error_docs:
            messages.error(request, f'Documentos: {error_docs}')
        else:
            votacion = Votacion.objects.create(consorcio=consorcio, tipo=tipo, detalle=detalle, fecha_limite=limite)
            for doc in documentos:
                _guardar_doc_votacion(votacion, doc)
            if tipo == Votacion.CONTRATACION:
                for proveedor in proveedores:
                    _invitar_proveedor(votacion, proveedor, usuario)
                messages.success(request, f'Votación creada. Le mandamos la invitación por chat a '
                                          f'{len(proveedores)} proveedor{"es" if len(proveedores) > 1 else ""}; '
                                          f'se lanza cuando alguno acepte.')
            else:
                messages.success(request, 'Votación creada.')
            return redirect('votacion_detalle', id_votacion=votacion.pk)
        return redirect(request.get_full_path())

    # Filtros: por consorcio y por estado (abiertas / cerradas)
    filtro_consorcio = request.GET.get('consorcio', '')
    filtro_estado = request.GET.get('estado', '')
    lista = (
        Votacion.objects.filter(consorcio__in=mis_consorcios)
        .select_related('consorcio')
        .annotate(cant_votos=Count('votos', distinct=True), cant_propuestas=Count('propuestas', distinct=True))
    )
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    ahora = timezone.now()
    if filtro_estado == 'abiertas':
        lista = lista.filter(fecha_limite__gt=ahora)
    elif filtro_estado == 'cerradas':
        lista = lista.filter(fecha_limite__lte=ahora)

    lista = list(lista)
    mis_votos = dict(VotacionUser.objects.filter(usuario=usuario, votacion__in=lista).values_list('votacion_id', 'voto'))
    for v in lista:
        v.ya_vote = v.pk in mis_votos

    return render(request, 'admin/votaciones.html', {
        'page_title': 'Votaciones',
        'page_subtitle': 'Decisiones de los consorcios: votaciones comunes y contrataciones',
        'usuario': usuario,
        'votaciones': lista,
        'puede_gestionar': puede_gestionar,
        'mis_consorcios': mis_consorcios,
        'filtro_consorcio': filtro_consorcio,
        'filtro_estado': filtro_estado,
        'consorcio_filtrado': mis_consorcios.filter(pk=filtro_consorcio).first() if filtro_consorcio else None,
        'proveedores': Usuario.objects.filter(rol__rol='Proveedor').exclude(estado_cuenta__estado='Baja').order_by('name_surname'),
        'manana': (timezone.localtime() + timedelta(days=7)).strftime('%Y-%m-%dT%H:%M'),
        'ahora_min': timezone.localtime().strftime('%Y-%m-%dT%H:%M'),
    })


def votacion_detalle(request, id_votacion):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    votacion = get_object_or_404(
        Votacion.objects.select_related('consorcio').filter(consorcio__in=_consorcios_del_usuario(usuario)),
        pk=id_votacion,
    )

    gestiona = votacion.consorcio.representante_id == usuario.pk

    if request.method == 'POST':
        accion = request.POST.get('accion')
        if accion != 'votar' and not gestiona:
            messages.error(request, 'Solo el administrador del consorcio puede hacer eso.')
            return redirect('votacion_detalle', id_votacion=votacion.pk)

        # Votar (o cambiar el voto) mientras la votación está abierta
        if accion == 'votar':
            voto = request.POST.get('voto', '')
            if votacion.tipo == Votacion.CONTRATACION:
                validos = {str(pk) for pk in votacion.propuestas.filter(aceptacion=True).values_list('pk', flat=True)}
                validos.add(NINGUNO)
            else:
                validos = {valor for valor, _ in VotacionUser.OPCIONES_COMUN}
            if not votacion.abierta:
                messages.error(request, 'La votación ya cerró.')
            elif not votacion.lanzada:
                messages.error(request, 'La votación se lanza cuando algún proveedor acepte participar.')
            elif usuario.pk not in _votantes(votacion):
                messages.error(request, 'Solo votan los propietarios del consorcio y su administrador.')
            elif voto not in validos:
                messages.error(request, 'Elegí una opción.')
            else:
                VotacionUser.objects.update_or_create(
                    votacion=votacion, usuario=usuario, defaults={'voto': voto, 'votado_en': timezone.now()},
                )
                messages.success(request, 'Tu voto se registró.')

        # Invitar a otro proveedor (le llega por chat)
        elif accion == 'invitar' and votacion.tipo == Votacion.CONTRATACION:
            proveedor = Usuario.objects.filter(pk=request.POST.get('id_proveedor'), rol__rol='Proveedor').first()
            if not votacion.abierta:
                messages.error(request, 'La votación ya cerró.')
            elif proveedor is None:
                messages.error(request, 'Elegí el proveedor.')
            elif votacion.propuestas.filter(proveedor=proveedor).exists():
                messages.error(request, 'Ese proveedor ya está invitado.')
            else:
                _invitar_proveedor(votacion, proveedor, usuario)
                messages.success(request, f'Invitación enviada por chat a {proveedor.name_surname}.')

        # Cancelar una invitación que todavía no respondió
        elif accion == 'cancelar_invitacion':
            votacion.propuestas.filter(pk=request.POST.get('id_propuesta'), aceptacion__isnull=True).delete()
            messages.success(request, 'Invitación cancelada.')

        # Cuando cierra, el administrador marca a qué proveedor se contrata
        elif accion == 'contratar' and not votacion.abierta:
            propuesta = votacion.propuestas.filter(pk=request.POST.get('id_propuesta'), aceptacion=True).first()
            if propuesta:
                votacion.propuestas.update(contratado=False)
                propuesta.contratado = True
                propuesta.save(update_fields=['contratado'])
                _registrar_historial(votacion.consorcio, HistorialConsorcio.VOTACION,
                                     f'Por votación se eligió a {propuesta.proveedor.name_surname}: {votacion.detalle[:150]}', usuario)
                messages.success(request, f'Se contrató a {propuesta.proveedor.name_surname}. Cargá el precio y la forma de pago.')
                if not hasattr(propuesta, 'contrato'):
                    return redirect(f"{reverse('contratos')}?propuesta={propuesta.pk}")

        # Documentos de la votación (acta, notas...)
        elif accion == 'subir_doc':
            docs = request.FILES.getlist('doc')
            if not docs or any(_validar_adjunto_chat(d) for d in docs):
                messages.error(request, 'Subí PDF, Word, Excel o fotos de hasta 15 MB.')
            else:
                for d in docs:
                    _guardar_doc_votacion(votacion, d)
                messages.success(request, 'Documento subido.' if len(docs) == 1 else f'{len(docs)} documentos subidos.')

        elif accion == 'borrar_doc':
            doc = votacion.documentos.filter(pk=request.POST.get('id_doc')).first()
            if doc:
                doc.delete()
                try:
                    default_storage.delete(doc.doc)
                except OSError:
                    pass  # archivo abierto en Windows: el registro igual se borra
                messages.success(request, 'Documento eliminado.')

        elif accion == 'cerrar' and votacion.abierta:
            votacion.fecha_limite = timezone.now()
            votacion.save(update_fields=['fecha_limite'])
            _registrar_historial(votacion.consorcio, HistorialConsorcio.VOTACION,
                                 f'Se cerró la votación: {votacion.detalle[:180]}', usuario)
            messages.success(request, 'Votación cerrada.')

        elif accion == 'eliminar':
            consorcio_id = votacion.consorcio_id
            votacion.delete()
            messages.success(request, 'Votación eliminada.')
            return redirect(f"{reverse('votaciones')}?consorcio={consorcio_id}")

        return redirect('votacion_detalle', id_votacion=votacion.pk)

    resultados, participacion = _resultados(votacion)
    mi_voto = votacion.votos.filter(usuario=usuario).first()
    ids_invitados = votacion.propuestas.values_list('proveedor_id', flat=True)

    return render(request, 'admin/votacion_detalle.html', {
        'page_title': f'Votación · {votacion.consorcio.name_consorcio}',
        'page_subtitle': votacion.get_tipo_display(),
        'usuario': usuario,
        'votacion': votacion,
        'resultados': resultados,
        'participacion': participacion,
        'mi_voto': mi_voto.voto if mi_voto else None,
        'puedo_votar': usuario.pk in _votantes(votacion),
        'votos': votacion.votos.select_related('usuario').order_by('-votado_en'),
        'documentos': votacion.documentos.all(),
        'invitaciones': votacion.propuestas.select_related('proveedor').order_by('proveedor__name_surname'),
        'proveedores': Usuario.objects.filter(rol__rol='Proveedor').exclude(pk__in=ids_invitados).order_by('name_surname'),
        'contratado': votacion.propuestas.filter(contratado=True).select_related('proveedor').first(),
        'contrato': ContratoProveedor.objects.filter(propuesta__votacion=votacion).first(),
        'gestiona': gestiona,
    })


def responder_invitacion(request, id_invitacion):
    """El proveedor acepta o rechaza, desde el chat, participar en una votación de contratación.
    Al aceptar puede adjuntar su propuesta en PDF; la respuesta queda como mensaje en el chat."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    invitacion = get_object_or_404(
        VotacionContrato.objects.select_related('votacion__consorcio'), pk=id_invitacion, proveedor=usuario,
    )
    mensaje = invitacion.mensajes.select_related('chat').first()
    volver = f"{reverse('chat')}?chat_id={mensaje.chat_id}" if mensaje else reverse('chat')

    if request.method != 'POST':
        return redirect(volver)
    if invitacion.aceptacion is not None:
        messages.error(request, 'Ya respondiste esta invitación.')
        return redirect(volver)
    if not invitacion.votacion.abierta:
        messages.error(request, 'La votación ya cerró.')
        return redirect(volver)

    if request.POST.get('respuesta') == 'aceptar':
        pdf = request.FILES.get('propuesta_pdf')
        if pdf and (not pdf.name.lower().endswith('.pdf') or pdf.read(5) != b'%PDF-'):
            messages.error(request, 'La propuesta tiene que ser un PDF.')
            return redirect(volver)
        if pdf:
            pdf.seek(0)
            invitacion.propuesta_pdf = _guardar_archivo(pdf, f'docs/votaciones/votacion_{invitacion.votacion_id}')
        invitacion.aceptacion = True
        invitacion.save(update_fields=['aceptacion', 'propuesta_pdf'])
        texto = 'Acepto participar en la votación.' + (' Te adjunto mi propuesta.' if pdf else '')
    else:
        invitacion.aceptacion = False
        invitacion.save(update_fields=['aceptacion'])
        texto = 'Gracias por la invitación, pero esta vez no voy a participar.'

    if mensaje:
        MensajeChat.objects.create(
            chat=mensaje.chat, usuario=usuario, mensaje=texto,
            archivo=invitacion.propuesta_pdf if invitacion.aceptacion and invitacion.propuesta_pdf else None,
            nombre_archivo='Propuesta.pdf' if invitacion.aceptacion and invitacion.propuesta_pdf else None,
        )
    return redirect(volver)


# ---------------------------------------------------------------------------
# Reuniones del consorcio (virtuales con Google Meet o presenciales)
# ---------------------------------------------------------------------------

DURACIONES_REUNION = [(30, '30 minutos'), (60, '1 hora'), (90, '1 hora y media'), (120, '2 horas'), (180, '3 horas')]


def _reuniones_visibles(usuario):
    """Reuniones de los consorcios que ve el usuario, más aquellas en las que participa.
    El proveedor solo ve las reuniones a las que lo invitaron (y no rechazó)."""
    mias = ReunionParticipante.objects.filter(usuario=usuario).exclude(
        respuesta=ReunionParticipante.RECHAZA, rol_participante=ReunionParticipante.PROVEEDOR,
    ).values('reunion_id')
    return Reunion.objects.filter(Q(consorcio__in=_consorcios_del_usuario(usuario)) | Q(pk__in=mias)).distinct()


def _proximas_reuniones(usuario, cantidad=4):
    """Próximas reuniones (o en curso) que ve el usuario, para los inicios."""
    return list(
        _reuniones_visibles(usuario).exclude(estado=Reunion.CANCELADA)
        .filter(fecha_fin__gt=timezone.now()).select_related('consorcio')[:cantidad]
    )


def _gestiona_reunion(reunion, usuario):
    return reunion.organizador_id == usuario.pk or reunion.consorcio.representante_id == usuario.pk


def _guardar_doc_reunion(reunion, archivo, tipo):
    """Guarda el documento en docs/reuniones/reunion_<id>/ como "<código>_<nombre original>"."""
    nombre = get_valid_filename(os.path.basename(archivo.name))[:150]
    ruta = default_storage.save(f'docs/reuniones/reunion_{reunion.pk}/{uuid.uuid4().hex[:8]}_{nombre}', archivo)
    tipo = tipo if tipo in dict(DocReunion.TIPOS) else DocReunion.OTRO
    return DocReunion.objects.create(reunion=reunion, doc=ruta, tipo=tipo)


def _agregar_participantes(reunion):
    """El organizador (confirmado), los propietarios del consorcio y sus asistentes habilitados."""
    ahora = timezone.now()
    ReunionParticipante.objects.get_or_create(
        reunion=reunion, usuario=reunion.organizador,
        defaults={'rol_participante': ReunionParticipante.ADMINISTRADOR,
                  'respuesta': ReunionParticipante.ACEPTA, 'respondido_en': ahora},
    )
    for pc in reunion.consorcio.propietarios.select_related('usuario'):
        ReunionParticipante.objects.get_or_create(
            reunion=reunion, usuario=pc.usuario,
            defaults={'rol_participante': ReunionParticipante.PROPIETARIO, 'invitado_por': reunion.organizador},
        )
    asignaciones = reunion.consorcio.asistentes_asignados.filter(habilitado=True, asistente__habilitado=True)
    for asignacion in asignaciones.select_related('asistente__usuario'):
        ReunionParticipante.objects.get_or_create(
            reunion=reunion, usuario=asignacion.asistente.usuario,
            defaults={'rol_participante': ReunionParticipante.ASISTENTE, 'invitado_por': reunion.organizador},
        )


def _invitar_proveedor_reunion(reunion, proveedor, admin):
    """Invita a un proveedor como participante opcional: le llega por chat y solo participa si acepta."""
    participante, creado = ReunionParticipante.objects.get_or_create(
        reunion=reunion, usuario=proveedor,
        defaults={'rol_participante': ReunionParticipante.PROVEEDOR, 'es_opcional': True, 'invitado_por': admin},
    )
    if creado:
        inicio = timezone.localtime(reunion.fecha_inicio)
        MensajeChat.objects.create(
            chat=_chat_entre(admin, proveedor), usuario=admin, invitacion_reunion=participante,
            mensaje=(f'Te invitamos a la reunión "{reunion.titulo}" de {reunion.consorcio.name_consorcio}, '
                     f'el {inicio:%d/%m} a las {inicio:%H:%M}.'),
        )
    return participante, creado


def _crear_meet_google(request, reunion):
    """Si el organizador vinculó su cuenta de Google, crea el evento en su calendario con el link de Meet.
    Si no se puede, la reunión queda creada igual y el link se carga a mano."""
    if not reunion.es_virtual or reunion.google_event_id:
        return False
    if not google_meet.disponible() or not GoogleCuenta.objects.filter(usuario=reunion.organizador).exists():
        return False
    emails = [p.usuario.mail for p in reunion.participantes.select_related('usuario')
              if not p.es_opcional and p.usuario_id != reunion.organizador_id]
    try:
        link, codigo, evento_id = google_meet.crear_evento(reunion, emails)
    except google_meet.GoogleError as e:
        messages.warning(request, f'No se pudo crear la reunión en Google Calendar ({e}). Podés cargar el link de Meet a mano.')
        return False
    reunion.meet_link, reunion.meet_codigo, reunion.google_event_id = link, codigo, evento_id
    reunion.save(update_fields=['meet_link', 'meet_codigo', 'google_event_id'])
    return True


def _responder_reunion(participante, acepta):
    """Guarda la respuesta. Si un proveedor acepta, se lo suma al evento de Google Calendar
    y la respuesta queda como mensaje en el chat donde le llegó la invitación."""
    participante.respuesta = ReunionParticipante.ACEPTA if acepta else ReunionParticipante.RECHAZA
    participante.respondido_en = timezone.now()
    participante.save(update_fields=['respuesta', 'respondido_en'])

    reunion = participante.reunion
    if acepta and participante.es_opcional and reunion.google_event_id and google_meet.disponible():
        try:
            google_meet.agregar_invitado(reunion, participante.usuario.mail)
        except google_meet.GoogleError:
            logger.warning('No se pudo sumar al proveedor al evento de Google', exc_info=True)

    mensaje = participante.mensajes.select_related('chat').first()
    if mensaje:
        MensajeChat.objects.create(
            chat=mensaje.chat, usuario=participante.usuario,
            mensaje='Acepto la invitación, ahí estaré.' if acepta else 'Gracias por la invitación, pero no voy a poder participar.',
        )


def _cancelar_meet_google(request, reunion):
    if reunion.google_event_id and google_meet.disponible():
        try:
            google_meet.cancelar_evento(reunion)
        except google_meet.GoogleError as e:
            messages.warning(request, f'La reunión se canceló en CONSORA, pero no en Google Calendar ({e}).')


def _parsear_inicio(texto):
    try:
        return timezone.make_aware(datetime.strptime(texto or '', '%Y-%m-%dT%H:%M'))
    except ValueError:
        return None


def _link_valido(link):
    if not link:
        return None
    if not link.startswith(('http://', 'https://')):
        link = 'https://' + link
    try:
        URLValidator(schemes=['https'])(link)
    except ValidationError:
        return False
    return link


def reuniones(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    # La usan todos los roles; solo el administrador crea reuniones
    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')
    puede_gestionar = _es_admin(usuario)

    if request.method == 'POST' and puede_gestionar:
        datos = request.POST
        consorcio = mis_consorcios.filter(pk=datos.get('id_consorcio') or None).first()
        titulo = datos.get('titulo', '').strip()
        modalidad = datos.get('modalidad')
        inicio = _parsear_inicio(datos.get('fecha_inicio'))
        try:
            duracion = int(datos.get('duracion', 60))
        except ValueError:
            duracion = 0
        link = _link_valido(datos.get('meet_link', '').strip())
        lugar = datos.get('lugar', '').strip() or None
        proveedores = []
        if datos.get('invitar_proveedor'):
            proveedores = list(Usuario.objects.filter(pk__in=datos.getlist('proveedores'), rol__rol=PROVEEDOR))
        documentos = request.FILES.getlist('docs')
        error_docs = next((e for e in map(_validar_adjunto_chat, documentos) if e), None)

        if consorcio is None or not titulo or modalidad not in dict(Reunion.MODALIDADES):
            messages.error(request, 'Elegí el consorcio, la modalidad y escribí el título de la reunión.')
        elif inicio is None or inicio <= timezone.now():
            messages.error(request, 'La reunión tiene que ser en una fecha y hora posterior a ahora.')
        elif duracion not in dict(DURACIONES_REUNION):
            messages.error(request, 'Elegí la duración.')
        elif link is False:
            messages.error(request, 'El link de la reunión no es válido (tiene que empezar con https://).')
        elif modalidad == Reunion.PRESENCIAL and not lugar:
            messages.error(request, 'Indicá el lugar de la reunión presencial.')
        elif datos.get('invitar_proveedor') and not proveedores:
            messages.error(request, 'Elegí el proveedor a invitar, o destildá "Invitar proveedor".')
        elif error_docs:
            messages.error(request, f'Documentos: {error_docs}')
        else:
            local = timezone.localtime(inicio)
            evento = AgendaEvento.objects.create(
                usuario=usuario, consorcio=consorcio, titulo=f'Reunión: {titulo}'[:150],
                descripcion=datos.get('descripcion', '').strip() or None, fecha=local.date(), hora=local.time(),
            )
            reunion = Reunion.objects.create(
                consorcio=consorcio, organizador=usuario, evento=evento, titulo=titulo,
                descripcion=datos.get('descripcion', '').strip() or None, modalidad=modalidad,
                lugar=lugar if modalidad == Reunion.PRESENCIAL else None,
                fecha_inicio=inicio, fecha_fin=inicio + timedelta(minutes=duracion),
                meet_link=link if modalidad == Reunion.VIRTUAL else None,
            )
            _agregar_participantes(reunion)
            _registrar_historial(consorcio, HistorialConsorcio.REUNION,
                                 f'Se programó la reunión "{titulo}" para el {timezone.localtime(inicio):%d/%m/%Y %H:%M}.', usuario)
            for doc in documentos:
                _guardar_doc_reunion(reunion, doc, DocReunion.ORDEN_DEL_DIA)
            con_google = False if link else _crear_meet_google(request, reunion)
            for proveedor in proveedores:
                _invitar_proveedor_reunion(reunion, proveedor, usuario)

            texto = 'Reunión creada'
            if con_google:
                texto += ' con su link de Google Meet'
            if proveedores:
                texto += (f'. Le mandamos la invitación por chat a {len(proveedores)} '
                          f'proveedor{"es" if len(proveedores) > 1 else ""}: participa si la acepta')
            messages.success(request, texto + '.')
            return redirect('reunion_detalle', id_reunion=reunion.pk)
        return redirect(request.get_full_path())

    # Filtros: por consorcio y por próximas / pasadas
    filtro_consorcio = request.GET.get('consorcio', '')
    filtro_estado = request.GET.get('estado', 'proximas')
    lista = _reuniones_visibles(usuario).select_related('consorcio').annotate(
        cant_confirmados=Count('participantes', filter=Q(participantes__respuesta=ReunionParticipante.ACEPTA), distinct=True),
    )
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    ahora = timezone.now()
    if filtro_estado == 'pasadas':
        lista = lista.filter(Q(fecha_fin__lte=ahora) | Q(estado=Reunion.CANCELADA)).order_by('-fecha_inicio')
    else:
        lista = lista.filter(fecha_fin__gt=ahora).exclude(estado=Reunion.CANCELADA).order_by('fecha_inicio')

    lista = list(lista)
    mis_respuestas = dict(ReunionParticipante.objects.filter(usuario=usuario, reunion__in=lista)
                          .values_list('reunion_id', 'respuesta'))
    for r in lista:
        r.mi_respuesta = mis_respuestas.get(r.pk)

    vinculada = GoogleCuenta.objects.filter(usuario=usuario).first() if puede_gestionar else None
    inicio_sugerido = (timezone.localtime() + timedelta(days=3)).replace(hour=19, minute=0, second=0, microsecond=0)

    return render(request, 'admin/reuniones.html', {
        'page_title': 'Reuniones',
        'page_subtitle': 'Reuniones de los consorcios, virtuales con Google Meet o presenciales',
        'usuario': usuario,
        'reuniones': lista,
        'puede_gestionar': puede_gestionar,
        'mis_consorcios': mis_consorcios,
        'filtro_consorcio': filtro_consorcio,
        'filtro_estado': filtro_estado,
        'consorcio_filtrado': mis_consorcios.filter(pk=filtro_consorcio).first() if filtro_consorcio else None,
        'proveedores': Usuario.objects.filter(rol__rol=PROVEEDOR).exclude(estado_cuenta__estado='Baja').order_by('name_surname'),
        'duraciones': DURACIONES_REUNION,
        'inicio_sugerido': inicio_sugerido.strftime('%Y-%m-%dT%H:%M'),
        'ahora_min': timezone.localtime().strftime('%Y-%m-%dT%H:%M'),
        'google_disponible': google_meet.disponible(),
        'google_vinculada': vinculada,
    })


def reunion_detalle(request, id_reunion):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    reunion = get_object_or_404(
        _reuniones_visibles(usuario).select_related('consorcio', 'organizador'), pk=id_reunion,
    )
    gestiona = _gestiona_reunion(reunion, usuario)
    mi_participacion = reunion.participantes.filter(usuario=usuario).first()
    volver = redirect('reunion_detalle', id_reunion=reunion.pk)

    if request.method == 'POST':
        accion = request.POST.get('accion')

        # Cualquier participante confirma si va o no
        if accion == 'responder':
            if mi_participacion is None or not reunion.activa:
                messages.error(request, 'No podés responder esta reunión.')
            else:
                acepta = request.POST.get('respuesta') == 'acepta'
                _responder_reunion(mi_participacion, acepta)
                messages.success(request, 'Confirmaste que vas a participar.' if acepta else 'Avisaste que no vas a participar.')
            return volver

        if not gestiona:
            messages.error(request, 'Solo el administrador del consorcio puede hacer eso.')
            return volver

        if accion == 'invitar':
            proveedor = Usuario.objects.filter(pk=request.POST.get('id_proveedor'), rol__rol=PROVEEDOR).first()
            if not reunion.activa:
                messages.error(request, 'La reunión ya terminó o se canceló.')
            elif proveedor is None:
                messages.error(request, 'Elegí el proveedor.')
            else:
                _, creado = _invitar_proveedor_reunion(reunion, proveedor, usuario)
                if creado:
                    messages.success(request, f'Invitación enviada por chat a {proveedor.name_surname}.')
                else:
                    messages.error(request, 'Ese proveedor ya fue invitado a esta reunión.')

        elif accion == 'cancelar_invitacion':
            reunion.participantes.filter(
                pk=request.POST.get('id_participante'), es_opcional=True, respuesta=ReunionParticipante.PENDIENTE,
            ).delete()
            messages.success(request, 'Invitación cancelada.')

        # Link cargado a mano (cuando no se usa la integración con Google)
        elif accion == 'guardar_link':
            link = _link_valido(request.POST.get('meet_link', '').strip())
            if link is False:
                messages.error(request, 'El link no es válido (tiene que empezar con https://).')
            else:
                reunion.meet_link = link
                reunion.meet_codigo = request.POST.get('meet_codigo', '').strip() or None
                reunion.save(update_fields=['meet_link', 'meet_codigo'])
                messages.success(request, 'Link de la reunión guardado.')

        elif accion == 'crear_meet':
            if _crear_meet_google(request, reunion):
                messages.success(request, 'Se creó la reunión en Google Calendar con su link de Meet.')
            elif not reunion.google_event_id:
                messages.error(request, 'Para generar el link automáticamente, vinculá tu cuenta de Google desde Mi cuenta.')

        elif accion == 'subir_doc':
            docs = request.FILES.getlist('doc')
            if not docs or any(_validar_adjunto_chat(d) for d in docs):
                messages.error(request, 'Subí PDF, Word, Excel o fotos de hasta 15 MB.')
            else:
                for d in docs:
                    _guardar_doc_reunion(reunion, d, request.POST.get('tipo'))
                messages.success(request, 'Documento subido.' if len(docs) == 1 else f'{len(docs)} documentos subidos.')

        elif accion == 'borrar_doc':
            doc = reunion.documentos.filter(pk=request.POST.get('id_doc')).first()
            if doc:
                doc.delete()
                try:
                    default_storage.delete(doc.doc)
                except OSError:
                    pass  # archivo abierto en Windows: el registro igual se borra
                messages.success(request, 'Documento eliminado.')

        # Después de empezar, el administrador marca quiénes asistieron
        elif accion == 'asistencia' and reunion.estado_actual != Reunion.PROGRAMADA:
            presentes = set(request.POST.getlist('presentes'))
            for p in reunion.participantes.filter(respuesta=ReunionParticipante.ACEPTA):
                p.asistio = str(p.pk) in presentes
                p.save(update_fields=['asistio'])
            messages.success(request, 'Asistencia guardada.')

        elif accion == 'cancelar' and reunion.activa:
            _cancelar_meet_google(request, reunion)
            reunion.estado = Reunion.CANCELADA
            reunion.save(update_fields=['estado'])
            _registrar_historial(reunion.consorcio, HistorialConsorcio.REUNION,
                                 f'Se canceló la reunión "{reunion.titulo}".', usuario)
            if reunion.evento_id:
                reunion.evento.delete()  # deja de aparecer en la agenda
            messages.success(request, 'Reunión cancelada.')

        elif accion == 'eliminar':
            if reunion.activa:
                _cancelar_meet_google(request, reunion)
            consorcio_id = reunion.consorcio_id
            if reunion.evento_id:
                reunion.evento.delete()
            for doc in reunion.documentos.all():
                try:
                    default_storage.delete(doc.doc)
                except OSError:
                    pass
            reunion.delete()
            messages.success(request, 'Reunión eliminada.')
            return redirect(f"{reverse('reuniones')}?consorcio={consorcio_id}")

        return volver

    participantes = list(reunion.participantes.select_related('usuario').order_by('rol_participante', 'usuario__name_surname'))
    invitados_ids = [p.usuario_id for p in participantes]
    # El proveedor ve el link recién cuando acepta la invitación
    ve_link = gestiona or (mi_participacion is not None and (not mi_participacion.es_opcional or mi_participacion.acepto)) \
        or (mi_participacion is None and reunion.consorcio in _consorcios_del_usuario(usuario))

    return render(request, 'admin/reunion_detalle.html', {
        'page_title': f'Reunión · {reunion.consorcio.name_consorcio}',
        'page_subtitle': reunion.get_modalidad_display(),
        'usuario': usuario,
        'reunion': reunion,
        'gestiona': gestiona,
        'mi_participacion': mi_participacion,
        've_link': ve_link,
        'confirmados': [p for p in participantes if p.respuesta == ReunionParticipante.ACEPTA],
        'pendientes': [p for p in participantes if p.respuesta == ReunionParticipante.PENDIENTE],
        'rechazaron': [p for p in participantes if p.respuesta == ReunionParticipante.RECHAZA],
        'invitados_proveedores': [p for p in participantes if p.rol_participante == ReunionParticipante.PROVEEDOR],
        'documentos': reunion.documentos.all(),
        'tipos_doc': DocReunion.TIPOS,
        'proveedores': Usuario.objects.filter(rol__rol=PROVEEDOR).exclude(estado_cuenta__estado='Baja')
                       .exclude(pk__in=invitados_ids).order_by('name_surname'),
        'google_disponible': google_meet.disponible(),
        'google_vinculada': GoogleCuenta.objects.filter(usuario=usuario).exists() if gestiona else False,
    })


def responder_invitacion_reunion(request, id_participante):
    """El proveedor acepta o rechaza, desde el chat, la invitación a una reunión."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    participante = get_object_or_404(
        ReunionParticipante.objects.select_related('reunion__consorcio'), pk=id_participante, usuario=usuario,
    )
    mensaje = participante.mensajes.first()
    volver = f"{reverse('chat')}?chat_id={mensaje.chat_id}" if mensaje else reverse('reunion_detalle', args=[participante.reunion_id])

    if request.method != 'POST':
        return redirect(volver)
    if participante.respuesta != ReunionParticipante.PENDIENTE:
        messages.error(request, 'Ya respondiste esta invitación.')
    elif not participante.reunion.activa:
        messages.error(request, 'La reunión ya terminó o se canceló.')
    else:
        _responder_reunion(participante, request.POST.get('respuesta') == 'aceptar')
    return redirect(volver)


# ---------- Vincular la cuenta de Google (para crear los Meet) ----------

def google_conectar(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')
    if not _es_admin(usuario):
        return redirect('cuenta')
    if not google_meet.configurado():
        messages.error(request, 'Todavía no están cargadas las credenciales de Google en el servidor (archivo .env).')
        return redirect('cuenta')
    if not google_meet.librerias_instaladas():
        messages.error(request, 'Faltan instalar las librerías de Google (pip install -r requirements.txt).')
        return redirect('cuenta')

    url, state, code_verifier = google_meet.url_autorizacion()
    request.session['google_oauth'] = {'state': state, 'code_verifier': code_verifier,
                                       'volver': request.GET.get('volver') == 'app'}
    return redirect(url)


def google_callback(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    datos = request.session.pop('google_oauth', None)
    if request.GET.get('error'):
        messages.error(request, 'No se vinculó la cuenta de Google: cancelaste el permiso.')
    elif not datos or request.GET.get('state') != datos.get('state'):
        messages.error(request, 'La respuesta de Google no es válida. Probá vincular la cuenta de nuevo.')
    else:
        try:
            cuenta = google_meet.vincular(usuario, request.build_absolute_uri(), datos['state'], datos.get('code_verifier'))
            messages.success(request, f'Vinculaste tu cuenta de Google ({cuenta.google_email}). '
                                      'Las reuniones virtuales se van a crear con su link de Meet.')
        except google_meet.GoogleError:
            messages.error(request, 'No se pudo vincular la cuenta de Google. Probá de nuevo.')
    # Si se vinculó desde la app de React, vuelve a su pantalla de cuenta
    if datos and datos.get('volver'):
        return redirect('/app/cuenta')
    return redirect('cuenta')


def google_desconectar(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    cuenta = GoogleCuenta.objects.filter(usuario=usuario).first()
    if request.method == 'POST' and cuenta:
        if google_meet.librerias_instaladas():
            google_meet.desvincular(cuenta)
        else:
            cuenta.delete()
        messages.success(request, 'Desvinculaste tu cuenta de Google.')
    return redirect('cuenta')


# ---------------------------------------------------------------------------
# Contratos con proveedores (contado o cuotas) e historial de los consorcios
# ---------------------------------------------------------------------------

TIPO_DEUDA_CONTRATO = 'Contrato con proveedor'


def _registrar_historial(consorcio, categoria, descripcion, usuario=None, manual=False):
    ahora = timezone.localtime()
    return HistorialConsorcio.objects.create(
        consorcio=consorcio, categoria=categoria, descripcion=descripcion[:255],
        usuario=usuario, fecha=ahora.date(), time=ahora.time(), manual=manual,
    )


def _sumar_meses(fecha, meses):
    """Misma fecha N meses después; si el mes es más corto, el último día del mes."""
    mes = fecha.month - 1 + meses
    anio, mes = fecha.year + mes // 12, mes % 12 + 1
    for dia in range(fecha.day, 27, -1):
        try:
            return fecha.replace(year=anio, month=mes, day=dia)
        except ValueError:
            continue
    return fecha.replace(year=anio, month=mes, day=28)


def _importes_cuotas(precio, cuotas):
    """Divide el precio en cuotas iguales; la última absorbe los centavos que sobran."""
    base = (precio / cuotas).quantize(Decimal('0.01'), rounding=ROUND_DOWN)
    return [base] * (cuotas - 1) + [precio - base * (cuotas - 1)]


def _crear_contrato(consorcio, proveedor, descripcion, precio, forma_pago, primer_vencimiento, usuario, propuesta=None):
    """Crea el contrato y una deuda por cuota (una sola al contado), que aparecen en Facturación."""
    contrato = ContratoProveedor.objects.create(
        consorcio=consorcio, proveedor=proveedor, propuesta=propuesta, descripcion=descripcion,
        precio=precio, forma_pago=forma_pago, fecha=primer_vencimiento, creado_por=usuario,
    )
    tipo, _ = TipoDeuda.objects.get_or_create(nombre=TIPO_DEUDA_CONTRATO)
    n = forma_pago.cuotas
    for i, importe in enumerate(_importes_cuotas(precio, n), start=1):
        Deuda.objects.create(
            consorcio=consorcio, tipo_deuda=tipo, proveedor=proveedor.name_surname, importe=importe,
            descripcion=f'{descripcion} · ' + ('pago único' if n == 1 else f'cuota {i}/{n}'),
            vencimiento=_sumar_meses(primer_vencimiento, i - 1), contrato=contrato, nro_cuota=i,
        )
    _registrar_historial(consorcio, HistorialConsorcio.CONTRATO,
                         f'Se contrató a {proveedor.name_surname}: {descripcion} '
                         f'({_moneda(precio)}, {str(forma_pago).lower()}).', usuario)
    return contrato


TAMANO_MAXIMO_CONTRATO = 15 * 1024 * 1024  # 15 MB


def _pdf_contrato_invalido(archivo):
    """Mensaje de error si el archivo no es un PDF válido de hasta 15 MB; None si está bien."""
    if not archivo.name.lower().endswith('.pdf') or archivo.read(5) != b'%PDF-':
        return 'El contrato tiene que ser un archivo PDF.'
    archivo.seek(0)
    if archivo.size > TAMANO_MAXIMO_CONTRATO:
        return 'El PDF del contrato puede pesar hasta 15 MB.'
    return None


def _guardar_pdf_contrato(contrato, archivo, usuario):
    """Guarda el contrato formal en docs/contratos/contrato_<id>/ como "<código>_<nombre original>";
    si ya había uno, lo reemplaza."""
    anterior = contrato.contrato_pdf
    nombre = get_valid_filename(os.path.basename(archivo.name))[:120] or 'contrato.pdf'
    contrato.contrato_pdf = default_storage.save(
        f'docs/contratos/contrato_{contrato.pk}/{uuid.uuid4().hex[:8]}_{nombre}', archivo)
    contrato.save(update_fields=['contrato_pdf'])
    if anterior:
        try:
            default_storage.delete(anterior)
        except OSError:
            pass  # archivo abierto en Windows: queda el nuevo igual
    _registrar_historial(contrato.consorcio, HistorialConsorcio.CONTRATO,
                         f'Se cargó el contrato firmado con {contrato.proveedor.name_surname}: {contrato.descripcion}.', usuario)


def _actualizar_contrato(contrato, usuario=None):
    """Cuando se paga la última cuota el contrato pasa a finalizado."""
    if contrato.estado == ContratoProveedor.VIGENTE and not contrato.cuotas.filter(factura__isnull=True).exists():
        contrato.estado = ContratoProveedor.FINALIZADO
        contrato.save(update_fields=['estado'])
        _registrar_historial(contrato.consorcio, HistorialConsorcio.CONTRATO,
                             f'Se terminó de pagar el contrato con {contrato.proveedor.name_surname}: {contrato.descripcion}.', usuario)


def _contratos_visibles(usuario):
    """El proveedor ve sus contratos; el resto, los de los consorcios que ve."""
    if usuario.rol.rol == PROVEEDOR:
        return ContratoProveedor.objects.filter(proveedor=usuario)
    return ContratoProveedor.objects.filter(consorcio__in=_consorcios_del_usuario(usuario))


def _con_avance(contratos):
    """Agrega a cada contrato las cuotas pagas, lo pagado y la próxima cuota a vencer."""
    contratos = list(contratos.annotate(
        cant_cuotas=Count('cuotas', distinct=True),
        cant_pagas=Count('cuotas', filter=Q(cuotas__factura__isnull=False), distinct=True),
        pagado=Sum('cuotas__importe', filter=Q(cuotas__factura__isnull=False)),
    ))
    proximas = {}
    for d in Deuda.objects.filter(contrato__in=contratos, factura__isnull=True).order_by('vencimiento'):
        proximas.setdefault(d.contrato_id, d)
    for c in contratos:
        c.pagado = c.pagado or Decimal('0')
        c.saldo = c.precio - c.pagado if c.estado != ContratoProveedor.CANCELADO else Decimal('0')
        c.porcentaje = int(c.cant_pagas * 100 / c.cant_cuotas) if c.cant_cuotas else 0
        c.proxima = proximas.get(c.pk)
    return contratos


def _proveedores_con_info():
    """Proveedores activos con lo que muestra el selector: foto, descripción del Marketplace y reseñas."""
    proveedores = list(
        Usuario.objects.filter(rol__rol=PROVEEDOR).exclude(estado_cuenta__estado='Baja')
        .select_related('cuenta_marketplace')
        .annotate(promedio=Avg('cuenta_marketplace__resenas__estrellas'), cant_resenas=Count('cuenta_marketplace__resenas'))
        .order_by('name_surname')
    )
    for p in proveedores:
        cuenta = getattr(p, 'cuenta_marketplace', None)
        p.info = (cuenta.descripcion if cuenta and cuenta.descripcion else p.mail)
        p.estrellas = f"⭐ {_formato_estrellas(p.promedio)} ({p.cant_resenas})" if p.promedio else ''
    return proveedores


def contratos(request):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')
    # Crea contratos el administrador, en los consorcios que representa
    gestionables = Consorcio.objects.filter(representante=usuario).order_by('name_consorcio')
    puede_gestionar = _es_admin(usuario) and gestionables.exists()
    formas_pago = FormaPagoContrato.objects.all()

    if request.method == 'POST' and puede_gestionar:
        datos = request.POST
        errores = []
        consorcio = gestionables.filter(pk=datos.get('id_consorcio') or None).first()
        proveedor = Usuario.objects.filter(pk=datos.get('id_proveedor') or None, rol__rol=PROVEEDOR).first()
        forma_pago = formas_pago.filter(pk=datos.get('id_forma_pago') or None).first()
        descripcion = datos.get('descripcion', '').strip()
        propuesta = None
        if datos.get('id_propuesta'):
            propuesta = VotacionContrato.objects.filter(
                pk=datos['id_propuesta'], contratado=True, votacion__consorcio__in=gestionables, contrato__isnull=True,
            ).select_related('votacion').first()
            if propuesta:
                consorcio, proveedor = propuesta.votacion.consorcio, propuesta.proveedor
        if consorcio is None:
            errores.append('Elegí el consorcio.')
        if proveedor is None:
            errores.append('Elegí el proveedor.')
        if not descripcion:
            errores.append('Describí el trabajo contratado.')
        if forma_pago is None:
            errores.append('Elegí la forma de pago.')
        try:
            precio = Decimal(datos.get('precio', '').replace(',', '.'))
            if precio <= 0:
                raise InvalidOperation
        except InvalidOperation:
            precio = None
            errores.append('El precio tiene que ser un número mayor a cero.')
        primer_vencimiento = _fecha_o_none(datos.get('fecha'))
        if primer_vencimiento is None:
            errores.append('Elegí la fecha del primer pago.')
        pdf = request.FILES.get('contrato_pdf')
        if pdf and _pdf_contrato_invalido(pdf):
            errores.append(_pdf_contrato_invalido(pdf))

        if errores:
            for e in errores:
                messages.error(request, e)
            return redirect(request.get_full_path())
        contrato = _crear_contrato(consorcio, proveedor, descripcion, precio, forma_pago,
                                   primer_vencimiento, usuario, propuesta)
        if pdf:
            pdf.seek(0)
            _guardar_pdf_contrato(contrato, pdf, usuario)
        n = forma_pago.cuotas
        messages.success(request, 'Contrato creado. ' + (
            'El pago quedó en Facturación como deuda pendiente.' if n == 1
            else f'Las {n} cuotas quedaron en Facturación como deudas pendientes.'))
        return redirect('contrato_detalle', id_contrato=contrato.pk)

    filtro_consorcio = request.GET.get('consorcio', '')
    filtro_estado = request.GET.get('estado', '')
    lista = _contratos_visibles(usuario).select_related('consorcio', 'proveedor', 'forma_pago')
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    if filtro_estado in dict(ContratoProveedor.ESTADOS):
        lista = lista.filter(estado=filtro_estado)
    lista = _con_avance(lista)

    # Viniendo de una votación: el formulario se abre con el proveedor elegido
    propuesta = None
    if puede_gestionar and request.GET.get('propuesta'):
        propuesta = VotacionContrato.objects.filter(
            pk=request.GET['propuesta'], contratado=True, votacion__consorcio__in=gestionables, contrato__isnull=True,
        ).select_related('votacion__consorcio', 'proveedor').first()

    es_proveedor = usuario.rol.rol == PROVEEDOR
    return render(request, 'admin/contratos.html', {
        'page_title': 'Mis contratos' if es_proveedor else 'Contratos',
        'page_subtitle': 'Trabajos contratados por los consorcios' if es_proveedor
                         else 'Trabajos contratados a proveedores, al contado o en cuotas',
        'usuario': usuario,
        'contratos': lista,
        'es_proveedor': es_proveedor,
        'puede_gestionar': puede_gestionar,
        'mis_consorcios': mis_consorcios if not es_proveedor else [],
        'gestionables': gestionables,
        'consorcio_filtrado': mis_consorcios.filter(pk=filtro_consorcio).first() if filtro_consorcio and not es_proveedor else None,
        'filtro_consorcio': filtro_consorcio,
        'filtro_estado': filtro_estado,
        'estados': ContratoProveedor.ESTADOS,
        'formas_pago': formas_pago,
        'proveedores': _proveedores_con_info(),
        'propuesta': propuesta,
        'hoy': timezone.localdate(),
    })


def contrato_detalle(request, id_contrato):
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    contrato = get_object_or_404(
        _contratos_visibles(usuario).select_related('consorcio', 'proveedor', 'forma_pago', 'propuesta__votacion'),
        pk=id_contrato,
    )
    gestiona = contrato.consorcio.representante_id == usuario.pk
    es_el_proveedor = contrato.proveedor_id == usuario.pk

    # Estado del trabajo: lo marcan terminado el administrador o el mismo proveedor
    if request.method == 'POST' and (gestiona or es_el_proveedor) and request.POST.get('accion') in ('terminar_trabajo', 'reabrir_trabajo'):
        if request.POST['accion'] == 'terminar_trabajo' and not contrato.trabajo_terminado and contrato.estado != ContratoProveedor.CANCELADO:
            contrato.trabajo_terminado_en = _fecha_o_none(request.POST.get('fecha')) or timezone.localdate()
            contrato.save(update_fields=['trabajo_terminado_en'])
            _registrar_historial(contrato.consorcio, HistorialConsorcio.OBRA,
                                 f'{contrato.proveedor.name_surname} terminó el trabajo: {contrato.descripcion}.', usuario)
            messages.success(request, 'Trabajo marcado como terminado.')
        elif request.POST['accion'] == 'reabrir_trabajo' and contrato.trabajo_terminado and gestiona:
            contrato.trabajo_terminado_en = None
            contrato.save(update_fields=['trabajo_terminado_en'])
            messages.success(request, 'El trabajo volvió a quedar en proceso.')
        return redirect(request.POST.get('volver') or reverse('contrato_detalle', args=[contrato.pk]))

    if request.method == 'POST' and gestiona:
        accion = request.POST.get('accion')
        pendientes = contrato.cuotas.filter(factura__isnull=True)
        if accion == 'subir_pdf':
            pdf = request.FILES.get('contrato_pdf')
            error = _pdf_contrato_invalido(pdf) if pdf else 'Elegí el PDF del contrato.'
            if error:
                messages.error(request, error)
            else:
                pdf.seek(0)
                reemplazo = bool(contrato.contrato_pdf)
                _guardar_pdf_contrato(contrato, pdf, usuario)
                messages.success(request, 'Contrato formal reemplazado.' if reemplazo else 'Contrato formal cargado.')
        elif accion == 'quitar_pdf' and contrato.contrato_pdf:
            try:
                default_storage.delete(contrato.contrato_pdf)
            except OSError:
                pass
            contrato.contrato_pdf = None
            contrato.save(update_fields=['contrato_pdf'])
            messages.success(request, 'Se quitó el PDF del contrato.')
        elif accion == 'cancelar' and contrato.estado == ContratoProveedor.VIGENTE:
            # Las cuotas que no se pagaron dejan de ser deuda
            cant = pendientes.count()
            pendientes.delete()
            contrato.estado = ContratoProveedor.CANCELADO
            contrato.save(update_fields=['estado'])
            _registrar_historial(contrato.consorcio, HistorialConsorcio.CONTRATO,
                                 f'Se canceló el contrato con {contrato.proveedor.name_surname}: {contrato.descripcion}.', usuario)
            messages.success(request, 'Contrato cancelado.' + (f' Se quitaron {cant} cuota{"s" if cant != 1 else ""} sin pagar.' if cant else ''))
        elif accion == 'eliminar':
            if contrato.cuotas.filter(factura__isnull=False).exists():
                messages.error(request, 'No se puede eliminar un contrato con cuotas pagas: cancelalo.')
            else:
                consorcio_id = contrato.consorcio_id
                pendientes.delete()
                contrato.delete()
                messages.success(request, 'Contrato eliminado.')
                return redirect(f"{reverse('contratos')}?consorcio={consorcio_id}")
        return redirect('contrato_detalle', id_contrato=contrato.pk)

    contrato = _con_avance(ContratoProveedor.objects.filter(pk=contrato.pk)
                           .select_related('consorcio', 'proveedor', 'forma_pago', 'propuesta__votacion'))[0]
    facturas = {f.deuda_id: f for f in Factura.objects.filter(deuda__contrato=contrato)}
    cuotas = list(contrato.cuotas.order_by('nro_cuota'))
    for c in cuotas:
        c.factura_pago = facturas.get(c.pk)

    return render(request, 'admin/contrato_detalle.html', {
        'page_title': f'Contrato · {contrato.consorcio.name_consorcio}',
        'page_subtitle': contrato.proveedor.name_surname,
        'usuario': usuario,
        'contrato': contrato,
        'cuotas': cuotas,
        'gestiona': gestiona,
        'es_el_proveedor': es_el_proveedor,
        'puede_pagar': _rol_permitido(usuario, ASISTENTE) and contrato.consorcio in _consorcios_del_usuario(usuario),
        'hoy': timezone.localdate(),
    })


def historial_consorcios(request):
    """Historial de los edificios: el administrador ve el de los suyos, el asistente el de los
    habilitados y el propietario el de los edificios donde tiene unidad."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')
    gestionables = Consorcio.objects.filter(representante=usuario)

    if request.method == 'POST' and _es_admin(usuario):
        accion = request.POST.get('accion')
        if accion == 'agregar':
            consorcio = gestionables.filter(pk=request.POST.get('id_consorcio') or None).first()
            descripcion = request.POST.get('descripcion', '').strip()
            categoria = request.POST.get('categoria')
            fecha = _fecha_o_none(request.POST.get('fecha')) or timezone.localdate()
            if consorcio is None or not descripcion or categoria not in HistorialConsorcio.MANUALES:
                messages.error(request, 'Elegí el consorcio, el tipo y escribí qué pasó.')
            elif fecha > timezone.localdate():
                messages.error(request, 'La fecha no puede ser futura.')
            else:
                HistorialConsorcio.objects.create(
                    consorcio=consorcio, categoria=categoria, descripcion=descripcion[:255],
                    usuario=usuario, fecha=fecha, time=timezone.localtime().time(), manual=True,
                )
                messages.success(request, 'Entrada agregada al historial.')
        elif accion == 'borrar':
            HistorialConsorcio.objects.filter(pk=request.POST.get('id_historial'), manual=True,
                                              consorcio__in=gestionables).delete()
            messages.success(request, 'Entrada eliminada.')
        return redirect(request.get_full_path())

    filtro_consorcio = request.GET.get('consorcio', '')
    filtro_categoria = request.GET.get('categoria', '')
    desde = _fecha_o_none(request.GET.get('desde'))
    hasta = _fecha_o_none(request.GET.get('hasta'))
    lista = HistorialConsorcio.objects.filter(consorcio__in=mis_consorcios).select_related('consorcio', 'usuario')
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    if filtro_categoria in dict(HistorialConsorcio.CATEGORIAS):
        lista = lista.filter(categoria=filtro_categoria)
    if desde:
        lista = lista.filter(fecha__gte=desde)
    if hasta:
        lista = lista.filter(fecha__lte=hasta)

    # Agrupado por mes
    meses = []
    for h in lista[:300]:
        clave = (h.fecha.year, h.fecha.month)
        if not meses or meses[-1]['clave'] != clave:
            meses.append({'clave': clave, 'titulo': f'{MESES[h.fecha.month - 1].capitalize()} {h.fecha.year}', 'eventos': []})
        meses[-1]['eventos'].append(h)

    return render(request, 'admin/historial.html', {
        'page_title': 'Historial',
        'page_subtitle': 'Lo que pasó en cada edificio: contratos, pagos, votaciones, reuniones y obras',
        'usuario': usuario,
        'meses': meses,
        'mis_consorcios': mis_consorcios,
        'gestionables': gestionables.order_by('name_consorcio') if _es_admin(usuario) else [],
        'consorcio_filtrado': mis_consorcios.filter(pk=filtro_consorcio).first() if filtro_consorcio else None,
        'filtro_consorcio': filtro_consorcio,
        'filtro_categoria': filtro_categoria,
        'desde': request.GET.get('desde', ''),
        'hasta': request.GET.get('hasta', ''),
        'categorias': HistorialConsorcio.CATEGORIAS,
        'manuales': [(c, t) for c, t in HistorialConsorcio.CATEGORIAS if c in HistorialConsorcio.MANUALES],
        'hoy': timezone.localdate(),
    })


def trabajos(request):
    """Trabajos contratados a proveedores, en proceso o terminados (independiente de cómo se van pagando)."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    es_proveedor = usuario.rol.rol == PROVEEDOR
    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio') if not es_proveedor else Consorcio.objects.none()
    filtro_consorcio = request.GET.get('consorcio', '')
    vista = request.GET.get('vista', 'proceso')
    q = request.GET.get('q', '').strip()

    base = _contratos_visibles(usuario).exclude(estado=ContratoProveedor.CANCELADO).select_related('consorcio', 'proveedor', 'forma_pago')
    if filtro_consorcio:
        base = base.filter(consorcio_id=filtro_consorcio)
    if q:
        base = base.filter(Q(descripcion__icontains=q) | Q(proveedor__name_surname__icontains=q))
    en_proceso = base.filter(trabajo_terminado_en__isnull=True)
    terminados = base.filter(trabajo_terminado_en__isnull=False)
    lista = _con_avance((terminados.order_by('-trabajo_terminado_en') if vista == 'terminados' else en_proceso.order_by('fecha')))

    hoy = timezone.localdate()
    for c in lista:
        c.dias = (c.trabajo_terminado_en - c.fecha).days if c.trabajo_terminado_en else (hoy - c.fecha).days
        c.gestiona = c.consorcio.representante_id == usuario.pk or c.proveedor_id == usuario.pk

    return render(request, 'admin/trabajos.html', {
        'page_title': 'Trabajos',
        'page_subtitle': 'Trabajos contratados a proveedores: en proceso y terminados',
        'usuario': usuario,
        'trabajos': lista,
        'vista': vista,
        'cant_proceso': en_proceso.count(),
        'cant_terminados': terminados.count(),
        'mis_consorcios': mis_consorcios,
        'filtro_consorcio': filtro_consorcio,
        'busqueda': q,
        'es_proveedor': es_proveedor,
        'hoy': hoy,
    })


def facturas_procesadas(request):
    """Todas las facturas cargadas de los consorcios que ve el usuario, con filtros y totales."""
    usuario = _get_logged_usuario(request)

    if usuario is None:
        return redirect('login')

    if not _rol_permitido(usuario, ASISTENTE, PROPIETARIO):
        return redirect('dashboard')

    mis_consorcios = _consorcios_del_usuario(usuario).order_by('name_consorcio')
    filtro_consorcio = request.GET.get('consorcio', '')
    filtro_tipo = request.GET.get('tipo', '')
    q = request.GET.get('q', '').strip()
    desde = _fecha_o_none(request.GET.get('desde'))
    hasta = _fecha_o_none(request.GET.get('hasta'))

    lista = (Factura.objects.filter(consorcio__in=mis_consorcios)
             .select_related('consorcio', 'deuda__tipo_deuda', 'deuda__contrato', 'usuario'))
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    if filtro_tipo:
        lista = lista.filter(deuda__tipo_deuda_id=filtro_tipo)
    if q:
        lista = lista.filter(Q(proveedor__icontains=q) | Q(nro_comprobante__icontains=q) | Q(deuda__descripcion__icontains=q))
    if desde:
        lista = lista.filter(fecha__gte=desde)
    if hasta:
        lista = lista.filter(fecha__lte=hasta)

    total = lista.aggregate(t=Sum('importe'))['t'] or Decimal('0')
    por_tipo = list(lista.values('deuda__tipo_deuda__nombre').annotate(total=Sum('importe'), cant=Count('pk')).order_by('-total'))
    for t in por_tipo:
        t['porcentaje'] = int(t['total'] * 100 / total) if total else 0

    return render(request, 'admin/facturas_procesadas.html', {
        'page_title': 'Facturas procesadas',
        'page_subtitle': 'Facturas cargadas de todos tus consorcios',
        'usuario': usuario,
        'facturas': lista.order_by('-fecha', '-id_factura')[:300],
        'cantidad': lista.count(),
        'total': total,
        'por_tipo': por_tipo,
        'mis_consorcios': mis_consorcios,
        'tipos': TipoDeuda.objects.order_by('nombre'),
        'filtro_consorcio': filtro_consorcio,
        'filtro_tipo': filtro_tipo,
        'busqueda': q,
        'desde': request.GET.get('desde', ''),
        'hasta': request.GET.get('hasta', ''),
        'hay_filtros': any([filtro_consorcio, filtro_tipo, q, desde, hasta]),
    })


# ---------------------------------------------------------------------------
# Front en React (compilado con "npm run build" en web/static/app)
# ---------------------------------------------------------------------------

def app_react(request, ruta=''):
    """Sirve la app de React: sus archivos (JS, CSS, fuentes) o, para cualquier otra ruta, su index.html
    (las rutas internas las resuelve React Router)."""
    from django.conf import settings
    from django.http import FileResponse
    from django.views.static import serve

    carpeta = os.path.join(settings.BASE_DIR, 'web', 'static', 'app')
    if ruta and os.path.isfile(os.path.join(carpeta, ruta)):
        return serve(request, ruta, document_root=carpeta)
    indice = os.path.join(carpeta, 'index.html')
    if not os.path.isfile(indice):
        raise Http404('Falta compilar el front: npm run build en la carpeta frontend.')
    return FileResponse(open(indice, 'rb'), content_type='text/html')
