# CONSORA

Plataforma de gestión de consorcios: administraciones, propietarios, asistentes y proveedores en un mismo lugar.
Proyecto de tesis.

| Carpeta | Contenido |
|---|---|
| `web/consora` | Backend en **Django 5** + **Django REST Framework** (API JSON) y base **MySQL/MariaDB** |
| `web/frontend` | Interfaz web en **React 18** (Vite, React Router, Bootstrap 5) |
| `mobile` | Aplicación **Android** en **Kotlin** con base local **SQLite** |

## Web

Requisitos: Python 3.11, MySQL o MariaDB, Node.js 20 o superior.

```bash
cd web
python -m venv venv
venv\Scripts\activate            # en Linux/Mac: source venv/bin/activate
pip install -r requirements.txt

cd consora
copy .env.example .env           # completar SECRET_KEY y DB_PASSWORD (ver comentarios del archivo)
```

Crear la base vacía `consora_db` en MySQL y después:

```bash
python manage.py migrate
python manage.py seed_data       # carga los datos de ejemplo
```

Compilar la interfaz en React (Django la sirve en `/app/`):

```bash
cd ../frontend
npm install
npm run build
```

Levantar el servidor y entrar a **http://127.0.0.1:8000/app/**:

```bash
cd ../consora
python manage.py runserver
```

Para trabajar en la interfaz con recarga automática: `npm run dev` en `web/frontend` y abrir **http://127.0.0.1:5173/app/** (con Django corriendo).

### Usuarios de ejemplo

Todos con la contraseña `consora123`:

| Rol | Email |
|---|---|
| Administración | `tomasfrancorusso@gmail.com` |
| Propietario | `carlos.perez@mail.com` · `ana.fernandez@mail.com` |
| Asistente | `valentina.lopez@consora.com.ar` |
| Proveedor | `cimeco.adm@gmail.com` · `info@ascensur.com.ar` |

### Google Meet (opcional)

Para que las reuniones virtuales se creen solas en Google Calendar con su link de Meet, cargar en `.env`
las credenciales OAuth (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) de un cliente "Aplicación web" de Google Cloud,
con la URI de redirección `http://127.0.0.1:8000/google/callback/`. Sin ellas, el link se carga a mano.

## Mobile

Abrir la carpeta `mobile` en Android Studio y ejecutar en un emulador o dispositivo con Android 8.0 o superior.
La app trae sus propios datos de ejemplo (mismos usuarios y contraseña que la web).

## Seguridad

El archivo `.env` (clave secreta, contraseña de la base y credenciales de Google) **no se sube** al repositorio:
cada instalación usa el suyo a partir de `.env.example`.
