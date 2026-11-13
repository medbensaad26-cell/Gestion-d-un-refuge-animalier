import { useEffect } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import PawIcon from '../components/PawIcon.jsx'
import { useAdminSession } from '../context/AdminSessionContext.js'

export default function SiteLayout() {
  const { pathname, hash } = useLocation()
  const { session } = useAdminSession()

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'start' })
      else window.scrollTo({ top: 0 })
    })
    return () => cancelAnimationFrame(frame)
  }, [pathname, hash])

  return (
    <>
      <a className="skip-link" href="#contenu">Aller au contenu</a>
      <header className="site-header">
        <div className="container header-content">
          <Link className="brand" to="/" aria-label="Le Refuge — accueil">
            <span className="brand-icon"><PawIcon /></span>
            <span>Le Refuge<span className="brand-caption">Une place pour chacun.</span></span>
          </Link>
          <nav className="main-nav" aria-label="Navigation principale">
            <Link to="/" className={pathname === '/' && !hash ? 'nav-link active' : 'nav-link'} aria-current={pathname === '/' && !hash ? 'page' : undefined}>Accueil</Link>
            <Link to="/#animaux" className={hash === '#animaux' || pathname.startsWith('/animaux/') ? 'nav-link active' : 'nav-link'} aria-current={pathname.startsWith('/animaux/') || hash === '#animaux' ? 'location' : undefined}>Les animaux</Link>
            <Link to="/#refuge" className={hash === '#refuge' ? 'nav-link active' : 'nav-link'}>Notre refuge</Link>
            <Link to="/annonces" className={pathname === '/annonces' ? 'nav-link active' : 'nav-link'} aria-current={pathname === '/annonces' ? 'page' : undefined}>Annonces</Link>
          </nav>
        </div>
      </header>
      <main id="contenu" tabIndex={-1}><Outlet /></main>
      <footer className="site-footer">
        <div className="container footer-content">
          <Link className="footer-brand" to="/"><PawIcon />Le Refuge</Link>
          <p>Un nouveau départ, une rencontre à la fois.</p>
          <Link to="/#animaux">Découvrir nos animaux <span aria-hidden="true">↗</span></Link>
          <Link to={session ? '/admin' : '/admin/connexion'}>Administration</Link>
        </div>
      </footer>
    </>
  )
}
