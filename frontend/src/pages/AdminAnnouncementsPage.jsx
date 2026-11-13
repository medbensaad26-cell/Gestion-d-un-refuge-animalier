import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminAnnouncements } from '../services/adminAnnouncements.js'
import { getAdminAnimals } from '../services/adminAnimals.js'
import { publicationDateLabel } from '../services/announcements.js'

export default function AdminAnnouncementsPage() {
  const { session, expire } = useAdminSession()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ status: 'loading', announcements: [], animals: [] })
  useEffect(() => { const previous = document.title; document.title = 'Gestion des annonces · Le Refuge'; return () => { document.title = previous } }, [])
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    Promise.all([getAdminAnnouncements(session.token, { signal: controller.signal }), getAdminAnimals(session.token, { signal: controller.signal })])
      .then(([announcements, animals]) => { if (current) setState({ status: 'success', announcements, animals }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: 'error', announcements: [], animals: [], message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, attempt, expire])
  function refresh() { setState({ status: 'loading', announcements: [], animals: [] }); setAttempt(value => value + 1) }
  const names = new Map(state.animals.map(animal => [animal.id, animal.nom]))
  return <section aria-labelledby="admin-announcements-title"><p className="eyebrow">Les nouvelles du refuge</p><div className="dashboard-heading"><div><h1 id="admin-announcements-title">Les annonces</h1><p>Préparez et publiez les annonces concernant les animaux.</p></div><Link className="button button-primary" to="/admin/annonces/nouvelle">Créer une annonce</Link></div>
    <p className="admin-announcement-intro">Seules les annonces portant la visibilité « Publiée » apparaissent sur le site public.</p>
    <div className="animal-results-heading"><Link to="/annonces">Consulter les annonces publiques ↗</Link><button className="text-button" onClick={refresh}>Actualiser les annonces</button></div>
    {state.status === 'loading' && <div className="feedback-panel" role="status">Chargement des annonces…</div>}
    {state.status === 'error' && <div className="feedback-panel" role="alert"><h2>Les annonces se font attendre.</h2><p>{state.message}</p><button className="button button-outline" onClick={refresh}>Réessayer</button></div>}
    {state.status === 'success' && <><p className="admin-announcement-count" role="status">{state.announcements.length} {state.announcements.length === 1 ? 'annonce enregistrée' : 'annonces enregistrées'}, tous statuts confondus.</p>{state.announcements.length === 0 ? <div className="feedback-panel"><h2>Aucune annonce pour le moment.</h2><p>Créez une première annonce pour présenter un animal du refuge.</p></div> : <ul className="admin-announcement-list">{state.announcements.map(item => <li className="admin-announcement-card" key={item.id}><div className="admin-announcement-meta"><span className={`publication-badge ${item.statut === 'PUBLIEE' ? 'is-published' : ''}`}>{item.statut === 'PUBLIEE' ? 'Publiée' : 'Non publiée'}</span><span>Annonce n° {item.id}</span><time dateTime={item.datePublication}>{publicationDateLabel(item.datePublication)}</time></div><h2>{item.titre}</h2><p className="admin-announcement-description">{item.description || 'Description non renseignée'}</p><div className="admin-announcement-links"><Link to={`/animaux/${item.animalId}`}>{names.get(item.animalId) || 'Animal'} · Fiche n° {item.animalId} ↗</Link><Link className="button button-outline" to={`/admin/annonces/${item.id}/modifier`} aria-label={`Modifier l’annonce ${item.id} : ${item.titre}`}>{item.statut === 'PUBLIEE' ? 'Modifier l’annonce' : 'Modifier ou publier'}</Link></div></li>)}</ul>}</>}
  </section>
}
