import { Link } from 'react-router'
import AnimalPortrait from './AnimalPortrait.jsx'
import { ageLabel, sexLabel } from '../utils/animalLabels.js'
import { tiltCard, resetCard } from '../utils/cardMotion.js'

export default function MatchingResultCard({ recommendation, index, evaluated }) {
  const { animal, score, explication } = recommendation
  return <article className={`matching-card tone-${index % 3}`} aria-labelledby={`matching-animal-${animal.id}`} onPointerMove={tiltCard} onPointerLeave={resetCard}>
    <div className="matching-card-visual">
      <span className="matching-rank">{evaluated ? `N° ${index + 1} · ${index === 0 ? 'Votre première rencontre' : 'À découvrir aussi'}` : `Rencontre ${String(index + 1).padStart(2, '0')}`}</span>
      <AnimalPortrait species={animal.espece} variant={animal.id} />
    </div>
    <div className="matching-card-content">
      <div className="matching-card-heading"><div><p className="animal-species">{animal.espece}</p><h3 id={`matching-animal-${animal.id}`}>{animal.nom}</h3></div><span className="matching-available">Disponible</span></div>
      <p className="matching-animal-meta">{ageLabel(animal.age)}<span aria-hidden="true"> · </span>{sexLabel(animal.sexe)}</p>
      {evaluated ? <div className="matching-score">
        <p><strong>{score}<span> / 100</span></strong><span>Accord avec vos préférences</span></p>
        <div className="matching-score-track" aria-hidden="true"><span style={{ width: `${score}%` }} /></div>
      </div> : <div className="matching-neutral-score"><strong>À découvrir, tout simplement.</strong><span>Score non évalué</span></div>}
      <p className="matching-explanation">{explication}</p>
      <div className="matching-card-actions">
        <Link className="button button-primary" to={`/animaux/${animal.id}`} aria-label={`Voir la fiche de ${animal.nom}`}>Voir sa fiche <span aria-hidden="true">↗</span></Link>
        <Link className="matching-adoption-link" to={`/animaux/${animal.id}/demande-adoption`}>Faire une demande pour {animal.nom} <span aria-hidden="true">→</span></Link>
      </div>
    </div>
  </article>
}
