import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import AdminAnimalRecord from '../components/AdminAnimalRecord.jsx'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { ANIMAL_IMAGE_TYPES, deleteAnimalImage, saveAdminAnimal, uploadAnimalImage, validateAnimal, validateAnimalImage } from '../services/adminAnimals.js'

function AnimalForm({ animal = null, reload }) {
  const { session, expire } = useAdminSession()
  const navigate = useNavigate()
  const adopted = animal?.statut?.trim().toUpperCase() === 'ADOPTE'
  const [fields, setFields] = useState(() => ({ nom: animal?.nom ?? '', espece: animal?.espece ?? '', race: animal?.race ?? '', age: animal?.age ?? '', sexe: animal?.sexe ?? '', etatSante: animal?.etatSante ?? '', statut: animal ? animal.statut?.trim().toUpperCase() ?? '' : 'DISPONIBLE' }))
  const [errors, setErrors] = useState({})
  const [state, setState] = useState({ phase: 'idle', message: '' })
  const [image, setImage] = useState({ file: null, preview: null, remove: false })
  const active = useRef(true), lock = useRef(false), request = useRef(null), inputs = useRef({}), feedback = useRef(null)
  const busy = state.phase === 'sending'
  const blocked = busy || ['uncertain', 'conflict', 'notFound'].includes(state.phase)
  useEffect(() => { active.current = true; return () => { active.current = false; request.current?.abort() } }, [])
  useEffect(() => { if (state.message) feedback.current?.focus() }, [state])
  useEffect(() => () => { if (image.preview) URL.revokeObjectURL(image.preview) }, [image.preview])

  function chooseImage(event) {
    const file = event.target.files?.[0] ?? null
    const problem = file ? validateAnimalImage(file) : ''
    setErrors(({ image: _previous, ...others }) => problem ? { ...others, image: problem } : others)
    if (!file || problem) {
      event.target.value = ''
      setImage(current => ({ ...current, file: null, preview: null }))
      return
    }
    setImage({ file, preview: URL.createObjectURL(file), remove: false })
  }

  function removeImage() {
    if (inputs.current.image) inputs.current.image.value = ''
    setErrors(({ image: _previous, ...others }) => others)
    setImage({ file: null, preview: null, remove: Boolean(animal?.imageUrl) })
  }

  // The animal is already saved here, so a photo problem is reported without losing it.
  async function saveImage(id, signal) {
    if (!image.file && !image.remove) return ''
    if (!id) return 'La photo n’a pas pu être associée à la fiche.'
    try {
      if (image.file) await uploadAnimalImage(session.token, id, image.file, { signal })
      else await deleteAnimalImage(session.token, id, { signal })
      return ''
    } catch (error) {
      if (error instanceof AdminSessionExpiredError || error?.name === 'AbortError') throw error
      return error.message
    }
  }

  async function submit(event) {
    event.preventDefault()
    if (lock.current || blocked) return
    const validation = validateAnimal(fields, { originalStatus: animal?.statut })
    setErrors(validation.errors)
    const first = Object.keys(validation.errors)[0]
    if (first) { inputs.current[first]?.focus(); return }
    lock.current = true
    const controller = new AbortController()
    request.current = controller
    setState({ phase: 'sending', message: '' })
    try {
      const saved = await saveAdminAnimal(session.token, animal?.id ?? null, fields, { originalStatus: animal?.statut, signal: controller.signal })
      const imageProblem = await saveImage(animal?.id ?? saved.id, controller.signal)
      const notice = animal ? 'La fiche de l’animal a été mise à jour.' : 'L’animal et son dossier médical ont été créés.'
      if (active.current) navigate('/admin/animaux', { replace: true, state: { notice: imageProblem ? `${notice} La photo n’a pas été enregistrée : ${imageProblem} Vous pouvez réessayer depuis « Modifier ».` : notice } })
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (error instanceof AdminSessionExpiredError) expire(session.token)
      else { setErrors(error.fields ?? {}); setState({ phase: error.kind ?? 'uncertain', message: error.message }) }
    } finally { lock.current = false }
  }

  function control(key) {
    return { id: `animal-${key}`, value: fields[key], onChange: event => setFields({ ...fields, [key]: event.target.value }), ref: node => { inputs.current[key] = node }, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': errors[key] ? `animal-${key}-error` : undefined }
  }
  const error = key => errors[key] && <span className="adoption-field-error" id={`animal-${key}-error`}>{errors[key]}</span>
  const shownImage = image.preview ?? (image.remove ? null : animal?.imageUrl ?? null)
  return <><Link className="admin-back-link" to="/admin/animaux">← Retour aux animaux</Link><p className="eyebrow">{animal ? `Fiche n° ${animal.id}` : 'Une nouvelle arrivée'}</p><h1 className="animal-editor-title">{animal ? `Modifier ${animal.nom}` : 'Ajouter un animal'}</h1><p className="animal-editor-intro">Nom, espèce, âge, sexe et statut sont obligatoires. La race et l’état de santé sont facultatifs.</p>
    {animal && <div className="animal-management-links"><Link className="medical-profile-link" to={`/admin/animaux/${animal.id}/dossier-medical`}>Consulter le dossier médical →</Link><Link className="medical-profile-link" to={`/admin/animaux/${animal.id}/annonce`}>Créer une annonce pour cet animal →</Link></div>}
    <form className="admin-animal-form" onSubmit={submit} noValidate aria-busy={busy}>
      {state.message && <div className="admin-error" ref={feedback} tabIndex={-1} role="alert"><p>{state.message}</p>{reload && ['conflict', 'uncertain'].includes(state.phase) && <><p>Recharger la fiche remplacera vos modifications par les informations enregistrées.</p><button className="button button-outline" type="button" onClick={reload}>Recharger la fiche</button></>}<Link to="/admin/animaux">Vérifier la liste des animaux</Link></div>}
      <fieldset className="animal-form-grid" disabled={blocked}><legend>Informations de l’animal</legend>
        <div className="adoption-field"><label htmlFor="animal-nom">Nom</label><input {...control('nom')} required autoComplete="off" />{error('nom')}</div>
        <div className="adoption-field"><label htmlFor="animal-espece">Espèce</label><input {...control('espece')} required placeholder="Ex. Chat" />{error('espece')}</div>
        <div className="adoption-field"><label htmlFor="animal-race">Race (facultatif)</label><input {...control('race')} />{error('race')}</div>
        <div className="adoption-field"><label htmlFor="animal-age">Âge en années</label><input {...control('age')} type="number" min="0" max="2147483647" step="1" inputMode="numeric" required /><span className="animal-field-hint">0 pour un animal de moins d’un an.</span>{error('age')}</div>
        <div className="adoption-field"><label htmlFor="animal-sexe">Sexe</label><input {...control('sexe')} required list="animal-sex-options" /><datalist id="animal-sex-options"><option value="MALE">Mâle</option><option value="FEMELLE">Femelle</option><option value="INCONNU">Inconnu</option></datalist>{error('sexe')}</div>
        <div className="adoption-field"><label htmlFor="animal-statut">Statut</label><select {...control('statut')} required disabled={blocked || adopted}>{adopted ? <option value="ADOPTE">Adopté</option> : <><option value="">Choisir un statut</option>{fields.statut && !['DISPONIBLE', 'EN_SOIN'].includes(fields.statut) && <option value={fields.statut} disabled>Statut à préciser</option>}<option value="DISPONIBLE">Disponible</option><option value="EN_SOIN">En soins</option></>}</select>{error('statut')}</div>
        <div className="adoption-field animal-health-field"><label htmlFor="animal-etatSante">État de santé (facultatif)</label><textarea {...control('etatSante')} rows="3" />{error('etatSante')}</div>
        <div className="adoption-field animal-image-field"><label htmlFor="animal-image">Photo de l’animal (facultatif)</label><div className="animal-image-editor"><div className="animal-image-preview">{shownImage ? <img src={shownImage} alt={`Aperçu de la photo${fields.nom.trim() ? ` de ${fields.nom.trim()}` : ''}`} /> : <span>Aucune photo</span>}</div><div className="animal-image-controls"><input id="animal-image" type="file" accept={ANIMAL_IMAGE_TYPES.join(',')} ref={node => { inputs.current.image = node }} onChange={chooseImage} aria-invalid={Boolean(errors.image)} aria-describedby={errors.image ? 'animal-image-hint animal-image-error' : 'animal-image-hint'} /><span className="animal-field-hint" id="animal-image-hint">JPEG, PNG ou WebP, 5 Mo maximum. Elle sera visible sur le site public.</span>{shownImage && <button className="text-button" type="button" onClick={removeImage}>Retirer la photo</button>}{error('image')}</div></div></div>
      </fieldset>
      <p className="adoption-note">{animal ? adopted ? 'Cet animal est adopté. Ses informations restent modifiables ; son statut et son historique sont conservés.' : 'Le statut « Adopté » sera attribué lors de l’acceptation d’une demande d’adoption.' : 'Un dossier médical vide est créé automatiquement. L’adoption sera confirmée lors de l’acceptation d’une demande.'}</p>
      <div className="adoption-actions"><button className="button button-primary" type="submit" disabled={blocked}>{busy ? 'Enregistrement…' : animal ? 'Enregistrer les modifications' : 'Ajouter l’animal'}</button><Link to="/admin/animaux">Annuler</Link></div>{busy && <p role="status" className="adoption-progress">Enregistrement de la fiche en cours.</p>}
    </form></>
}

export default function AdminAnimalEditorPage({ creating = false }) {
  const { id } = useParams()
  useEffect(() => { const previous = document.title; document.title = `${creating ? 'Ajouter un animal' : 'Modifier un animal'} · Pawtale`; return () => { document.title = previous } }, [creating])
  return <section className="animal-editor">{creating ? <AnimalForm /> : <AdminAnimalRecord key={id} id={id}>{(animal, reload) => <AnimalForm animal={animal} reload={reload} />}</AdminAnimalRecord>}</section>
}
