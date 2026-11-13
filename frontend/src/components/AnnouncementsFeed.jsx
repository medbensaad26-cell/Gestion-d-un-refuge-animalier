import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAnnouncements } from '../services/announcements.js'
import AnnouncementCard from './AnnouncementCard.jsx'
import PawIcon from './PawIcon.jsx'

export default function AnnouncementsFeed({ filters, limit, headingLevel = 2 }) {
  const [request, setRequest] = useState(0)
  const [state, setState] = useState({ status: 'loading', announcements: [] })
  const q = filters?.q ?? ''
  const espece = filters?.espece ?? ''
  const statutAnimal = filters?.statutAnimal ?? ''
  const filtered = Boolean(q || espece || statutAnimal)
  const Heading = headingLevel === 3 ? 'h3' : 'h2'

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    getAnnouncements({ filters: { q, espece, statutAnimal }, signal: controller.signal })
      .then((announcements) => { if (active) setState({ status: 'success', announcements }) })
      .catch(() => { if (active && !controller.signal.aborted) setState({ status: 'error', announcements: [] }) })
    return () => { active = false; controller.abort() }
  }, [q, espece, statutAnimal, request])

  function retry() {
    setState({ status: 'loading', announcements: [] })
    setRequest((value) => value + 1)
  }

  if (state.status === 'loading') return <div className="feedback-panel" role="status"><span className="loading-spinner" aria-hidden="true" /><Heading>Les nouvelles du refuge arrivent…</Heading><p>Chargement des annonces publiées.</p></div>
  if (state.status === 'error') return <div className="feedback-panel" role="alert"><Heading>Les annonces se font attendre.</Heading><p>La liste est momentanément indisponible. Vous pouvez réessayer.</p><button className="button button-outline" type="button" onClick={retry}>Réessayer les annonces</button></div>
  if (state.announcements.length === 0) return <div className="feedback-panel" role="status"><PawIcon className="feedback-paw" /><Heading>{filtered ? 'Aucune annonce ne correspond à votre recherche.' : 'Pas encore d’annonce publiée.'}</Heading><p>{filtered ? 'Essayez un autre mot ou élargissez vos filtres.' : 'Revenez bientôt découvrir les nouvelles du refuge.'}</p>{filtered && <Link className="button button-outline" to="/annonces">Effacer les filtres</Link>}</div>
  const items = limit ? state.announcements.slice(0, limit) : state.announcements
  return <>
    {!limit && <p className="announcement-count" role="status">{items.length} {items.length === 1 ? 'annonce trouvée' : 'annonces trouvées'}</p>}
    <div className="announcements-grid">{items.map((announcement) => <AnnouncementCard key={announcement.id} announcement={announcement} headingLevel={headingLevel} />)}</div>
  </>
}
