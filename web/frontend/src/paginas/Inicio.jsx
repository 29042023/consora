import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAccion } from '../acciones'
import { Cargando, ErrorCarga, Vacio } from '../componentes/Cargando'
import { useTitulo } from '../componentes/Layout'
import { diaLargo, diaMes, fecha, fechaHora, hora, moneda } from '../formatos'
import { useSesion } from '../sesion'
import { TarjetaContrato } from './Contratos'
import { LOGO_MEET } from './Reuniones'

function Tarjeta({ titulo, a, accion, children }) {
  return (
    <div className="panel-card mb-4" style={{ height: 'auto' }}>
      <div className="panel-card-header d-flex align-items-center justify-content-between">
        <h5 className="mb-0">{titulo}</h5>
        {a && <Link to={a} className="small fw-semibold">{accion || 'Ver todo'} <i className="bi bi-arrow-right" /></Link>}
      </div>
      {children}
    </div>
  )
}

function Metrica({ icono, valor, texto, a, alerta }) {
  return (
    <div className="col-6 col-xl-3">
      <Link to={a} className="panel-card d-block text-decoration-none text-reset h-100">
        <div className="d-flex align-items-center gap-3">
          <div className={'invoice-icon' + (alerta ? ' pending' : '')}><i className={'bi ' + icono} /></div>
          <div className="min-w-0">
            <div className="fw-bold fs-5">{valor}</div>
            <div className="small text-secondary">{texto}</div>
          </div>
        </div>
      </Link>
    </div>
  )
}

function ListaReuniones({ reuniones }) {
  if (!reuniones.length) return <Vacio chico icono="bi-camera-video" texto="No hay reuniones próximas." />
  return reuniones.map(r => (
    <Link to={`/reuniones/${r.id}`} className="invoice-row text-decoration-none text-reset" key={r.id}>
      <div className="invoice-icon">{r.es_virtual ? <img src={LOGO_MEET} alt="" className="meet-logo lg" /> : <i className="bi bi-geo-alt" />}</div>
      <div className="flex-grow-1 min-w-0">
        <div className="invoice-title">{r.titulo}</div>
        <div className="invoice-meta">{r.consorcio.nombre} · {diaLargo(r.fecha_inicio)} · {hora(r.fecha_inicio)} h</div>
      </div>
      <span className={'meet-state ' + r.estado}>{r.estado_texto}</span>
    </Link>
  ))
}

function ListaEventos({ eventos }) {
  if (!eventos.length) return <Vacio chico icono="bi-calendar-event" texto="No hay eventos próximos." />
  return eventos.map(e => (
    <div className="invoice-row" key={e.id}>
      <div className="invoice-icon"><i className="bi bi-calendar-event" /></div>
      <div className="flex-grow-1 min-w-0">
        <div className="invoice-title">{e.titulo}</div>
        <div className="invoice-meta">{diaLargo(e.fecha)}{e.hora && ` · ${e.hora} h`}{e.consorcio && ` · ${e.consorcio}`}</div>
      </div>
    </div>
  ))
}

function ListaDeudas({ deudas, hoy }) {
  if (!deudas.length) return <Vacio chico icono="bi-check2-circle" texto="No hay deudas pendientes." />
  return deudas.map(d => (
    <div className="invoice-row" key={d.id}>
      <div className="invoice-icon pending"><i className="bi bi-hourglass-split" /></div>
      <div className="flex-grow-1 min-w-0">
        <div className="invoice-title">{d.descripcion}</div>
        <div className="invoice-meta">
          <span className="chip-soft">{d.tipo}</span> {d.consorcio}
          {d.vencimiento && <> · <span className={d.vencimiento < hoy ? 'text-danger' : ''}>vence {diaMes(d.vencimiento)}</span></>}
        </div>
      </div>
      <div className="invoice-amount">{moneda(d.importe)}</div>
    </div>
  ))
}

