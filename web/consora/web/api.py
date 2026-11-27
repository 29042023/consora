"""
API JSON para el front en React (Django REST Framework).

Usa la misma sesión que la web: el usuario logueado se guarda en request.session['usuario_id'].
La lógica de negocio (cuotas, historial, permisos por rol) es la misma que usan las vistas de views.py.
"""
from datetime import timedelta
from decimal import Decimal, InvalidOperation

from django.contrib.messages import get_messages
from django.core.files.storage import default_storage
from django.db.models import Avg, Count, Q, Sum
from django.utils import timezone
from django.views.decorators.csrf import ensure_csrf_cookie
from rest_framework.decorators import api_view, authentication_classes, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from . import google_meet, views
from .models import (
    Usuario, Consorcio, Factura, FormaPagoContrato, ContratoProveedor, HistorialConsorcio, VotacionContrato,
    AgendaEvento, DocReunion, GoogleCuenta, Reunion, ReunionParticipante, Votacion, VotacionUser,
    Asistente, Chat, CuentaMarketplace, Deuda, MensajeChat, TipoDeuda,
)
from .templatetags.consora_tags import img_url
from .views import (
    ASISTENTE, PROPIETARIO, PROVEEDOR,
    _get_logged_usuario, _es_admin, _rol_permitido, _consorcios_del_usuario, _guardar_archivo, _fecha_o_none,
    _registrar_historial, _crear_contrato, _actualizar_contrato, _contratos_visibles, _con_avance,
    _proveedores_con_info, _pdf_contrato_invalido, _guardar_pdf_contrato,
)


# ---------------------------------------------------------------------------
# Respuestas
# ---------------------------------------------------------------------------

def _error(*mensajes, status=400):
    return Response({'errores': list(mensajes)}, status=status)


def _num(valor):
    return float(valor) if valor is not None else None


def _fecha(valor):
    return valor.isoformat() if valor else None


# ---------------------------------------------------------------------------
# Sesión
# ---------------------------------------------------------------------------

def _yo(u):
    return {
        'id': u.pk,
        'nombre': u.name_surname,
        'mail': u.mail,
        'img': img_url(u.img),
        'iniciales': u.iniciales,
        'rol': u.rol.rol,
        'es_admin': _es_admin(u),
        'es_asistente': u.rol.rol == ASISTENTE,
        'es_propietario': u.rol.rol == PROPIETARIO,
        'es_proveedor': u.rol.rol == PROVEEDOR,
    }


@api_view(['GET'])
@permission_classes([AllowAny])
@ensure_csrf_cookie
def csrf(request):
    """Deja la cookie csrftoken para que el front la mande en los POST."""
    return Response({'ok': True})


@api_view(['POST'])
@authentication_classes([])
@permission_classes([AllowAny])
def login(request):
    email = (request.data.get('email') or '').strip()
    password = request.data.get('password') or ''
    usuario = Usuario.objects.select_related('rol', 'estado_cuenta').filter(mail__iexact=email).first()
    if usuario is None or not usuario.check_password(password):
        return _error('Email o contraseña incorrectos.', status=401)
    if usuario.estado_cuenta and usuario.estado_cuenta.estado != 'Activa':
        return _error('Tu cuenta no está activa. Contactá al soporte.', status=403)
    request.session['usuario_id'] = usuario.pk
    return Response(_yo(usuario))


@api_view(['POST'])
@permission_classes([AllowAny])
def logout(request):
    request.session.flush()
    return Response({'ok': True})


@api_view(['GET'])
def yo(request):
    return Response(_yo(request.user))


# ---------------------------------------------------------------------------
# Consorcios y facturación
# ---------------------------------------------------------------------------

def _consorcio(c):
    return {'id': c.pk, 'nombre': c.name_consorcio, 'direccion': c.direccion, 'descripcion': c.descripcion,
            'img': img_url(c.img_consorcio)}


@api_view(['GET'])
def consorcios(request):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a los consorcios.', status=403)
    lista = (_consorcios_del_usuario(u)
             .annotate(cant_facturas=Count('facturas', distinct=True),
                       cant_pendientes=Count('deudas', filter=Q(deudas__factura__isnull=True), distinct=True))
             .order_by('name_consorcio'))
    q = (request.query_params.get('q') or '').strip()
    if q:
        lista = lista.filter(Q(name_consorcio__icontains=q) | Q(direccion__icontains=q))
    return Response({
        'puede_gestionar': _es_admin(u),
        'consorcios': [{**_consorcio(c), 'cant_facturas': c.cant_facturas, 'cant_pendientes': c.cant_pendientes} for c in lista],
    })


def _factura(f):
    return {
        'id': f.pk, 'descripcion': f.deuda.descripcion, 'tipo': f.deuda.tipo_deuda.nombre, 'proveedor': f.proveedor,
        'nro_comprobante': f.nro_comprobante, 'fecha': _fecha(f.fecha), 'importe': _num(f.importe),
        'archivo': img_url(f.archivo), 'contrato_id': f.deuda.contrato_id,
    }


def _deuda(d):
    return {'id': d.pk, 'descripcion': d.descripcion, 'tipo': d.tipo_deuda.nombre, 'proveedor': d.proveedor,
            'importe': _num(d.importe), 'vencimiento': _fecha(d.vencimiento), 'contrato_id': d.contrato_id}


@api_view(['GET', 'POST'])
def consorcio_facturacion(request, id_consorcio):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a este consorcio.', status=403)
    consorcio = _consorcios_del_usuario(u).filter(pk=id_consorcio).first()
    if consorcio is None:
        return _error('Consorcio no encontrado.', status=404)
    pendientes = list(consorcio.deudas.filter(factura__isnull=True).select_related('tipo_deuda'))
    puede_cargar = _rol_permitido(u, ASISTENTE)

    if request.method == 'POST':
        if not puede_cargar:
            return _error('Solo la administración y los asistentes cargan facturas.', status=403)
        datos = request.data
        errores = []
        deuda = next((d for d in pendientes if str(d.pk) == str(datos.get('id_deuda'))), None)
        if deuda is None:
            errores.append('Elegí la deuda a la que corresponde la factura.')
        proveedor = (datos.get('proveedor') or '').strip()
        nro = (datos.get('nro_comprobante') or '').strip()
        if not proveedor or not nro:
            errores.append('Completá el proveedor y el número de comprobante.')
        fecha = _fecha_o_none(datos.get('fecha'))
        if fecha is None:
            errores.append('La fecha no es válida.')
        try:
            importe = Decimal(str(datos.get('importe') or '').replace(',', '.'))
            if importe <= 0:
                raise InvalidOperation
        except InvalidOperation:
            importe = None
            errores.append('El importe tiene que ser un número mayor a cero.')
        if errores:
            return _error(*errores)
        archivo = request.FILES.get('archivo')
        Factura.objects.create(
            consorcio=consorcio, deuda=deuda, usuario=u, proveedor=proveedor, nro_comprobante=nro, fecha=fecha,
            importe=importe, archivo=_guardar_archivo(archivo, 'docs/facturas') if archivo else None,
        )
        from .templatetags.consora_tags import moneda
        _registrar_historial(consorcio, HistorialConsorcio.PAGO, f'Se pagó "{deuda.descripcion}" a {proveedor} ({moneda(importe)}).', u)
        if deuda.contrato_id:
            _actualizar_contrato(deuda.contrato, u)
        return Response({'ok': True, 'mensaje': 'Factura cargada.'}, status=201)

    facturas = consorcio.facturas.select_related('deuda__tipo_deuda')
    return Response({
        'consorcio': _consorcio(consorcio),
        'facturas': [_factura(f) for f in facturas],
        'pendientes': [_deuda(d) for d in pendientes],
        'puede_cargar': puede_cargar,
        'hoy': _fecha(timezone.localdate()),
    })


