import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import ShelterPhoto from '../components/ShelterPhoto.jsx'
import Icon from '../components/Icon.jsx'
import PawIcon from '../components/PawIcon.jsx'
import MatchingResultCard from '../components/MatchingResultCard.jsx'
import { getMatchingRecommendations, hasMatchingPreferences, normalizeMatchingPreferences } from '../services/matching.js'

const emptyPreferences = { espece: '', categorieAge: '', sexe: '' }
const questions = [
  { key: 'espece', title: 'Un chien, un chat…', hint: 'Qui aimeriez-vous rencontrer ?', options: [['', 'Sans préférence'], ['Chien', 'Un chien'], ['Chat', 'Un chat']] },
  { key: 'categorieAge', title: 'À quel âge ?', hint: 'Choisissez une tranche d’âge.', options: [['', 'Sans préférence'], ['JEUNE', 'Jeune', '0 à 1 an'], ['ADULTE', 'Adulte', '2 à 7 ans'], ['SENIOR', 'Senior', '8 ans et plus']] },
  { key: 'sexe', title: 'Mâle ou femelle ?', hint: 'Vous pouvez laisser ce choix ouvert.', options: [['', 'Sans préférence'], ['MALE', 'Mâle'], ['FEMELLE', 'Femelle']] },
]

