import { Link } from 'react-router'
import PawIcon from './PawIcon.jsx'
import { publicationDateLabel } from '../services/announcements.js'

export default function AnnouncementCard({ announcement, headingLevel = 2 }) {
  const Heading = headingLevel === 3 ? 'h3' : 'h2'
  return (
    <article className="announcement-card" aria-labelledby={`announcement-${announcement.id}`}>
      <div className="announcement-meta"><span className="announcement-mark"><PawIcon /></span><span>Publié le <time dateTime={announcement.datePublication}>{publicationDateLabel(announcement.datePublication)}</time></span></div>
      <Heading id={`announcement-${announcement.id}`}>{announcement.titre}</Heading>
      <p className="announcement-description">{announcement.description?.trim() || 'Découvrez l’animal concerné pour en savoir plus sur son profil.'}</p>
      <Link className="announcement-animal-link" to={`/animaux/${announcement.animalId}`} aria-label={`Voir l’animal de l’annonce « ${announcement.titre} »`}>Découvrir cet animal <span aria-hidden="true">↗</span></Link>
    </article>
  )
}
