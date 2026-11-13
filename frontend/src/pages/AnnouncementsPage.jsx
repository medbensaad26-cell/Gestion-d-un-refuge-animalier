import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import AnnouncementsFeed from '../components/AnnouncementsFeed.jsx'
import { announcementQuery, filtersFromSearchParams, normalizeAnnouncementFilters } from '../services/announcements.js'

function FiltersForm({ filters, onSearch }) {
  const [q, setQ] = useState(filters.q)
  const [espece, setEspece] = useState(filters.espece)
  const [statutAnimal, setStatutAnimal] = useState(filters.statutAnimal)
  const [error, setError] = useState('')

  function submit(event) {
    event.preventDefault()
    try {
      const criteria = normalizeAnnouncementFilters({ q, espece, statutAnimal })
      setError('')
      onSearch(criteria)
    } catch (failure) { setError(failure.message) }
  }

  return <form className="announcement-filters" role="search" aria-label="Rechercher les annonces" onSubmit={submit}>
    <div className="filter-field filter-search"><label htmlFor="announcement-search">Rechercher</label><input id="announcement-search" type="search" value={q} onChange={(event) => setQ(event.target.value)} placeholder="Un nom, un mot dans l’annonce…" aria-describedby={error ? 'filter-error' : undefined} /></div>
    <div className="filter-field"><label htmlFor="announcement-species">Espèce</label><input id="announcement-species" type="text" value={espece} onChange={(event) => setEspece(event.target.value)} placeholder="Ex. Chat" /></div>
    <div className="filter-field"><label htmlFor="announcement-status">Statut de l’animal</label><select id="announcement-status" value={statutAnimal} onChange={(event) => setStatutAnimal(event.target.value)}><option value="">Tous les statuts</option><option value="DISPONIBLE">Disponible</option><option value="EN_SOIN">En soins</option><option value="ADOPTE">Adopté</option></select></div>
    <button className="button button-primary" type="submit">Rechercher</button>
    <div className="filter-footer"><p>L’espèce correspond au nom exact, par exemple « Chat ».</p><Link to="/annonces">Réinitialiser les filtres</Link></div>
    {error && <p className="filter-error" id="filter-error" role="alert">{error}</p>}
  </form>
}

export default function AnnouncementsPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  let filters, invalid = false
  try { filters = filtersFromSearchParams(params) } catch { invalid = true }

  useEffect(() => {
    const original = document.title
    document.title = 'Les annonces · Le Refuge'
    return () => { document.title = original }
  }, [])

  function search(criteria) {
    setParams(announcementQuery(criteria))
  }

  return <section className="announcements-page container" aria-labelledby="announcements-page-title">
    <p className="eyebrow">Les nouvelles du refuge</p>
    <h1 id="announcements-page-title">Une histoire à partager.</h1>
    <p className="announcements-intro">Retrouvez les annonces d’adoption publiées par le refuge. Un nom vous parle ? Découvrez le profil de l’animal pour faire connaissance.</p>
    {invalid ? <div className="feedback-panel" role="alert"><h2>Cette recherche n’est pas valide.</h2><p>Repartez de la liste des annonces pour choisir vos critères.</p><Link className="button button-primary" to="/annonces">Voir toutes les annonces</Link></div> : <>
      <FiltersForm key={location.key} filters={filters} onSearch={search} />
      <section className="announcement-results" aria-label="Résultats des annonces publiées"><AnnouncementsFeed key={location.key} filters={filters} /></section>
    </>}
  </section>
}