function ListaVotaciones({ votaciones }) {
  if (!votaciones.length) return <Vacio chico icono="bi-check2-square" texto="No hay votaciones abiertas." />
  return votaciones.map(v => (
    <Link to={`/votaciones/${v.id}`} className="invoice-row text-decoration-none text-reset" key={v.id}>
      <div className="invoice-icon"><i className={'bi ' + (v.tipo === 'comun' ? 'bi-check2-square' : 'bi-briefcase')} /></div>
      <div className="flex-grow-1 min-w-0">
        <div className="invoice-title">{v.detalle.length > 80 ? v.detalle.slice(0, 80) + '…' : v.detalle}</div>
        <div className="invoice-meta">{v.consorcio.nombre} · cierra {fechaHora(v.fecha_limite)}</div>
      </div>
      {v.ya_vote === true && <span className="small text-success fw-semibold"><i className="bi bi-check-circle-fill" /> Votaste</span>}
      {v.ya_vote === false && v.lanzada && <span className="small text-warning-emphasis fw-semibold">Votá</span>}
    </Link>
  ))
}

function InicioAdmin({ d }) {
  const m = d.metricas
  return (
    <>
      <div className="row g-3 mb-4">
        <Metrica a="/consorcios" icono="bi-buildings" valor={m.consorcios} texto="Consorcios" />
        <Metrica a="/consorcios" icono="bi-hourglass-split" valor={moneda(m.importe_pendiente)} texto={`${m.deudas_pendientes} deudas pendientes`} alerta={m.vencidas > 0} />
        <Metrica a="/facturas" icono="bi-receipt" valor={moneda(m.pagado_mes)} texto={`${m.facturas_mes} facturas este mes`} />
        <Metrica a="/trabajos" icono="bi-tools" valor={m.trabajos_en_proceso} texto={`Trabajos en proceso · ${m.contratos_vigentes} contratos vigentes`} />
      </div>
      <div className="row g-4">
        <div className="col-lg-7">
          <Tarjeta titulo="Próximos vencimientos" a="/facturas" accion="Facturas"><ListaDeudas deudas={d.deudas} hoy={d.hoy} /></Tarjeta>
          <Tarjeta titulo="Tus consorcios" a="/consorcios">
            {d.consorcios.map(c => (
              <Link to={`/consorcios/${c.id}/facturacion`} className="invoice-row text-decoration-none text-reset" key={c.id}>
                <div className="invoice-icon"><i className="bi bi-building" /></div>
                <div className="flex-grow-1 min-w-0"><div className="invoice-title">{c.nombre}</div><div className="invoice-meta">{c.direccion}</div></div>
                {c.cant_pendientes > 0 && <span className="small text-warning-emphasis fw-semibold">{c.cant_pendientes} pendientes</span>}
              </Link>
            ))}
          </Tarjeta>
        </div>
        <div className="col-lg-5">
          <Tarjeta titulo="Próximas reuniones" a="/reuniones"><ListaReuniones reuniones={d.reuniones} /></Tarjeta>
          <Tarjeta titulo="Agenda" a="/agenda"><ListaEventos eventos={d.eventos} /></Tarjeta>
          <Tarjeta titulo="Equipo y votaciones">
            <div className="data-row"><span>Votaciones abiertas</span><Link to="/votaciones?estado=abiertas" className="fw-semibold">{m.votaciones_abiertas}</Link></div>
            <div className="data-row"><span>Asistentes</span><Link to="/asistentes" className="fw-semibold">{m.asistentes}</Link></div>
          </Tarjeta>
        </div>
      </div>
    </>
  )
}

function InicioAsistente({ d, recargar }) {
  const [accion] = useAccion('/inicio/', recargar)
  const enProceso = d.tareas.filter(t => !t.completada).length
  return (
    <div className="row g-4">
      <div className="col-lg-7">
        <Tarjeta titulo={`Mis tareas · ${enProceso} en proceso`}>
          {d.tareas.map(t => (
            <label className="data-row" key={t.id} style={{ cursor: 'pointer' }}>
              <span className={t.completada ? 'text-decoration-line-through text-secondary' : ''}>
                <input type="checkbox" className="form-check-input me-2" checked={t.completada}
                  onChange={() => accion({ accion: 'cambiar_tarea', id_tarea: t.id }, {}, { quedarse: true })} />
                {t.descripcion}
              </span>
              <small className="text-secondary">{fecha(t.fecha)}</small>
            </label>
          ))}
          {!d.tareas.length && <Vacio chico icono="bi-list-check" texto="No tenés tareas asignadas." />}
        </Tarjeta>
        <Tarjeta titulo="Edificios habilitados" a="/consorcios">
          {d.consorcios.map(c => (
            <Link to={`/consorcios/${c.id}/facturacion`} className="invoice-row text-decoration-none text-reset" key={c.id}>
              <div className="invoice-icon"><i className="bi bi-building" /></div>
              <div className="flex-grow-1 min-w-0"><div className="invoice-title">{c.nombre}</div><div className="invoice-meta">{c.direccion}</div></div>
              {c.cant_pendientes > 0 && <span className="small text-warning-emphasis fw-semibold">{c.cant_pendientes} pendientes</span>}
            </Link>
          ))}
          {!d.consorcios.length && <Vacio chico icono="bi-buildings" texto="Tu administrador todavía no te habilitó edificios." />}
        </Tarjeta>
      </div>
      <div className="col-lg-5">
        <Tarjeta titulo="Votaciones abiertas" a="/votaciones"><ListaVotaciones votaciones={d.votaciones} /></Tarjeta>
        <Tarjeta titulo="Próximas reuniones" a="/reuniones"><ListaReuniones reuniones={d.reuniones} /></Tarjeta>
        <Tarjeta titulo="Agenda" a="/agenda"><ListaEventos eventos={d.eventos} /></Tarjeta>
      </div>
    </div>
  )
}

