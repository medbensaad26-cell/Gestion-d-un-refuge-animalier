import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminAnimals, validAnimalId } from '../services/adminAnimals.js'
import { getAdminAnnouncement, saveAdminAnnouncement, validateAnnouncement, announcementToday } from '../services/adminAnnouncements.js'
import { publicationDateLabel } from '../services/announcements.js'
import { getAnimalStatus } from '../utils/animalLabels.js'

function AnnouncementForm({ record, animals, selectedId, reload }) {
  const { session, expire } = useAdminSession()
  const [fields, setFields] = useState(() => ({ titre: record?.titre ?? '', description: record?.description ?? '', datePublication: record?.datePublication ?? announcementToday(), statut: record?.statut ?? 'INTERNE', animalId: String(record?.animalId ?? selectedId ?? '') }))
  const [errors, setErrors] = useState({})
  const [state, setState] = useState({ phase: 'idle', message: '' })
  const active = useRef(true), lock = useRef(false), request = useRef(null), inputs = useRef({}), feedback = useRef(null)
  const busy = state.phase === 'sending', published = fields.statut === 'PUBLIEE'
  const blocked = busy || ['uncertain', 'conflict', 'notFound', 'success'].includes(state.phase)
  const chosen = animals.find(animal => animal.id === Number(fields.animalId))
  useEffect(() => { active.current = true; return () => { active.current = false; request.current?.abort() } }, [])
  useEffect(() => { if (state.message) feedback.current?.focus() }, [state])
  function control(key) { return { id: `announcement-${key}`, value: fields[key], onChange: event => setFields({ ...fields, [key]: event.target.value }), ref: node => { inputs.current[key] = node }, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': errors[key] ? `announcement-${key}-error` : undefined } }
  const fieldError = key => errors[key] && <span className="adoption-field-error" id={`announcement-${key}-error`}>{errors[key]}</span>
  async function submit(event) {
    event.preventDefault()
    if (lock.current || blocked) return
    const validation = validateAnnouncement(fields, { originalStatus: record?.statut })
    if (!chosen) validation.errors.animalId = 'Choisissez un animal présent dans la liste.'
    setErrors(validation.errors)
    const first = ['animalId', 'titre', 'description', 'datePublication', 'statut'].find(key => validation.errors[key])
    if (first) { inputs.current[first]?.focus(); return }
    lock.current = true
    const controller = new AbortController()
    request.current = controller
    setState({ phase: 'sending', message: '' })
    try {
      await saveAdminAnnouncement(session.token, record?.id ?? null, fields, { originalStatus: record?.statut, signal: controller.signal })
      if (active.current) setState({ phase: 'success', message: published ? 'L’annonce a bien été publiée. Elle est visible sur le site public.' : 'L’annonce a bien été enregistrée. Elle n’est pas visible sur le site public.' })
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (error instanceof AdminSessionExpiredError) expire(session.token)
      else { setErrors(error.fields ?? {}); setState({ phase: error.kind ?? 'uncertain', message: error.message }) }
    } finally { lock.current = false }
  }
  if (state.phase === 'success') return <div className="feedback-panel announcement-save-success" role="status" tabIndex={-1} ref={feedback}><h2>{published ? 'Annonce publiée.' : 'Annonce enregistrée.'}</h2><p>{state.message}</p><div className="adoption-actions"><Link className="button button-primary" to="/admin/annonces">Retour aux annonces</Link>{published && <Link to="/annonces">Voir les annonces publiques ↗</Link>}</div></div>
  return <form className="admin-announcement-form" onSubmit={submit} noValidate aria-busy={busy}>
    {state.message && <div className="admin-error medical-feedback" role="alert" ref={feedback} tabIndex={-1}><p>{state.message}</p>{record && ['conflict', 'uncertain', 'notFound'].includes(state.phase) && <><p>Recharger remplacera vos modifications par les informations enregistrées.</p><button type="button" className="button button-outline" onClick={reload}>Recharger l’annonce</button></>}<Link to="/admin/annonces">Vérifier la liste des annonces</Link>{!record && state.phase === 'conflict' && <button className="button button-outline" type="button" onClick={reload}>Actualiser les animaux</button>}</div>}
    <fieldset className="announcement-editor-fields" disabled={blocked}><legend>Informations de l’annonce</legend>
      <div className="adoption-field"><label htmlFor="announcement-animalId">Animal concerné</label><select {...control('animalId')} required><option value="">Choisir un animal</option>{animals.map(animal => <option value={animal.id} key={animal.id}>{animal.nom} · {animal.espece} · {getAnimalStatus(animal.statut).label} · n° {animal.id}</option>)}</select>{fieldError('animalId')}</div>
      <div className="adoption-field"><label htmlFor="announcement-titre">Titre</label><input {...control('titre')} required placeholder="Ex. Une famille pour Milo" />{fieldError('titre')}</div>
      <div className="adoption-field"><label htmlFor="announcement-description">Description (facultatif)</label><textarea {...control('description')} rows="6" />{fieldError('description')}</div>
      <div className="announcement-publication-fields"><div className="adoption-field"><label htmlFor="announcement-datePublication">Date de publication</label><input {...control('datePublication')} type="date" min="1000-01-01" max="9999-12-31" required />{fieldError('datePublication')}</div><div className="adoption-field"><label htmlFor="announcement-statut">Visibilité</label><select {...control('statut')} required><option value="INTERNE">Non publiée</option><option value="PUBLIEE">Publiée sur le site public</option>{record && !['INTERNE', 'PUBLIEE'].includes(record.statut) && <option value={record.statut}>{record.statut} (non publiée)</option>}</select>{fieldError('statut')}</div></div>
    </fieldset>
    <p className="adoption-note">{published ? 'La publication est immédiate après enregistrement. La date renseigne l’annonce ; elle ne programme pas sa diffusion.' : 'L’annonce restera réservée à l’administrateur. Choisissez « Publiée » pour la rendre visible aux visiteurs.'}</p>
    <section className="announcement-preview" aria-label="Aperçu avant enregistrement"><p className="eyebrow">Aperçu avant enregistrement</p><h2>{fields.titre.trim() || 'Le titre de votre annonce'}</h2><p className="announcement-preview-animal">{chosen ? `${chosen.nom} · Fiche n° ${chosen.id}` : 'Choisissez un animal'} · {publicationDateLabel(fields.datePublication)}</p><p className="announcement-preview-description">{fields.description.trim() || 'Votre description apparaîtra ici.'}</p></section>
    <div className="adoption-actions"><button className="button button-primary" type="submit" disabled={blocked}>{busy ? 'Enregistrement…' : published ? 'Publier l’annonce' : record?.statut === 'PUBLIEE' ? 'Retirer du site et enregistrer' : 'Enregistrer sans publier'}</button><Link to="/admin/annonces">Annuler</Link></div>{busy && <p role="status" className="adoption-progress">Enregistrement de l’annonce en cours.</p>}
  </form>
}

function Editor({ id, animalId, creating }) {
  const { session, expire } = useAdminSession()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ status: 'loading' })
  const invalidAnimal = creating && animalId != null && !validAnimalId(animalId)
  function reload() { setState({ status: 'loading' }); setAttempt(value => value + 1) }
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    if (invalidAnimal) return
    Promise.all([getAdminAnimals(session.token, { signal: controller.signal }), creating ? Promise.resolve(null) : getAdminAnnouncement(session.token, id, { signal: controller.signal })])
      .then(([animals, record]) => {
        if (!current) return
        const selected = record?.animalId ?? animalId
        if (selected != null && !animals.some(animal => animal.id === Number(selected))) setState({ status: 'notFound', message: 'L’animal lié à cette annonce est introuvable. Choisissez un animal depuis la liste du refuge.' })
        else setState({ status: 'success', animals, record })
      })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: error.kind === 'notFound' ? 'notFound' : 'error', message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, id, animalId, creating, invalidAnimal, attempt, expire])
  if (invalidAnimal) return <div className="feedback-panel" role="alert"><h2>Animal introuvable.</h2><Link to="/admin/annonces/nouvelle">Choisir un autre animal</Link></div>
  if (state.status === 'loading') return <div className="feedback-panel" role="status">Chargement des informations de l’annonce…</div>
  if (state.status !== 'success') return <div className="feedback-panel" role="alert"><h2>{state.status === 'notFound' ? 'Annonce ou animal introuvable.' : 'L’annonce se fait attendre.'}</h2><p>{state.message}</p>{state.status === 'error' && <button className="button button-outline" onClick={reload}>Réessayer</button>} <Link to="/admin/annonces">Retour aux annonces</Link></div>
  if (state.animals.length === 0) return <div className="feedback-panel"><h2>Ajoutez d’abord un animal.</h2><p>Une annonce doit concerner un animal enregistré au refuge.</p><Link className="button button-primary" to="/admin/animaux/nouveau">Ajouter un animal</Link></div>
  return <AnnouncementForm record={state.record} animals={state.animals} selectedId={animalId} reload={reload} />
}

export default function AdminAnnouncementEditorPage({ creating = false }) {
  const { id, animalId } = useParams()
  useEffect(() => { const previous = document.title; document.title = `${creating ? 'Créer une annonce' : 'Modifier une annonce'} · Le Refuge`; return () => { document.title = previous } }, [creating])
  return <section className="animal-editor announcement-editor"><Link className="admin-back-link" to="/admin/annonces">← Retour aux annonces</Link><p className="eyebrow">Partager les nouvelles du refuge</p><h1 className="animal-editor-title">{creating ? 'Créer une annonce' : 'Modifier une annonce'}</h1><p className="animal-editor-intro">Présentez un animal et choisissez la visibilité de son annonce.</p><Editor key={`${id ?? 'new'}:${animalId ?? ''}`} id={id} animalId={animalId} creating={creating} /></section>
}