# ---------------------------------------------------------------------------
# Contratos con proveedores y trabajos
# ---------------------------------------------------------------------------

def _contrato(c, usuario):
    return {
        'id': c.pk, 'descripcion': c.descripcion, 'precio': _num(c.precio), 'estado': c.estado,
        'estado_texto': c.get_estado_display(), 'fecha': _fecha(c.fecha),
        'cuotas': c.forma_pago.cuotas, 'forma_pago': str(c.forma_pago), 'es_contado': c.forma_pago.tipo == FormaPagoContrato.CONTADO,
        'consorcio': _consorcio(c.consorcio),
        'proveedor': {'id': c.proveedor_id, 'nombre': c.proveedor.name_surname, 'img': img_url(c.proveedor.img),
                      'iniciales': c.proveedor.iniciales},
        'cant_cuotas': c.cant_cuotas, 'cant_pagas': c.cant_pagas, 'pagado': _num(c.pagado), 'saldo': _num(c.saldo),
        'porcentaje': c.porcentaje,
        'proxima': {'importe': _num(c.proxima.importe), 'vencimiento': _fecha(c.proxima.vencimiento)} if c.proxima else None,
        'trabajo_terminado_en': _fecha(c.trabajo_terminado_en),
        'contrato_pdf': img_url(c.contrato_pdf), 'nombre_pdf': c.nombre_pdf,
        'gestiona': c.consorcio.representante_id == usuario.pk,
        'es_el_proveedor': c.proveedor_id == usuario.pk,
    }


@api_view(['GET', 'POST'])
def contratos(request):
    u = request.user
    gestionables = Consorcio.objects.filter(representante=u).order_by('name_consorcio')
    puede_gestionar = _es_admin(u) and gestionables.exists()

    if request.method == 'POST':
        if not puede_gestionar:
            return _error('Solo la administración crea contratos.', status=403)
        datos = request.data
        errores = []
        consorcio = gestionables.filter(pk=datos.get('id_consorcio') or None).first()
        proveedor = Usuario.objects.filter(pk=datos.get('id_proveedor') or None, rol__rol=PROVEEDOR).first()
        forma_pago = FormaPagoContrato.objects.filter(pk=datos.get('id_forma_pago') or None).first()
        descripcion = (datos.get('descripcion') or '').strip()
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
            precio = Decimal(str(datos.get('precio') or '').replace(',', '.'))
            if precio <= 0:
                raise InvalidOperation
        except InvalidOperation:
            precio = None
            errores.append('El precio tiene que ser un número mayor a cero.')
        primer_pago = _fecha_o_none(datos.get('fecha'))
        if primer_pago is None:
            errores.append('Elegí la fecha del primer pago.')
        pdf = request.FILES.get('contrato_pdf')
        if pdf and _pdf_contrato_invalido(pdf):
            errores.append(_pdf_contrato_invalido(pdf))
        if errores:
            return _error(*errores)
        contrato = _crear_contrato(consorcio, proveedor, descripcion, precio, forma_pago, primer_pago, u, propuesta)
        if pdf:
            pdf.seek(0)
            _guardar_pdf_contrato(contrato, pdf, u)
        n = forma_pago.cuotas
        return Response({'id': contrato.pk, 'mensaje': 'Contrato creado. ' + (
            'El pago quedó en Facturación como deuda pendiente.' if n == 1
            else f'Las {n} cuotas quedaron en Facturación como deudas pendientes.')}, status=201)

    es_proveedor = u.rol.rol == PROVEEDOR
    lista = _contratos_visibles(u).select_related('consorcio', 'proveedor', 'forma_pago')
    filtro_consorcio = request.query_params.get('consorcio') or ''
    filtro_estado = request.query_params.get('estado') or ''
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    if filtro_estado in dict(ContratoProveedor.ESTADOS):
        lista = lista.filter(estado=filtro_estado)
    mis_consorcios = [] if es_proveedor else [_consorcio(c) for c in _consorcios_del_usuario(u).order_by('name_consorcio')]
    return Response({
        'contratos': [_contrato(c, u) for c in _con_avance(lista)],
        'mis_consorcios': mis_consorcios,
        'puede_gestionar': puede_gestionar,
        'es_proveedor': es_proveedor,
        'hoy': _fecha(timezone.localdate()),
    })


@api_view(['GET'])
def contrato_opciones(request):
    """Lo que necesita el formulario de nuevo contrato: consorcios, proveedores con foto e info, formas de pago."""
    u = request.user
    gestionables = Consorcio.objects.filter(representante=u).order_by('name_consorcio')
    propuesta = None
    if request.query_params.get('propuesta'):
        p = VotacionContrato.objects.filter(
            pk=request.query_params['propuesta'], contratado=True, votacion__consorcio__in=gestionables, contrato__isnull=True,
        ).select_related('votacion__consorcio', 'proveedor').first()
        if p:
            propuesta = {'id': p.pk, 'proveedor': p.proveedor.name_surname, 'consorcio': p.votacion.consorcio.name_consorcio,
                         'detalle': p.votacion.detalle}
    return Response({
        'consorcios': [_consorcio(c) for c in gestionables],
        'proveedores': [{'id': p.pk, 'nombre': p.name_surname, 'img': img_url(p.img), 'iniciales': p.iniciales,
                         'info': p.info, 'estrellas': p.estrellas} for p in _proveedores_con_info()],
        'formas_pago': [{'id': f.pk, 'cuotas': f.cuotas, 'texto': str(f)} for f in FormaPagoContrato.objects.all()],
        'propuesta': propuesta,
        'hoy': _fecha(timezone.localdate()),
    })


