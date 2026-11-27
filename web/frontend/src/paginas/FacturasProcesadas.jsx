import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import { fecha, moneda } from '../formatos'

export default function FacturasProcesadas() {
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') || '')
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')

  useTitulo('Facturas procesadas', 'Facturas cargadas de todos tus consorcios')

  useEffect(() => {
    const t = setTimeout(() => {
      const p = new URLSearchParams(params)
      if (q.trim()) p.set('q', q.trim()); else p.delete('q')
      api.get('/facturas/?' + p).then(setDatos).catch(e => setError(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [params, q])

  const filtro = (k, v) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v); else p.delete(k)
    setParams(p)
  }

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />
  const consorcio = params.get('consorcio') || ''
  const hayFiltros = consorcio || params.get('tipo') || q || params.get('desde') || params.get('hasta')

  return (
    <>
      <div className="page-header-row">
        <h2>Facturas procesadas</h2>
        <p>Facturas cargadas de todos tus consorcios. Se cargan desde la Facturación de cada edificio.</p>
      </div>

      <div className="vote-filters">
        <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
          <option value="">Todos los consorcios</option>
          {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <select className="form-select form-select-sm" value={params.get('tipo') || ''} onChange={e => filtro('tipo', e.target.value)}>
          <option value="">Todos los tipos</option>
          {datos.tipos.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
        </select>
        <input type="search" className="form-control form-control-sm" style={{ maxWidth: 240 }} placeholder="Proveedor o N° de comprobante" value={q} onChange={e => setQ(e.target.value)} />
        <div className="d-flex align-items-center gap-2 small">
          <span className="text-secondary">Desde</span>
          <input type="date" className="form-control form-control-sm" value={params.get('desde') || ''} onChange={e => filtro('desde', e.target.value)} />
          <span className="text-secondary">hasta</span>
          <input type="date" className="form-control form-control-sm" value={params.get('hasta') || ''} onChange={e => filtro('hasta', e.target.value)} />
        </div>
        {hayFiltros && <button type="button" className="btn btn-link btn-sm p-0" onClick={() => { setQ(''); setParams({}) }}>Limpiar filtros</button>}
      </div>

      <div className="row g-4">
        <div className="col-lg-8">
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header d-flex justify-content-between align-items-center">
              <h5 className="mb-0">{datos.cantidad} factura{datos.cantidad === 1 ? '' : 's'}</h5>
              <strong>{moneda(datos.total)}</strong>
            </div>
            {datos.facturas.map(f => (
              <div className="invoice-row" key={f.id}>
                <div className="invoice-icon"><i className="bi bi-receipt" /></div>
                <div className="flex-grow-1 min-w-0">
                  <div className="invoice-title">{f.descripcion}</div>
                  <div className="invoice-meta">
                    <span className="chip-soft">{f.tipo}</span> {f.proveedor} · N° {f.nro_comprobante} · {fecha(f.fecha)}
                    {!consorcio && <> · <Link to={`/consorcios/${f.consorcio.id}/facturacion`} className="text-reset">{f.consorcio.nombre}</Link></>}
                    {f.contrato_id && <> · <Link to={`/contratos/${f.contrato_id}`}>contrato</Link></>}
                  </div>
                  <div className="small text-secondary">Cargada por {f.cargada_por}</div>
                </div>
                <div className="text-end">
                  <div className="invoice-amount">{moneda(f.importe)}</div>
                  {f.archivo && <a href={f.archivo} target="_blank" rel="noreferrer" className="small"><i className="bi bi-paperclip" /> Ver archivo</a>}
                </div>
              </div>
            ))}
            {!datos.facturas.length && <Vacio chico icono="bi-receipt" texto={hayFiltros ? 'No hay facturas con esos filtros.' : 'Todavía no se cargaron facturas.'} />}
          </div>
        </div>
        <div className="col-lg-4">
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Gasto por tipo</h5></div>
            {datos.por_tipo.map(t => (
              <div className="type-sum" key={t.tipo}>
                <div className="d-flex justify-content-between small">
                  <span className="fw-semibold">{t.tipo} <span className="text-secondary fw-normal">({t.cant})</span></span>
                  <span>{moneda(t.total)}</span>
                </div>
                <div className="progress mt-1" style={{ height: 6 }}><div className="progress-bar" style={{ width: t.porcentaje + '%' }} /></div>
              </div>
            ))}
            {!datos.por_tipo.length && <p className="small text-secondary mb-0">Sin datos.</p>}
          </div>
        </div>
      </div>
    </>
  )
}
