import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminRequests, requestStatus } from '../services/adminRequests.js'
import { getAdminAnimals } from '../services/adminAnimals.js'
import { publicationDateLabel as requestDateLabel } from '../services/announcements.js'

export default function AdminRequestsPage() {
  const { session, expire } = useAdminSession()
  const [attempt, setAttempt] = useState(0)
  const [filter, setFilter] = useState('')
  const [state, setState] = useState({ status: 'loading', requests: [], animals: [] })
  useEffect(() => { const previous = document.title; document.title = 'Demandes d’adoption · Pawtale'; return () => { document.title = previous } }, [])
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    Promise.all([getAdminRequests(session.token, { signal: controller.signal }), getAdminAnimals(session.token, { signal: controller.signal })])
      .then(([requests, animals]) => { if (current) setState({ status: 'success', requests, animals }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: 'error', requests: [], animals: [], message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, attempt, expire])
  function refresh() { setState({ status: 'loading', requests: [], animals: [] }); setAttempt(value => value + 1) }
  const names = new Map(state.animals.map(animal => [animal.id, animal.nom]))
  const visible = state.requests.filter(item => !filter || item.statut === filter)
  return <section aria-labelledby="admin-requests-title"><p className="eyebrow">Accompagner les adoptions</p><div className="dashboard-heading"><div><h1 id="admin-requests-title">Demandes d’adoption</h1><p>Consultez les demandes reçues et prenez une décision.</p></div><button className="button button-outline" onClick={refresh}>Actualiser les demandes</button></div>
    <div className="request-filter filter-field"><label htmlFor="request-status-filter">Afficher les demandes</label><select id="request-status-filter" value={filter} onChange={event => setFilter(event.target.value)}><option value="">Tous les statuts</option><option value="EN_ATTENTE">En attente</option><option value="ACCEPTEE">Acceptées</option><option value="REFUSEE">Refusées</option></select></div>
    {state.status === 'loading' && <div className="feedback-panel" role="status">Chargement des demandes…</div>}
    {state.status === 'error' && <div className="feedback-panel" role="alert"><h2>Les demandes se font attendre.</h2><p>{state.message}</p><button className="button button-outline" onClick={refresh}>Réessayer</button></div>}
    {state.status === 'success' && <><p className="request-result-count" role="status">{visible.length} {visible.length === 1 ? 'demande affichée' : 'demandes affichées'} · {state.requests.filter(item => item.statut === 'EN_ATTENTE').length} en attente au total.</p>{visible.length === 0 ? <div className="feedback-panel"><h2>Aucune demande à afficher.</h2><p>{filter ? 'Choisissez un autre statut pour consulter les demandes.' : 'Les nouvelles demandes apparaîtront ici après leur envoi depuis le site public.'}</p></div> : <ul className="admin-request-list">{visible.map(item => <li className="admin-request-card" key={item.id}><div className="request-card-body"><div className="request-meta"><span className={`request-status request-status-${item.statut}`}>{requestStatus(item.statut)}</span><span>Demande n° {item.id}</span><time dateTime={item.dateDemande}>{requestDateLabel(item.dateDemande)}</time></div><h2>{item.nomDemandeur}</h2><p>Pour {names.get(item.animalId) || 'l’animal'} · Fiche n° {item.animalId}</p></div><Link className="button button-outline" to={`/admin/demandes/${item.id}`} aria-label={`Consulter la demande ${item.id}`}>Consulter la demande</Link></li>)}</ul>}</>}
  </section>
}
