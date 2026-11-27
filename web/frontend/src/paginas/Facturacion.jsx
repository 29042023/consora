import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useAvisos, useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import TabsConsorcio from '../componentes/TabsConsorcio'
import { diaMes, fecha, moneda } from '../formatos'

/** Formulario para cargar la factura de una deuda pendiente (al elegirla, completa proveedor e importe). */
function CargarFactura({ id, pendientes, hoy, abierto, onCerrar, onCargada }) {
  const avisar = useAvisos()
  const vacio = { id_deuda: '', proveedor: '', nro_comprobante: '', fecha: hoy, importe: '' }
  const [form, setForm] = useState(vacio)
  const [archivo, setArchivo] = useState(null)
  const [errores, setErrores] = useState([])
  const [enviando, setEnviando] = useState(false)

  useEffect(() => { if (abierto) { setForm(vacio); setArchivo(null); setErrores([]) } }, [abierto]) // eslint-disable-line

  const elegirDeuda = idDeuda => {
    const d = pendientes.find(x => String(x.id) === idDeuda)
    setForm(f => ({ ...f, id_deuda: idDeuda, proveedor: f.proveedor || d?.proveedor || '', importe: f.importe || (d ? String(d.importe) : '') }))
  }

  const enviar = async e => {
    e.preventDefault()
    const datos = new FormData()
    Object.entries(form).forEach(([k, v]) => datos.append(k, v))
    if (archivo) datos.append('archivo', archivo)
    setEnviando(true)
    try {
      const r = await api.post(`/consorcios/${id}/facturacion/`, datos)
      avisar(r.mensaje)
      onCargada()
    } catch (err) {
      setErrores(err.errores)
    } finally {
      setEnviando(false)
    }
  }

  const campo = (nombre, props = {}) => (
    <input className="form-control" value={form[nombre]} onChange={e => setForm(f => ({ ...f, [nombre]: e.target.value }))} {...props} />
  )

  return (
    <Modal titulo="Cargar factura" abierto={abierto} onCerrar={onCerrar} onSubmit={enviar} pie={
      <>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onCerrar}>Cancelar</button>
        <button type="submit" className="btn btn-primary btn-sm px-3" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
      </>
    }>
      {errores.length > 0 && <div className="alert alert-danger py-2 small">{errores.map(e => <div key={e}>{e}</div>)}</div>}
      <label className="form-label small fw-semibold">Deuda</label>
      <select className="form-select mb-3" value={form.id_deuda} onChange={e => elegirDeuda(e.target.value)}>
        <option value="">Elegí la deuda a la que corresponde</option>
        {pendientes.map(d => <option key={d.id} value={d.id}>{d.tipo} · {d.descripcion} ({moneda(d.importe)})</option>)}
      </select>
      <div className="row g-3">
        <div className="col-sm-6"><label className="form-label small fw-semibold">Proveedor</label>{campo('proveedor')}</div>
        <div className="col-sm-6"><label className="form-label small fw-semibold">N° de comprobante</label>{campo('nro_comprobante', { placeholder: '0001-00001234' })}</div>
        <div className="col-sm-6"><label className="form-label small fw-semibold">Fecha</label>{campo('fecha', { type: 'date' })}</div>
        <div className="col-sm-6"><label className="form-label small fw-semibold">Importe</label>{campo('importe', { type: 'number', step: '0.01', min: '0.01' })}</div>
        <div className="col-12">
          <label className="form-label small fw-semibold">Archivo (PDF o imagen)</label>
          <input type="file" className="form-control" accept="application/pdf,image/*" onChange={e => setArchivo(e.target.files[0] || null)} />
        </div>
      </div>
    </Modal>
  )
}

export default function Facturacion() {
  const { id } = useParams()
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [modal, setModal] = useState(false)

  const cargar = useCallback(() => {
    api.get(`/consorcios/${id}/facturacion/`).then(setDatos).catch(e => setError(e.message))
  }, [id])
  useEffect(cargar, [cargar])

  useTitulo(datos?.consorcio.nombre || 'Facturación', datos?.consorcio.direccion || '')

  if (error) return <ErrorCarga error={error} />
  if (!datos) return <Cargando />

  return (
    <>
      <TabsConsorcio id={id} activa="facturas" />
      <div className="page-header-row d-flex flex-wrap align-items-center justify-content-between gap-3">
        <h2 className="mb-0">Facturación</h2>
        {datos.puede_cargar && (
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!datos.pendientes.length} onClick={() => setModal(true)}>
            <i className="bi bi-plus-lg me-1" /> Cargar factura
          </button>
        )}
      </div>

      <div className="row g-4">
        <div className="col-lg-7">
          <div className="panel-card">
            <div className="panel-card-header"><h5 className="mb-0">Facturas</h5></div>
            {datos.facturas.map(f => (
              <div className="invoice-row" key={f.id}>
                <div className="invoice-icon"><i className="bi bi-receipt" /></div>
                <div className="flex-grow-1 min-w-0">
                  <div className="invoice-title">{f.descripcion}</div>
                  <div className="invoice-meta">
                    <span className="chip-soft">{f.tipo}</span> {f.proveedor} · N° {f.nro_comprobante} · {fecha(f.fecha)}
                    {f.contrato_id && <> · <Link to={`/contratos/${f.contrato_id}`}>contrato</Link></>}
                  </div>
                </div>
                <div className="text-end">
                  <div className="invoice-amount">{moneda(f.importe)}</div>
                  {f.archivo && <a href={f.archivo} target="_blank" rel="noreferrer" className="small"><i className="bi bi-paperclip" /> Ver archivo</a>}
                </div>
              </div>
            ))}
            {!datos.facturas.length && <Vacio chico icono="bi-receipt" texto="Todavía no se cargaron facturas de este consorcio." />}
          </div>
        </div>

        <div className="col-lg-5">
          <div className="panel-card">
            <div className="panel-card-header"><h5 className="mb-0">Deudas pendientes</h5></div>
            {datos.pendientes.map(d => (
              <div className="invoice-row" key={d.id}>
                <div className="invoice-icon pending"><i className="bi bi-hourglass-split" /></div>
                <div className="flex-grow-1 min-w-0">
                  <div className="invoice-title">{d.descripcion}</div>
                  <div className="invoice-meta">
                    <span className="chip-soft">{d.tipo}</span> {d.proveedor && `${d.proveedor} · `}
                    {d.contrato_id && <><Link to={`/contratos/${d.contrato_id}`}>contrato</Link> · </>}
                    {d.vencimiento && <span className={d.vencimiento < datos.hoy ? 'text-danger' : ''}>vence {diaMes(d.vencimiento)}</span>}
                  </div>
                </div>
                <div className="invoice-amount">{moneda(d.importe)}</div>
              </div>
            ))}
            {!datos.pendientes.length && <Vacio chico icono="bi-check2-circle" texto="No hay deudas pendientes." />}
          </div>
        </div>
      </div>

      <CargarFactura id={id} pendientes={datos.pendientes} hoy={datos.hoy} abierto={modal}
        onCerrar={() => setModal(false)} onCargada={() => { setModal(false); cargar() }} />
    </>
  )
}
