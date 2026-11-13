import { useCallback, useEffect, useRef, useState } from 'react'
import { AdminSessionContext } from './AdminSessionContext.js'
import { clearAdminSession, loginAdmin, logoutAdmin, restoreAdminSession, storeAdminSession } from '../services/adminAuth.js'

export default function AdminSessionProvider({ children }) {
  const [session, setSession] = useState(() => restoreAdminSession())
  const [notice, setNotice] = useState('')
  const current = useRef(session)
  const version = useRef(0)

  const expire = useCallback((token) => {
    if (current.current?.token !== token) return
    version.current++
    current.current = null
    clearAdminSession()
    setSession(null)
    setNotice('Votre session a expiré. Veuillez vous reconnecter.')
  }, [])

  useEffect(() => {
    if (!session) return
    const timer = setTimeout(() => expire(session.token), Math.max(0, session.expiresAt - Date.now()))
    return () => clearTimeout(timer)
  }, [session, expire])

  async function signIn(email, password, options) {
    const attempt = ++version.current
    const result = await loginAdmin(email, password, options)
    if (attempt !== version.current || options?.signal?.aborted) throw new DOMException('Cancelled', 'AbortError')
    const next = { token: result.token, expiresAt: result.expiresAt }
    current.current = next
    storeAdminSession(next)
    setSession(next)
    setNotice('')
  }

  async function signOut() {
    const previous = current.current
    version.current++
    current.current = null
    clearAdminSession()
    setSession(null)
    setNotice('Vous êtes déconnecté.')
    if (previous) {
      try { await logoutAdmin(previous.token) } catch (error) { if (!current.current) setNotice(error.message) }
    }
  }

  return <AdminSessionContext.Provider value={{ session, notice, signIn, signOut, expire }}>{children}</AdminSessionContext.Provider>
}
