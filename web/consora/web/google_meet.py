"""Integración con Google Calendar para las reuniones virtuales: al crear el evento,
Google genera el link de Meet (conferenceData). Usa la cuenta de Google que el
administrador vinculó desde "Mi cuenta".

Librerías: google-auth, google-auth-oauthlib, google-api-python-client y cryptography
(están en requirements.txt). Se importan dentro de cada función para que el resto del
sistema funcione aunque todavía no estén instaladas.
"""
import base64
import hashlib
import logging
import os
import uuid
from datetime import timezone as dt_timezone

from django.conf import settings
from django.utils import timezone

logger = logging.getLogger(__name__)

SCOPES = [
    'openid',
    'https://www.googleapis.com/auth/userinfo.email',
    'https://www.googleapis.com/auth/calendar.events',
]


class GoogleError(Exception):
    """Error al hablar con Google (credenciales vencidas, API deshabilitada, sin internet...)."""


def configurado():
    """Hay credenciales OAuth cargadas en el .env."""
    return bool(settings.GOOGLE_CLIENT_ID and settings.GOOGLE_CLIENT_SECRET)


def librerias_instaladas():
    try:
        import googleapiclient  # noqa: F401
        import google_auth_oauthlib  # noqa: F401
        import cryptography  # noqa: F401
    except ImportError:
        return False
    return True


def disponible():
    return configurado() and librerias_instaladas()


# ---------- Cifrado de los tokens ----------

def _fernet():
    from cryptography.fernet import Fernet
    clave = settings.GOOGLE_TOKEN_KEY
    if not clave:
        # Deriva una clave válida para Fernet (32 bytes en base64) a partir de SECRET_KEY
        clave = base64.urlsafe_b64encode(hashlib.sha256(settings.SECRET_KEY.encode()).digest())
    return Fernet(clave)


def cifrar(texto):
    return _fernet().encrypt(texto.encode()).decode() if texto else None


def descifrar(texto):
    return _fernet().decrypt(texto.encode()).decode() if texto else None


# ---------- OAuth: vincular la cuenta ----------

def _flow(state=None):
    from google_auth_oauthlib.flow import Flow
    if settings.DEBUG:
        # En desarrollo el callback es http://127.0.0.1 (sin HTTPS)
        os.environ.setdefault('OAUTHLIB_INSECURE_TRANSPORT', '1')
    # Google puede devolver los scopes en otro orden o con alias: no se trata como error
    os.environ.setdefault('OAUTHLIB_RELAX_TOKEN_SCOPE', '1')
    return Flow.from_client_config(
        {'web': {
            'client_id': settings.GOOGLE_CLIENT_ID,
            'client_secret': settings.GOOGLE_CLIENT_SECRET,
            'auth_uri': 'https://accounts.google.com/o/oauth2/auth',
            'token_uri': 'https://oauth2.googleapis.com/token',
            'redirect_uris': [settings.GOOGLE_REDIRECT_URI],
        }},
        scopes=SCOPES, state=state, redirect_uri=settings.GOOGLE_REDIRECT_URI,
    )


def url_autorizacion():
    """Devuelve (url de Google, state, code_verifier). state y code_verifier se guardan en la sesión."""
    flow = _flow()
    # access_type=offline + prompt=consent: Google devuelve el refresh_token para renovar el acceso
    url, state = flow.authorization_url(access_type='offline', prompt='consent', include_granted_scopes='true')
    return url, state, getattr(flow, 'code_verifier', None)


