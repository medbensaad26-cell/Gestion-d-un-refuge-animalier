import { Link } from 'react-router'
import AnimalPortrait from './AnimalPortrait.jsx'
import { ageLabel, sexLabel, getAnimalStatus } from '../utils/animalLabels.js'
import { tiltCard, resetCard } from '../utils/cardMotion.js'

export default function AnimalCard({ animal, index }) {
  const status = getAnimalStatus(animal.statut)
  return (
    <article className={`animal-card tone-${index % 3}`} aria-labelledby={`animal-${animal.id}`} onPointerMove={tiltCard} onPointerLeave={resetCard}>
      <Link className="animal-card-link" to={`/animaux/${animal.id}`} aria-label={`Voir la fiche de ${animal.nom}`}>
        <div className="animal-card-visual">
          <span className={`status-badge status-${status.className}`}><span aria-hidden="true" />{status.cardLabel}</span>
          <AnimalPortrait species={animal.espece} variant={animal.id} imageUrl={animal.imageUrl} name={animal.nom} />
        </div>
        <div className="animal-card-content">
          <p className="animal-species">{animal.espece}{animal.race ? ` · ${animal.race}` : ''}</p>
          <h3 id={`animal-${animal.id}`}>{animal.nom}</h3>
          <p className="animal-meta"><span>{ageLabel(animal.age)}</span><span aria-hidden="true">·</span><span>{sexLabel(animal.sexe)}</span></p>
          {animal.etatSante && <p className="animal-health"><span className="health-dot" aria-hidden="true" />{animal.etatSante}</p>}
          <span className="animal-card-action">Voir la fiche <span aria-hidden="true">↗</span></span>
        </div>
      </Link>
    </article>
  )
}
