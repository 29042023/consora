import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useSesion } from '../sesion'

export default function Login() {
  const { usuario, ingresar } = useSesion()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  if (usuario) return <Navigate to={location.state?.desde || '/'} replace />

  const enviar = async e => {
    e.preventDefault()
    if (!email.trim() || !password) { setError('Completá el email y la contraseña.'); return }
    setEnviando(true)
    setError('')
    try {
      await ingresar(email.trim(), password)
      navigate(location.state?.desde || '/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-brand">
        <div className="brand-overlay" />
        <div className="brand-content">
          <span className="brand-logo"><img src="/static/img/logo.png" alt="Consora" /></span>
          <span className="brand-badge"><i className="bi bi-stars" /> Gestión inteligente de consorcios</span>
          <h1>Todo tu edificio. <span>En un solo lugar.</span></h1>
          <p>Gestioná reclamos, documentación, proveedores, presupuestos y comunicación desde una única plataforma.</p>
          <div className="brand-features">
            <div><i className="bi bi-check-circle-fill" /><span>Información centralizada</span></div>
            <div><i className="bi bi-check-circle-fill" /><span>Seguimiento de cada reclamo</span></div>
            <div><i className="bi bi-check-circle-fill" /><span>Comunicación simple y transparente</span></div>
          </div>
        </div>
        <div className="brand-watermark">CONSORA</div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="mobile-logo"><img src="/static/img/logo.png" alt="Consora" /></div>
          <div className="login-heading">
            <span className="section-label">BIENVENIDO</span>
            <h2>Iniciar sesión</h2>
            <p>Accedé a tu cuenta para continuar con la gestión de tu consorcio.</p>
          </div>

          {error && <div className="alert alert-danger py-2 small">{error}</div>}

          <form onSubmit={enviar} noValidate>
            <div className="form-group">
              <label htmlFor="email">Email</label>
              <div className="input-wrapper">
                <i className="bi bi-person" />
                <input id="email" type="email" className="form-control custom-input" placeholder="tu@email.com"
                  autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} />
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="password">Contraseña</label>
              <div className="input-wrapper">
                <i className="bi bi-lock" />
                <input id="password" type={verClave ? 'text' : 'password'} className="form-control custom-input"
                  placeholder="••••••••" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} />
                <button type="button" className="password-toggle" aria-label="Mostrar contraseña" onClick={() => setVerClave(v => !v)}>
                  <i className={'bi ' + (verClave ? 'bi-eye-slash' : 'bi-eye')} />
                </button>
              </div>
            </div>
            <button type="submit" className="login-btn" disabled={enviando}>
              {enviando ? 'Ingresando…' : <>Ingresar <i className="bi bi-arrow-right" /></>}
            </button>
          </form>
        </div>
      </section>
    </main>
  )
}
