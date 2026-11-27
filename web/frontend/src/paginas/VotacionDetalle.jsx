import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, DJANGO } from '../api'
import { useAccion } from '../acciones'
import Avatar from '../componentes/Avatar'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import ElegirProveedores from '../componentes/ElegirProveedores'
import { useTitulo } from '../componentes/Layout'
import Modal from '../componentes/Modal'
import { fechaHora, iconoArchivo } from '../formatos'

export default function VotacionDetalle() {
  const { id } = useParams()
  const [v, setV] = useState(null)
  const [error, setError] = useState('')
  const [voto, setVoto] = useState('')
  const [docs, setDocs] = useState([])
  const [invitar, setInvitar] = useState(false)
  const [elegido, setElegido] = useState([])

  const cargar = useCallback(() => {
    api.get(`/votaciones/${id}/`).then(d => { setV(d); setVoto(d.mi_voto || '') }).catch(e => setError(e.message))
  }, [id])
  useEffect(cargar, [cargar])
  const [accion, ocupado] = useAccion(`/votaciones/${id}/`, cargar)

  useTitulo(v ? `Votación · ${v.consorcio.nombre}` : 'Votación', v?.tipo_texto || '')

  if (error) return <ErrorCarga error={error} />
  if (!v) return <Cargando />
  const contratacion = v.tipo === 'contratacion'

  return (
    <>
      <Link to={`/votaciones?consorcio=${v.consorcio.id}`} className="back-link mb-3 d-inline-block"><i className="bi bi-arrow-left" /> Votaciones</Link>

      <div className="row g-4">
        <div className="col-lg-7">
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <span className={'vote-type ' + v.tipo}><i className={'bi ' + (contratacion ? 'bi-briefcase' : 'bi-check2-square')} /> {v.tipo_texto}</span>
              {v.abierta
                ? <span className="vote-state open">{v.lanzada ? <><i className="bi bi-circle-fill" /> Abierta hasta {fechaHora(v.fecha_limite)}</> : 'Esperando proveedores'}</span>
                : <span className="vote-state closed">Cerró el {fechaHora(v.fecha_limite)}</span>}
            </div>
            <p className="vote-detail-full">{v.detalle}</p>
            <small className="text-secondary">Creada el {fechaHora(v.fecha)} · {v.consorcio.nombre}</small>
            {v.contratado && (
              <div className="vote-winner mt-3 d-flex flex-wrap align-items-center gap-2">
                <span><i className="bi bi-award-fill" /> Se contrató a <strong>{v.contratado.nombre}</strong></span>
                {v.contrato_id
                  ? <Link to={`/contratos/${v.contrato_id}`} className="ms-auto small fw-semibold"><i className="bi bi-file-earmark-text" /> Ver contrato y cuotas</Link>
                  : v.gestiona && <Link to={`/contratos?propuesta=${v.id_propuesta_contratada}`} className="btn btn-success btn-sm ms-auto"><i className="bi bi-file-earmark-plus" /> Cargar contrato</Link>}
              </div>
            )}
          </div>

          {/* Proveedores invitados (contratación): la invitación les llega por chat */}
          {contratacion && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header d-flex align-items-center justify-content-between">
                <h5 className="mb-0">Proveedores invitados</h5>
                {v.gestiona && v.abierta && (
                  <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => { setElegido([]); setInvitar(true) }}>
                    <i className="bi bi-send me-1" /> Invitar
                  </button>
                )}
              </div>
              {v.invitaciones.map(inv => (
                <div className={'proposal-row' + (inv.contratado ? ' accepted' : '')} key={inv.id}>
                  <div className="proposal-avatar"><Avatar img={inv.proveedor.img} iniciales={inv.proveedor.iniciales} clase="" /></div>
                  <div className="flex-grow-1 min-w-0">
                    <div className="fw-bold">
                      <Link to={`/marketplace/proveedor/${inv.proveedor.id}`} className="text-reset text-decoration-none">{inv.proveedor.nombre}</Link>
                      {inv.contratado && <span className="badge text-bg-success ms-1">Contratado</span>}
                    </div>
                    <div className="small">
                      {inv.aceptacion === true && <span className="text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Aceptó participar</span>}
                      {inv.aceptacion === false && <span className="text-danger fw-semibold"><i className="bi bi-x-circle-fill" /> Rechazó</span>}
                      {inv.aceptacion === null && <span className="text-warning-emphasis fw-semibold"><i className="bi bi-hourglass-split" /> Esperando respuesta</span>}
                      {inv.propuesta_pdf && <> · <a href={inv.propuesta_pdf} target="_blank" rel="noreferrer"><i className="bi bi-file-earmark-pdf text-danger" /> Ver propuesta</a></>}
                      {inv.chat_id && <> · <Link to={`/chat/${inv.chat_id}`}><i className="bi bi-chat-dots" /> Chat</Link></>}
                    </div>
                  </div>
                  {v.gestiona && !v.abierta && inv.aceptacion && !inv.contratado && (
                    <button type="button" className="btn btn-success btn-sm" disabled={ocupado}
                      onClick={() => accion({ accion: 'contratar', id_propuesta: inv.id }, {}, { confirmar: `¿Contratar a ${inv.proveedor.nombre}?` })}>
                      <i className="bi bi-award me-1" /> Contratar
                    </button>
                  )}
                  {v.gestiona && v.abierta && inv.aceptacion === null && (
                    <button type="button" className="btn-icon-soft" title="Cancelar invitación"
                      onClick={() => accion({ accion: 'cancelar_invitacion', id_propuesta: inv.id }, {}, { confirmar: '¿Cancelar la invitación?' })}>
                      <i className="bi bi-x-lg" />
                    </button>
                  )}
                </div>
              ))}
              {!v.invitaciones.length && <Vacio chico icono="bi-send" texto="Invitá proveedores: la votación se lanza cuando alguno acepte." />}
            </div>
          )}

          {/* Votar */}
          {v.abierta && !v.lanzada && (
            <div className="vote-waiting mb-4">
              <i className="bi bi-hourglass-split" />
              <div><strong>La votación todavía no se lanzó.</strong><br />Se lanza cuando algún proveedor acepte la invitación desde el chat.</div>
            </div>
          )}
          {v.abierta && v.lanzada && v.puedo_votar && (
            <div className="panel-card mb-4" style={{ height: 'auto' }}>
              <div className="panel-card-header"><h5 className="mb-0">{v.mi_voto ? 'Cambiar mi voto' : 'Votar'}</h5></div>
              <div className="vote-options">
                {v.resultados.map(r => (
                  <label className="vote-option" key={r.valor}>
                    <input type="radio" name="voto" checked={voto === r.valor} onChange={() => setVoto(r.valor)} />
                    <span>{r.texto}</span>
                  </label>
                ))}
              </div>
              <button type="button" className="btn btn-primary btn-sm px-4 mt-3" disabled={!voto || ocupado} onClick={() => accion({ accion: 'votar', voto })}>
                {v.mi_voto ? 'Cambiar voto' : 'Votar'}
              </button>
            </div>
          )}

          {/* Documentos */}
          <div className="panel-card" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Documentos</h5></div>
            {v.documentos.map(d => (
              <div className="invoice-row" key={d.id}>
                <div className="invoice-icon"><i className={'bi ' + iconoArchivo(d.extension)} /></div>
                <div className="flex-grow-1 min-w-0">
                  <a href={d.url} target="_blank" rel="noreferrer" className="invoice-title text-decoration-none">{d.nombre}</a>
                  <div className="invoice-meta">{d.extension.replace('.', '').toUpperCase()}</div>
                </div>
                {v.gestiona && (
                  <button type="button" className="btn-icon-soft" title="Eliminar"
                    onClick={() => accion({ accion: 'borrar_doc', id_doc: d.id }, {}, { confirmar: '¿Eliminar este documento?' })}>
                    <i className="bi bi-trash" />
                  </button>
                )}
              </div>
            ))}
            {!v.documentos.length && <p className="text-secondary small">Sin documentos{v.gestiona ? '. Podés subir presupuestos, planos, fotos o el acta con el resultado' : ''}.</p>}
            {v.gestiona && (
              <div className="d-flex gap-2 mt-2">
                <input type="file" className="form-control form-control-sm" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,image/*"
                  onChange={e => setDocs([...e.target.files])} />
                <button type="button" className="btn btn-outline-primary btn-sm text-nowrap" disabled={!docs.length || ocupado}
                  onClick={async () => { await accion({ accion: 'subir_doc' }, { doc: docs }); setDocs([]) }}>
                  <i className="bi bi-upload me-1" /> Subir
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="col-lg-5">
          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Resultados</h5></div>
            {v.resultados.map(r => (
              <div className="result-row" key={r.valor}>
                <div className="d-flex justify-content-between small mb-1">
                  <span className="fw-semibold">{r.gana && !v.abierta && <i className="bi bi-trophy-fill text-warning" />} {r.texto}</span>
                  <span className="text-secondary">{r.cantidad} · {r.porcentaje}%</span>
                </div>
                <div className="result-bar"><div className={'result-bar-fill' + (r.gana ? ' lead' : '')} style={{ width: r.porcentaje + '%' }} /></div>
              </div>
            ))}
            {!v.resultados.length && <p className="text-secondary small mb-0">Sin opciones todavía.</p>}
            <div className="vote-participation">
              <i className="bi bi-people" /> Votaron <strong>{v.participacion.votos}</strong> de {v.participacion.habilitados} ({v.participacion.porcentaje}%)
            </div>
          </div>

          <div className="panel-card mb-4" style={{ height: 'auto' }}>
            <div className="panel-card-header"><h5 className="mb-0">Votos</h5></div>
            {v.votos.map((x, i) => (
              <div className="data-row" key={i}><span>{x.usuario.nombre}</span><span>{x.voto}</span></div>
            ))}
            {!v.votos.length && <p className="text-secondary small mb-0">Todavía nadie votó.</p>}
          </div>

          {v.gestiona ? (
            <div className="d-flex gap-2">
              {v.abierta && (
                <button type="button" className="btn btn-outline-secondary btn-sm flex-grow-1" disabled={ocupado}
                  onClick={() => accion({ accion: 'cerrar' }, {}, { confirmar: '¿Cerrar la votación ahora? Ya no se va a poder votar.' })}>
                  <i className="bi bi-lock me-1" /> Cerrar ahora
                </button>
              )}
              <button type="button" className="btn btn-outline-danger btn-sm flex-grow-1" disabled={ocupado}
                onClick={() => accion({ accion: 'eliminar' }, {}, { confirmar: '¿Eliminar la votación con sus votos y documentos?' })}>
                <i className="bi bi-trash me-1" /> Eliminar
              </button>
            </div>
          ) : !v.puedo_votar && (
            <div className="vote-waiting"><i className="bi bi-eye" /><div>Ves esta votación porque tenés habilitado este consorcio. Votan los propietarios y el administrador.</div></div>
          )}
        </div>
      </div>

      <Modal titulo="Invitar proveedor" abierto={invitar} onCerrar={() => setInvitar(false)} pie={
        <>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setInvitar(false)}>Cancelar</button>
          <button type="button" className="btn btn-primary btn-sm px-3" disabled={!elegido.length || ocupado}
            onClick={async () => { await accion({ accion: 'invitar', id_proveedor: elegido[0] }); setInvitar(false) }}>
            <i className="bi bi-send me-1" /> Enviar invitación
          </button>
        </>
      }>
        <p className="small text-secondary">Le va a llegar una invitación por chat para aceptar o rechazar participar.</p>
        <ElegirProveedores proveedores={v.proveedores} elegidos={elegido} onCambiar={setElegido} multiple={false} />
      </Modal>
    </>
  )
}
