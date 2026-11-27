"""Autenticación y permisos de la API (aparte de api.py para que DRF los pueda importar desde settings)."""
from rest_framework.authentication import SessionAuthentication
from rest_framework.permissions import BasePermission

from .models import Usuario


class SesionConsora(SessionAuthentication):
    """El usuario sale de la sesión de CONSORA; los POST exigen el token CSRF (cabecera X-CSRFToken)."""

    def authenticate(self, request):
        from .views import _get_logged_usuario
        usuario = _get_logged_usuario(request._request)
        if usuario is None:
            return None
        self.enforce_csrf(request)
        return usuario, None


class Logueado(BasePermission):
    def has_permission(self, request, view):
        return isinstance(request.user, Usuario)
