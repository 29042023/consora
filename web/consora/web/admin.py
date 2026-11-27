from django.contrib import admin
from .models import (
    Rol, EstadoCuenta, Usuario, Administrador, Asistente, Consorcio,
    AsistenteConsorcio, MotivoHistorial, Historial, HistorialDoc,
    TipoChat, Chat, MiembroChat, MensajeChat,
    PropietarioConsorcio, TareaAsistente, CuentaMarketplace, ImgCuentaMarketplace, Resena,
    TipoDeuda, Deuda, Factura, AgendaEvento, TituloAdministrador,
    Votacion, VotacionUser, VotacionContrato, DocVotacion,
    GoogleCuenta, Reunion, ReunionParticipante, DocReunion,
    FormaPagoContrato, ContratoProveedor, HistorialConsorcio,
)


@admin.register(Rol)
class RolAdmin(admin.ModelAdmin):
    list_display = ('id_rol', 'rol')


@admin.register(EstadoCuenta)
class EstadoCuentaAdmin(admin.ModelAdmin):
    list_display = ('id_estado_cuenta', 'estado', 'motivo')


@admin.register(Usuario)
class UsuarioAdmin(admin.ModelAdmin):
    list_display = ('id_user', 'name_surname', 'mail', 'rol', 'estado_cuenta', 'created_at')
    search_fields = ('name_surname', 'mail', 'dni')
    list_filter = ('rol', 'estado_cuenta')

    def save_model(self, request, obj, form, change):
        if 'password' in form.changed_data:
            obj.set_password(obj.password)
        super().save_model(request, obj, form, change)


@admin.register(Administrador)
class AdministradorAdmin(admin.ModelAdmin):
    list_display = ('id_administrador', 'usuario', 'matricula', 'fecha_alta_matricula')


@admin.register(Asistente)
class AsistenteAdmin(admin.ModelAdmin):
    list_display = ('id_asistente', 'usuario', 'admin', 'habilitado', 'creado_en')


@admin.register(Consorcio)
class ConsorcioAdmin(admin.ModelAdmin):
    list_display = ('id_consorcio', 'name_consorcio', 'representante', 'direccion')
    search_fields = ('name_consorcio', 'direccion')


@admin.register(AsistenteConsorcio)
class AsistenteConsorcioAdmin(admin.ModelAdmin):
    list_display = ('id_asistente_consorcio', 'asistente', 'consorcio', 'habilitado', 'asignado_en')
    list_filter = ('habilitado',)


@admin.register(MotivoHistorial)
class MotivoHistorialAdmin(admin.ModelAdmin):
    list_display = ('id_motivo_historial', 'categoria', 'motivo')
    list_filter = ('categoria',)


@admin.register(Historial)
class HistorialAdmin(admin.ModelAdmin):
    list_display = ('id_historial', 'usuario', 'motivo', 'descripcion', 'usuario_involucrado', 'creado_en')
    list_filter = ('motivo__categoria',)
    search_fields = ('descripcion',)


@admin.register(HistorialDoc)
class HistorialDocAdmin(admin.ModelAdmin):
    list_display = ('id_historial_doc', 'historial', 'doc')


@admin.register(TipoChat)
class TipoChatAdmin(admin.ModelAdmin):
    list_display = ('id_tipo_chat', 'name_chat')


@admin.register(Chat)
class ChatAdmin(admin.ModelAdmin):
    list_display = ('id_chat', 'tipo_chat')


@admin.register(MiembroChat)
class MiembroChatAdmin(admin.ModelAdmin):
    list_display = ('id_miembro', 'chat', 'usuario')


@admin.register(MensajeChat)
class MensajeChatAdmin(admin.ModelAdmin):
    list_display = ('id_mensaje', 'chat', 'usuario', 'mensaje', 'enviado_en')
    search_fields = ('mensaje',)


@admin.register(PropietarioConsorcio)
class PropietarioConsorcioAdmin(admin.ModelAdmin):
    list_display = ('id_propietario_consorcio', 'usuario', 'consorcio', 'unidad')


@admin.register(TareaAsistente)
class TareaAsistenteAdmin(admin.ModelAdmin):
    list_display = ('id_tarea', 'usuario', 'descripcion', 'estado', 'created_at')
    list_filter = ('estado',)