def vincular(usuario, url_respuesta, state, code_verifier):
    """Cambia el código que devolvió Google por los tokens y los guarda en google_cuenta."""
    from googleapiclient.discovery import build
    from .models import GoogleCuenta

    flow = _flow(state)
    if code_verifier:
        flow.code_verifier = code_verifier
    try:
        flow.fetch_token(authorization_response=url_respuesta)
        cred = flow.credentials
        info = build('oauth2', 'v2', credentials=cred, cache_discovery=False).userinfo().get().execute()
    except Exception as e:  # noqa: BLE001 - cualquier error de Google se informa igual
        logger.exception('No se pudo vincular la cuenta de Google')
        raise GoogleError(str(e)) from e

    anterior = GoogleCuenta.objects.filter(usuario=usuario).first()
    return GoogleCuenta.objects.update_or_create(
        usuario=usuario,
        defaults={
            'google_email': info.get('email', ''),
            'access_token': cifrar(cred.token),
            # Si Google no manda un refresh_token nuevo, se conserva el anterior
            'refresh_token': cifrar(cred.refresh_token) if cred.refresh_token else (anterior.refresh_token if anterior else None),
            'token_expira': cred.expiry.replace(tzinfo=dt_timezone.utc) if cred.expiry else None,
            'scopes': ' '.join(cred.scopes or SCOPES),
            'vinculado_en': timezone.now(),
        },
    )[0]


def desvincular(cuenta):
    """Revoca el acceso en Google (si se puede) y borra la cuenta vinculada."""
    try:
        import requests  # viene con google-auth
        token = descifrar(cuenta.refresh_token) or descifrar(cuenta.access_token)
        requests.post('https://oauth2.googleapis.com/revoke', params={'token': token}, timeout=10)
    except Exception:  # noqa: BLE001 - si falla la revocación, igual se desvincula
        logger.warning('No se pudo revocar el token de Google', exc_info=True)
    cuenta.delete()


# ---------- Calendar ----------

def _servicio(cuenta):
    """Cliente de Google Calendar con las credenciales del usuario; si el token venció, lo renueva y lo guarda."""
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build

    cred = Credentials(
        token=descifrar(cuenta.access_token),
        refresh_token=descifrar(cuenta.refresh_token),
        token_uri='https://oauth2.googleapis.com/token',
        client_id=settings.GOOGLE_CLIENT_ID,
        client_secret=settings.GOOGLE_CLIENT_SECRET,
        scopes=(cuenta.scopes or '').split() or SCOPES,
        # google-auth compara la expiración sin zona horaria (en UTC)
        expiry=cuenta.token_expira.astimezone(dt_timezone.utc).replace(tzinfo=None) if cuenta.token_expira else None,
    )
    if not cred.valid:
        if not cred.refresh_token:
            raise GoogleError('La cuenta de Google no tiene permiso para renovar el acceso. Volvé a vincularla.')
        try:
            cred.refresh(Request())
        except Exception as e:  # noqa: BLE001
            raise GoogleError('Venció el acceso a Google. Volvé a vincular tu cuenta.') from e
        cuenta.access_token = cifrar(cred.token)
        cuenta.token_expira = cred.expiry.replace(tzinfo=dt_timezone.utc) if cred.expiry else None
        cuenta.save(update_fields=['access_token', 'token_expira'])
    return build('calendar', 'v3', credentials=cred, cache_discovery=False)


def _ejecutar(peticion):
    try:
        return peticion.execute()
    except Exception as e:  # noqa: BLE001
        logger.exception('Error de Google Calendar')
        raise GoogleError(str(e)) from e


def _url_consora(ruta=''):
    """Dirección de la web de CONSORA (la misma base que la URI de redirección de Google)."""
    base = settings.GOOGLE_REDIRECT_URI.split('/google/')[0]
    return f'{base}{ruta}'


def _descripcion_evento(reunion):
    """Descripción del evento con formato de CONSORA. Google Calendar acepta un HTML básico
    (negrita, cursiva, saltos de línea, listas y links) y la muestra igual en el mail de invitación."""
    from django.utils.html import escape, linebreaks
    from django.urls import reverse

    c = reunion.consorcio
    organizador = reunion.organizador
    partes = [
        '<b>CONSORA</b> · Gestión inteligente de consorcios',
        '━━━━━━━━━━━━━━━━━━━━',
        f'🏢 <b>{escape(c.name_consorcio)}</b>' + (f' · {escape(c.direccion)}' if c.direccion else ''),
        f'👤 Organiza: <b>{escape(organizador.name_surname)}</b>, administrador del consorcio',
    ]
    if reunion.descripcion:
        partes += ['', '<b>📋 Orden del día</b>', '<br>'.join(escape(reunion.descripcion).splitlines())]
    documentos = list(reunion.documentos.all()) if reunion.pk else []
    if documentos:
        partes += ['', '<b>📎 Documentos</b> (disponibles en CONSORA)',
                   '<ul>' + ''.join(f'<li>{escape(d.nombre)}</li>' for d in documentos) + '</ul>']
    url = _url_consora(reverse('reunion_detalle', args=[reunion.pk])) if reunion.pk else _url_consora()
    partes += [
        '',
        f'👉 <a href="{url}">Ver la reunión en CONSORA</a> para confirmar tu asistencia y ver los documentos.',
        '',
        '<i>Invitación generada automáticamente desde la web de CONSORA.</i>',
    ]
    return '<br>'.join(partes)


