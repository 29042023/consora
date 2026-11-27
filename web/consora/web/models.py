import os

from django.db import models
from django.contrib.auth.hashers import make_password, check_password
from django.utils import timezone


class Rol(models.Model):
    id_rol = models.AutoField(primary_key=True)
    rol = models.CharField(max_length=50, unique=True)

    class Meta:
        db_table = 'roles'

    def __str__(self):
        return self.rol


class EstadoCuenta(models.Model):
    id_estado_cuenta = models.AutoField(primary_key=True)
    estado = models.CharField(max_length=50)
    motivo = models.CharField(max_length=255, blank=True, null=True)

    class Meta:
        db_table = 'estados_cuenta'

    def __str__(self):
        return self.estado


class Usuario(models.Model):
    id_user = models.AutoField(primary_key=True)
    img = models.CharField(max_length=255, blank=True, null=True)
    name_surname = models.CharField(max_length=150)
    password = models.CharField(max_length=255)  # se guarda el hash, nunca texto plano
    mail = models.EmailField(max_length=150, unique=True)
    rol = models.ForeignKey(
        Rol, on_delete=models.PROTECT, related_name='usuarios', db_column='role',
    )
    dni = models.CharField(max_length=20, blank=True, null=True, unique=True)
    phone = models.CharField(max_length=30, blank=True, null=True)
    date_nacimiento = models.DateField(blank=True, null=True)
    estado_cuenta = models.ForeignKey(
        EstadoCuenta, on_delete=models.PROTECT, related_name='usuarios',
        blank=True, null=True, db_column='id_estado_cuenta',
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'user'

    def set_password(self, raw_password):
        self.password = make_password(raw_password)

    def check_password(self, raw_password):
        return check_password(raw_password, self.password)

    @property
    def iniciales(self):
        partes = self.name_surname.split()[:2]
        return "".join(p[0] for p in partes).upper() if partes else "?"

    def __str__(self):
        return f"{self.name_surname} ({self.mail})"


class Administrador(models.Model):
    id_administrador = models.AutoField(primary_key=True)
    usuario = models.OneToOneField(
        Usuario, on_delete=models.CASCADE, related_name='administrador', db_column='id_user',
    )
    matricula = models.CharField(max_length=50, unique=True)
    fecha_alta_matricula = models.DateField(blank=True, null=True)
    ente_otorgante = models.CharField(max_length=150, blank=True, null=True)

    class Meta:
        db_table = 'administrador'

    def __str__(self):
        return f"Administrador: {self.usuario.name_surname}"


class Asistente(models.Model):
    id_asistente = models.AutoField(primary_key=True)
    usuario = models.OneToOneField(
        Usuario, on_delete=models.CASCADE, related_name='asistente', db_column='id_user',
    )
    admin = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='asistentes_a_cargo', db_column='id_admin',
    )
    creado_en = models.DateTimeField(auto_now_add=True)
    habilitado = models.BooleanField(default=True)

    class Meta:
        db_table = 'asistente'

    def __str__(self):
        return f"Asistente: {self.usuario.name_surname}"


class Consorcio(models.Model):
    id_consorcio = models.AutoField(primary_key=True)
    representante = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, related_name='consorcios_administrados',
        db_column='representante',
    )
    name_consorcio = models.CharField(max_length=150)
    img_consorcio = models.CharField(max_length=255, blank=True, null=True)
    descripcion = models.TextField(blank=True, null=True)
    direccion = models.CharField(max_length=255)

    class Meta:
        db_table = 'consorcio'

    def __str__(self):
        return self.name_consorcio


class AsistenteConsorcio(models.Model):
    """Qué consorcios puede gestionar cada asistente, y si ese acceso puntual está habilitado."""
    id_asistente_consorcio = models.AutoField(primary_key=True)
    asistente = models.ForeignKey(
        Asistente, on_delete=models.CASCADE, related_name='consorcios_asignados',
    )
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='asistentes_asignados',
    )
    habilitado = models.BooleanField(default=True)
    asignado_en = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'asistente_consorcio'
        constraints = [
            models.UniqueConstraint(fields=['asistente', 'consorcio'], name='uq_asistente_consorcio'),
        ]

    def __str__(self):
        return f"{self.asistente.usuario.name_surname} → {self.consorcio.name_consorcio}"