@api_view(['GET', 'POST'])
def contrato_detalle(request, id_contrato):
    u = request.user
    base = _contratos_visibles(u).select_related('consorcio', 'proveedor', 'forma_pago', 'propuesta__votacion')
    contrato = base.filter(pk=id_contrato).first()
    if contrato is None:
        return _error('Contrato no encontrado.', status=404)
    gestiona = contrato.consorcio.representante_id == u.pk
    es_el_proveedor = contrato.proveedor_id == u.pk

    if request.method == 'POST':
        accion = request.data.get('accion')
        mensaje = None
        if accion == 'terminar_trabajo' and (gestiona or es_el_proveedor):
            if contrato.trabajo_terminado or contrato.estado == ContratoProveedor.CANCELADO:
                return _error('El trabajo ya está terminado o el contrato está cancelado.')
            fecha = _fecha_o_none(request.data.get('fecha')) or timezone.localdate()
            if fecha > timezone.localdate():
                return _error('La fecha no puede ser futura.')
            contrato.trabajo_terminado_en = fecha
            contrato.save(update_fields=['trabajo_terminado_en'])
            _registrar_historial(contrato.consorcio, HistorialConsorcio.OBRA,
                                 f'{contrato.proveedor.name_surname} terminó el trabajo: {contrato.descripcion}.', u)
            mensaje = 'Trabajo marcado como terminado.'
        elif accion == 'reabrir_trabajo' and gestiona:
            contrato.trabajo_terminado_en = None
            contrato.save(update_fields=['trabajo_terminado_en'])
            mensaje = 'El trabajo volvió a quedar en proceso.'
        elif accion == 'subir_pdf' and gestiona:
            pdf = request.FILES.get('contrato_pdf')
            error = _pdf_contrato_invalido(pdf) if pdf else 'Elegí el PDF del contrato.'
            if error:
                return _error(error)
            pdf.seek(0)
            reemplazo = bool(contrato.contrato_pdf)
            _guardar_pdf_contrato(contrato, pdf, u)
            mensaje = 'Contrato formal reemplazado.' if reemplazo else 'Contrato formal cargado.'
        elif accion == 'quitar_pdf' and gestiona and contrato.contrato_pdf:
            try:
                default_storage.delete(contrato.contrato_pdf)
            except OSError:
                pass
            contrato.contrato_pdf = None
            contrato.save(update_fields=['contrato_pdf'])
            mensaje = 'Se quitó el PDF del contrato.'
        elif accion == 'cancelar' and gestiona and contrato.estado == ContratoProveedor.VIGENTE:
            pendientes = contrato.cuotas.filter(factura__isnull=True)
            cant = pendientes.count()
            pendientes.delete()
            contrato.estado = ContratoProveedor.CANCELADO
            contrato.save(update_fields=['estado'])
            _registrar_historial(contrato.consorcio, HistorialConsorcio.CONTRATO,
                                 f'Se canceló el contrato con {contrato.proveedor.name_surname}: {contrato.descripcion}.', u)
            mensaje = 'Contrato cancelado.' + (f' Se quitaron {cant} cuota{"s" if cant != 1 else ""} sin pagar.' if cant else '')
        elif accion == 'eliminar' and gestiona:
            if contrato.cuotas.filter(factura__isnull=False).exists():
                return _error('No se puede eliminar un contrato con cuotas pagas: cancelalo.')
            contrato.cuotas.all().delete()
            if contrato.contrato_pdf:
                try:
                    default_storage.delete(contrato.contrato_pdf)
                except OSError:
                    pass
            contrato.delete()
            return Response({'ok': True, 'eliminado': True, 'mensaje': 'Contrato eliminado.'})
        else:
            return _error('No podés hacer eso con este contrato.', status=403)
        return Response({'ok': True, 'mensaje': mensaje})

    c = _con_avance(base.filter(pk=contrato.pk))[0]
    facturas = {f.deuda_id: f for f in Factura.objects.filter(deuda__contrato=c)}
    cuotas = []
    for d in c.cuotas.order_by('nro_cuota'):
        f = facturas.get(d.pk)
        cuotas.append({'id': d.pk, 'nro': d.nro_cuota, 'importe': _num(d.importe), 'vencimiento': _fecha(d.vencimiento),
                       'pagada_el': _fecha(f.fecha) if f else None, 'comprobante': f.nro_comprobante if f else None,
                       'archivo': img_url(f.archivo) if f else ''})
    return Response({
        **_contrato(c, u),
        'cuotas_detalle': cuotas,
        'votacion': {'id': c.propuesta.votacion_id, 'detalle': c.propuesta.votacion.detalle} if c.propuesta else None,
        'creado_por': c.creado_por.name_surname if c.creado_por else None,
        'puede_pagar': _rol_permitido(u, ASISTENTE) and _consorcios_del_usuario(u).filter(pk=c.consorcio_id).exists(),
        'hoy': _fecha(timezone.localdate()),
    })


@api_view(['GET'])
def trabajos(request):
    u = request.user
    es_proveedor = u.rol.rol == PROVEEDOR
    base = _contratos_visibles(u).exclude(estado=ContratoProveedor.CANCELADO).select_related('consorcio', 'proveedor', 'forma_pago')
    filtro_consorcio = request.query_params.get('consorcio') or ''
    q = (request.query_params.get('q') or '').strip()
    if filtro_consorcio:
        base = base.filter(consorcio_id=filtro_consorcio)
    if q:
        base = base.filter(Q(descripcion__icontains=q) | Q(proveedor__name_surname__icontains=q))
    en_proceso = base.filter(trabajo_terminado_en__isnull=True)
    terminados = base.filter(trabajo_terminado_en__isnull=False)
    vista = request.query_params.get('vista') or 'proceso'
    lista = _con_avance(terminados.order_by('-trabajo_terminado_en') if vista == 'terminados' else en_proceso.order_by('fecha'))
    hoy = timezone.localdate()
    salida = []
    for c in lista:
        d = _contrato(c, u)
        d['dias'] = ((c.trabajo_terminado_en or hoy) - c.fecha).days
        salida.append(d)
    return Response({
        'trabajos': salida,
        'cant_proceso': en_proceso.count(),
        'cant_terminados': terminados.count(),
        'es_proveedor': es_proveedor,
        'mis_consorcios': [] if es_proveedor else [_consorcio(c) for c in _consorcios_del_usuario(u).order_by('name_consorcio')],
        'hoy': _fecha(hoy),
    })



# ---------------------------------------------------------------------------
# Votaciones, reuniones, agenda e historial
#
# Los GET arman los datos para React. Las acciones (crear, votar, invitar, cancelar...) se delegan
# en las vistas de Django de views.py, así la lógica (invitaciones por chat, Google Meet, historial)
# está en un solo lugar: la vista procesa el formulario y sus mensajes se devuelven como JSON.
# Por eso esas acciones se mandan como formulario (FormData), no como JSON.
# ---------------------------------------------------------------------------

def _delegar(request, vista, *args, **kwargs):
    """Ejecuta la vista de Django con el mismo pedido y devuelve sus mensajes y a dónde redirige."""
    respuesta = vista(request._request, *args, **kwargs)
    mensajes = [{'tipo': m.level_tag or 'info', 'texto': str(m)} for m in get_messages(request._request)]
    destino = respuesta.get('Location') if respuesta.status_code in (301, 302) else None
    ok = not any(m['tipo'] == 'error' for m in mensajes)
    return Response({'ok': ok, 'mensajes': mensajes, 'destino': destino}, status=200 if ok else 400)


def _persona(u):
    return {'id': u.pk, 'nombre': u.name_surname, 'img': img_url(u.img), 'iniciales': u.iniciales}


