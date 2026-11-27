import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { useSesion } from '../sesion'
import Avatar from './Avatar'

const PaginaContext = createContext(null)

/** Cada página indica su título y subtítulo para la barra superior. */
export function useTitulo(titulo, subtitulo) {
  const { setTitulos } = useContext(PaginaContext)
  useEffect(() => { setTitulos({ titulo, subtitulo }) }, [titulo, subtitulo, setTitulos])
}

/** Avisos de éxito o error arriba del contenido (como los mensajes de Django). */
export function useAvisos() {
  return useContext(PaginaContext).avisar
}

/** Ítem del menú: las secciones ya hechas en React navegan dentro de la app; el resto abre la web en Django. */
function Item({ a, django, icono, children, activo }) {
  if (a) {
    return (
      <NavLink to={a} end={a === '/'} className={({ isActive }) => 'sidebar-link' + (isActive || activo ? ' active' : '')}>
        <i className={'bi ' + icono} /> {children}
      </NavLink>
    )
  }
  return (
    <a href={django ? DJANGO + django : '#'} className="sidebar-link">
      <i className={'bi ' + icono} /> {children}
    </a>
  )
}

function Grupo({ titulo, children }) {
  return (
    <div className="nav-group">
      <div className="nav-group-label">{titulo}</div>
      {children}
    </div>
  )
}

function MenuAdmin() {
  const { pathname } = useLocation()
  return (
    <>
      <Grupo titulo="MOVIMIENTOS">
        <Item a="/" icono="bi-grid-1x2">Dashboard</Item>
        <Item icono="bi-file-earmark-bar-graph">Resumen</Item>
        <Item a="/historial" icono="bi-clock-history">Historial</Item>
      </Grupo>
      <Grupo titulo="SOLICITUDES">
        <Item icono="bi-list-check">Todas las solicitudes</Item>
        <Item icono="bi-plus-circle">Nueva solicitud</Item>
      </Grupo>
      <Grupo titulo="PROVEEDORES">
        <Item a="/marketplace" icono="bi-shop" activo={pathname.startsWith('/marketplace/proveedor')}>Marketplace</Item>
        <Item a="/contratos" icono="bi-file-earmark-text">Contratos y cuotas</Item>
        <Item a="/trabajos" icono="bi-clipboard-check">Trabajos en proceso y terminados</Item>
        <Item a="/facturas" icono="bi-receipt">Facturas procesadas</Item>
      </Grupo>
      <Grupo titulo="CONSORCIOS">
        <Item a="/consorcios" icono="bi-buildings" activo={pathname.startsWith('/consorcios')}>Edificios</Item>
        <Item icono="bi-people">Propietarios y residentes</Item>
        <Item icono="bi-folder2-open">Documentación</Item>
        <Item a="/agenda" icono="bi-calendar-event">Actividades</Item>
        <Item a="/votaciones" icono="bi-check2-square" activo={pathname.startsWith('/votaciones')}>Votaciones</Item>
        <Item a="/reuniones" icono="bi-camera-video" activo={pathname.startsWith('/reuniones')}>Reuniones</Item>
        <Item a="/marketplace/consorcios" icono="bi-shop">Marketplace</Item>
      </Grupo>
      <Grupo titulo="EQUIPO">
        <Item a="/asistentes" icono="bi-person-badge">Asistentes</Item>
      </Grupo>
      <Grupo titulo="COMUNICACIÓN">
        <Item a="/chat" icono="bi-chat-dots" activo={pathname.startsWith('/chat')}>Chat</Item>
        <Item icono="bi-bell">Notificaciones</Item>
      </Grupo>
    </>
  )
}

function MenuRoles({ usuario }) {
  const { pathname } = useLocation()
  const titulo = usuario.es_asistente ? 'MI TRABAJO' : usuario.es_proveedor ? 'MI EMPRESA' : 'MI CONSORCIO'
  return (
    <Grupo titulo={titulo}>
      <Item a="/" icono="bi-house">Inicio</Item>
      {usuario.es_asistente && <Item a="/consorcios" icono="bi-buildings" activo={pathname.startsWith('/consorcios')}>Edificios habilitados</Item>}
      {usuario.es_propietario && <Item a="/consorcios" icono="bi-building" activo={pathname.startsWith('/consorcios')}>Mi edificio y expensas</Item>}
      {!usuario.es_proveedor && (
        <>
          <Item a="/votaciones" icono="bi-check2-square" activo={pathname.startsWith('/votaciones')}>Votaciones</Item>
          <Item a="/agenda" icono="bi-calendar-event">Agenda</Item>
          <Item a="/historial" icono="bi-clock-history">Historial {usuario.es_propietario ? 'del edificio' : 'de los edificios'}</Item>
        </>
      )}
      <Item a="/reuniones" icono="bi-camera-video" activo={pathname.startsWith('/reuniones')}>Reuniones</Item>
      <Item a="/contratos" icono="bi-file-earmark-text">{usuario.es_proveedor ? 'Mis contratos' : 'Contratos'}</Item>
      {usuario.es_proveedor && (
        <>
          <Item a="/trabajos" icono="bi-clipboard-check">Mis trabajos</Item>
          <Item a={`/marketplace/proveedor/${usuario.id}`} icono="bi-person-badge">Mi perfil del Marketplace</Item>
        </>
      )}
    </Grupo>
  )
}