class MotivoHistorial(models.Model):
    """Catálogo de motivos posibles de un evento de historial, agrupados por categoría
    (la categoría es lo que se usa para el filtro por tipo en la interfaz)."""
    id_motivo_historial = models.AutoField(primary_key=True)
    categoria = models.CharField(max_length=30)  # 'acceso' | 'mensaje' | 'solicitud' | 'presupuesto' | ...
    motivo = models.CharField(max_length=150)

    class Meta:
        db_table = 'motivo_historial'

    def __str__(self):
        return f"[{self.categoria}] {self.motivo}"


class Historial(models.Model):
    id_historial = models.AutoField(primary_key=True)
    motivo = models.ForeignKey(
        MotivoHistorial, on_delete=models.PROTECT, related_name='eventos',
        db_column='id_motivo_historial',
    )
    descripcion = models.CharField(max_length=255)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='historial_generado', db_column='id_user',
    )
    usuario_involucrado = models.ForeignKey(
        Usuario, on_delete=models.SET_NULL, related_name='historial_involucrado',
        db_column='id_user_involucrado', blank=True, null=True,
    )
    # No se usa auto_now_add a propósito: así el seed_data puede cargar eventos
    # con fecha pasada para tener datos de ejemplo realistas. Si no se especifica
    # al crear un evento nuevo, toma la fecha/hora actual igual.
    creado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'historial'
        ordering = ['-creado_en']

    def __str__(self):
        return f"{self.descripcion} ({self.creado_en:%d/%m/%Y})"


class HistorialDoc(models.Model):
    id_historial_doc = models.AutoField(primary_key=True)
    historial = models.ForeignKey(
        Historial, on_delete=models.CASCADE, related_name='documentos', db_column='id_historial',
    )
    doc = models.CharField(max_length=255)

    class Meta:
        db_table = 'historial_doc'

    def __str__(self):
        return self.doc


class TipoChat(models.Model):
    id_tipo_chat = models.AutoField(primary_key=True)
    name_chat = models.CharField(max_length=50)  # 'Individual' | 'Grupal'

    class Meta:
        db_table = 'tipo_chat'

    def __str__(self):
        return self.name_chat


class Chat(models.Model):
    id_chat = models.AutoField(primary_key=True)
    tipo_chat = models.ForeignKey(
        TipoChat, on_delete=models.PROTECT, related_name='chats', db_column='id_tipo_chat',
    )

    class Meta:
        db_table = 'chat'

    def __str__(self):
        return f"Chat #{self.id_chat}"


class MiembroChat(models.Model):
    id_miembro = models.AutoField(primary_key=True)
    chat = models.ForeignKey(
        Chat, on_delete=models.CASCADE, related_name='miembros', db_column='id_chat',
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='chats_de_los_que_participa', db_column='id_user',
    )
    # Hasta cuándo leyó la conversación: los mensajes de otros posteriores a esta fecha
    # son "no leídos". Vacío = nunca la abrió (todos los mensajes de otros cuentan).
    ultimo_leido = models.DateTimeField(blank=True, null=True)

    class Meta:
        db_table = 'miembros_chat'
        constraints = [
            models.UniqueConstraint(fields=['chat', 'usuario'], name='uq_miembro_por_chat'),
        ]

    def __str__(self):
        return f"{self.usuario.name_surname} en chat #{self.chat_id}"