@admin.register(CuentaMarketplace)
class CuentaMarketplaceAdmin(admin.ModelAdmin):
    list_display = ('id_cuentas_marketplace', 'dueno', 'link')


@admin.register(ImgCuentaMarketplace)
class ImgCuentaMarketplaceAdmin(admin.ModelAdmin):
    list_display = ('id_img_cuentas_marketplace', 'cuenta', 'img', 'creation')


@admin.register(Resena)
class ResenaAdmin(admin.ModelAdmin):
    list_display = ('id_resenas_cuentas_marketplace', 'cuenta', 'autor', 'estrellas', 'creation')


@admin.register(TipoDeuda)
class TipoDeudaAdmin(admin.ModelAdmin):
    list_display = ('id_tipo_deuda', 'nombre')


@admin.register(Deuda)
class DeudaAdmin(admin.ModelAdmin):
    list_display = ('id_deuda', 'consorcio', 'tipo_deuda', 'descripcion', 'importe', 'vencimiento')
    list_filter = ('tipo_deuda',)


@admin.register(Factura)
class FacturaAdmin(admin.ModelAdmin):
    list_display = ('id_factura', 'consorcio', 'proveedor', 'nro_comprobante', 'fecha', 'importe')


@admin.register(AgendaEvento)
class AgendaEventoAdmin(admin.ModelAdmin):
    list_display = ('id_evento', 'usuario', 'consorcio', 'titulo', 'fecha', 'hora')


@admin.register(TituloAdministrador)
class TituloAdministradorAdmin(admin.ModelAdmin):
    list_display = ('id_titulo', 'usuario', 'titulo')


@admin.register(Votacion)
class VotacionAdmin(admin.ModelAdmin):
    list_display = ('id_votacion', 'consorcio', 'tipo', 'detalle', 'fecha', 'fecha_limite')
    list_filter = ('tipo', 'consorcio')


@admin.register(VotacionUser)
class VotacionUserAdmin(admin.ModelAdmin):
    list_display = ('id_votacion_user', 'votacion', 'usuario', 'voto', 'votado_en')


@admin.register(VotacionContrato)
class VotacionContratoAdmin(admin.ModelAdmin):
    list_display = ('id_votacion_contrato', 'votacion', 'proveedor', 'propuesta_pdf', 'aceptacion')


@admin.register(DocVotacion)
class DocVotacionAdmin(admin.ModelAdmin):
    list_display = ('id_doc_votacion', 'votacion', 'doc')


@admin.register(GoogleCuenta)
class GoogleCuentaAdmin(admin.ModelAdmin):
    list_display = ('id_google_cuenta', 'usuario', 'google_email', 'token_expira', 'vinculado_en')
    exclude = ('access_token', 'refresh_token')


@admin.register(Reunion)
class ReunionAdmin(admin.ModelAdmin):
    list_display = ('id_reunion', 'consorcio', 'titulo', 'modalidad', 'fecha_inicio', 'estado', 'meet_link')
    list_filter = ('modalidad', 'estado', 'consorcio')


@admin.register(ReunionParticipante)
class ReunionParticipanteAdmin(admin.ModelAdmin):
    list_display = ('id_reunion_participante', 'reunion', 'usuario', 'rol_participante', 'es_opcional', 'respuesta', 'asistio')
    list_filter = ('rol_participante', 'respuesta')


@admin.register(DocReunion)
class DocReunionAdmin(admin.ModelAdmin):
    list_display = ('id_doc_reunion', 'reunion', 'tipo', 'doc', 'subido_en')


@admin.register(FormaPagoContrato)
class FormaPagoContratoAdmin(admin.ModelAdmin):
    list_display = ('id_forma_pago_contrato', 'tipo', 'cuotas')


@admin.register(ContratoProveedor)
class ContratoProveedorAdmin(admin.ModelAdmin):
    list_display = ('id_contrato_proveedor', 'consorcio', 'proveedor', 'descripcion', 'precio', 'forma_pago', 'estado', 'fecha')
    list_filter = ('estado', 'consorcio')


@admin.register(HistorialConsorcio)
class HistorialConsorcioAdmin(admin.ModelAdmin):
    list_display = ('consorcio', 'categoria', 'descripcion', 'fecha', 'time', 'manual')
    list_filter = ('categoria', 'consorcio')
