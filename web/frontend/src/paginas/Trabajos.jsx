import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, DJANGO } from '../api'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useAvisos, useTitulo } from '../componentes/Layout'
import { fecha, moneda } from '../formatos'

export default function Trabajos() {
  const [params, setParams] = useSearchParams()
  const avisar = useAvisos()
  const vista = params.get('vista') || 'proceso'
  const consorcio = params.get('consorcio') || ''
  const [q, setQ] = useState(params.get('q') || '')
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')

  const cargar = useCallback(() => {
    const p = new URLSearchParams({ vista })
    if (consorcio) p.set('consorcio', consorcio)
    if (q.trim()) p.set('q', q.trim())
    api.get('/trabajos/?' + p).then(setDatos).catch(e => setError(e.message))
  }, [vista, consorcio, q])

  useEffect(() => { const t = setTimeout(cargar, 250); return () => clearTimeout(t) }, [cargar])

  useTitulo(datos?.es_proveedor ? 'Mis trabajos' : 'Trabajos', 'Trabajos contratados a proveedores: en proceso y terminados')

  const filtro = (k, v) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v); else p.delete(k)
    setParams(p)
  }

  const terminar = async t => {
    if (!window.confirm('¿Marcar el trabajo como terminado?')) return
    try {
      const r = await api.post(`/contratos/${t.id}/`, { accion: 'terminar_trabajo' })
      avisar(r.mensaje)
      cargar()
    } catch (err) {
      avisar(err.message, 'error')
    }
  }

  const reabrir = async t => {
    try {
      const r = await api.post(`/contratos/${t.id}/`, { accion: 'reabrir_trabajo' })
      avisar(r.mensaje)
      cargar()
    } catch (err) {
      avisar(err.message, 'error')
    }
  }

  if (error) return <ErrorCarga error={error} />

  return (
    <>
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>Trabajos en proceso y terminados</h2>
          <p>Cada trabajo sale de un contrato con un proveedor. Se marca terminado aparte de cómo se va pagando.</p>
        </div>
        {datos && !datos.es_proveedor && (
          <Link to="/contratos" className="btn btn-outline-primary btn-sm px-3"><i className="bi bi-file-earmark-text me-1" /> Contratos y cuotas</Link>
        )}
      </div>

      <div className="vote-filters">
        {datos?.mis_consorcios.length > 0 && (
          <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
            <option value="">Todos los consorcios</option>
            {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        )}
        <input type="search" className="form-control form-control-sm" style={{ maxWidth: 260 }} placeholder="Buscar trabajo o proveedor"
          value={q} onChange={e => setQ(e.target.value)} />
        <div className="vote-chips">
          <button type="button" className={'vote-chip' + (vista !== 'terminados' ? ' active' : '')} onClick={() => filtro('vista', '')}>
            En proceso{datos ? ` (${datos.cant_proceso})` : ''}
          </button>
          <button type="button" className={'vote-chip' + (vista === 'terminados' ? ' active' : '')} onClick={() => filtro('vista', 'terminados')}>
            Terminados{datos ? ` (${datos.cant_terminados})` : ''}
          </button>
        </div>
      </div>

      {!datos ? <Cargando /> : (
        <div className="panel-card" style={{ height: 'auto' }}>
          {datos.trabajos.map(t => (
            <div className="work-row" key={t.id}>
              {/* Foto del proveedor (o del edificio, si mira el proveedor) con el estado del trabajo */}
              {datos.es_proveedor ? (
                <div className="work-avatar edificio">
                  {t.consorcio.img ? <img src={t.consorcio.img} alt="" /> : <i className="bi bi-building" />}
                  <span className={'work-badge' + (t.trabajo_terminado_en ? ' ok' : '')}><i className={'bi ' + (t.trabajo_terminado_en ? 'bi-check' : 'bi-tools')} /></span>
                </div>
              ) : (
                <Link to={`/marketplace/proveedor/${t.proveedor.id}`} className="work-avatar" title={'Ver perfil de ' + t.proveedor.nombre}>
                  <Avatar img={t.proveedor.img} iniciales={t.proveedor.iniciales} clase="" />
                  <span className={'work-badge' + (t.trabajo_terminado_en ? ' ok' : '')}><i className={'bi ' + (t.trabajo_terminado_en ? 'bi-check' : 'bi-tools')} /></span>
                </Link>
              )}
              <div className="flex-grow-1 min-w-0">
                <Link to={`/contratos/${t.id}`} className="history-text text-decoration-none">{t.descripcion}</Link>
                <div className="invoice-meta">
                  {datos.es_proveedor
                    ? <span><i className="bi bi-building" /> {t.consorcio.nombre}</span>
                    : <><Link to={`/marketplace/proveedor/${t.proveedor.id}`} className="work-prov">{t.proveedor.nombre}</Link> · <span>{t.consorcio.nombre}</span></>}
                  {' · '}
                  {t.trabajo_terminado_en
                    ? <><span className="text-success fw-semibold">Terminado el {fecha(t.trabajo_terminado_en)}</span> <span className="text-secondary">({t.dias} día{t.dias === 1 ? '' : 's'})</span></>
                    : <><span>Desde el {fecha(t.fecha)}</span> <span className="text-secondary">· hace {t.dias} día{t.dias === 1 ? '' : 's'}</span></>}
                </div>
                <div className="work-pay">
                  <div className="progress" style={{ height: 5 }}>
                    <div className={'progress-bar' + (t.estado === 'finalizado' ? ' bg-success' : '')} style={{ width: t.porcentaje + '%' }} />
                  </div>
                  <span>{t.estado === 'finalizado'
                    ? `Pagado completo · ${moneda(t.precio)}`
                    : `${t.cant_pagas} de ${t.cant_cuotas} pago${t.cant_cuotas === 1 ? '' : 's'} · ${moneda(t.pagado)} de ${moneda(t.precio)}`}</span>
                </div>
              </div>
              {t.trabajo_terminado_en
                ? t.gestiona && <button type="button" className="btn btn-link btn-sm text-secondary text-nowrap" onClick={() => reabrir(t)}><i className="bi bi-arrow-counterclockwise" /> Reabrir</button>
                : (t.gestiona || t.es_el_proveedor) && <button type="button" className="btn btn-outline-success btn-sm text-nowrap" onClick={() => terminar(t)}><i className="bi bi-check2" /> Terminado</button>}
            </div>
          ))}
          {!datos.trabajos.length && (
            <Vacio chico icono="bi-tools" texto={vista === 'terminados' ? 'Todavía no hay trabajos terminados.' : 'No hay trabajos en proceso.'} />
          )}
        </div>
      )}
    </>
  )
}