class MensajeChat(models.Model):
    id_mensaje = models.AutoField(primary_key=True)
    chat = models.ForeignKey(
        Chat, on_delete=models.CASCADE, related_name='mensajes', db_column='id_chat',
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='mensajes_enviados', db_column='id_user',
    )
    mensaje = models.TextField(blank=True)  # puede quedar vacío si el mensaje es solo un archivo
    # Archivo adjunto (PDF, Word, Excel o foto), guardado en MEDIA_ROOT/docs/chats
    archivo = models.CharField(max_length=255, blank=True, null=True)
    nombre_archivo = models.CharField(max_length=255, blank=True, null=True)  # nombre original, para mostrarlo
    # Invitación a una votación de contratación (el proveedor la acepta o rechaza desde el chat)
    invitacion = models.ForeignKey(
        'VotacionContrato', on_delete=models.SET_NULL, related_name='mensajes',
        db_column='id_votacion_contrato', blank=True, null=True,
    )
    # Invitación a una reunión (el proveedor la acepta o rechaza desde el chat)
    invitacion_reunion = models.ForeignKey(
        'ReunionParticipante', on_delete=models.SET_NULL, related_name='mensajes',
        db_column='id_reunion_participante', blank=True, null=True,
    )
    # DATETIME en vez de TIME: con solo la hora se pierde la fecha del mensaje.
    # default=timezone.now (no auto_now_add) para que el seed_data pueda cargar
    # mensajes con fecha pasada, igual que hicimos con Historial.
    enviado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'mensaje_chat'
        ordering = ['enviado_en']

    @property
    def extension(self):
        return os.path.splitext(self.nombre_archivo or self.archivo or '')[1].lower()

    @property
    def es_imagen(self):
        return self.extension in ('.jpg', '.jpeg', '.png', '.gif', '.webp')

    @property
    def tipo_archivo(self):
        """'pdf' | 'word' | 'excel' | 'imagen', para elegir el ícono y el color."""
        if self.es_imagen:
            return 'imagen'
        return {'.pdf': 'pdf', '.doc': 'word', '.docx': 'word', '.xls': 'excel', '.xlsx': 'excel', '.csv': 'excel'}.get(self.extension, 'archivo')

    def __str__(self):
        return f"{self.usuario.name_surname}: {self.mensaje[:30] or self.nombre_archivo}"

    @classmethod
    def sin_leer_para(cls, usuario):
        """Mensajes de otros, en chats del usuario, posteriores a la última vez que abrió cada chat.
        Todo va en un solo filter() para que las dos condiciones usen la misma fila de miembros_chat."""
        return cls.objects.filter(
            models.Q(chat__miembros__usuario=usuario)
            & (models.Q(chat__miembros__ultimo_leido__isnull=True)
               | models.Q(enviado_en__gt=models.F('chat__miembros__ultimo_leido')))
        ).exclude(usuario=usuario)

class PropietarioConsorcio(models.Model):
    """Unidad que ocupa cada propietario/residente en un consorcio."""
    id_propietario_consorcio = models.AutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='unidades', db_column='id_user',
    )
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='propietarios', db_column='id_consorcio',
    )
    unidad = models.CharField(max_length=30, blank=True, null=True)  # ej. "4° B"

    class Meta:
        db_table = 'propietario_consorcio'
        constraints = [
            models.UniqueConstraint(fields=['usuario', 'consorcio'], name='uq_propietario_consorcio'),
        ]

    def __str__(self):
        return f"{self.usuario.name_surname} · {self.consorcio.name_consorcio} {self.unidad or ''}"


class TareaAsistente(models.Model):
    """Tarea que el administrador le asigna a un asistente."""
    EN_PROCESO = 'en proceso'
    COMPLETADO = 'completado'
    ESTADOS = [(EN_PROCESO, 'En proceso'), (COMPLETADO, 'Completado')]

    id_tarea = models.AutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='tareas', db_column='id_user',
    )
    descripcion = models.CharField(max_length=255)
    estado = models.CharField(max_length=20, choices=ESTADOS, default=EN_PROCESO)
    # default (no auto_now_add) para que el seed_data pueda cargar tareas con fecha pasada
    created_at = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'tareas_asistente'
        ordering = ['-created_at']

    @property
    def completada(self):
        return self.estado == self.COMPLETADO

    def __str__(self):
        return self.descripcion


class CuentaMarketplace(models.Model):
    """Perfil del Marketplace: el dueño puede ser un proveedor o un administrador."""
    id_cuentas_marketplace = models.AutoField(primary_key=True)
    dueno = models.OneToOneField(
        Usuario, on_delete=models.CASCADE, related_name='cuenta_marketplace', db_column='dueno',
    )
    descripcion = models.TextField(blank=True, null=True)
    link = models.URLField(max_length=255, blank=True, null=True)  # web propia, si tiene

    class Meta:
        db_table = 'cuentas_marketplace'

    def __str__(self):
        return f"Marketplace: {self.dueno.name_surname}"