function MenuComunicacion() {
  const { pathname } = useLocation()
  return (
    <Grupo titulo="COMUNICACIÓN">
      <Item a="/chat" icono="bi-chat-dots" activo={pathname.startsWith('/chat')}>Chat</Item>
      <Item a="/marketplace" icono="bi-shop" activo={pathname.startsWith('/marketplace')}>Marketplace</Item>
    </Grupo>
  )
}

export default function Layout({ children }) {
  const { usuario, salir } = useSesion()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [titulos, setTitulos] = useState({ titulo: '', subtitulo: '' })
  const [avisos, setAvisos] = useState([])
  const [menuAbierto, setMenuAbierto] = useState(false)

  const avisar = useCallback((texto, tipo = 'success') => {
    const id = Date.now() + Math.random()
    setAvisos(a => [...a, { id, texto, tipo }])
    setTimeout(() => setAvisos(a => a.filter(x => x.id !== id)), 6000)
  }, [])

  // Avisos que dejó Django en la sesión (por ejemplo, al volver de vincular la cuenta de Google)
  useEffect(() => {
    api.get('/mensajes/').then(r => r.mensajes.forEach(m => avisar(m.texto, m.tipo))).catch(() => {})
  }, [avisar])

  // Al cambiar de página se cierra el menú (celular) y se vuelve arriba
  useEffect(() => { setMenuAbierto(false); window.scrollTo(0, 0) }, [pathname])

  const cerrarSesion = async () => {
    await salir()
    navigate('/login')
  }

  return (
    <PaginaContext.Provider value={{ setTitulos, avisar }}>
      <div className="admin-shell">
        <aside className={'admin-sidebar' + (menuAbierto ? ' open' : '')}>
          <Link to="/" className="sidebar-brand">
            <img src="/static/img/logo.png" alt="Consora" />
            <span>Consora</span>
          </Link>
          <nav className="sidebar-nav">
            {usuario.es_admin ? <MenuAdmin /> : <><MenuRoles usuario={usuario} /><MenuComunicacion /></>}
            <Grupo titulo="CUENTA">
              <Item a="/cuenta" icono="bi-person-circle">Mi cuenta</Item>
              <button type="button" className="sidebar-link border-0 bg-transparent w-100 text-start" onClick={cerrarSesion}>
                <i className="bi bi-box-arrow-left" /> Cerrar sesión
              </button>
            </Grupo>
          </nav>
          <div className="sidebar-footer">© 2026 Consora</div>
        </aside>
        {menuAbierto && <div className="app-sidebar-fondo" onClick={() => setMenuAbierto(false)} />}

        <div className="admin-main">
          <header className="admin-topbar">
            <button type="button" className="sidebar-toggle" onClick={() => setMenuAbierto(a => !a)}>
              <i className="bi bi-list" />
            </button>
            <div className="topbar-title flex-grow-1">
              <h4>{titulos.titulo}</h4>
              <small>{titulos.subtitulo}</small>
            </div>
            <Link to="/chat" className="topbar-icon-btn" title="Chat">
              <i className="bi bi-chat-dots-fill" />
            </Link>
            <Link to="/cuenta" className="user-chip text-decoration-none text-reset">
              <Avatar img={usuario.img} iniciales={usuario.iniciales} clase="user-avatar" />
              <div className="d-none d-md-block">
                <div style={{ fontWeight: 600, fontSize: '0.88rem' }}>{usuario.nombre}</div>
                <small className="text-secondary">{usuario.mail}</small>
              </div>
            </Link>
          </header>

          <main className="admin-content">
            {avisos.length > 0 && (
              <div className="page-messages">
                {avisos.map(a => (
                  <div key={a.id} className={`alert alert-${a.tipo === 'error' ? 'danger' : a.tipo} alert-dismissible py-2`} role="alert">
                    {a.texto}
                    <button type="button" className="btn-close py-2" onClick={() => setAvisos(x => x.filter(y => y.id !== a.id))} />
                  </div>
                ))}
              </div>
            )}
            {children}
          </main>
        </div>
      </div>
    </PaginaContext.Provider>
  )
}
