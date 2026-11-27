from django.apps import AppConfig


# Carpetas de MEDIA_ROOT donde se guardan los archivos que cargan los usuarios
# (las mismas que usa la app mobile):
#   img/usuarios        fotos de perfil
#   img/publicaciones   fotos de las publicaciones del Marketplace
#   img/consorcios      fotos de los consorcios
#   docs/facturas       PDF o imagen de cada factura
#   docs/titulos        PDF del título de cada administrador
#   docs/chats          archivos y fotos enviados por chat
#   docs/votaciones     propuestas de proveedores y actas de las votaciones
#   docs/docgeneral     otros documentos
CARPETAS_MEDIA = [
    'img/usuarios', 'img/publicaciones', 'img/consorcios',
    'docs/facturas', 'docs/titulos', 'docs/chats', 'docs/votaciones', 'docs/docgeneral',
]


class WebConfig(AppConfig):
    name = 'web'

    def ready(self):
        # Crea todas las carpetas al arrancar, si todavía no existen
        from pathlib import Path
        from django.conf import settings
        for carpeta in CARPETAS_MEDIA:
            (Path(settings.MEDIA_ROOT) / carpeta).mkdir(parents=True, exist_ok=True)