class ImgCuentaMarketplace(models.Model):
    """Publicación de una cuenta del Marketplace: una foto con su texto."""
    id_img_cuentas_marketplace = models.AutoField(primary_key=True)
    cuenta = models.ForeignKey(
        CuentaMarketplace, on_delete=models.CASCADE, related_name='publicaciones', db_column='cuenta',
    )
    img = models.CharField(max_length=255)
    descripcion = models.TextField(blank=True, null=True)
    creation = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'img_cuentas_marketplace'
        ordering = ['-creation']

    def __str__(self):
        return self.img


class Resena(models.Model):
    """Reseña de una cuenta del Marketplace; cada usuario deja una sola por cuenta."""
    id_resenas_cuentas_marketplace = models.AutoField(primary_key=True)
    cuenta = models.ForeignKey(
        CuentaMarketplace, on_delete=models.CASCADE, related_name='resenas', db_column='cuentas_marketplace',
    )
    autor = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='resenas_escritas', db_column='autor',
    )
    estrellas = models.PositiveSmallIntegerField()  # 1 a 5
    descripcion = models.TextField(blank=True, null=True)
    creation = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'resenas'
        ordering = ['-creation']
        constraints = [
            models.UniqueConstraint(fields=['cuenta', 'autor'], name='uq_resena_por_autor'),
            models.CheckConstraint(condition=models.Q(estrellas__gte=1, estrellas__lte=5), name='ck_resena_estrellas'),
        ]

    def __str__(self):
        return f"{self.estrellas}★ de {self.autor.name_surname}"


class TipoDeuda(models.Model):
    id_tipo_deuda = models.AutoField(primary_key=True)
    nombre = models.CharField(max_length=80, unique=True)  # Luz, Agua, Ascensor...

    class Meta:
        db_table = 'tipo_deuda'

    def __str__(self):
        return self.nombre


class Deuda(models.Model):
    """Deuda de un consorcio (servicio, impuesto, trabajo...). Queda pendiente hasta que se le carga su factura."""
    id_deuda = models.AutoField(primary_key=True)
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='deudas', db_column='id_consorcio',
    )
    tipo_deuda = models.ForeignKey(
        TipoDeuda, on_delete=models.PROTECT, related_name='deudas', db_column='id_tipo_deuda',
    )
    descripcion = models.CharField(max_length=255)
    proveedor = models.CharField(max_length=150, blank=True, null=True)
    importe = models.DecimalField(max_digits=12, decimal_places=2)
    vencimiento = models.DateField(blank=True, null=True)
    # Si la deuda es una cuota de un contrato con un proveedor
    contrato = models.ForeignKey(
        'ContratoProveedor', on_delete=models.SET_NULL, related_name='cuotas',
        db_column='id_contrato_proveedor', blank=True, null=True,
    )
    nro_cuota = models.PositiveSmallIntegerField(blank=True, null=True)

    class Meta:
        db_table = 'deuda'
        ordering = ['vencimiento']

    def __str__(self):
        return f"{self.tipo_deuda} · {self.descripcion}"


class Factura(models.Model):
    id_factura = models.AutoField(primary_key=True)
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='facturas', db_column='id_consorcio',
    )
    deuda = models.OneToOneField(
        Deuda, on_delete=models.PROTECT, related_name='factura', db_column='id_deuda',
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, related_name='facturas_cargadas', db_column='id_user',
    )
    proveedor = models.CharField(max_length=150)
    nro_comprobante = models.CharField(max_length=50)
    fecha = models.DateField()
    importe = models.DecimalField(max_digits=12, decimal_places=2)
    archivo = models.CharField(max_length=255, blank=True, null=True)  # ruta dentro de MEDIA_ROOT
    creado_en = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'factura'
        ordering = ['-fecha', '-id_factura']

    def __str__(self):
        return f"{self.proveedor} {self.nro_comprobante}"


class AgendaEvento(models.Model):
    id_evento = models.AutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='eventos_agenda', db_column='id_user',
    )
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.SET_NULL, related_name='eventos_agenda',
        db_column='id_consorcio', blank=True, null=True,
    )
    titulo = models.CharField(max_length=150)
    descripcion = models.TextField(blank=True, null=True)
    fecha = models.DateField()
    hora = models.TimeField(blank=True, null=True)

    class Meta:
        db_table = 'agenda_evento'
        ordering = ['fecha', 'hora']

    def __str__(self):
        return f"{self.fecha:%d/%m} {self.titulo}"


