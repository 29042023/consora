/** Estado de carga y de error de una página. */
export function Cargando() {
  return <div className="text-center py-5"><div className="spinner-border text-primary" /></div>
}

export function ErrorCarga({ error }) {
  return (
    <div className="empty-state">
      <i className="bi bi-exclamation-triangle" />
      <h5>No se pudo cargar</h5>
      <p>{error}</p>
    </div>
  )
}

export function Vacio({ icono, titulo, texto, chico }) {
  return (
    <div className={'empty-state' + (chico ? ' small-state' : '')}>
      <i className={'bi ' + icono} />
      {titulo && <h5>{titulo}</h5>}
      {texto && <p>{texto}</p>}
    </div>
  )
}
