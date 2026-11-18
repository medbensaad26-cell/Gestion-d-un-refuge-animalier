import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAnimal, AnimalNotFoundError } from '../services/animals.js'
import { submitAdoptionRequest, validateApplicant } from '../services/adoptionRequests.js'
import { getAnimalStatus } from '../utils/animalLabels.js'
import AnimalPortrait from '../components/AnimalPortrait.jsx'
import Icon from '../components/Icon.jsx'

const emptyFields = { nomDemandeur: '', email: '', telephone: '' }

function AdoptionRequest({ id }) {
  const [record, setRecord] = useState({ status: 'loading', animal: null })
  const [refresh, setRefresh] = useState(0)
  const [fields, setFields] = useState(emptyFields)
  const [errors, setErrors] = useState({})
  const [submission, setSubmission] = useState({ phase: 'idle', message: '' })
  const lock = useRef(false)
  const active = useRef(true)
  const sendController = useRef(null)
  const feedback = useRef(null)
  const formHeading = useRef(null)
  const inputs = useRef({})
  const animal = record.animal
  const busy = submission.phase === 'checking' || submission.phase === 'sending'

  useEffect(() => {
    active.current = true
    return () => { active.current = false; sendController.current?.abort() }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAnimal(id, { signal: controller.signal })
      .then((value) => { if (current) setRecord({ status: 'success', animal: value }) })
      .catch((error) => { if (current && !controller.signal.aborted) setRecord({ status: error instanceof AnimalNotFoundError ? 'notFound' : 'error', animal: null }) })
    return () => { current = false; controller.abort() }
  }, [id, refresh])

  useEffect(() => {
    const original = document.title
    document.title = 'Demande d’adoption · Pawtale'
    return () => { document.title = original }
  }, [])

  useEffect(() => {
    if (submission.phase !== 'idle' && !busy) feedback.current?.focus()
  }, [submission.phase, busy])

  function reloadAvailability() {
    setRecord({ status: 'loading', animal: null })
    setSubmission({ phase: 'idle', message: '' })
    setRefresh(value => value + 1)
  }

  async function submit(event) {
    event.preventDefault()
    if (lock.current || submission.phase === 'success' || submission.phase === 'uncertain' || submission.phase === 'conflict') return
    const validation = validateApplicant(fields)
    setErrors(validation.errors)
    const firstError = Object.keys(validation.errors)[0]
    if (firstError) { inputs.current[firstError]?.focus(); return }
    lock.current = true
    const controller = new AbortController()
    sendController.current = controller
    let posting = false
    setSubmission({ phase: 'checking', message: '' })
    try {
      const latest = await getAnimal(id, { signal: controller.signal })
      if (!active.current) return
      setRecord({ status: 'success', animal: latest })
      if (latest.statut !== 'DISPONIBLE') {
        setSubmission({ phase: 'conflict', message: 'La situation de cet animal a changé. Il ne peut pas recevoir de demande pour le moment.' })
        return
      }
      posting = true
      setSubmission({ phase: 'sending', message: '' })
      await submitAdoptionRequest(latest.id, validation.values, { signal: controller.signal })
      if (active.current) {
        setFields(emptyFields)
        setErrors({})
        setSubmission({ phase: 'success', message: 'Votre demande a bien été enregistrée. Elle est en attente d’examen par le refuge.' })
      }
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (!posting && error instanceof AnimalNotFoundError) {
        setRecord({ status: 'notFound', animal: null })
        setSubmission({ phase: 'idle', message: '' })
      } else if (!posting) {
        setSubmission({ phase: 'error', message: 'Impossible de vérifier la disponibilité. Aucun envoi n’a été effectué. Vous pouvez réessayer.' })
      } else {
        const kind = error.kind ?? 'uncertain'
        if (kind === 'notFound') setRecord({ status: 'notFound', animal: null })
        setErrors(error.fields ?? {})
        setSubmission({ phase: kind === 'validation' ? 'error' : kind, message: error.message })
      }
    } finally { lock.current = false; sendController.current = null }
  }

  const editable = submission.phase === 'idle' || submission.phase === 'error'
  return <section className="adoption-page container" aria-labelledby="adoption-title">
    <nav className="detail-breadcrumb" aria-label="Fil d’Ariane"><Link to="/#animaux">Les animaux</Link><span aria-hidden="true">/</span>{animal && <><Link to={`/animaux/${animal.id}`}>{animal.nom}</Link><span aria-hidden="true">/</span></>}<span>Demande d’adoption</span></nav>
    <p className="eyebrow">Un premier pas vers une rencontre</p>
    <h1 id="adoption-title">{submission.phase === 'success' ? 'Votre demande est envoyée.' : 'Une nouvelle histoire commence.'}</h1>
    <ol className="adoption-steps" aria-label="Parcours de la demande"><li className="is-complete"><Icon name="check" />Choisir une rencontre</li><li className={submission.phase === 'success' ? 'is-complete' : 'is-current'} aria-current={submission.phase === 'success' ? undefined : 'step'}><span>02</span>Vos coordonnées</li><li className={submission.phase === 'success' ? 'is-current' : ''} aria-current={submission.phase === 'success' ? 'step' : undefined}><span>03</span>Examen par le refuge</li></ol>

    {record.status === 'loading' && <div className="feedback-panel" role="status"><span className="loading-spinner" aria-hidden="true" /><h2>Vérification de l’animal…</h2><p>Nous préparons sa fiche.</p></div>}
    {record.status === 'error' && <div className="feedback-panel" role="alert"><h2>La fiche se fait attendre.</h2><p>Impossible de charger les informations de l’animal.</p><button type="button" className="button button-outline" onClick={reloadAvailability}>Réessayer</button></div>}
    {record.status === 'notFound' && <div className="feedback-panel" role="status"><h2>Cet animal est introuvable.</h2><p>Retrouvez les autres pensionnaires du refuge.</p><Link to="/#animaux" className="button button-primary">Retour aux animaux</Link></div>}

    {animal && record.status === 'success' && <>
      <div className="adoption-animal"><span className="adoption-animal-photo"><AnimalPortrait species={animal.espece} variant={animal.id} imageUrl={animal.imageUrl} name={animal.nom} /></span><div><p>Votre demande concerne</p><h2>{animal.nom}</h2><p>{animal.espece}{animal.race ? ` · ${animal.race}` : ''}</p>{!animal.imageUrl && <small>Photographie illustrative</small>}</div><Link to={`/animaux/${animal.id}`}>Voir sa fiche ↗</Link></div>
      {submission.phase === 'success' ? <div className="feedback-panel adoption-success" ref={feedback} tabIndex={-1} role="status"><span className="success-mark"><Icon name="check" /></span><h2>Merci pour votre intérêt pour {animal.nom}.</h2><p>{submission.message}</p><Link className="button button-primary" to={`/animaux/${animal.id}`}>Retour à sa fiche</Link><Link className="detail-return" to="/#animaux">Découvrir les autres animaux</Link></div>
        : animal.statut !== 'DISPONIBLE' ? <div className="feedback-panel" ref={feedback} tabIndex={-1} role="status"><h2>Les demandes ne sont pas ouvertes pour cet animal.</h2><p>{getAnimalStatus(animal.statut).message}</p><Link className="button button-outline" to={`/animaux/${animal.id}`}>Retour à sa fiche</Link></div>
          : <div className="adoption-form-panel">
            <h2 ref={formHeading} tabIndex={-1}>Vos coordonnées</h2>
            <p className="adoption-intro">Envoyez votre demande sans créer de compte. Le refuge pourra vous recontacter pour la suite. Tous les champs sont obligatoires.</p>
            {submission.message && <div className="adoption-feedback" ref={feedback} tabIndex={-1} role="alert"><p>{submission.message}</p>
              {submission.phase === 'conflict' && <button className="button button-outline" type="button" onClick={reloadAvailability}>Actualiser la disponibilité</button>}
              {submission.phase === 'uncertain' && <button className="button button-outline" type="button" onClick={() => { setSubmission({ phase: 'idle', message: '' }); formHeading.current?.focus() }}>Réessayer après vérification</button>}
            </div>}
            <form onSubmit={submit} noValidate aria-busy={busy}>
              <fieldset disabled={!editable || busy} className="adoption-fields"><legend>Coordonnées du demandeur</legend>
                {[['nomDemandeur', 'Nom complet', 'text', 'name'], ['email', 'Adresse e-mail', 'email', 'email'], ['telephone', 'Téléphone', 'tel', 'tel']].map(([name, label, type, autoComplete]) => <div className="adoption-field" key={name}>
                  <label htmlFor={`adoption-${name}`}>{label}</label><input id={`adoption-${name}`} name={name} type={type} autoComplete={autoComplete} required value={fields[name]} ref={element => { inputs.current[name] = element }} onChange={event => setFields(previous => ({ ...previous, [name]: event.target.value }))} aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `error-${name}` : undefined} />
                  {errors[name] && <p className="adoption-field-error" id={`error-${name}`}>{errors[name]}</p>}
                </div>)}
              </fieldset>
              <p className="adoption-note">L’envoi d’une demande ne réserve pas l’animal et ne confirme pas son adoption. La décision appartient au refuge.</p>
              <div className="adoption-actions"><button className="button button-primary" type="submit" disabled={!editable || busy}>{submission.phase === 'checking' ? 'Vérification…' : submission.phase === 'sending' ? 'Envoi en cours…' : 'Envoyer ma demande'}</button><Link to={`/animaux/${animal.id}`}>Retour à la fiche</Link></div>
              {busy && <p className="adoption-progress" role="status">{submission.phase === 'checking' ? 'Vérification de la disponibilité de l’animal.' : 'Envoi de votre demande au refuge.'}</p>}
            </form>
          </div>}
    </>}
  </section>
}

export default function AdoptionRequestPage() {
  const { id } = useParams()
  return <AdoptionRequest key={id} id={id} />
}