class TituloAdministrador(models.Model):
    """Título habilitante de un administrador: el PDF subido (ruta dentro de MEDIA_ROOT/docs/titulos)."""
    id_titulo = models.AutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='titulos', db_column='id_user',
    )
    titulo = models.CharField(max_length=255)

    class Meta:
        db_table = 'titulo_administrador'
        ordering = ['-id_titulo']

    @property
    def nombre_archivo(self):
        return self.titulo.rsplit('/', 1)[-1]

    def __str__(self):
        return f"{self.usuario.name_surname}: {self.nombre_archivo}"


class Votacion(models.Model):
    """Votación de un consorcio. COMUN: se vota Sí / No / Abstención.
    CONTRATACION: se vota entre las propuestas de proveedores (VotacionContrato)."""
    COMUN = 'comun'
    CONTRATACION = 'contratacion'
    TIPOS = [(COMUN, 'Común'), (CONTRATACION, 'Contratación')]

    id_votacion = models.AutoField(primary_key=True)
    # No está en el diagrama, pero cada votación pertenece a un consorcio
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='votaciones', db_column='id_consorcio',
    )
    tipo = models.CharField(max_length=20, choices=TIPOS, default=COMUN)
    detalle = models.TextField()
    fecha = models.DateTimeField(default=timezone.now)
    fecha_limite = models.DateTimeField()

    class Meta:
        db_table = 'votacion'
        ordering = ['-fecha']

    @property
    def abierta(self):
        return timezone.now() < self.fecha_limite

    @property
    def lanzada(self):
        """Una votación común arranca al crearla; una de contratación, cuando
        al menos un proveedor invitado acepta participar."""
        return self.tipo == self.COMUN or self.propuestas.filter(aceptacion=True).exists()

    def __str__(self):
        return f"{self.get_tipo_display()}: {self.detalle[:40]}"


class VotacionUser(models.Model):
    """Voto de un propietario o del administrador. En una votación COMUN, voto es
    'si' | 'no' | 'abstencion'; en una de CONTRATACION, es el id de la propuesta elegida."""
    SI, NO, ABSTENCION = 'si', 'no', 'abstencion'
    OPCIONES_COMUN = [(SI, 'Sí'), (NO, 'No'), (ABSTENCION, 'Abstención')]

    id_votacion_user = models.AutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='votos', db_column='id_user',
    )
    votacion = models.ForeignKey(
        Votacion, on_delete=models.CASCADE, related_name='votos', db_column='id_votacion',
    )
    voto = models.CharField(max_length=20)
    votado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'votacion_user'
        constraints = [
            models.UniqueConstraint(fields=['votacion', 'usuario'], name='uq_un_voto_por_usuario'),
        ]

    def __str__(self):
        return f"{self.usuario.name_surname}: {self.voto}"


class VotacionContrato(models.Model):
    """Proveedor invitado a una votación de contratación. La invitación le llega por chat:
    aceptacion es None mientras no responde, True si aceptó participar y False si rechazó.
    Al aceptar puede adjuntar su propuesta en PDF (docs/votaciones)."""
    id_votacion_contrato = models.AutoField(primary_key=True)
    proveedor = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='propuestas', db_column='id_proveedor',
    )
    votacion = models.ForeignKey(
        Votacion, on_delete=models.CASCADE, related_name='propuestas', db_column='id_votacion',
    )
    propuesta_pdf = models.CharField(max_length=255, blank=True, null=True)
    aceptacion = models.BooleanField(null=True, blank=True)
    contratado = models.BooleanField(default=False)  # el proveedor que se contrató al cerrar

    class Meta:
        db_table = 'votacion_contrato'

    def __str__(self):
        return f"{self.proveedor.name_surname} en votación #{self.votacion_id}"


