import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api } from './api'

const SesionContext = createContext(null)

/** Usuario logueado (o null) y acciones de sesión, compartidos por toda la app. */
export function SesionProvider({ children }) {
  const [usuario, setUsuario] = useState(undefined) // undefined = todavía cargando

  useEffect(() => {
    // Primero la cookie CSRF; después quién está logueado
    api.get('/csrf/')
      .then(() => api.get('/yo/'))
      .then(setUsuario)
      .catch(() => setUsuario(null))
  }, [])

  const ingresar = useCallback(async (email, password) => {
    const u = await api.post('/login/', { email, password })
    await api.get('/csrf/') // la sesión nueva rota el token
    setUsuario(u)
    return u
  }, [])

  const salir = useCallback(async () => {
    await api.post('/logout/').catch(() => {})
    setUsuario(null)
  }, [])

  return <SesionContext.Provider value={{ usuario, ingresar, salir }}>{children}</SesionContext.Provider>
}

export function useSesion() {
  return useContext(SesionContext)
}