export default function MatchingPage() {
  const [preferences, setPreferences] = useState(emptyPreferences)
  const [view, setView] = useState({ status: 'idle', results: [], preferences: emptyPreferences })
  const [invalidField, setInvalidField] = useState(null)
  const active = useRef(true)
  const request = useRef(null)
  const generation = useRef(0)
  const feedback = useRef(null)
  const form = useRef(null)
  const busy = view.status === 'loading'

  useEffect(() => {
    active.current = true
    const original = document.title
    document.title = 'Trouvez votre compagnon idéal · Pawtale'
    return () => { active.current = false; request.current?.abort(); document.title = original }
  }, [])

  useEffect(() => {
    if (view.status === 'success' || view.status === 'error') feedback.current?.focus()
  }, [view.status])

  function cancelAndClear() {
    generation.current++
    request.current?.abort()
    request.current = null
    setView({ status: 'idle', results: [], preferences: emptyPreferences })
    setInvalidField(null)
  }

  function change(key, value) {
    cancelAndClear()
    setPreferences(previous => ({ ...previous, [key]: value }))
  }

  function reset() {
    cancelAndClear()
    setPreferences(emptyPreferences)
    form.current?.querySelector('input')?.focus()
  }

  async function search(values) {
    if (request.current) return
    let submitted
    try { submitted = normalizeMatchingPreferences(values) } catch (error) {
      setInvalidField(error.field)
      form.current?.querySelector(`[name="${error.field}"]`)?.focus()
      return
    }
    const controller = new AbortController()
    const current = ++generation.current
    request.current = controller
    setInvalidField(null)
    setView({ status: 'loading', results: [], preferences: submitted })
    try {
      const results = await getMatchingRecommendations({ preferences: submitted, signal: controller.signal })
      if (active.current && current === generation.current && !controller.signal.aborted) setView({ status: 'success', results, preferences: submitted })
    } catch (error) {
      if (active.current && current === generation.current && !controller.signal.aborted) setView({ status: 'error', results: [], preferences: submitted, message: error.message })
    } finally {
      if (current === generation.current) request.current = null
    }
  }

  const evaluated = hasMatchingPreferences(view.preferences)
  const selectedCount = Object.values(preferences).filter(Boolean).length
  return <div className="matching-page">
    <section className="matching-intro container" aria-labelledby="matching-title">
      <nav className="detail-breadcrumb" aria-label="Fil d’Ariane"><Link to="/">Accueil</Link><span aria-hidden="true">/</span><span>Votre compagnon</span></nav>
      <div className="matching-intro-grid">
        <div><p className="eyebrow"><span />Une rencontre, à votre rythme</p><h1 id="matching-title">Trouvez votre<br /><span>compagnon idéal.</span></h1><p className="matching-lead">Quelques préférences pour vous orienter.<br />Et peut-être, le début d’une belle histoire.</p></div>
        <div className="matching-intro-art"><div className="matching-portrait matching-portrait-dog"><ShelterPhoto name="puppy" priority sizes="240px" /></div><div className="matching-portrait matching-portrait-cat"><ShelterPhoto name="kittens" priority sizes="210px" /></div><span>Photos illustratives · De vraies rencontres à découvrir.<PawIcon /></span><div className="matching-art-heart" aria-hidden="true"><Icon name="heart" /></div></div>
      </div>
      <p className="matching-method-label"><Icon name="sparkle" />Vos préférences. Les animaux disponibles. Une première rencontre à imaginer.</p>
    </section>

    <section className="container matching-questionnaire" aria-labelledby="preferences-title">
      <div className="matching-section-heading"><div><p className="eyebrow">01 — VOS ENVIES, EN QUELQUES MOTS</p><h2 id="preferences-title">Votre rencontre commence ici.</h2></div><p>3 questions, toutes facultatives.</p></div>
      <div className="matching-progress"><span>{selectedCount ? `${selectedCount} préférence${selectedCount > 1 ? 's' : ''} choisie${selectedCount > 1 ? 's' : ''}` : 'Toutes les rencontres restent possibles'}</span><div aria-hidden="true">{questions.map(question => <span className={preferences[question.key] ? 'is-selected' : ''} key={question.key} />)}</div></div>
      <form ref={form} onSubmit={event => { event.preventDefault(); search(preferences) }} noValidate>
        <div className="matching-questions">{questions.map((question, index) => <fieldset key={question.key} className="matching-question" aria-describedby={`matching-hint-${question.key}${invalidField === question.key ? ` matching-error-${question.key}` : ''}`}>
          <legend><span className="matching-step" aria-hidden="true">{preferences[question.key] ? <Icon name="check" /> : `0${index + 1}`}</span>{question.title}</legend>
          <p className="matching-question-hint" id={`matching-hint-${question.key}`}>{question.hint}</p>
          <div className="matching-options">{question.options.map(([value, label, hint]) => <label className="matching-choice" key={value}>
            <input type="radio" name={question.key} value={value} checked={preferences[question.key] === value} onChange={() => change(question.key, value)} aria-invalid={invalidField === question.key || undefined} />
            {question.key === 'espece' && value && <span className="matching-choice-photo" aria-hidden="true"><ShelterPhoto name={value === 'Chien' ? 'puppy' : 'kittens'} sizes="110px" /></span>}
            <span>{label}{hint && <small>{hint}</small>}</span>
          </label>)}</div>
          {invalidField === question.key && <p className="matching-field-error" id={`matching-error-${question.key}`}>Choisissez une option proposée.</p>}
        </fieldset>)}</div>
        <div className="matching-form-actions"><div><button className="button button-primary" type="submit" disabled={busy}>{busy ? 'Recherche en cours…' : 'Voir mes recommandations'}<span aria-hidden="true">↗</span></button><button type="button" className="matching-reset" onClick={reset}>Réinitialiser</button></div><p>Pas de compte. Juste une première découverte.</p></div>
      </form>
    </section>

    <section className="container matching-results" aria-labelledby="matching-results-title" aria-busy={busy}>
      <p className="eyebrow">02 — LAISSEZ LA RENCONTRE SE DESSINER</p>
      <h2 id="matching-results-title" className="matching-results-heading" ref={feedback} tabIndex={-1}>{view.status === 'success' ? 'Vos compagnons à découvrir.' : 'Les rencontres se préparent.'}</h2>
      {view.status === 'idle' && <div className="matching-idle"><PawIcon /><p>Choisissez ce qui vous tient à cœur, ou gardez toutes les possibilités ouvertes. Vos recommandations apparaîtront ici.</p></div>}
      {busy && <div className="feedback-panel" role="status"><span className="loading-spinner" aria-hidden="true" /><h3>Nous cherchons vos compagnons…</h3><p>Vous pouvez modifier vos préférences pour annuler cette recherche.</p></div>}
      {view.status === 'error' && <div className="feedback-panel" role="alert"><h3>La rencontre se fait attendre.</h3><p>{view.message}</p><button className="button button-outline" type="button" onClick={() => search(view.preferences)}>Réessayer les recommandations</button></div>}
      {view.status === 'success' && view.results.length === 0 && <div className="feedback-panel" role="status"><PawIcon className="feedback-paw" /><h3>Aucun animal disponible pour le moment.</h3><p>Le refuge n’a pas d’animal à proposer actuellement. Revenez bientôt découvrir les nouvelles arrivées.</p><Link className="button button-outline" to="/#animaux">Voir tous les animaux</Link></div>}
      {view.status === 'success' && view.results.length > 0 && <>
        <p className="matching-result-count" role="status">{view.results.length} {view.results.length === 1 ? 'animal disponible à découvrir' : 'animaux disponibles à découvrir'}</p>
        <p className="matching-results-description">{evaluated ? 'Le score mesure l’accord avec vos préférences. Un animal peut aussi être proposé comme alternative si certains critères diffèrent.' : 'Vous n’avez indiqué aucune préférence : découvrez ces animaux sans classement de compatibilité. Leur score est non évalué.'}</p>
        <ol className="matching-results-grid">{view.results.map((recommendation, index) => <li key={recommendation.animal.id}><MatchingResultCard recommendation={recommendation} index={index} evaluated={evaluated} /></li>)}</ol>
        <p className="matching-availability-note">Ces recommandations ne réservent pas l’animal. Sa disponibilité sera vérifiée avant toute demande d’adoption.</p>
      </>}
    </section>

    <div className="container matching-method"><details><summary>Comment les recommandations sont-elles classées ?</summary><div><p>Le serveur compare uniquement les préférences renseignées : espèce (poids 50), tranche d’âge (30) et sexe (20). Le score est ramené sur 100 à partir de ces seuls critères. Les égalités sont départagées par identifiant ; seuls les trois premiers animaux disponibles sont proposés.</p><p>Sans préférence, aucun score n’est évalué. Ces règles ne prédisent ni la réussite d’une adoption, ni la santé, le tempérament ou l’adaptation à votre mode de vie. Les tranches d’âge servent simplement à la recherche.</p></div></details></div>
  </div>
}
