import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import NuevoContrato from '../componentes/NuevoContrato'
import TabsConsorcio from '../componentes/TabsConsorcio'
import { fecha, moneda } from '../formatos'

const ESTADOS = [['', 'Todos'], ['vigente', 'Vigentes'], ['finalizado', 'Finalizados'], ['cancelado', 'Cancelados']]

/** Tarjeta de un contrato: forma de pago, estado, avance de pagos y próximo vencimiento. */
export function TarjetaContrato({ c, esProveedor, conConsorcio, hoy }) {
  return (
    <Link to={`/contratos/${c.id}`} className="vote-card">
      <div className="d-flex align-items-center justify-content-between gap-2 mb-2">
        <span className={'contract-pay ' + (c.es_contado ? 'contado' : 'cuotas')}>
          <i className={'bi ' + (c.es_contado ? 'bi-cash' : 'bi-calendar3')} /> {c.forma_pago}
        </span>
        <span className={'contract-state ' + c.estado}>{c.estado_texto}</span>
      </div>
      <p className="vote-detail mb-1">
        {c.descripcion} {c.contrato_pdf && <i className="bi bi-file-earmark-pdf text-danger" title="Tiene el contrato formal en PDF" />}
      </p>
      <div className="vote-meta mb-2">
        {esProveedor
          ? <span><i className="bi bi-building" /> {c.consorcio.nombre}</span>
          : <>
              <span><i className="bi bi-person-workspace" /> {c.proveedor.nombre}</span>
              {conConsorcio && <span><i className="bi bi-building" /> {c.consorcio.nombre}</span>}
            </>}
      </div>
      <div className="contract-progress">
        <div className="d-flex justify-content-between small mb-1">
          <span className="fw-semibold">{c.cant_pagas} de {c.cant_cuotas} pago{c.cant_cuotas === 1 ? '' : 's'}</span>
          <span className="text-secondary">{moneda(c.pagado)} de {moneda(c.precio)}</span>
        </div>
        <div className="progress" style={{ height: 6 }}>
          <div className={'progress-bar' + (c.estado === 'finalizado' ? ' bg-success' : '')} style={{ width: c.porcentaje + '%' }} />
        </div>
      </div>
      <div className="vote-foot">
        {c.estado === 'vigente' && c.proxima
          ? <span className={c.proxima.vencimiento < hoy ? 'text-danger fw-semibold' : ''}>
              <i className="bi bi-hourglass-split" /> Próximo pago {moneda(c.proxima.importe)} · vence {fecha(c.proxima.vencimiento)}
            </span>
          : c.estado === 'finalizado'
            ? <span className="text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Pagado completo</span>
            : <span><i className="bi bi-x-circle" /> Cancelado</span>}
      </div>
    </Link>
  )
}

export default function Contratos() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const consorcio = params.get('consorcio') || ''
  const estado = params.get('estado') || ''
  const propuesta = params.get('propuesta') || ''
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(Boolean(propuesta))

  const cargar = useCallback(() => {
    const q = new URLSearchParams()
    if (consorcio) q.set('consorcio', consorcio)
    if (estado) q.set('estado', estado)
    api.get('/contratos/?' + q).then(setDatos).catch(e => setError(e.message))
  }, [consorcio, estado])
  useEffect(cargar, [cargar])

  const filtrado = datos?.mis_consorcios.find(c => String(c.id) === consorcio)
  useTitulo(datos?.es_proveedor ? 'Mis contratos' : 'Contratos',
    datos?.es_proveedor ? 'Trabajos contratados por los consorcios' : 'Trabajos contratados a proveedores, al contado o en cuotas')

  const filtro = (k, v) => {
    const q = new URLSearchParams(params)
    if (v) q.set(k, v); else q.delete(k)
    q.delete('propuesta')
    setParams(q)
  }

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />

  return (
    <>
      {filtrado && <TabsConsorcio id={filtrado.id} activa="contratos" />}
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2>{filtrado ? `Contratos de ${filtrado.nombre}` : datos.es_proveedor ? 'Mis contratos' : 'Contratos con proveedores'}</h2>
          <p>{datos.es_proveedor ? 'Trabajos que te contrataron y cómo se te van pagando.' : 'Cada cuota se paga desde Facturación, cargándole su factura.'}</p>
        </div>
        {datos.puede_gestionar && (
          <button type="button" className="btn btn-primary btn-sm px-3" onClick={() => setModal(true)}>
            <i className="bi bi-file-earmark-plus me-1" /> Nuevo contrato
          </button>
        )}
      </div>

      <div className="vote-filters">
        {datos.mis_consorcios.length > 0 && (
          <select className="form-select form-select-sm" value={consorcio} onChange={e => filtro('consorcio', e.target.value)}>
            <option value="">Todos los consorcios</option>
            {datos.mis_consorcios.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        )}
        <div className="vote-chips">
          {ESTADOS.map(([v, t]) => (
            <button key={v} type="button" className={'vote-chip' + (estado === v ? ' active' : '')} onClick={() => filtro('estado', v)}>{t}</button>
          ))}
        </div>
      </div>

      <div className="row g-3">
        {datos.contratos.map(c => (
          <div className="col-md-6 col-xl-4" key={c.id}>
            <TarjetaContrato c={c} esProveedor={datos.es_proveedor} conConsorcio={!filtrado} hoy={datos.hoy} />
          </div>
        ))}
        {!datos.contratos.length && (
          <div className="col-12">
            <Vacio icono="bi-file-earmark-text" titulo="No hay contratos" texto={
              datos.puede_gestionar ? 'Cargá uno con "Nuevo contrato" o desde una votación de contratación cerrada.'
                : datos.es_proveedor ? 'Cuando un consorcio te contrate, lo vas a ver acá.' : 'Cuando se contrate a un proveedor, lo vas a ver acá.'} />
          </div>
        )}
      </div>

      {datos.puede_gestionar && (
        <NuevoContrato abierto={modal} onCerrar={() => setModal(false)} consorcioInicial={consorcio ? Number(consorcio) : null}
          idPropuesta={propuesta} onCreado={id => { setModal(false); navigate(`/contratos/${id}`) }} />
      )}
    </>
  )
}
