import { Link, NavLink } from 'react-router-dom'

/** Pestañas de las actividades de un consorcio (las que no están en React abren la web clásica). */
export default function TabsConsorcio({ id, activa }) {
  const clase = nombre => (activa === nombre ? 'active' : undefined)
  return (
    <>
      <Link to="/consorcios" className="back-link mb-2 d-inline-block"><i className="bi bi-arrow-left" /> Consorcios</Link>
      <div className="consorcio-tabs">
        <NavLink to={`/consorcios/${id}/facturacion`} className={clase('facturas')}><i className="bi bi-receipt" /> Facturación</NavLink>
        <NavLink to={`/contratos?consorcio=${id}`} className={clase('contratos')}><i className="bi bi-file-earmark-text" /> Contratos</NavLink>
        <NavLink to={`/votaciones?consorcio=${id}`} className={clase('votaciones')}><i className="bi bi-check2-square" /> Votaciones</NavLink>
        <NavLink to={`/reuniones?consorcio=${id}`} className={clase('reuniones')}><i className="bi bi-camera-video" /> Reuniones</NavLink>
        <NavLink to={`/historial?consorcio=${id}`} className={clase('historial')}><i className="bi bi-clock-history" /> Historial</NavLink>
      </div>
    </>
  )
}