def _hora_local(dt):
    return timezone.localtime(dt).isoformat() if dt else None


def _proveedores_activos(excluir=()):
    return [{**_persona(p), 'info': p.info, 'estrellas': p.estrellas}
            for p in _proveedores_con_info() if p.pk not in set(excluir)]


@api_view(['GET'])
def proveedores(request):
    return Response({'proveedores': _proveedores_activos()})


# ---------- Votaciones ----------

def _votacion(v, usuario=None):
    return {
        'id': v.pk, 'tipo': v.tipo, 'tipo_texto': v.get_tipo_display(), 'detalle': v.detalle,
        'fecha': _hora_local(v.fecha), 'fecha_limite': _hora_local(v.fecha_limite),
        'abierta': v.abierta, 'lanzada': v.lanzada, 'consorcio': _consorcio(v.consorcio),
        'gestiona': usuario is not None and v.consorcio.representante_id == usuario.pk,
    }


@api_view(['GET', 'POST'])
def votaciones(request):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a las votaciones.', status=403)
    if request.method == 'POST':
        return _delegar(request, views.votaciones)

    mis_consorcios = _consorcios_del_usuario(u).order_by('name_consorcio')
    lista = (Votacion.objects.filter(consorcio__in=mis_consorcios).select_related('consorcio')
             .annotate(cant_votos=Count('votos', distinct=True), cant_propuestas=Count('propuestas', distinct=True)))
    filtro_consorcio = request.query_params.get('consorcio') or ''
    estado = request.query_params.get('estado') or ''
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    ahora = timezone.now()
    if estado == 'abiertas':
        lista = lista.filter(fecha_limite__gt=ahora)
    elif estado == 'cerradas':
        lista = lista.filter(fecha_limite__lte=ahora)
    lista = list(lista)
    mis_votos = set(VotacionUser.objects.filter(usuario=u, votacion__in=lista).values_list('votacion_id', flat=True))
    return Response({
        'votaciones': [{**_votacion(v, u), 'cant_votos': v.cant_votos, 'cant_propuestas': v.cant_propuestas,
                        'ya_vote': v.pk in mis_votos} for v in lista],
        'mis_consorcios': [_consorcio(c) for c in mis_consorcios],
        'puede_gestionar': _es_admin(u),
        'proveedores': _proveedores_activos() if _es_admin(u) else [],
        'limite_sugerido': (timezone.localtime() + timedelta(days=7)).strftime('%Y-%m-%dT%H:%M'),
        'ahora_min': timezone.localtime().strftime('%Y-%m-%dT%H:%M'),
    })


@api_view(['GET', 'POST'])
def votacion_detalle(request, id_votacion):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a las votaciones.', status=403)
    v = Votacion.objects.select_related('consorcio').filter(consorcio__in=_consorcios_del_usuario(u), pk=id_votacion).first()
    if v is None:
        return _error('Votación no encontrada.', status=404)
    if request.method == 'POST':
        return _delegar(request, views.votacion_detalle, id_votacion=v.pk)

    resultados, participacion = views._resultados(v)
    textos = {r['valor']: r['texto'] for r in resultados}
    mi_voto = v.votos.filter(usuario=u).first()
    invitaciones = list(v.propuestas.select_related('proveedor').order_by('proveedor__name_surname'))
    for inv in invitaciones:
        mensaje = inv.mensajes.first()
        inv.chat_id = mensaje.chat_id if mensaje else None
    contrato = ContratoProveedor.objects.filter(propuesta__votacion=v).first()
    contratado = next((i for i in invitaciones if i.contratado), None)
    return Response({
        **_votacion(v, u),
        'resultados': [{k: r[k] for k in ('valor', 'texto', 'cantidad', 'porcentaje', 'gana')} for r in resultados],
        'participacion': participacion,
        'mi_voto': mi_voto.voto if mi_voto else None,
        'puedo_votar': u.pk in views._votantes(v),
        'votos': [{'usuario': _persona(x.usuario), 'voto': textos.get(x.voto, x.voto), 'votado_en': _hora_local(x.votado_en)}
                  for x in v.votos.select_related('usuario').order_by('-votado_en')],
        'documentos': [{'id': d.pk, 'nombre': d.nombre, 'url': img_url(d.doc), 'extension': d.extension} for d in v.documentos.all()],
        'invitaciones': [{'id': i.pk, 'proveedor': _persona(i.proveedor), 'aceptacion': i.aceptacion,
                          'propuesta_pdf': img_url(i.propuesta_pdf), 'contratado': i.contratado, 'chat_id': i.chat_id}
                         for i in invitaciones],
        'proveedores': _proveedores_activos(excluir=[i.proveedor_id for i in invitaciones]) if v.consorcio.representante_id == u.pk else [],
        'contratado': _persona(contratado.proveedor) if contratado else None,
        'id_propuesta_contratada': contratado.pk if contratado else None,
        'contrato_id': contrato.pk if contrato else None,
    })


# ---------- Reuniones ----------

def _reunion(r):
    return {
        'id': r.pk, 'titulo': r.titulo, 'descripcion': r.descripcion, 'modalidad': r.modalidad,
        'modalidad_texto': r.get_modalidad_display(), 'es_virtual': r.es_virtual, 'lugar': r.lugar,
        'fecha_inicio': _hora_local(r.fecha_inicio), 'fecha_fin': _hora_local(r.fecha_fin),
        'estado': r.estado_actual, 'estado_texto': r.estado_actual_display, 'activa': r.activa,
        'consorcio': _consorcio(r.consorcio),
    }


@api_view(['GET', 'POST'])
def reuniones(request):
    u = request.user
    if request.method == 'POST':
        if not _es_admin(u):
            return _error('Solo la administración crea reuniones.', status=403)
        return _delegar(request, views.reuniones)

    mis_consorcios = _consorcios_del_usuario(u).order_by('name_consorcio')
    lista = views._reuniones_visibles(u).select_related('consorcio').annotate(
        cant_confirmados=Count('participantes', filter=Q(participantes__respuesta=ReunionParticipante.ACEPTA), distinct=True),
    )
    filtro_consorcio = request.query_params.get('consorcio') or ''
    if filtro_consorcio:
        lista = lista.filter(consorcio_id=filtro_consorcio)
    ahora = timezone.now()
    if request.query_params.get('estado') == 'pasadas':
        lista = lista.filter(Q(fecha_fin__lte=ahora) | Q(estado=Reunion.CANCELADA)).order_by('-fecha_inicio')
    else:
        lista = lista.filter(fecha_fin__gt=ahora).exclude(estado=Reunion.CANCELADA).order_by('fecha_inicio')
    lista = list(lista)
    respuestas = dict(ReunionParticipante.objects.filter(usuario=u, reunion__in=lista).values_list('reunion_id', 'respuesta'))
    vinculada = GoogleCuenta.objects.filter(usuario=u).first() if _es_admin(u) else None
    return Response({
        'reuniones': [{**_reunion(r), 'cant_confirmados': r.cant_confirmados, 'tiene_link': bool(r.meet_link),
                       'mi_respuesta': respuestas.get(r.pk)} for r in lista],
        'mis_consorcios': [_consorcio(c) for c in mis_consorcios],
        'puede_gestionar': _es_admin(u),
        'proveedores': _proveedores_activos() if _es_admin(u) else [],
        'duraciones': [{'minutos': m, 'texto': t} for m, t in views.DURACIONES_REUNION],
        'inicio_sugerido': (timezone.localtime() + timedelta(days=3)).replace(hour=19, minute=0).strftime('%Y-%m-%dT%H:%M'),
        'ahora_min': timezone.localtime().strftime('%Y-%m-%dT%H:%M'),
        'google_disponible': google_meet.disponible(),
        'google_vinculada': vinculada.google_email if vinculada else None,
    })


