import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import AdminAnimalRecord from '../components/AdminAnimalRecord.jsx'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminMedical, saveAdminMedical, validateMedical, medicalDateLabel, medicalToday } from '../services/adminMedical.js'
import { getAnimalStatus } from '../utils/animalLabels.js'

const sections = [['vaccins', 'Vaccins'], ['traitements', 'Traitements'], ['observations', 'Observations']]

function MedicalForm({ animalId, record, onSaved, onCancel, reload }) {
  const { session, expire } = useAdminSession()
  const creating = !record
  const [fields, setFields] = useState(() => ({ vaccins: record?.vaccins ?? '', traitements: record?.traitements ?? '', observations: record?.observations ?? '', dateMiseAJour: record ? record.dateMiseAJour ?? '' : medicalToday() }))
  const [errors, setErrors] = useState({})
  const [state, setState] = useState({ phase: 'idle', message: '' })
  const active = useRef(true), lock = useRef(false), request = useRef(null), inputs = useRef({}), feedback = useRef(null), title = useRef(null)
  const busy = state.phase === 'sending'
  const blocked = busy || ['uncertain', 'conflict', 'notFound'].includes(state.phase)
  useEffect(() => { active.current = true; title.current?.focus(); return () => { active.current = false; request.current?.abort() } }, [])
  useEffect(() => { if (state.message) feedback.current?.focus() }, [state])
  function control(key) {
    return { id: `medical-${key}`, value: fields[key], onChange: event => setFields({ ...fields, [key]: event.target.value }), ref: node => { inputs.current[key] = node }, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': errors[key] ? `medical-${key}-error` : undefined }
  }
  const fieldError = key => errors[key] && <span id={`medical-${key}-error`} className="adoption-field-error">{errors[key]}</span>
  async function submit(event) {
    event.preventDefault()
    if (lock.current || blocked) return
    const validation = validateMedical(fields)
    setErrors(validation.errors)
    const first = Object.keys(validation.errors)[0]
    if (first) { inputs.current[first]?.focus(); return }
    lock.current = true
    const controller = new AbortController()
    request.current = controller
    setState({ phase: 'sending', message: '' })
    try {
      const saved = await saveAdminMedical(session.token, animalId, fields, { creating, signal: controller.signal })
      if (active.current) onSaved(saved)
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (error instanceof AdminSessionExpiredError) expire(session.token)
      else { setErrors(error.fields ?? {}); setState({ phase: error.kind ?? 'uncertain', message: error.message }) }
    } finally { lock.current = false }
  }
  return <form className="admin-medical-form" onSubmit={submit} noValidate aria-busy={busy}>
    <h2 tabIndex={-1} ref={title}>{creating ? 'Créer le dossier médical' : 'Modifier le dossier médical'}</h2><p className="medical-form-intro">Les textes sont facultatifs. Un champ vide signifie « non renseigné ». La date de mise à jour est obligatoire.</p>
    {state.message && <div className="admin-error medical-feedback" role="alert" ref={feedback} tabIndex={-1}><p>{state.message}</p>{['uncertain', 'conflict', 'notFound'].includes(state.phase) && <><p>Recharger remplacera les modifications de ce formulaire par les informations du refuge.</p><button className="button button-outline" type="button" onClick={reload}>Recharger le dossier</button></>}</div>}
    <fieldset className="medical-fields" disabled={blocked}><legend>Contenu du dossier médical</legend>
      {sections.map(([key, label]) => <div className="adoption-field" key={key}><label htmlFor={`medical-${key}`}>{label} (facultatif)</label><textarea {...control(key)} rows={key === 'observations' ? 5 : 3} spellCheck="true" />{fieldError(key)}</div>)}
      <div className="adoption-field medical-date-field"><label htmlFor="medical-dateMiseAJour">Date de mise à jour</label><input {...control('dateMiseAJour')} type="date" min="1000-01-01" max="9999-12-31" required />{fieldError('dateMiseAJour')}</div>
    </fieldset>
    <div className="adoption-actions"><button className="button button-primary" type="submit" disabled={blocked}>{busy ? 'Enregistrement…' : creating ? 'Créer le dossier' : 'Enregistrer le dossier'}</button><button className="text-button" type="button" onClick={onCancel} disabled={blocked}>Annuler les modifications</button></div>{busy && <p role="status" className="adoption-progress">Enregistrement du dossier en cours.</p>}
  </form>
}

function MedicalWorkspace({ animal, reload }) {
  const { session, expire } = useAdminSession()
  const [state, setState] = useState({ status: 'loading', record: null })
  const [editing, setEditing] = useState(false)
  const [notice, setNotice] = useState('')
  const feedback = useRef(null), editButton = useRef(null)
  const status = getAnimalStatus(animal.statut)
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAdminMedical(session.token, animal.id, { signal: controller.signal })
      .then(record => { if (current) setState({ status: 'success', record }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: error.kind === 'notFound' ? 'missing' : 'error', record: null, message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, animal.id, expire])
  useEffect(() => { if (notice) feedback.current?.focus() }, [notice])
  function saved(record) { setState({ status: 'success', record }); setEditing(false); setNotice('Le dossier médical a bien été enregistré.') }
  function cancel() { setEditing(false); requestAnimationFrame(() => editButton.current?.focus()) }
  return <><Link className="admin-back-link" to="/admin/animaux">← Retour aux animaux</Link><p className="eyebrow">Suivi de l’animal · Fiche n° {animal.id}</p><h1 className="animal-editor-title">Dossier médical</h1>
    <div className="medical-animal-banner"><div><h2>{animal.nom}</h2><p>{animal.espece} · {status.label}</p></div><Link to={`/admin/animaux/${animal.id}/modifier`}>Voir la fiche de l’animal →</Link></div>
    {notice && <div className="admin-notice medical-save-notice" role="status" ref={feedback} tabIndex={-1}>{notice}</div>}
    {state.status === 'loading' && <div className="feedback-panel" role="status">Chargement du dossier médical…</div>}
    {state.status === 'error' && <div className="feedback-panel" role="alert"><h2>Le dossier se fait attendre.</h2><p>{state.message}</p><button className="button button-outline" onClick={reload}>Réessayer</button></div>}
    {!editing && state.status === 'missing' && <div className="feedback-panel"><h2>Aucun dossier médical enregistré.</h2><p>Chaque animal doit avoir un dossier. Vous pouvez créer celui-ci sans inventer de données médicales.</p><button ref={editButton} className="button button-primary" onClick={() => { setNotice(''); setEditing(true) }}>Créer un dossier</button><button className="text-button medical-reload" onClick={reload}>Vérifier à nouveau</button></div>}
    {!editing && state.status === 'success' && <section className="medical-record" aria-label="Dossier médical enregistré"><div className="medical-record-heading"><div><p className="animal-record-id">Dossier n° {state.record.id}</p><p>Date de mise à jour : <strong>{medicalDateLabel(state.record.dateMiseAJour)}</strong></p></div><button ref={editButton} className="button button-outline" onClick={() => { setNotice(''); setEditing(true) }}>Modifier le dossier</button></div><dl className="medical-record-sections">{sections.map(([key, label]) => <div key={key}><dt>{label}</dt><dd className={state.record[key] ? '' : 'medical-unspecified'}>{state.record[key] || 'Non renseigné'}</dd></div>)}</dl><button className="text-button" onClick={reload}>Actualiser le dossier</button></section>}
    {editing && <MedicalForm animalId={animal.id} record={state.record} onSaved={saved} onCancel={cancel} reload={reload} />}
  </>
}

export default function AdminMedicalPage() {
  const { id } = useParams()
  useEffect(() => { const previous = document.title; document.title = 'Dossier médical · Pawtale'; return () => { document.title = previous } }, [])
  return <section className="animal-editor medical-page"><AdminAnimalRecord key={id} id={id}>{(animal, reload) => <MedicalWorkspace animal={animal} reload={reload} />}</AdminAnimalRecord></section>
}