function InicioPropietario({ d }) {
  return (
    <div className="row g-4">
      <div className="col-lg-7">
        {d.unidades.map(u => (
          <div className="panel-card mb-4" style={{ height: 'auto' }} key={u.consorcio.id}>
            <div className="d-flex gap-3 align-items-center">
              {u.consorcio.img ? <img src={u.consorcio.img} alt="" style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 12 }} />
                : <div className="invoice-icon"><i className="bi bi-building" /></div>}
              <div className="min-w-0">
                <h5 className="fw-bold mb-0">{u.consorcio.nombre}</h5>
                <div className="small text-secondary">{u.consorcio.direccion}{u.unidad && ` · Unidad ${u.unidad}`}</div>
                <div className="small mt-1">Administra <strong>{u.administrador.nombre}</strong></div>
              </div>
            </div>
          </div>
        ))}
        <Tarjeta titulo="Votaciones abiertas" a="/votaciones"><ListaVotaciones votaciones={d.votaciones} /></Tarjeta>
        <Tarjeta titulo="Gastos pendientes del edificio" a="/consorcios"><ListaDeudas deudas={d.deudas} hoy={d.hoy} /></Tarjeta>
      </div>
      <div className="col-lg-5">
        <Tarjeta titulo="Próximas reuniones" a="/reuniones"><ListaReuniones reuniones={d.reuniones} /></Tarjeta>
        <Tarjeta titulo="Agenda del edificio" a="/agenda"><ListaEventos eventos={d.eventos} /></Tarjeta>
        <Tarjeta titulo="Últimas facturas">
          {d.facturas.map(f => (
            <div className="invoice-row" key={f.id}>
              <div className="invoice-icon"><i className="bi bi-receipt" /></div>
              <div className="flex-grow-1 min-w-0"><div className="invoice-title">{f.descripcion}</div><div className="invoice-meta">{f.proveedor} · {fecha(f.fecha)}</div></div>
              <div className="invoice-amount">{moneda(f.importe)}</div>
            </div>
          ))}
          {!d.facturas.length && <Vacio chico icono="bi-receipt" texto="Todavía no hay facturas." />}
        </Tarjeta>
      </div>
    </div>
  )
}

