/** Foto de perfil o, si no hay, las iniciales. */
export default function Avatar({ img, iniciales, clase = 'sr-avatar redondo', titulo }) {
  if (img) return <img src={img} alt="" className={clase} style={{ objectFit: 'cover' }} title={titulo} />
  return <div className={clase} title={titulo}>{iniciales}</div>
}