class DocVotacion(models.Model):
    """Documento de una votación (presupuesto, plano, acta...), en docs/votaciones/votacion_<id>/.
    El archivo se guarda como "<código>_<nombre original>" para poder mostrar su nombre."""
    id_doc_votacion = models.AutoField(primary_key=True)
    votacion = models.ForeignKey(
        Votacion, on_delete=models.CASCADE, related_name='documentos', db_column='id_votacion',
    )
    doc = models.CharField(max_length=255)

    class Meta:
        db_table = 'doc_votacion'
        ordering = ['id_doc_votacion']

    @property
    def nombre(self):
        archivo = os.path.basename(self.doc)
        return archivo.split('_', 1)[1] if '_' in archivo else archivo

    @property
    def extension(self):
        return os.path.splitext(self.doc)[1].lower()

    def __str__(self):
        return self.nombre


class GoogleCuenta(models.Model):
    """Cuenta de Google vinculada a un usuario, para crear las reuniones en Google Calendar con su link de Meet.
    Los tokens se guardan cifrados (ver google_meet.py)."""
    id_google_cuenta = models.AutoField(primary_key=True)
    usuario = models.OneToOneField(
        Usuario, on_delete=models.CASCADE, related_name='google_cuenta', db_column='id_user',
    )
    google_email = models.EmailField(max_length=150)
    access_token = models.TextField()
    refresh_token = models.TextField(blank=True, null=True)
    token_expira = models.DateTimeField(blank=True, null=True)
    scopes = models.CharField(max_length=500, blank=True, null=True)
    vinculado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'google_cuenta'

    def __str__(self):
        return f"{self.usuario.name_surname} → {self.google_email}"


class Reunion(models.Model):
    """Reunión de un consorcio (virtual con Google Meet, o presencial). La crea el administrador;
    participan los propietarios, los asistentes habilitados y los proveedores que acepten la invitación."""
    VIRTUAL = 'virtual'
    PRESENCIAL = 'presencial'
    MODALIDADES = [(VIRTUAL, 'Virtual'), (PRESENCIAL, 'Presencial')]

    PROGRAMADA = 'programada'
    EN_CURSO = 'en_curso'
    FINALIZADA = 'finalizada'
    CANCELADA = 'cancelada'
    ESTADOS = [(PROGRAMADA, 'Programada'), (EN_CURSO, 'En curso'), (FINALIZADA, 'Finalizada'), (CANCELADA, 'Cancelada')]

    id_reunion = models.AutoField(primary_key=True)
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='reuniones', db_column='id_consorcio',
    )
    organizador = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, related_name='reuniones_organizadas', db_column='id_organizador',
    )
    # Evento de la agenda del consorcio, para que la reunión aparezca en la agenda
    evento = models.OneToOneField(
        AgendaEvento, on_delete=models.SET_NULL, related_name='reunion', db_column='id_evento',
        blank=True, null=True,
    )
    titulo = models.CharField(max_length=150)
    descripcion = models.TextField(blank=True, null=True)
    modalidad = models.CharField(max_length=20, choices=MODALIDADES, default=VIRTUAL)
    lugar = models.CharField(max_length=255, blank=True, null=True)  # solo si es presencial
    fecha_inicio = models.DateTimeField()
    fecha_fin = models.DateTimeField()
    meet_link = models.URLField(max_length=255, blank=True, null=True)
    meet_codigo = models.CharField(max_length=50, blank=True, null=True)
    google_event_id = models.CharField(max_length=255, blank=True, null=True)
    estado = models.CharField(max_length=20, choices=ESTADOS, default=PROGRAMADA)
    created_at = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'reunion'
        ordering = ['fecha_inicio']

    @property
    def estado_actual(self):
        """El estado guardado solo cambia al cancelarla; en curso / finalizada salen de la hora."""
        if self.estado == self.CANCELADA:
            return self.CANCELADA
        ahora = timezone.now()
        if ahora >= self.fecha_fin:
            return self.FINALIZADA
        if ahora >= self.fecha_inicio:
            return self.EN_CURSO
        return self.PROGRAMADA

    @property
    def estado_actual_display(self):
        return dict(self.ESTADOS)[self.estado_actual]

    @property
    def activa(self):
        """Todavía se puede invitar, responder o unirse."""
        return self.estado_actual in (self.PROGRAMADA, self.EN_CURSO)

    @property
    def es_virtual(self):
        return self.modalidad == self.VIRTUAL

    def __str__(self):
        return f"{self.titulo} ({self.fecha_inicio:%d/%m/%Y %H:%M})"


