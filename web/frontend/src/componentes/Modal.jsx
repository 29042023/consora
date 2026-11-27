import { useEffect } from 'react'

/** Ventana modal con el estilo de Bootstrap, manejada con estado de React (sin el JS de Bootstrap). */
export default function Modal({ titulo, abierto, onCerrar, children, pie, onSubmit }) {
  useEffect(() => {
    if (!abierto) return undefined
    document.body.classList.add('modal-open')
    const esc = e => e.key === 'Escape' && onCerrar()
    window.addEventListener('keydown', esc)
    return () => { document.body.classList.remove('modal-open'); window.removeEventListener('keydown', esc) }
  }, [abierto, onCerrar])

  if (!abierto) return null
  const Contenido = onSubmit ? 'form' : 'div'
  return (
    <>
      <div className="modal d-block" tabIndex="-1" onMouseDown={e => e.target === e.currentTarget && onCerrar()}>
        <div className="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <Contenido className="modal-content" onSubmit={onSubmit} noValidate={onSubmit ? true : undefined}>
            <div className="modal-header">
              <h5 className="modal-title fw-bold">{titulo}</h5>
              <button type="button" className="btn-close" onClick={onCerrar} />
            </div>
            <div className="modal-body">{children}</div>
            {pie && <div className="modal-footer">{pie}</div>}
          </Contenido>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  )
}