@api_view(['GET', 'POST'])
def reunion_detalle(request, id_reunion):
    u = request.user
    r = views._reuniones_visibles(u).select_related('consorcio', 'organizador').filter(pk=id_reunion).first()
    if r is None:
        return _error('Reunión no encontrada.', status=404)
    if request.method == 'POST':
        return _delegar(request, views.reunion_detalle, id_reunion=r.pk)

    gestiona = views._gestiona_reunion(r, u)
    yo = r.participantes.filter(usuario=u).first()
    ve_link = gestiona or (yo is not None and (not yo.es_opcional or yo.acepto)) \
        or (yo is None and _consorcios_del_usuario(u).filter(pk=r.consorcio_id).exists())
    participantes = list(r.participantes.select_related('usuario').order_by('rol_participante', 'usuario__name_surname'))
    for p in participantes:
        mensaje = p.mensajes.first() if p.rol_participante == ReunionParticipante.PROVEEDOR else None
        p.chat_id = mensaje.chat_id if mensaje else None
    return Response({
        **_reunion(r),
        'organizador': r.organizador.name_surname,
        'meet_link': r.meet_link if ve_link else None,
        'meet_codigo': r.meet_codigo if ve_link else None,
        'hay_link': bool(r.meet_link),
        'con_google': bool(r.google_event_id),
        'gestiona': gestiona,
        'yo': {'id': yo.pk, 'respuesta': yo.respuesta, 'es_opcional': yo.es_opcional} if yo else None,
        'participantes': [{'id': p.pk, 'usuario': _persona(p.usuario), 'rol': p.rol_participante,
                           'rol_texto': p.get_rol_participante_display(), 'es_opcional': p.es_opcional,
                           'respuesta': p.respuesta, 'asistio': p.asistio, 'chat_id': p.chat_id} for p in participantes],
        'documentos': [{'id': d.pk, 'nombre': d.nombre, 'url': img_url(d.doc), 'extension': d.extension,
                        'tipo': d.tipo, 'tipo_texto': d.get_tipo_display()} for d in r.documentos.all()],
        'tipos_doc': [{'valor': v, 'texto': t} for v, t in DocReunion.TIPOS],
        'proveedores': _proveedores_activos(excluir=[p.usuario_id for p in participantes]) if gestiona else [],
        'google_disponible': google_meet.disponible(),
        'google_vinculada': GoogleCuenta.objects.filter(usuario=u).exists() if gestiona else False,
    })


# ---------- Agenda ----------

@api_view(['GET', 'POST'])
def agenda(request):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a la agenda.', status=403)
    if request.method == 'POST':
        return _delegar(request, views.agenda)

    hoy = timezone.localdate()
    mis_consorcios = _consorcios_del_usuario(u).order_by('name_consorcio')
    if _es_admin(u):
        eventos = AgendaEvento.objects.filter(usuario=u)
    elif u.rol.rol == PROPIETARIO:
        eventos = AgendaEvento.objects.filter(consorcio__in=mis_consorcios)
    else:
        eventos = AgendaEvento.objects.filter(Q(usuario=u) | Q(consorcio__in=mis_consorcios))
    dias = []
    for e in eventos.filter(fecha__gte=hoy).select_related('consorcio').distinct():
        if not dias or dias[-1]['fecha'] != _fecha(e.fecha):
            dias.append({'fecha': _fecha(e.fecha), 'eventos': []})
        dias[-1]['eventos'].append({
            'id': e.pk, 'titulo': e.titulo, 'descripcion': e.descripcion, 'hora': e.hora.strftime('%H:%M') if e.hora else None,
            'consorcio': e.consorcio.name_consorcio if e.consorcio else None, 'propio': e.usuario_id == u.pk,
        })
    return Response({
        'dias': dias, 'hoy': _fecha(hoy), 'manana': _fecha(hoy + timedelta(days=1)),
        'mis_consorcios': [_consorcio(c) for c in mis_consorcios],
        'puede_agendar': _rol_permitido(u, ASISTENTE),
    })


# ---------- Historial de los edificios ----------

@api_view(['GET', 'POST'])
def historial(request):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso al historial.', status=403)
    if request.method == 'POST':
        return _delegar(request, views.historial_consorcios)

    mis_consorcios = _consorcios_del_usuario(u).order_by('name_consorcio')
    gestionables = set(Consorcio.objects.filter(representante=u).values_list('pk', flat=True)) if _es_admin(u) else set()
    lista = HistorialConsorcio.objects.filter(consorcio__in=mis_consorcios).select_related('consorcio', 'usuario')
    p = request.query_params
    if p.get('consorcio'):
        lista = lista.filter(consorcio_id=p['consorcio'])
    if p.get('categoria') in dict(HistorialConsorcio.CATEGORIAS):
        lista = lista.filter(categoria=p['categoria'])
    desde, hasta = _fecha_o_none(p.get('desde')), _fecha_o_none(p.get('hasta'))
    if desde:
        lista = lista.filter(fecha__gte=desde)
    if hasta:
        lista = lista.filter(fecha__lte=hasta)
    meses = []
    for h in lista[:300]:
        clave = h.fecha.strftime('%Y-%m')
        if not meses or meses[-1]['clave'] != clave:
            meses.append({'clave': clave, 'titulo': f'{views.MESES[h.fecha.month - 1].capitalize()} {h.fecha.year}', 'eventos': []})
        meses[-1]['eventos'].append({
            'id': h.pk, 'categoria': h.categoria, 'categoria_texto': h.get_categoria_display(), 'descripcion': h.descripcion,
            'consorcio': h.consorcio.name_consorcio, 'fecha': _fecha(h.fecha), 'hora': h.time.strftime('%H:%M') if h.time else None,
            'usuario': h.usuario.name_surname if h.usuario else None, 'manual': h.manual,
            'puede_borrar': h.manual and h.consorcio_id in gestionables,
        })
    return Response({
        'meses': meses,
        'mis_consorcios': [_consorcio(c) for c in mis_consorcios],
        'gestionables': [_consorcio(c) for c in mis_consorcios if c.pk in gestionables],
        'categorias': [{'valor': v, 'texto': t} for v, t in HistorialConsorcio.CATEGORIAS],
        'manuales': [{'valor': v, 'texto': t} for v, t in HistorialConsorcio.CATEGORIAS if v in HistorialConsorcio.MANUALES],
        'hoy': _fecha(timezone.localdate()),
    })


