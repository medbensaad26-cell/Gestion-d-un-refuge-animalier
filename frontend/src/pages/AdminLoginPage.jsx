import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import PawIcon from '../components/PawIcon.jsx'

export default function AdminLoginPage() {
  const { session, notice, signIn } = useAdminSession()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const lock = useRef(false)
  const request = useRef(null)
  const active = useRef(true)
  const errorPanel = useRef(null)

  useEffect(() => {
    active.current = true
    const original = document.title
    document.title = 'Connexion administrateur · Le Refuge'
    return () => { active.current = false; request.current?.abort(); document.title = original }
  }, [])
  useEffect(() => { if (error) errorPanel.current?.focus() }, [error])

  async function submit(event) {
    event.preventDefault()
    if (lock.current) return
    if (!email.trim() || !password.trim()) { setError('Renseignez votre e-mail et votre mot de passe.'); return }
    lock.current = true
    setBusy(true)
    setError('')
    const controller = new AbortController()
    request.current = controller
    try { await signIn(email, password, { signal: controller.signal }) } catch (failure) {
      if (active.current && !controller.signal.aborted) setError(failure.message)
    } finally {
      lock.current = false
      if (active.current) { setPassword(''); setBusy(false) }
    }
  }

  if (session) return <Navigate to="/admin" replace />
  return <section className="admin-login container" aria-labelledby="admin-login-title">
    <div className="admin-login-mark"><PawIcon /></div><p className="eyebrow">Espace administrateur</p>
    <h1 id="admin-login-title">Bienvenue au refuge.</h1><p className="admin-login-intro">Connectez-vous avec le compte administrateur du refuge pour accéder à votre tableau de bord.</p>
    {notice && <p className="admin-notice" role="status">{notice}</p>}
    {error && <div className="admin-error" ref={errorPanel} tabIndex={-1} role="alert">{error}</div>}
    <form onSubmit={submit} noValidate aria-busy={busy}>
      <fieldset disabled={busy} className="admin-login-fields"><legend>Identifiants de connexion</legend>
        <div className="adoption-field"><label htmlFor="admin-email">Adresse e-mail</label><input id="admin-email" type="email" autoComplete="username" required value={email} onChange={event => setEmail(event.target.value)} /></div>
        <div className="adoption-field"><label htmlFor="admin-password">Mot de passe</label><input id="admin-password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} /><button className="password-toggle" type="button" aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}</button></div>
      </fieldset>
      <button className="button button-primary admin-signin" type="submit" disabled={busy}>{busy ? 'Connexion…' : 'Se connecter'}</button>
      {busy && <p className="admin-login-progress" role="status">Vérification de vos identifiants.</p>}
    </form>
  </section>
}
