import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import AdminAnimalRecord from '../components/AdminAnimalRecord.jsx'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { deleteAdminAnimal } from '../services/adminAnimals.js'

function DeleteConfirmation({ animal, reload }) {
  const { session, expire } = useAdminSession()
  const navigate = useNavigate()
  const [state, setState] = useState({ phase: 'idle', message: '' })
  const active = useRef(true), lock = useRef(false), request = useRef(null), feedback = useRef(null)
  const adopted = animal.statut?.trim().toUpperCase() === 'ADOPTE'
  useEffect(() => { active.current = true; return () => { active.current = false; request.current?.abort() } }, [])
  useEffect(() => { if (state.message) feedback.current?.focus() }, [state])
  async function confirm() {
    if (lock.current || adopted || state.phase !== 'idle') return
    lock.current = true
    const controller = new AbortController()
    request.current = controller
    setState({ phase: 'sending', message: '' })
    try {
      await deleteAdminAnimal(session.token, animal.id, { signal: controller.signal })
      if (active.current) navigate('/admin/animaux', { replace: true, state: { notice: 'L’animal et son dossier médical ont été supprimés.' } })
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (error instanceof AdminSessionExpiredError) expire(session.token)
      else setState({ phase: error.kind ?? 'uncertain', message: error.message })
    } finally { lock.current = false }
  }
  return <><Link className="admin-back-link" to="/admin/animaux">← Retour aux animaux</Link><p className="eyebrow">Fiche n° {animal.id}</p><h1 className="animal-editor-title">Supprimer {animal.nom} ?</h1><div className="admin-delete-panel">
    <h2>{animal.nom}</h2><p>{animal.espece} · {animal.race || 'Race non renseignée'} · Fiche n° {animal.id}</p>
    {adopted ? <p className="adoption-note">Cet animal est adopté. Son historique doit être conservé et sa suppression est bloquée.</p> : <p className="adoption-note">Cette action supprime définitivement l’animal et son dossier médical. Elle est bloquée si des annonces ou des demandes lui sont liées.</p>}
    {state.message && <div className="admin-error" role="alert" ref={feedback} tabIndex={-1}><p>{state.message}</p>{state.phase !== 'notFound' && <button className="button button-outline" onClick={reload}>Recharger la fiche</button>}<Link to="/admin/animaux">Vérifier la liste des animaux</Link></div>}
    <div className="adoption-actions">{!adopted && <button className="button button-danger" disabled={state.phase !== 'idle'} onClick={confirm}>{state.phase === 'sending' ? 'Suppression…' : 'Confirmer la suppression'}</button>}<Link className="button button-outline" to="/admin/animaux">{adopted ? 'Retour aux animaux' : 'Annuler'}</Link></div>{state.phase === 'sending' && <p role="status" className="adoption-progress">Suppression en cours.</p>}
  </div></>
}

export default function AdminAnimalDeletePage() {
  const { id } = useParams()
  useEffect(() => { const previous = document.title; document.title = 'Supprimer un animal · Pawtale'; return () => { document.title = previous } }, [])
  return <section className="animal-editor"><AdminAnimalRecord key={id} id={id}>{(animal, reload) => <DeleteConfirmation animal={animal} reload={reload} />}</AdminAnimalRecord></section>
}