# ---------------------------------------------------------------------------
# Etapa 3: inicios por rol, facturas procesadas, chat, Marketplace, asistentes y cuenta
# ---------------------------------------------------------------------------

@api_view(['GET'])
def mensajes_pendientes(request):
    """Mensajes que dejó Django en la sesión (por ejemplo, al volver de vincular Google)."""
    return Response({'mensajes': [{'tipo': m.level_tag or 'info', 'texto': str(m)} for m in get_messages(request._request)]})


def _evento(e):
    return {'id': e.pk, 'titulo': e.titulo, 'fecha': _fecha(e.fecha), 'hora': e.hora.strftime('%H:%M') if e.hora else None,
            'consorcio': e.consorcio.name_consorcio if e.consorcio else None}


def _reuniones_proximas(u):
    return [_reunion(r) for r in views._proximas_reuniones(u)]


@api_view(['GET', 'POST'])
def inicio(request):
    u = request.user
    if request.method == 'POST':  # el asistente marca sus tareas desde el inicio
        return _delegar(request, views.dashboard)

    hoy = timezone.localdate()
    mis_consorcios = _consorcios_del_usuario(u)
    datos = {'rol': u.rol.rol, 'hoy': _fecha(hoy)}

    if _es_admin(u):
        pendientes = Deuda.objects.filter(consorcio__in=mis_consorcios, factura__isnull=True)
        mes = Factura.objects.filter(consorcio__in=mis_consorcios, fecha__year=hoy.year, fecha__month=hoy.month)
        contratos_vigentes = _contratos_visibles(u).filter(estado=ContratoProveedor.VIGENTE)
        datos.update({
            'metricas': {
                'consorcios': mis_consorcios.count(),
                'deudas_pendientes': pendientes.count(),
                'importe_pendiente': _num(pendientes.aggregate(t=Sum('importe'))['t'] or 0),
                'vencidas': pendientes.filter(vencimiento__lt=hoy).count(),
                'pagado_mes': _num(mes.aggregate(t=Sum('importe'))['t'] or 0),
                'facturas_mes': mes.count(),
                'contratos_vigentes': contratos_vigentes.count(),
                'trabajos_en_proceso': contratos_vigentes.filter(trabajo_terminado_en__isnull=True).count(),
                'votaciones_abiertas': Votacion.objects.filter(consorcio__in=mis_consorcios, fecha_limite__gt=timezone.now()).count(),
                'asistentes': u.asistentes_a_cargo.count(),
            },
            'deudas': [{**_deuda(d), 'consorcio': d.consorcio.name_consorcio}
                       for d in pendientes.select_related('tipo_deuda', 'consorcio').order_by('vencimiento')[:6]],
            'reuniones': _reuniones_proximas(u),
            'eventos': [_evento(e) for e in views._proximos_eventos(AgendaEvento.objects.filter(usuario=u))],
            'consorcios': [{**_consorcio(c), 'cant_pendientes': c.cant} for c in mis_consorcios.annotate(
                cant=Count('deudas', filter=Q(deudas__factura__isnull=True), distinct=True)).order_by('name_consorcio')],
        })
    elif u.rol.rol == ASISTENTE:
        asistente = getattr(u, 'asistente', None)
        tareas = sorted(u.tareas.all(), key=lambda t: t.completada)
        datos.update({
            'admin': asistente.admin.name_surname if asistente else None,
            'tareas': [{'id': t.pk, 'descripcion': t.descripcion, 'completada': t.completada, 'fecha': _fecha(t.created_at)} for t in tareas],
            'consorcios': [{**_consorcio(c), 'cant_pendientes': c.cant} for c in mis_consorcios.annotate(
                cant=Count('deudas', filter=Q(deudas__factura__isnull=True), distinct=True)).order_by('name_consorcio')],
            'votaciones': [_votacion(v) for v in Votacion.objects.filter(consorcio__in=mis_consorcios, fecha_limite__gt=timezone.now())
                           .select_related('consorcio').order_by('fecha_limite')],
            'eventos': [_evento(e) for e in views._proximos_eventos(AgendaEvento.objects.filter(Q(usuario=u) | Q(consorcio__in=mis_consorcios)))],
            'reuniones': _reuniones_proximas(u),
        })
    elif u.rol.rol == PROPIETARIO:
        abiertas = list(Votacion.objects.filter(consorcio__in=mis_consorcios, fecha_limite__gt=timezone.now())
                        .select_related('consorcio').order_by('fecha_limite'))
        votadas = set(VotacionUser.objects.filter(usuario=u, votacion__in=abiertas).values_list('votacion_id', flat=True))
        datos.update({
            'unidades': [{'consorcio': _consorcio(x.consorcio), 'unidad': x.unidad,
                          'administrador': _persona(x.consorcio.representante)}
                         for x in u.unidades.select_related('consorcio__representante')],
            'votaciones': [{**_votacion(v), 'ya_vote': v.pk in votadas} for v in abiertas],
            'deudas': [{**_deuda(d), 'consorcio': d.consorcio.name_consorcio} for d in Deuda.objects.filter(
                consorcio__in=mis_consorcios, factura__isnull=True).select_related('tipo_deuda', 'consorcio')[:5]],
            'facturas': [_factura(f) for f in Factura.objects.filter(consorcio__in=mis_consorcios).select_related('deuda__tipo_deuda')[:5]],
            'eventos': [_evento(e) for e in views._proximos_eventos(AgendaEvento.objects.filter(consorcio__in=mis_consorcios))],
            'reuniones': _reuniones_proximas(u),
        })
    else:  # proveedor
        cuenta = CuentaMarketplace.objects.filter(dueno=u).first()
        invitaciones = list(VotacionContrato.objects.filter(proveedor=u).select_related('votacion__consorcio').order_by('-votacion__fecha'))
        for inv in invitaciones:
            m = inv.mensajes.first()
            inv.chat_id = m.chat_id if m else None
        participaciones = [p for p in ReunionParticipante.objects.filter(usuario=u).exclude(respuesta=ReunionParticipante.RECHAZA)
                           .select_related('reunion__consorcio').order_by('reunion__fecha_inicio') if p.reunion.activa]
        for p in participaciones:
            m = p.mensajes.first()
            p.chat_id = m.chat_id if m else None
        resenas = list(cuenta.resenas.select_related('autor')[:3]) if cuenta else []
        promedio = cuenta.resenas.aggregate(p=Avg('estrellas'))['p'] if cuenta else None
        inv_dict = lambda i: {'id': i.pk, 'votacion': _votacion(i.votacion), 'aceptacion': i.aceptacion, 'contratado': i.contratado,
                              'propuesta_pdf': img_url(i.propuesta_pdf), 'chat_id': i.chat_id}
        datos.update({
            'marketplace': {'descripcion': cuenta.descripcion if cuenta else None,
                            'publicaciones': cuenta.publicaciones.count() if cuenta else 0,
                            'resenas': cuenta.resenas.count() if cuenta else 0,
                            'promedio': views._formato_estrellas(promedio) if promedio else None},
            'ultimas_resenas': [{'autor': r.autor.name_surname, 'estrellas': r.estrellas, 'descripcion': r.descripcion} for r in resenas],
            'invitaciones_pendientes': [inv_dict(i) for i in invitaciones if i.aceptacion is None and i.votacion.abierta],
            'propuestas': [inv_dict(i) for i in invitaciones if i.aceptacion],
            'reuniones_pendientes': [{**_reunion(p.reunion), 'chat_id': p.chat_id} for p in participaciones if p.respuesta == ReunionParticipante.PENDIENTE],
            'reuniones_confirmadas': [_reunion(p.reunion) for p in participaciones if p.acepto],
            'contratos': [_contrato(c, u) for c in _con_avance(_contratos_visibles(u).filter(estado=ContratoProveedor.VIGENTE)
                                                                .select_related('consorcio', 'proveedor', 'forma_pago'))],
        })
    return Response(datos)