function InicioProveedor({ d, usuario }) {
  const mk = d.marketplace
  return (
    <div className="row g-4">
      <div className="col-lg-7">
        <Tarjeta titulo="Invitaciones por responder" a="/chat" accion="Ir al chat">
          {[...d.invitaciones_pendientes.map(i => ({ tipo: 'votacion', id: 'v' + i.id, titulo: i.votacion.detalle, meta: `${i.votacion.consorcio.nombre} · cierra ${fechaHora(i.votacion.fecha_limite)}`, chat: i.chat_id })),
            ...d.reuniones_pendientes.map(r => ({ tipo: 'reunion', id: 'r' + r.id, titulo: r.titulo, meta: `${r.consorcio.nombre} · ${diaLargo(r.fecha_inicio)} ${hora(r.fecha_inicio)} h`, chat: r.chat_id }))]
            .map(x => (
              <Link to={x.chat ? `/chat/${x.chat}` : '/chat'} className="invoice-row text-decoration-none text-reset" key={x.id}>
                <div className="invoice-icon pending"><i className={'bi ' + (x.tipo === 'votacion' ? 'bi-briefcase' : 'bi-camera-video')} /></div>
                <div className="flex-grow-1 min-w-0"><div className="invoice-title">{x.titulo}</div><div className="invoice-meta">{x.meta}</div></div>
                <span className="small fw-semibold text-primary">Responder</span>
              </Link>
            ))}
          {!d.invitaciones_pendientes.length && !d.reuniones_pendientes.length && <Vacio chico icono="bi-envelope-open" texto="No tenés invitaciones por responder." />}
        </Tarjeta>
        <Tarjeta titulo="Mis contratos" a="/contratos">
          <div className="row g-3">
            {d.contratos.map(c => <div className="col-12" key={c.id}><TarjetaContrato c={c} esProveedor hoy={d.hoy} /></div>)}
          </div>
          {!d.contratos.length && <Vacio chico icono="bi-file-earmark-text" texto="Cuando un consorcio te contrate, lo vas a ver acá." />}
        </Tarjeta>
        <Tarjeta titulo="Mis propuestas">
          {d.propuestas.map(i => (
            <div className="invoice-row" key={i.id}>
              <div className="invoice-icon"><i className="bi bi-file-earmark-text" /></div>
              <div className="flex-grow-1 min-w-0">
                <div className="invoice-title">{i.votacion.detalle}</div>
                <div className="invoice-meta">
                  {i.votacion.consorcio.nombre} · {i.contratado ? <span className="text-success fw-semibold">Te contrataron</span>
                    : i.votacion.abierta ? <span className="text-primary fw-semibold">En votación hasta {diaMes(i.votacion.fecha_limite)}</span> : 'Votación cerrada'}
                  {i.propuesta_pdf && <> · <a href={i.propuesta_pdf} target="_blank" rel="noreferrer">Ver propuesta</a></>}
                </div>
              </div>
            </div>
          ))}
          {!d.propuestas.length && <Vacio chico icono="bi-file-earmark-text" texto="Todavía no participaste en contrataciones." />}
        </Tarjeta>
      </div>
      <div className="col-lg-5">
        <Tarjeta titulo="Mi perfil del Marketplace" a={`/marketplace/proveedor/${usuario.id}`} accion="Ver perfil">
          <div className="data-row"><span>Publicaciones</span><strong>{mk.publicaciones}</strong></div>
          <div className="data-row"><span>Reseñas</span><strong>{mk.resenas}</strong></div>
          <div className="data-row"><span>Calificación</span><strong>{mk.promedio ? `⭐ ${mk.promedio}` : '—'}</strong></div>
          {mk.descripcion && <p className="small text-secondary mt-2 mb-0">{mk.descripcion}</p>}
        </Tarjeta>
        <Tarjeta titulo="Reuniones confirmadas" a="/reuniones"><ListaReuniones reuniones={d.reuniones_confirmadas} /></Tarjeta>
        <Tarjeta titulo="Últimas reseñas">
          {d.ultimas_resenas.map((r, i) => (
            <div className="data-row" key={i}><span><strong>{r.autor}</strong>{r.descripcion && ` · ${r.descripcion}`}</span><span>{'⭐'.repeat(r.estrellas)}</span></div>
          ))}
          {!d.ultimas_resenas.length && <Vacio chico icono="bi-star" texto="Todavía no tenés reseñas." />}
        </Tarjeta>
      </div>
    </div>
  )
}

export default function Inicio() {
  const { usuario } = useSesion()
  const [d, setD] = useState(null)
  const [error, setError] = useState('')
  const cargar = useCallback(() => { api.get('/inicio/').then(setD).catch(e => setError(e.message)) }, [])
  useEffect(cargar, [cargar])

  useTitulo(usuario.es_admin ? 'Dashboard' : usuario.es_proveedor ? usuario.nombre : `Hola, ${usuario.nombre.split(' ')[0]}`,
    usuario.es_admin ? 'Resumen general de tus consorcios'
      : usuario.es_asistente ? (d?.admin ? `Asistente de ${d.admin}` : 'Asistente')
        : usuario.es_proveedor ? 'Tu panel de proveedor' : 'Tu consorcio en un vistazo')

  if (error) return <ErrorCarga error={error} />
  if (!d) return <Cargando />
  if (usuario.es_admin) return <InicioAdmin d={d} />
  if (usuario.es_asistente) return <InicioAsistente d={d} recargar={cargar} />
  if (usuario.es_propietario) return <InicioPropietario d={d} />
  return <InicioProveedor d={d} usuario={usuario} />
}
