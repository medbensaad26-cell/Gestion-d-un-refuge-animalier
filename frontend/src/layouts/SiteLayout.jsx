import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import PawIcon from '../components/PawIcon.jsx'
import Icon from '../components/Icon.jsx'
import { useAdminSession } from '../context/AdminSessionContext.js'

export default function SiteLayout() {
  const { pathname, hash } = useLocation()
  const { session } = useAdminSession()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButton = useRef(null)

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'start' })
      else window.scrollTo({ top: 0 })
    })
    return () => cancelAnimationFrame(frame)
  }, [pathname, hash])

  return <>
    <a className="skip-link" href="#contenu">Aller au contenu</a>
    <div className="site-topbar"><PawIcon /><span>Une place pour chacun. Une rencontre pour la vie.</span><PawIcon /></div>
    <header className="site-header">
      <div className="container header-content" onKeyDown={event => { if (event.key === 'Escape' && menuOpen) { setMenuOpen(false); menuButton.current?.focus() } }}>
        <Link className="brand" to="/" aria-label="Pawtale — accueil" onClick={() => setMenuOpen(false)}>
          <span className="brand-icon"><PawIcon /></span>
          <span>Pawtale<span className="brand-caption">DE BELLES HISTOIRES À ÉCRIRE</span></span>
        </Link>
        <button ref={menuButton} className="mobile-menu-toggle" type="button" aria-label={menuOpen ? 'Fermer le menu' : 'Ouvrir le menu'} aria-expanded={menuOpen} aria-controls="public-navigation" onClick={() => setMenuOpen(value => !value)}><Icon name={menuOpen ? 'close' : 'menu'} /></button>
        <nav id="public-navigation" className={`main-nav ${menuOpen ? 'is-open' : ''}`} aria-label="Navigation principale" onClick={event => { if (event.target.closest('a')) setMenuOpen(false) }}>
          <Link to="/" className={pathname === '/' && !hash ? 'nav-link active' : 'nav-link'} aria-current={pathname === '/' && !hash ? 'page' : undefined}>Accueil</Link>
          <Link to="/#animaux" className={hash === '#animaux' || pathname.startsWith('/animaux/') ? 'nav-link active' : 'nav-link'} aria-current={pathname.startsWith('/animaux/') || hash === '#animaux' ? 'location' : undefined}>Les animaux</Link>
          <Link to="/#refuge" className={hash === '#refuge' ? 'nav-link active' : 'nav-link'}>Notre refuge</Link>
          <Link to="/annonces" className={pathname === '/annonces' ? 'nav-link active' : 'nav-link'} aria-current={pathname === '/annonces' ? 'page' : undefined}>Annonces</Link>
          <Link to="/trouver-compagnon" className={`nav-match ${pathname === '/trouver-compagnon' ? 'active' : ''}`} aria-current={pathname === '/trouver-compagnon' ? 'page' : undefined}><Icon name="sparkle" />Trouver un compagnon</Link>
        </nav>
      </div>
    </header>
    <main id="contenu" tabIndex={-1}><Outlet /></main>
    <footer className="site-footer">
      <div className="container footer-grid">
        <div><Link className="brand footer-brand" to="/"><span className="brand-icon"><PawIcon /></span><span>Pawtale</span></Link><p className="footer-manifesto">Les plus belles histoires<br />commencent par une rencontre.</p><span className="footer-small">À chacun son histoire. À chacun sa place.</span></div>
        <div className="footer-links"><p>Faire connaissance</p><Link to="/#animaux">Les animaux du refuge</Link><Link to="/trouver-compagnon">Votre compagnon idéal</Link><Link to="/annonces">Les annonces</Link></div>
        <div className="footer-links"><p>Un projet d’adoption ?</p><Link to="/#parcours">Les étapes de l’adoption</Link><Link to="/#refuge">L’esprit du refuge</Link><Link to="/#questions">Questions fréquentes</Link><Link to={session ? '/admin' : '/admin/connexion'}>Administration <span aria-hidden="true">↗</span></Link></div>
      </div>
      <div className="container footer-bottom"><span>Pawtale · Accueillir. Prendre soin. Réunir.</span><details className="photo-credits"><summary>Crédits photographiques</summary><p>Photos illustratives, distinctes des animaux et du lieu réels. <a href="https://www.pexels.com/photo/short-coated-tan-dog-2253275/">Helena Lopes</a>, <a href="https://www.pexels.com/photo/brown-and-white-short-coated-puppy-1805164/">Valeria Boltneva</a>, <a href="https://www.pexels.com/photo/white-and-grey-kitten-on-brown-and-black-leopard-print-textile-45201/">Pixabay</a> et <a href="https://www.pexels.com/photo/two-yellow-labrador-retriever-puppies-1108099/">Chevanon Photography</a>, sous <a href="https://www.pexels.com/license/">licence Pexels</a>.</p></details></div>
    </footer>
  </>
}
