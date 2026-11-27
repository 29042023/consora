import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useSesion } from './sesion'
import Layout from './componentes/Layout'
import Login from './paginas/Login'
import Inicio from './paginas/Inicio'
import Consorcios from './paginas/Consorcios'
import Facturacion from './paginas/Facturacion'
import Contratos from './paginas/Contratos'
import ContratoDetalle from './paginas/ContratoDetalle'
import Trabajos from './paginas/Trabajos'
import Votaciones from './paginas/Votaciones'
import VotacionDetalle from './paginas/VotacionDetalle'
import Reuniones from './paginas/Reuniones'
import ReunionDetalle from './paginas/ReunionDetalle'
import Agenda from './paginas/Agenda'
import Historial from './paginas/Historial'
import FacturasProcesadas from './paginas/FacturasProcesadas'
import Chat from './paginas/Chat'
import { Marketplace, MarketplaceConsorcios } from './paginas/Marketplace'
import PerfilProveedor from './paginas/PerfilProveedor'
import Asistentes from './paginas/Asistentes'
import Cuenta from './paginas/Cuenta'

/** Si no hay sesión, manda al login (y vuelve a la página pedida después de ingresar). */
function Protegida({ children }) {
  const { usuario } = useSesion()
  const location = useLocation()
  if (usuario === undefined) return <div className="app-cargando"><div className="spinner-border text-primary" /></div>
  if (usuario === null) return <Navigate to="/login" replace state={{ desde: location.pathname + location.search }} />
  return <Layout>{children}</Layout>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Protegida><Inicio /></Protegida>} />
      <Route path="/consorcios" element={<Protegida><Consorcios /></Protegida>} />
      <Route path="/consorcios/:id/facturacion" element={<Protegida><Facturacion /></Protegida>} />
      <Route path="/contratos" element={<Protegida><Contratos /></Protegida>} />
      <Route path="/contratos/:id" element={<Protegida><ContratoDetalle /></Protegida>} />
      <Route path="/trabajos" element={<Protegida><Trabajos /></Protegida>} />
      <Route path="/votaciones" element={<Protegida><Votaciones /></Protegida>} />
      <Route path="/votaciones/:id" element={<Protegida><VotacionDetalle /></Protegida>} />
      <Route path="/reuniones" element={<Protegida><Reuniones /></Protegida>} />
      <Route path="/reuniones/:id" element={<Protegida><ReunionDetalle /></Protegida>} />
      <Route path="/agenda" element={<Protegida><Agenda /></Protegida>} />
      <Route path="/historial" element={<Protegida><Historial /></Protegida>} />
      <Route path="/facturas" element={<Protegida><FacturasProcesadas /></Protegida>} />
      <Route path="/chat" element={<Protegida><Chat /></Protegida>} />
      <Route path="/chat/:id" element={<Protegida><Chat /></Protegida>} />
      <Route path="/marketplace" element={<Protegida><Marketplace /></Protegida>} />
      <Route path="/marketplace/consorcios" element={<Protegida><MarketplaceConsorcios /></Protegida>} />
      <Route path="/marketplace/proveedor/:id" element={<Protegida><PerfilProveedor /></Protegida>} />
      <Route path="/asistentes" element={<Protegida><Asistentes /></Protegida>} />
      <Route path="/cuenta" element={<Protegida><Cuenta /></Protegida>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
