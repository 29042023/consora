from django import template
from django.conf import settings
from django.templatetags.static import static

register = template.Library()


@register.simple_tag
def mensajes_sin_leer(usuario):
    """Cantidad de mensajes del chat sin leer (para el globito rojo del topbar)."""
    from web.models import MensajeChat
    if not usuario:
        return 0
    return MensajeChat.sin_leer_para(usuario).count()


@register.filter
def img_url(ruta):
    """Convierte una ruta de imagen guardada en la base en una URL.

    Las imágenes se guardan en MEDIA_ROOT (img/usuarios, img/publicaciones, img/consorcios).
    Por compatibilidad con la app mobile, "asset:img/..." apunta a static/img/...
    """
    if not ruta:
        return ''
    if ruta.startswith('asset:'):
        return static(ruta[len('asset:'):])
    return settings.MEDIA_URL + ruta


@register.filter
def moneda(valor):
    """185000.5 -> "$ 185.000,50" (formato argentino)."""
    if valor is None:
        return ''
    texto = f"{float(valor):,.2f}".replace(',', 'X').replace('.', ',').replace('X', '.')
    if texto.endswith(',00'):
        texto = texto[:-3]
    return f"$ {texto}"


@register.filter
def estrellas(promedio):
    """4.666 -> "4,7"."""
    if promedio is None:
        return ''
    return f"{promedio:.1f}".replace('.', ',')
