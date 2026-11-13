import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import AnimalPortrait from '../components/AnimalPortrait.jsx'
import PawIcon from '../components/PawIcon.jsx'
import { AnimalNotFoundError, getAnimal } from '../services/animals.js'
import { ageLabel, sexLabel, getAnimalStatus } from '../utils/animalLabels.js'

function AnimalDetail({ id }) {
  const [request, setRequest] = useState(0)
  const [state, setState] = useState({ status: 'loading', animal: null })
  const heading = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    getAnimal(id, { signal: controller.signal })
      .then((animal) => { if (active) setState({ status: 'success', animal }) })
      .catch((error) => {
        if (active && !controller.signal.aborted) {
          setState({ status: error instanceof AnimalNotFoundError ? 'notFound' : 'error', animal: null })
        }
      })
    return () => { active = false; controller.abort() }
  }, [id, request])

  useEffect(() => {
    const original = document.title
    return () => { document.title = original }
  }, [])

  useEffect(() => {
    document.title = `${state.animal?.nom ?? 'Fiche de l’animal'} · Le Refuge`
    if (state.status !== 'loading') heading.current?.focus({ preventScroll: true })
  }, [state])

  const animal = state.animal
  const status = getAnimalStatus(animal?.statut)

  function retry() {
    setState({ status: 'loading', animal: null })
    setRequest((value) => value + 1)
  }

  return (
    <section className="animal-detail container" aria-labelledby="animal-detail-title">
      <nav className="detail-breadcrumb" aria-label="Fil d’Ariane">
        <Link to="/#animaux">Les animaux</Link><span aria-hidden="true">/</span><span>Fiche de l’animal</span>
      </nav>
      <p className="eyebrow">Les pensionnaires du refuge</p>
      <h1 id="animal-detail-title" ref={heading} tabIndex={-1}>{animal?.nom ?? 'Fiche de l’animal'}</h1>

      {state.status === 'loading' && <div className="feedback-panel" role="status" aria-live="polite"><span className="loading-spinner" aria-hidden="true" /><h2>Faisons connaissance…</h2><p>Chargement des informations de l’animal.</p></div>}
      {state.status === 'notFound' && <div className="feedback-panel" role="status"><PawIcon className="feedback-paw" /><h2>Cet animal est introuvable.</h2><p>Sa fiche n’existe pas ou n’est plus disponible. Retrouvez les autres pensionnaires du refuge.</p><Link className="button button-primary" to="/#animaux">Retour aux animaux</Link></div>}
      {state.status === 'error' && <div className="feedback-panel" role="alert"><h2>La rencontre se fait attendre.</h2><p>La fiche est momentanément indisponible. Vous pouvez réessayer.</p><button className="button button-outline" type="button" onClick={retry}>Réessayer</button><Link className="detail-return" to="/#animaux">Retour aux animaux</Link></div>}

      {animal && <div className="animal-detail-grid">
        <div className="detail-portrait-panel tone-1">
          <div className="animal-card-visual"><AnimalPortrait species={animal.espece} /><span className="illustration-label">Illustration</span></div>
          <p>Une rencontre commence par apprendre à se connaître.</p>
        </div>
        <div className="detail-profile">
          <h2>Son profil</h2>
          <dl className="detail-facts">
            <div><dt>Espèce</dt><dd>{animal.espece}</dd></div>
            <div><dt>Race</dt><dd>{animal.race?.trim() || 'Non renseignée'}</dd></div>
            <div><dt>Âge</dt><dd>{ageLabel(animal.age)}</dd></div>
            <div><dt>Sexe</dt><dd>{sexLabel(animal.sexe)}</dd></div>
            <div className="detail-health"><dt>État de santé</dt><dd>{animal.etatSante?.trim() || 'Non renseigné'}</dd></div>
            <div><dt>Statut</dt><dd><span className={`detail-status status-${status.className}`}><span aria-hidden="true" />{status.label}</span></dd></div>
          </dl>
          <p className="detail-status-note">{status.message}</p>
          {animal.statut === 'DISPONIBLE' && <Link className="button button-primary detail-adopt-link" to={`/animaux/${animal.id}/demande-adoption`}>Faire une demande d’adoption <span aria-hidden="true">↗</span></Link>}
          <Link className="button button-outline" to="/#animaux"><span aria-hidden="true">←</span> Retour aux animaux</Link>
        </div>
      </div>}
    </section>
  )
}

export default function AnimalDetailPage() {
  const { id } = useParams()
  // Reset the page immediately on ID changes; abort the previous request on unmount.
  return <AnimalDetail key={id} id={id} />
}
