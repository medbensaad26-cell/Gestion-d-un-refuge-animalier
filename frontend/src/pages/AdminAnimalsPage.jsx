import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { animalQuery, animalFiltersFromSearchParams, normalizeAnimalFilters, getAdminAnimals } from '../services/adminAnimals.js'
import { getAnimalStatus, ageLabel, sexLabel } from '../utils/animalLabels.js'

function SearchForm({ filters, onSearch }) {
  const [fields, setFields] = useState(filters)
  const [error, setError] = useState('')
  function submit(event) {
    event.preventDefault()
    try { onSearch(normalizeAnimalFilters(fields)); setError('') } catch (failure) { setError(failure.message) }
  }
  return <form className="announcement-filters" role="search" aria-label="Rechercher les animaux" onSubmit={submit}>
    <div className="filter-field filter-search"><label htmlFor="admin-animal-search">Rechercher</label><input type="search" id="admin-animal-search" placeholder="Nom, espèce ou race…" value={fields.q} onChange={e => setFields({ ...fields, q: e.target.value })} /></div>
    <div className="filter-field"><label htmlFor="admin-animal-species">Espèce</label><input id="admin-animal-species" placeholder="Ex. Chat" value={fields.espece} onChange={e => setFields({ ...fields, espece: e.target.value })} /></div>
    <div className="filter-field"><label htmlFor="admin-animal-status">Statut</label><select id="admin-animal-status" value={fields.statutAnimal} onChange={e => setFields({ ...fields, statutAnimal: e.target.value })}><option value="">Tous les statuts</option><option value="DISPONIBLE">Disponible</option><option value="EN_SOIN">En soins</option><option value="ADOPTE">Adopté</option></select></div>
    <button className="button button-primary" type="submit">Rechercher</button><div className="filter-footer"><p>L’espèce correspond au nom exact. Tous les statuts sont consultables.</p><Link to="/admin/animaux">Réinitialiser les filtres</Link></div>
    {error && <p className="filter-error" role="alert">{error}</p>}
  </form>
}

function AnimalList({ filters }) {
  const { session, expire } = useAdminSession()
  const [state, setState] = useState({ status: 'loading', animals: [] })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAdminAnimals(session.token, { filters, signal: controller.signal })
      .then(animals => { if (current) setState({ status: 'success', animals }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: 'error', animals: [], message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, filters, attempt, expire])
  function refresh() { setState({ status: 'loading', animals: [] }); setAttempt(value => value + 1) }
  if (state.status === 'loading') return <div className="feedback-panel" role="status">Chargement des animaux…</div>
  if (state.status === 'error') return <div className="feedback-panel" role="alert"><h2>La liste se fait attendre.</h2><p>{state.message}</p><button className="button button-outline" onClick={refresh}>Réessayer</button></div>
  return <section aria-label="Résultats des animaux"><div className="animal-results-heading"><p role="status">{state.animals.length} {state.animals.length === 1 ? 'animal trouvé' : 'animaux trouvés'}</p><button className="text-button" onClick={refresh}>Actualiser la liste</button></div>
    {state.animals.length === 0 ? <div className="feedback-panel"><h2>Aucun animal trouvé.</h2><p>Modifiez vos critères ou ajoutez un animal au refuge.</p></div> : <ul className="admin-animal-list">{state.animals.map(animal => {
      const status = getAnimalStatus(animal.statut)
      return <li className="admin-animal-card" key={animal.id}><div className="admin-animal-info"><p className="animal-record-id">Fiche n° {animal.id}</p><h2>{animal.nom}</h2><p>{animal.espece} · {animal.race || 'Race non renseignée'}</p><p>{ageLabel(animal.age)} · {sexLabel(animal.sexe)}</p><span className={`admin-animal-status status-${status.className}`}>{status.label}</span></div><div className="admin-animal-actions"><Link className="button button-outline" to={`/admin/animaux/${animal.id}/modifier`} aria-label={`Modifier ${animal.nom}, fiche ${animal.id}`}>Modifier</Link><Link to={`/admin/animaux/${animal.id}/dossier-medical`} aria-label={`Dossier médical de ${animal.nom}, fiche ${animal.id}`}>Dossier médical</Link><Link to={`/animaux/${animal.id}`}>Fiche publique ↗</Link>{animal.statut?.trim().toUpperCase() === 'ADOPTE' ? <span className="preserved-history">Historique conservé</span> : <Link className="delete-link" to={`/admin/animaux/${animal.id}/supprimer`} aria-label={`Supprimer ${animal.nom}, fiche ${animal.id}`}>Supprimer</Link>}</div></li>
    })}</ul>}
  </section>
}

export default function AdminAnimalsPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  let filters, invalid = false
  try { filters = animalFiltersFromSearchParams(params) } catch { invalid = true }
  useEffect(() => { const previous = document.title; document.title = 'Gestion des animaux · Le Refuge'; return () => { document.title = previous } }, [])
  return <section className="admin-animals" aria-labelledby="animals-management-title"><p className="eyebrow">Gestion du refuge</p><div className="dashboard-heading"><div><h1 id="animals-management-title">Les animaux</h1><p>Consultez et mettez à jour les fiches du refuge.</p></div><Link className="button button-primary" to="/admin/animaux/nouveau">Ajouter un animal</Link></div>
    {location.state?.notice && <p className="admin-notice animal-save-notice" role="status">{location.state.notice}</p>}
    {invalid ? <div className="feedback-panel" role="alert"><h2>Cette recherche n’est pas valide.</h2><Link to="/admin/animaux">Voir tous les animaux</Link></div> : <><SearchForm key={`form:${location.key}`} filters={filters} onSearch={criteria => setParams(animalQuery(criteria))} /><AnimalList key={`list:${location.key}`} filters={filters} /></>}
  </section>
}