class ReunionParticipante(models.Model):
    """Quién participa de una reunión y su respuesta. Los proveedores se invitan como opcionales:
    la invitación les llega por chat y solo pasan a participar si la aceptan."""
    PROPIETARIO = 'propietario'
    ASISTENTE = 'asistente'
    PROVEEDOR = 'proveedor'
    ADMINISTRADOR = 'administrador'
    ROLES = [(PROPIETARIO, 'Propietario'), (ASISTENTE, 'Asistente'), (PROVEEDOR, 'Proveedor'), (ADMINISTRADOR, 'Administrador')]

    PENDIENTE = 'pendiente'
    ACEPTA = 'acepta'
    RECHAZA = 'rechaza'
    RESPUESTAS = [(PENDIENTE, 'Pendiente'), (ACEPTA, 'Acepta'), (RECHAZA, 'Rechaza')]

    id_reunion_participante = models.AutoField(primary_key=True)
    reunion = models.ForeignKey(
        Reunion, on_delete=models.CASCADE, related_name='participantes', db_column='id_reunion',
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, related_name='participaciones_reunion', db_column='id_user',
    )
    invitado_por = models.ForeignKey(
        Usuario, on_delete=models.SET_NULL, related_name='invitaciones_reunion_enviadas',
        db_column='invitado_por', blank=True, null=True,
    )
    rol_participante = models.CharField(max_length=20, choices=ROLES)
    es_opcional = models.BooleanField(default=False)
    respuesta = models.CharField(max_length=20, choices=RESPUESTAS, default=PENDIENTE)
    respondido_en = models.DateTimeField(blank=True, null=True)
    asistio = models.BooleanField(blank=True, null=True)
    notificado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'reunion_participante'
        constraints = [
            models.UniqueConstraint(fields=['reunion', 'usuario'], name='uq_participante_por_reunion'),
        ]

    @property
    def acepto(self):
        return self.respuesta == self.ACEPTA

    def __str__(self):
        return f"{self.usuario.name_surname} en {self.reunion.titulo}: {self.respuesta}"


class DocReunion(models.Model):
    """Documento de una reunión (orden del día, acta...), en docs/reuniones/reunion_<id>/,
    guardado como "<código>_<nombre original>" igual que los de las votaciones."""
    ORDEN_DEL_DIA = 'orden_del_dia'
    ACTA = 'acta'
    OTRO = 'otro'
    TIPOS = [(ORDEN_DEL_DIA, 'Orden del día'), (ACTA, 'Acta'), (OTRO, 'Otro')]

    id_doc_reunion = models.AutoField(primary_key=True)
    reunion = models.ForeignKey(
        Reunion, on_delete=models.CASCADE, related_name='documentos', db_column='id_reunion',
    )
    doc = models.CharField(max_length=255)
    tipo = models.CharField(max_length=20, choices=TIPOS, default=OTRO)
    subido_en = models.DateTimeField(default=timezone.now)

    class Meta:
        db_table = 'doc_reunion'
        ordering = ['id_doc_reunion']

    @property
    def nombre(self):
        archivo = os.path.basename(self.doc)
        return archivo.split('_', 1)[1] if '_' in archivo else archivo

    @property
    def extension(self):
        return os.path.splitext(self.doc)[1].lower()

    def __str__(self):
        return self.nombre


class FormaPagoContrato(models.Model):
    """Catálogo de formas de pago de un contrato: al contado (1 pago) o en N cuotas."""
    CONTADO = 'contado'
    CUOTAS = 'cuotas'
    TIPOS = [(CONTADO, 'Contado'), (CUOTAS, 'Cuotas')]

    id_forma_pago_contrato = models.AutoField(primary_key=True)
    tipo = models.CharField(max_length=20, choices=TIPOS)
    cuotas = models.PositiveSmallIntegerField(default=1)

    class Meta:
        db_table = 'forma_pago_contrato'
        ordering = ['cuotas']
        constraints = [
            models.UniqueConstraint(fields=['tipo', 'cuotas'], name='uq_forma_pago'),
        ]

    def __str__(self):
        return 'Contado' if self.tipo == self.CONTADO else f'{self.cuotas} cuotas'


