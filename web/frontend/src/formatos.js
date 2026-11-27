// Formatos argentinos, iguales a los de la web en Django.

/** 185000.5 -> "$ 185.000,50"; sin centavos si son cero. */
export function moneda(valor) {
  if (valor === null || valor === undefined) return ''
  const texto = Number(valor).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return '$ ' + (texto.endsWith(',00') ? texto.slice(0, -3) : texto)
}

/** "2026-11-18" -> "18/11/2026" */
export function fecha(iso) {
  if (!iso) return ''
  const [a, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${a}`
}

/** "2026-11-18" -> "18/11" */
export function diaMes(iso) {
  return fecha(iso).slice(0, 5)
}

export function plural(n, singular, pluralTexto = singular + 's') {
  return `${n} ${n === 1 ? singular : pluralTexto}`
}

/** Para comparar textos sin tildes ni mayúsculas (buscadores). */
export function normalizar(texto) {
  return (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']

/** "2026-11-21T19:00:00-03:00" -> "19:00" (hora local de Argentina, como viene de la API) */
export function hora(iso) {
  return iso ? iso.slice(11, 16) : ''
}

/** "2026-11-21T19:00:00-03:00" -> "21/11 19:00" */
export function fechaHora(iso) {
  return iso ? `${diaMes(iso)} ${hora(iso)}` : ''
}

/** "2026-11-21" -> "Sábado 21/11/2026" */
export function diaLargo(iso) {
  if (!iso) return ''
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number)
  const nombre = DIAS[new Date(a, m - 1, d).getDay()]
  return nombre.charAt(0).toUpperCase() + nombre.slice(1) + ' ' + fecha(iso)
}

/** Ícono de Bootstrap según la extensión del archivo. */
export function iconoArchivo(extension) {
  const e = (extension || '').replace('.', '')
  if (e === 'pdf') return 'bi-file-earmark-pdf text-danger'
  if (['doc', 'docx'].includes(e)) return 'bi-file-earmark-word'
  if (['xls', 'xlsx', 'csv'].includes(e)) return 'bi-file-earmark-excel text-success'
  return 'bi-file-earmark-image'
}
