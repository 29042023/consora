import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useAvisos, useTitulo } from '../componentes/Layout'
import { fecha, moneda } from '../formatos'

export default function ContratoDetalle() {
  const { id } = useParams()
  const navigate = useNavigate()
  const avisar = useAvisos()
  const [c, setC] = useState(null)
  const [error, setError] = useState('')
  const [pdf, setPdf] = useState(null)
  const [fechaFin, setFechaFin] = useState('')
  const [ocupado, setOcupado] = useState(false)

  const cargar = useCallback(() => {
    api.get(`/contratos/${id}/`).then(d => { setC(d); setFechaFin(d.hoy) }).catch(e => setError(e.message))
  }, [id])
  useEffect(cargar, [cargar])

  useTitulo(c ? `Contrato · ${c.consorcio.nombre}` : 'Contrato', c?.proveedor.nombre || '')

  /** Manda una acción (cancelar, eliminar, terminar el trabajo, PDF...) y recarga. */
  const accion = async (nombre, extra = {}, confirmar = null) => {
    if (confirmar && !window.confirm(confirmar)) return
    let datos = { accion: nombre, ...extra }
    if (extra.archivo) {
      datos = new FormData()
      datos.append('accion', nombre)
      datos.append('contrato_pdf', extra.archivo)
    }
    setOcupado(true)
    try {
      const r = await api.post(`/contratos/${id}/`, datos)
      avisar(r.mensaje)
      if (r.eliminado) { navigate(`/contratos?consorcio=${c.consorcio.id}`); return }
      setPdf(null)
      cargar()
    } catch (err) {
      avisar(err.message, 'error')
    } finally {
      setOcupado(false)
    }
  }

  if (error) return <ErrorCarga error={error} />
  if (!c) return <Cargando />

  const vencido = iso => iso && iso < c.hoy

  return (
    <>
      <Link to={c.es_el_proveedor ? '/contratos' : `/contratos?consorcio=${c.consorcio.id}`} className="back-link mb-3 d-inline-block">
        <i className="bi bi-arrow-left" /> Contratos
      </Link>

      <div className="row g-4">
        <div className="col-lg-7">
          {/* Cabecera con el avance de pagos */}
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <span className={'contract-pay ' + (c.es_contado ? 'contado' : 'cuotas')}>
                <i className={'bi ' + (c.es_contado ? 'bi-cash' : 'bi-calendar3')} /> {c.forma_pago}
              </span>
              <span className={'contract-state ' + c.estado}>{c.estado_texto}</span>
            </div>
            <h4 className="fw-bold mb-1">{c.descripcion}</h4>
            <div className="meet-when">
              <span><i className="bi bi-person-workspace" /> <Link to={`/marketplace/proveedor/${c.proveedor.id}`}>{c.proveedor.nombre}</Link></span>
              <span><i className="bi bi-building" /> {c.consorcio.nombre}</span>
              <span><i className="bi bi-cash-stack" /> {moneda(c.precio)}</span>
            </div>
            {c.votacion && (
              <div className="small text-secondary mt-2">
                <i className="bi bi-check2-square" /> Surgió de la votación{' '}
                <Link to={`/votaciones/${c.votacion.id}`}>{c.votacion.detalle.slice(0, 70)}</Link>
              </div>
            )}
            <div className="contract-progress mt-3">
              <div className="d-flex justify-content-between small mb-1">
                <span className="fw-semibold">{c.cant_pagas} de {c.cant_cuotas} pagos hechos</span>
                <span>{c.porcentaje} %</span>
              </div>
              <div className="progress" style={{ height: 8 }}>
                <div className={'progress-bar' + (c.estado === 'finalizado' ? ' bg-success' : '')} style={{ width: c.porcentaje + '%' }} />
              </div>
            </div>
          </div>

          {/* Cuotas */}
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header d-flex align-items-center justify-content-between">
              <h5 className="mb-0">{c.cant_cuotas === 1 ? 'Pago' : 'Cuotas'}</h5>
              {c.puede_pagar && c.estado === 'vigente' && (
                <Link to={`/consorcios/${c.consorcio.id}/facturacion`} className="btn btn-outline-primary btn-sm"><i className="bi bi-receipt me-1" /> Pagar en Facturación</Link>
              )}
            </div>
            {c.cuotas_detalle.map(q => (
              <div className="invoice-row" key={q.id}>
                <div className={'invoice-icon' + (q.pagada_el ? '' : ' pending')}>
                  <i className={'bi ' + (q.pagada_el ? 'bi-check2' : 'bi-hourglass-split')} />
                </div>
                <div className="flex-grow-1 min-w-0">
                  <div className="invoice-title">{c.cant_cuotas === 1 ? 'Pago único' : `Cuota ${q.nro}`}</div>
                  <div className="invoice-meta">
                    {q.pagada_el
                      ? <><span className="text-success fw-semibold">Pagada el {fecha(q.pagada_el)}</span> · N° {q.comprobante}
                          {q.archivo && <> · <a href={q.archivo} target="_blank" rel="noreferrer"><i className="bi bi-paperclip" /> Factura</a></>}</>
                      : <span className={vencido(q.vencimiento) ? 'text-danger fw-semibold' : ''}>
                          Vence {fecha(q.vencimiento)}{vencido(q.vencimiento) && ' (vencida)'}
                        </span>}
                  </div>
                </div>
                <div className="invoice-amount">{moneda(q.importe)}</div>
              </div>
            ))}
            {!c.cuotas_detalle.length && <Vacio chico icono="bi-x-circle" texto="El contrato se canceló sin pagos." />}
          </div>
        </div>

        <div className="col-lg-5">
          {/* Resumen */}
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Resumen</h5></div>
            <div className="contract-sum"><span>Precio total</span><strong>{moneda(c.precio)}</strong></div>
            <div className="contract-sum"><span>Pagado</span><strong className="text-success">{moneda(c.pagado)}</strong></div>
            <div className="contract-sum"><span>Saldo</span><strong>{moneda(c.saldo)}</strong></div>
            {c.proxima && c.estado === 'vigente' && (
              <div className="contract-sum"><span>Próximo vencimiento</span>
                <strong className={vencido(c.proxima.vencimiento) ? 'text-danger' : ''}>{fecha(c.proxima.vencimiento)}</strong></div>
            )}
            {c.creado_por && <div className="small text-secondary mt-2">Cargado por {c.creado_por}</div>}
          </div>

          {/* Contrato formal (PDF) */}
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Contrato formal</h5></div>
            {c.contrato_pdf
              ? <a href={c.contrato_pdf} target="_blank" rel="noreferrer" className="contract-pdf">
                  <i className="bi bi-file-earmark-pdf-fill" />
                  <span className="min-w-0"><span className="d-block fw-semibold text-truncate">{c.nombre_pdf}</span><small className="text-secondary">Abrir PDF</small></span>
                </a>
              : <p className="small text-secondary mb-2">{c.gestiona ? 'Todavía no se cargó el contrato firmado.' : 'El administrador todavía no cargó el contrato firmado.'}</p>}
            {c.gestiona && (
              <>
                <div className="input-group input-group-sm mt-2">
                  <input type="file" className="form-control" accept="application/pdf,.pdf" onChange={e => setPdf(e.target.files[0] || null)} />
                  <button type="button" className="btn btn-outline-primary" disabled={!pdf || ocupado} onClick={() => accion('subir_pdf', { archivo: pdf })}>
                    {c.contrato_pdf ? 'Reemplazar' : 'Subir'}
                  </button>
                </div>
                {c.contrato_pdf && (
                  <button type="button" className="btn btn-link btn-sm p-0 mt-2 text-danger" onClick={() => accion('quitar_pdf', {}, '¿Quitar el PDF del contrato?')}>
                    <i className="bi bi-trash" /> Quitar PDF
                  </button>
                )}
              </>
            )}
          </div>

          {/* Trabajo: en proceso o terminado (aparte de los pagos) */}
          {c.estado !== 'cancelado' && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header"><h5 className="mb-0">Trabajo</h5></div>
              {c.trabajo_terminado_en ? (
                <>
                  <p className="mb-2"><span className="text-success fw-semibold"><i className="bi bi-check2-circle" /> Terminado el {fecha(c.trabajo_terminado_en)}</span></p>
                  {c.gestiona && (
                    <button type="button" className="btn btn-link btn-sm p-0 text-secondary" onClick={() => accion('reabrir_trabajo')}>
                      <i className="bi bi-arrow-counterclockwise" /> Volver a en proceso
                    </button>
                  )}
                </>
              ) : (
                <>
                  <p className="mb-2"><span className="text-warning-emphasis fw-semibold"><i className="bi bi-tools" /> En proceso</span></p>
                  {(c.gestiona || c.es_el_proveedor) && (
                    <div className="d-flex gap-2 align-items-center">
                      <input type="date" className="form-control form-control-sm" style={{ maxWidth: 160 }} value={fechaFin} max={c.hoy} onChange={e => setFechaFin(e.target.value)} />
                      <button type="button" className="btn btn-success btn-sm text-nowrap" disabled={ocupado} onClick={() => accion('terminar_trabajo', { fecha: fechaFin })}>
                        <i className="bi bi-check2" /> Marcar terminado
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* Acciones del administrador */}
          {c.gestiona && (
            <div className="panel-card" style={{ height: 'auto' }}>
              <div className="panel-card-header"><h5 className="mb-0">Acciones</h5></div>
              {c.estado === 'vigente' && (
                <button type="button" className="btn btn-outline-warning btn-sm w-100 mb-2" disabled={ocupado}
                  onClick={() => accion('cancelar', {}, '¿Cancelar el contrato? Las cuotas sin pagar dejan de ser deuda.')}>
                  <i className="bi bi-x-circle me-1" /> Cancelar contrato
                </button>
              )}
              {c.cant_pagas === 0
                ? <button type="button" className="btn btn-outline-danger btn-sm w-100" disabled={ocupado}
                    onClick={() => accion('eliminar', {}, '¿Eliminar el contrato y sus cuotas?')}>
                    <i className="bi bi-trash me-1" /> Eliminar
                  </button>
                : <p className="small text-secondary mb-0">Tiene pagos hechos: no se puede eliminar, solo cancelar.</p>}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