def _cuerpo_evento(reunion, emails):
    zona = settings.TIME_ZONE
    return {
        'summary': f'CONSORA | {reunion.titulo} · {reunion.consorcio.name_consorcio}',
        'description': _descripcion_evento(reunion),
        'start': {'dateTime': timezone.localtime(reunion.fecha_inicio).isoformat(), 'timeZone': zona},
        'end': {'dateTime': timezone.localtime(reunion.fecha_fin).isoformat(), 'timeZone': zona},
        'attendees': [{'email': e} for e in sorted(set(emails)) if e],
        # En el calendario del organizador: color azul y link de origen a CONSORA
        'colorId': '9',
        'source': {'title': 'CONSORA', 'url': _url_consora()},
        'reminders': {'useDefault': False, 'overrides': [
            {'method': 'popup', 'minutes': 30}, {'method': 'email', 'minutes': 24 * 60},
        ]},
    }


def actualizar_evento(reunion, avisar=False):
    """Vuelve a escribir título, descripción y horario del evento (por ejemplo, al sumar documentos)."""
    if not reunion.google_event_id:
        return
    cuerpo = _cuerpo_evento(reunion, [])
    cuerpo.pop('attendees')
    servicio = _servicio(reunion.organizador.google_cuenta)
    _ejecutar(servicio.events().patch(
        calendarId='primary', eventId=reunion.google_event_id, body=cuerpo,
        sendUpdates='all' if avisar else 'none',
    ))


def crear_evento(reunion, emails):
    """Crea el evento con su Meet en el calendario del organizador. Devuelve (link, código, id del evento)."""
    servicio = _servicio(reunion.organizador.google_cuenta)
    cuerpo = _cuerpo_evento(reunion, emails)
    cuerpo['conferenceData'] = {'createRequest': {
        'requestId': uuid.uuid4().hex,
        'conferenceSolutionKey': {'type': 'hangoutsMeet'},
    }}
    evento = _ejecutar(servicio.events().insert(
        calendarId='primary', body=cuerpo, conferenceDataVersion=1, sendUpdates='all',
    ))
    link = evento.get('hangoutLink')
    codigo = (evento.get('conferenceData') or {}).get('conferenceId')
    return link, codigo, evento.get('id')


def agregar_invitado(reunion, email):
    """Suma un invitado al evento (por ejemplo, el proveedor que aceptó); Google le manda la invitación."""
    if not reunion.google_event_id or not email:
        return
    servicio = _servicio(reunion.organizador.google_cuenta)
    evento = _ejecutar(servicio.events().get(calendarId='primary', eventId=reunion.google_event_id))
    invitados = evento.get('attendees', [])
    if any(a.get('email', '').lower() == email.lower() for a in invitados):
        return
    invitados.append({'email': email})
    _ejecutar(servicio.events().patch(
        calendarId='primary', eventId=reunion.google_event_id,
        body={'attendees': invitados}, sendUpdates='all',
    ))


def cancelar_evento(reunion):
    """Borra el evento del calendario; Google avisa a los invitados."""
    if not reunion.google_event_id:
        return
    servicio = _servicio(reunion.organizador.google_cuenta)
    _ejecutar(servicio.events().delete(calendarId='primary', eventId=reunion.google_event_id, sendUpdates='all'))
