import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import AnimalCard from '../components/AnimalCard.jsx'
import PawIcon from '../components/PawIcon.jsx'
import ShelterIllustration from '../components/ShelterIllustration.jsx'
import { getAnimals } from '../services/animals.js'
import HomeAnnouncements from '../components/HomeAnnouncements.jsx'

export default function HomePage() {
  const [request, setRequest] = useState(0)
  const [state, setState] = useState({ status: 'loading', animals: [] })

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    getAnimals({ signal: controller.signal })
      .then((animals) => {
        if (active) setState({ status: 'success', animals })
      })
      .catch(() => {
        if (active && !controller.signal.aborted) setState({ status: 'error', animals: [] })
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [request])

  function retry() {
    setState({ status: 'loading', animals: [] })
    setRequest((value) => value + 1)
  }

  return (
    <>
      <section className="hero-section">
        <div className="container hero-grid">
          <div className="hero-copy">
            <p className="eyebrow"><span />Bienvenue au refuge</p>
            <h1>Une rencontre.<br /><span>Un nouveau départ.</span></h1>
            <p className="hero-description">Derrière chaque regard, une histoire. Découvrez les animaux du refuge et ceux qui attendent une famille pour écrire la suite.</p>
            <Link className="button button-primary" to="/#animaux">Rencontrer nos animaux <span aria-hidden="true">↗</span></Link>
            <div className="hero-note"><PawIcon /><span>À chacun son histoire.<br /><strong>À chacun sa place.</strong></span></div>
          </div>
          <div className="hero-art"><ShelterIllustration /><div className="art-caption"><span aria-hidden="true">♡</span> Un petit pas vers une belle histoire.</div></div>
        </div>
      </section>

      <section className="animals-section container" id="animaux" aria-labelledby="animals-title">
        <div className="section-heading">
          <div><p className="eyebrow">Les petits visages du refuge</p><h2 id="animals-title">Faisons connaissance.</h2></div>
          {state.status === 'success' && state.animals.length > 0 && <p className="animal-count">{state.animals.length} {state.animals.length === 1 ? 'animal à découvrir' : 'animaux à découvrir'}</p>}
        </div>
        <p className="section-description">Des personnalités différentes, un même besoin d’attention. Retrouvez ici les animaux et leur situation au refuge.</p>

        {state.status === 'loading' && <div className="feedback-panel loading-panel" role="status" aria-live="polite"><span className="loading-spinner" aria-hidden="true" /><h3>Un instant, les rencontres se préparent…</h3><p>Chargement des animaux du refuge.</p></div>}
        {state.status === 'error' && <div className="feedback-panel" role="alert"><span className="feedback-icon" aria-hidden="true">↻</span><h3>Les animaux se font un peu attendre.</h3><p>La liste est momentanément indisponible. Vous pouvez réessayer dans un instant.</p><button className="button button-outline" type="button" onClick={retry}>Réessayer</button></div>}
        {state.status === 'success' && state.animals.length === 0 && <div className="feedback-panel" role="status"><PawIcon className="feedback-paw" /><h3>La liste est encore toute calme.</h3><p>Aucun animal n’est présenté pour le moment. Revenez nous rendre visite bientôt.</p></div>}
        {state.status === 'success' && state.animals.length > 0 && <div className="animals-grid">{state.animals.map((animal, index) => <AnimalCard key={animal.id} animal={animal} index={index} />)}</div>}
      </section>

      <HomeAnnouncements />

      <section className="about-section" id="refuge" aria-labelledby="about-title">
        <div className="container about-grid">
          <div className="about-mark" aria-hidden="true"><PawIcon /><span>De l’attention.<br />Et du temps.</span></div>
          <div className="about-copy"><p className="eyebrow">Notre refuge</p><h2 id="about-title">Un lieu pour se reconstruire.</h2><p>Accueillir, prendre soin et offrir une nouvelle chance : chaque animal mérite de trouver sa place, à son rythme.</p><p>Apprendre à le connaître est le premier pas. Prenez le temps de découvrir les pensionnaires et leur situation avant d’imaginer votre rencontre.</p></div>
        </div>
      </section>
    </>
  )
}