@api_view(['GET'])
def facturas_procesadas(request):
    u = request.user
    if not _rol_permitido(u, ASISTENTE, PROPIETARIO):
        return _error('No tenés acceso a las facturas.', status=403)
    mis_consorcios = _consorcios_del_usuario(u).order_by('name_consorcio')
    lista = Factura.objects.filter(consorcio__in=mis_consorcios).select_related('consorcio', 'deuda__tipo_deuda', 'usuario')
    p = request.query_params
    if p.get('consorcio'):
        lista = lista.filter(consorcio_id=p['consorcio'])
    if p.get('tipo'):
        lista = lista.filter(deuda__tipo_deuda_id=p['tipo'])
    q = (p.get('q') or '').strip()
    if q:
        lista = lista.filter(Q(proveedor__icontains=q) | Q(nro_comprobante__icontains=q) | Q(deuda__descripcion__icontains=q))
    desde, hasta = _fecha_o_none(p.get('desde')), _fecha_o_none(p.get('hasta'))
    if desde:
        lista = lista.filter(fecha__gte=desde)
    if hasta:
        lista = lista.filter(fecha__lte=hasta)
    total = lista.aggregate(t=Sum('importe'))['t'] or Decimal('0')
    por_tipo = list(lista.values('deuda__tipo_deuda__nombre').annotate(total=Sum('importe'), cant=Count('pk')).order_by('-total'))
    return Response({
        'facturas': [{**_factura(f), 'consorcio': _consorcio(f.consorcio), 'cargada_por': f.usuario.name_surname}
                     for f in lista.order_by('-fecha', '-id_factura')[:300]],
        'cantidad': lista.count(),
        'total': _num(total),
        'por_tipo': [{'tipo': t['deuda__tipo_deuda__nombre'], 'total': _num(t['total']), 'cant': t['cant'],
                      'porcentaje': int(t['total'] * 100 / total) if total else 0} for t in por_tipo],
        'mis_consorcios': [_consorcio(c) for c in mis_consorcios],
        'tipos': [{'id': t.pk, 'nombre': t.nombre} for t in TipoDeuda.objects.order_by('nombre')],
    })


# ---------- Chat ----------

def _mensaje(m, u):
    d = {'id': m.pk, 'mio': m.usuario_id == u.pk, 'autor': m.usuario.name_surname, 'texto': m.mensaje,
         'enviado_en': _hora_local(m.enviado_en), 'archivo': img_url(m.archivo), 'nombre_archivo': m.nombre_archivo,
         'es_imagen': bool(m.archivo) and m.es_imagen, 'tipo_archivo': m.tipo_archivo if m.archivo else None}
    inv = m.invitacion
    if inv:
        d['invitacion'] = {'id': inv.pk, 'votacion': _votacion(inv.votacion), 'aceptacion': inv.aceptacion,
                           'contratado': inv.contratado, 'para_mi': inv.proveedor_id == u.pk,
                           'propuesta_pdf': img_url(inv.propuesta_pdf)}
    p = m.invitacion_reunion
    if p:
        d['invitacion_reunion'] = {'id': p.pk, 'reunion': _reunion(p.reunion), 'respuesta': p.respuesta,
                                   'para_mi': p.usuario_id == u.pk}
    return d


@api_view(['GET'])
def chats(request):
    u = request.user
    lista = Chat.objects.filter(miembros__usuario=u).prefetch_related('miembros__usuario').distinct()
    salida = []
    ahora = timezone.now()
    for c in lista:
        otro = next((m.usuario for m in c.miembros.all() if m.usuario_id != u.pk), None)
        ultimo = c.mensajes.order_by('-enviado_en').first()
        salida.append({
            'id': c.pk, 'otro': {**_persona(otro), 'rol': otro.rol.rol} if otro else None,
            'ultimo': ({'texto': ultimo.mensaje or (ultimo.nombre_archivo and '📎 ' + ultimo.nombre_archivo) or '',
                        'enviado_en': _hora_local(ultimo.enviado_en), 'mio': ultimo.usuario_id == u.pk} if ultimo else None),
            '_orden': ultimo.enviado_en if ultimo else ahora,
        })
    salida.sort(key=lambda x: x.pop('_orden'), reverse=True)
    return Response({'chats': salida})


@api_view(['GET', 'POST'])
def chat_detalle(request, id_chat):
    u = request.user
    c = Chat.objects.filter(pk=id_chat, miembros__usuario=u).prefetch_related('miembros__usuario').first()
    if c is None:
        return _error('Conversación no encontrada.', status=404)
    if request.method == 'POST':
        return _delegar(request, views.chat)
    otro = next((m.usuario for m in c.miembros.all() if m.usuario_id != u.pk), None)
    mensajes = (MensajeChat.objects.filter(chat=c)
                .select_related('usuario', 'invitacion__votacion__consorcio', 'invitacion_reunion__reunion__consorcio')
                .order_by('enviado_en'))
    return Response({'id': c.pk, 'otro': {**_persona(otro), 'rol': otro.rol.rol} if otro else None,
                     'mensajes': [_mensaje(m, u) for m in mensajes]})


@api_view(['POST'])
def chat_con(request, id_user):
    """Abre (o crea) el chat individual con otro usuario y devuelve su id."""
    otro = Usuario.objects.filter(pk=id_user).first()
    if otro is None or otro.pk == request.user.pk:
        return _error('No se puede abrir esa conversación.')
    return Response({'id': views._chat_entre(request.user, otro).pk})


@api_view(['POST'])
def responder_invitacion_votacion(request, id_invitacion):
    return _delegar(request, views.responder_invitacion, id_invitacion=id_invitacion)


@api_view(['POST'])
def responder_invitacion_reunion(request, id_participante):
    return _delegar(request, views.responder_invitacion_reunion, id_participante=id_participante)


# ---------- Marketplace ----------

