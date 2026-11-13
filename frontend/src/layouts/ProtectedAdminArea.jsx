import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError, getAdminDashboard } from '../services/adminAuth.js'
import PawIcon from '../components/PawIcon.jsx'

function AdminAccess({ session }) {
  const { signOut, expire } = useAdminSession()
  const [request, setRequest] = useState(0)
  const [state, setState] = useState({ status: 'loading', summary: null, message: '' })

  useEffect(() => { window.scrollTo({ top: 0 }) }, [])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAdminDashboard(session.token, { signal: controller.signal })
      .then(summary => { if (current) setState({ status: 'success', summary, message: '' }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: 'error', summary: null, message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, request, expire])

  function refresh() {
    setState({ status: 'loading', summary: null, message: '' })
    setRequest(value => value + 1)
  }

  return <div className="admin-shell">
    <a className="skip-link" href="#admin-content">Aller au contenu</a>
    <header className="admin-header"><div className="container admin-header-content">
      <Link className="brand" to="/admin"><span className="brand-icon"><PawIcon /></span><span>Le Refuge<span className="brand-caption">Espace administrateur</span></span></Link>
      <div className="admin-header-actions"><Link to="/">Retour au site public ↗</Link><button type="button" className="button button-outline" onClick={signOut}>Se déconnecter</button></div>
    </div></header>
    <nav className="admin-nav container" aria-label="Navigation administrateur"><NavLink to="/admin" end>Tableau de bord</NavLink><NavLink to="/admin/animaux">Animaux</NavLink><NavLink to="/admin/annonces">Annonces</NavLink><NavLink to="/admin/demandes">Demandes</NavLink></nav>
    <main id="admin-content" className="container admin-main" tabIndex={-1}>
      {state.status === 'loading' && <div className="feedback-panel" role="status"><span className="loading-spinner" aria-hidden="true" /><h1>Vérification de votre accès…</h1><p>Chargement du tableau de bord.</p></div>}
      {state.status === 'error' && <div className="feedback-panel" role="alert"><h1>Le tableau de bord se fait attendre.</h1><p>{state.message}</p><button className="button button-outline" type="button" onClick={refresh}>Réessayer</button></div>}
      {state.status === 'success' && <Outlet context={{ summary: state.summary, refresh }} />}
    </main>
    <footer className="admin-footer container">Le Refuge · Espace de gestion</footer>
  </div>
}

export default function ProtectedAdminArea() {
  const { session } = useAdminSession()
  const location = useLocation()
  if (!session) return <Navigate to="/admin/connexion" replace />
  return <AdminAccess key={`${session.token}:${location.pathname}`} session={session} />
}
