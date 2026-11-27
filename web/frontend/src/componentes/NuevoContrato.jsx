import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import { moneda } from '../formatos'
import { useAvisos } from './Layout'
import Modal from './Modal'
import SelectorRico from './SelectorRico'

/**
 * Formulario de nuevo contrato: consorcio y proveedor con buscador y foto, precio, primer pago,
 * forma de pago (contado o cuotas) y el contrato formal en PDF (opcional).
 * Con idPropuesta viene de una votación de contratación: consorcio y proveedor ya están definidos.
 */
export default function NuevoContrato({ abierto, onCerrar, onCreado, consorcioInicial, idPropuesta }) {
  const avisar = useAvisos()
  const [opciones, setOpciones] = useState(null)
  const [form, setForm] = useState({})
  const [pdf, setPdf] = useState(null)
  const [errores, setErrores] = useState([])
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!abierto) return
    setErrores([])
    setPdf(null)
    api.get('/contratos/opciones/' + (idPropuesta ? '?propuesta=' + idPropuesta : '')).then(o => {
      setOpciones(o)
      setForm({
        id_consorcio: consorcioInicial || o.consorcios[0]?.id || '',
        id_proveedor: '',
        descripcion: o.propuesta ? o.propuesta.detalle.slice(0, 120) : '',
        precio: '',
        fecha: o.hoy,
        id_forma_pago: o.formas_pago[0]?.id,
      })
    }).catch(e => setErrores([e.message]))
  }, [abierto, consorcioInicial, idPropuesta])

  const consorcios = useMemo(() => (opciones?.consorcios || []).map(c => ({
    id: c.id, titulo: c.nombre, sub: c.direccion + (c.descripcion ? ' · ' + c.descripcion : ''), img: c.img, iniciales: c.nombre.slice(0, 2).toUpperCase(),
  })), [opciones])
  const proveedores = useMemo(() => (opciones?.proveedores || []).map(p => ({
    id: p.id, titulo: p.nombre, sub: p.info, img: p.img, iniciales: p.iniciales, extra: p.estrellas,
  })), [opciones])

  const forma = opciones?.formas_pago.find(f => f.id === form.id_forma_pago)
  const precio = parseFloat(form.precio)
  const resumen = !precio || precio <= 0 || !forma
    ? 'Cada pago queda en Facturación como deuda pendiente.'
    : forma.cuotas === 1
      ? `Un solo pago de ${moneda(precio)}, que queda en Facturación como deuda pendiente.`
      : `${forma.cuotas} cuotas mensuales de ${moneda(Math.floor(precio / forma.cuotas * 100) / 100)}, que quedan en Facturación como deudas pendientes.`

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const enviar = async e => {
    e.preventDefault()
    const datos = new FormData()
    Object.entries(form).forEach(([k, v]) => datos.append(k, v ?? ''))
    if (opciones?.propuesta) datos.append('id_propuesta', opciones.propuesta.id)
    if (pdf) datos.append('contrato_pdf', pdf)
    setEnviando(true)
    try {
      const r = await api.post('/contratos/', datos)
      avisar(r.mensaje)
      onCreado(r.id)
    } catch (err) {
      setErrores(err.errores)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal titulo="Nuevo contrato" abierto={abierto} onCerrar={onCerrar} onSubmit={enviar} pie={
      <>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onCerrar}>Cancelar</button>
        <button type="submit" className="btn btn-primary btn-sm px-3" disabled={enviando || !opciones}>{enviando ? 'Creando…' : 'Crear contrato'}</button>
      </>
    }>
      {errores.length > 0 && <div className="alert alert-danger py-2 small">{errores.map(x => <div key={x}>{x}</div>)}</div>}
      {!opciones ? <div className="text-center py-4"><div className="spinner-border text-primary" /></div> : (
        <>
          {opciones.propuesta ? (
            <div className="vote-winner mb-3">
              <i className="bi bi-award-fill" /> <strong>{opciones.propuesta.proveedor}</strong> en {opciones.propuesta.consorcio}
              <div className="small mt-1">Ganó la votación: {opciones.propuesta.detalle}</div>
            </div>
          ) : (
            <>
              <label className="form-label small fw-semibold">Consorcio</label>
              <div className="mb-3">
                <SelectorRico opciones={consorcios} valor={form.id_consorcio} onCambiar={v => set('id_consorcio', v)}
                  placeholder="Elegí el consorcio" buscar="Buscar edificio o dirección" />
              </div>
              <label className="form-label small fw-semibold">Proveedor</label>
              <div className="mb-3">
                <SelectorRico opciones={proveedores} valor={form.id_proveedor} onCambiar={v => set('id_proveedor', v)} redondo
                  placeholder="Elegí el proveedor" buscar="Buscar proveedor o rubro" />
              </div>
            </>
          )}

          <label className="form-label small fw-semibold">Trabajo contratado</label>
          <input className="form-control mb-3" maxLength={200} placeholder="Ej.: Impermeabilización de la terraza"
            value={form.descripcion || ''} onChange={e => set('descripcion', e.target.value)} />

          <div className="row g-2 mb-3">
            <div className="col-6">
              <label className="form-label small fw-semibold">Precio total</label>
              <div className="input-group">
                <span className="input-group-text">$</span>
                <input type="number" className="form-control" step="0.01" min="0.01" value={form.precio || ''} onChange={e => set('precio', e.target.value)} />
              </div>
            </div>
            <div className="col-6">
              <label className="form-label small fw-semibold">Primer pago</label>
              <input type="date" className="form-control" value={form.fecha || ''} onChange={e => set('fecha', e.target.value)} />
            </div>
          </div>

          <label className="form-label small fw-semibold">Forma de pago</label>
          <div className="pay-picker mb-2">
            {opciones.formas_pago.map(f => (
              <label key={f.id}>
                <input type="radio" name="forma" checked={form.id_forma_pago === f.id} onChange={() => set('id_forma_pago', f.id)} />
                <span>{f.cuotas === 1 ? 'Contado' : `${f.cuotas} cuotas`}</span>
              </label>
            ))}
          </div>
          <div className="small text-secondary mb-3">{resumen}</div>

          <label className="form-label small fw-semibold">Contrato formal (PDF, opcional)</label>
          <input type="file" className="form-control" accept="application/pdf,.pdf" onChange={e => setPdf(e.target.files[0] || null)} />
          <div className="form-text">El contrato firmado con el proveedor. También lo podés subir después, desde el detalle.</div>
        </>
      )}
    </Modal>
  )
}