class ContratoProveedor(models.Model):
    """Trabajo contratado a un proveedor por un consorcio. Al crearlo se genera una deuda
    por cuota (una sola si es al contado), que se paga cargándole su factura."""
    VIGENTE = 'vigente'
    FINALIZADO = 'finalizado'
    CANCELADO = 'cancelado'
    ESTADOS = [(VIGENTE, 'Vigente'), (FINALIZADO, 'Finalizado'), (CANCELADO, 'Cancelado')]

    id_contrato_proveedor = models.AutoField(primary_key=True)
    proveedor = models.ForeignKey(
        Usuario, on_delete=models.PROTECT, related_name='contratos', db_column='id_proveedor',
    )
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='contratos', db_column='id_consorcio',
    )
    # Propuesta ganadora de la votación de contratación, si el contrato salió de una
    propuesta = models.OneToOneField(
        VotacionContrato, on_delete=models.SET_NULL, related_name='contrato',
        db_column='id_votacion_contrato', blank=True, null=True,
    )
    descripcion = models.CharField(max_length=255)
    precio = models.DecimalField(max_digits=12, decimal_places=2)
    forma_pago = models.ForeignKey(
        FormaPagoContrato, on_delete=models.PROTECT, related_name='contratos', db_column='id_forma_pago',
    )
    estado = models.CharField(max_length=20, choices=ESTADOS, default=VIGENTE)
    fecha = models.DateField(default=timezone.localdate)   # vencimiento de la primera cuota
    # El trabajo en sí (independiente de los pagos): en proceso hasta que se marca terminado
    trabajo_terminado_en = models.DateField(blank=True, null=True)
    # Contrato formal firmado, en PDF (ruta dentro de MEDIA_ROOT/docs/contratos)
    contrato_pdf = models.CharField(max_length=255, blank=True, null=True)
    time = models.TimeField(default=timezone.localtime)     # hora en que se registró
    creado_por = models.ForeignKey(
        Usuario, on_delete=models.SET_NULL, related_name='contratos_creados',
        db_column='id_user', blank=True, null=True,
    )

    class Meta:
        db_table = 'contratos_proveedores'
        ordering = ['-fecha', '-id_contrato_proveedor']

    @property
    def nombre_pdf(self):
        if not self.contrato_pdf:
            return ''
        archivo = os.path.basename(self.contrato_pdf)
        return archivo.split('_', 1)[1] if '_' in archivo else archivo

    @property
    def trabajo_terminado(self):
        return self.trabajo_terminado_en is not None

    def __str__(self):
        return f"{self.proveedor.name_surname} · {self.descripcion}"


class HistorialConsorcio(models.Model):
    """Lo que pasó en un edificio: contratos, pagos, votaciones, reuniones y obras.
    Se registra solo desde cada sección; el administrador también puede cargar entradas a mano."""
    CONTRATO = 'contrato'
    PAGO = 'pago'
    VOTACION = 'votacion'
    REUNION = 'reunion'
    OBRA = 'obra'
    OTRO = 'otro'
    CATEGORIAS = [(CONTRATO, 'Contrato'), (PAGO, 'Pago'), (VOTACION, 'Votación'),
                  (REUNION, 'Reunión'), (OBRA, 'Obra / mantenimiento'), (OTRO, 'Otro')]
    MANUALES = [OBRA, OTRO]

    id_historial_consorcio = models.AutoField(primary_key=True)
    consorcio = models.ForeignKey(
        Consorcio, on_delete=models.CASCADE, related_name='historial', db_column='id_consorcio',
    )
    categoria = models.CharField(max_length=20, choices=CATEGORIAS, default=OTRO)
    descripcion = models.CharField(max_length=255)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.SET_NULL, related_name='historial_consorcios',
        db_column='id_user', blank=True, null=True,
    )
    fecha = models.DateField(default=timezone.localdate)
    time = models.TimeField(default=timezone.localtime)
    manual = models.BooleanField(default=False)

    class Meta:
        db_table = 'historial_consorcio'
        ordering = ['-fecha', '-time', '-id_historial_consorcio']

    def __str__(self):
        return f"{self.fecha:%d/%m/%Y} {self.descripcion}"