@api_view(['GET'])
def marketplace(request):
    cuentas = (CuentaMarketplace.objects.select_related('dueno')
               .filter(Q(dueno__estado_cuenta__isnull=True) | Q(dueno__estado_cuenta__estado='Activa'))
               .exclude(dueno__rol__rol__in=views.ROLES_ADMIN)
               # Un proveedor no se ve a sí mismo en el listado
               .exclude(dueno=request.user)
               .annotate(promedio=Avg('resenas__estrellas'), cant_resenas=Count('resenas', distinct=True))
               .prefetch_related('publicaciones').order_by('dueno__name_surname'))
    q = (request.query_params.get('q') or '').strip()
    if q:
        cuentas = cuentas.filter(Q(dueno__name_surname__icontains=q) | Q(descripcion__icontains=q))
    salida = []
    for c in cuentas:
        fotos = list(c.publicaciones.all())
        salida.append({**_persona(c.dueno), 'descripcion': c.descripcion or '',
                       'tema': views.TEMAS_MARKETPLACE[c.dueno.id_user % len(views.TEMAS_MARKETPLACE)],
                       'foto': img_url(fotos[0].img) if fotos else '', 'cant_publicaciones': len(fotos),
                       'promedio': views._formato_estrellas(c.promedio) if c.promedio else None, 'cant_resenas': c.cant_resenas})
    return Response({'proveedores': salida})


@api_view(['GET'])
def marketplace_consorcios(request):
    if not _es_admin(request.user):
        return _error('Solo para administraciones.', status=403)
    return Response({'edificios': [
        {'nombre': n, 'zona': z, 'icono': i, 'tema': t, 'texto': x, 'unidades': un, 'postulaciones': po}
        for n, z, i, t, _tipo, x, un, po in views.EDIFICIOS_BUSCAN_ADMINISTRADOR]})


@api_view(['GET', 'POST'])
def perfil_proveedor(request, id_user):
    u = request.user
    dueno = Usuario.objects.select_related('rol').filter(pk=id_user).first()
    if dueno is None or (_es_admin(dueno) and dueno.pk != u.pk and u.rol.rol != PROPIETARIO):
        return _error('Perfil no disponible.', status=404)
    if request.method == 'POST':
        return _delegar(request, views.proveedor_perfil, id_user=dueno.pk)
    cuenta = CuentaMarketplace.objects.filter(dueno=dueno).first()
    resenas = list(cuenta.resenas.select_related('autor')) if cuenta else []
    promedio = sum(r.estrellas for r in resenas) / len(resenas) if resenas else None
    mi = next((r for r in resenas if r.autor_id == u.pk), None)
    return Response({
        **_persona(dueno), 'mail': dueno.mail, 'telefono': dueno.phone, 'rol': dueno.rol.rol,
        'tiene_cuenta': cuenta is not None, 'descripcion': cuenta.descripcion if cuenta else None,
        'link': cuenta.link if cuenta else None, 'es_propio': dueno.pk == u.pk,
        'publicaciones': [{'id': x.pk, 'img': img_url(x.img), 'descripcion': x.descripcion} for x in (cuenta.publicaciones.all() if cuenta else [])],
        'resenas': [{'id': r.pk, 'autor': _persona(r.autor), 'estrellas': r.estrellas, 'descripcion': r.descripcion,
                     'fecha': _fecha(r.creation)} for r in resenas],
        'promedio': views._formato_estrellas(promedio) if promedio else None,
        'mi_resena': {'estrellas': mi.estrellas, 'descripcion': mi.descripcion} if mi else None,
        'trabajos': ContratoProveedor.objects.filter(proveedor=dueno).exclude(estado=ContratoProveedor.CANCELADO).count(),
    })


# ---------- Asistentes ----------

@api_view(['GET', 'POST'])
def asistentes(request):
    u = request.user
    if not _es_admin(u):
        return _error('Solo para administraciones.', status=403)
    if request.method == 'POST':
        return _delegar(request, views.asistentes)
    lista = (Asistente.objects.select_related('usuario', 'usuario__estado_cuenta').filter(admin=u)
             .prefetch_related('consorcios_asignados__consorcio').order_by('usuario__name_surname'))
    salida = []
    for a in lista:
        tareas = sorted(a.usuario.tareas.all(), key=lambda t: t.completada)
        historial = a.usuario.historial_generado.select_related('motivo').order_by('-creado_en')[:10]
        salida.append({
            'id': a.pk, 'usuario': {**_persona(a.usuario), 'mail': a.usuario.mail, 'telefono': a.usuario.phone},
            'habilitado': a.habilitado, 'creado_en': _fecha(a.creado_en),
            'consorcios': [{'id_asignacion': x.pk, **_consorcio(x.consorcio), 'habilitado': x.habilitado}
                           for x in a.consorcios_asignados.all()],
            'tareas': [{'id': t.pk, 'descripcion': t.descripcion, 'completada': t.completada, 'fecha': _fecha(t.created_at)} for t in tareas],
            'historial': [{'descripcion': h.descripcion, 'categoria': h.motivo.categoria, 'fecha': _hora_local(h.creado_en)} for h in historial],
        })
    return Response({
        'asistentes': salida,
        'mis_consorcios': [_consorcio(c) for c in Consorcio.objects.filter(representante=u).order_by('name_consorcio')],
    })


# ---------- Mi cuenta ----------

@api_view(['GET', 'POST'])
def cuenta(request):
    u = request.user
    if request.method == 'POST':
        if request._request.POST.get('accion') == 'desvincular_google':
            return _delegar(request, views.google_desconectar)
        return _delegar(request, views.cuenta)
    periodo = timezone.localdate().strftime('%Y-%m')
    sus = views._suscripcion(u, request.session.get('pago_suscripcion') == periodo)
    admin = getattr(u, 'administrador', None)
    cm = CuentaMarketplace.objects.filter(dueno=u).first()
    g = GoogleCuenta.objects.filter(usuario=u).first()
    return Response({
        **_yo(u), 'dni': u.dni, 'telefono': u.phone, 'nacimiento': _fecha(u.date_nacimiento),
        'estado': u.estado_cuenta.estado if u.estado_cuenta else None, 'miembro_desde': _fecha(u.created_at),
        'administrador': {'matricula': admin.matricula, 'fecha_alta_matricula': _fecha(admin.fecha_alta_matricula),
                          'ente_otorgante': admin.ente_otorgante} if admin else None,
        'titulos': [{'id': t.pk, 'nombre': t.nombre_archivo, 'url': img_url(t.titulo)} for t in u.titulos.all()],
        'marketplace': {'descripcion': cm.descripcion, 'link': cm.link} if cm else None,
        'suscripcion': {**sus, 'lineas': [{**l, 'importe': _num(l['importe'])} for l in sus['lineas']],
                        'total': _num(sus['total']), 'vencimiento': _fecha(sus['vencimiento'])} if sus else None,
        'consorcios_a_cargo': [_consorcio(c) for c in u.consorcios_administrados.order_by('name_consorcio')],
        'google': {'disponible': google_meet.disponible(), 'email': g.google_email if g else None},
    })
