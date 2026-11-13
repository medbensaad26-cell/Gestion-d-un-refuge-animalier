import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminAnimal } from '../services/adminAnimals.js'

export default function AdminAnimalRecord({ id, children }) {
  const { session, expire } = useAdminSession()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ status: 'loading' })
  function reload() { setState({ status: 'loading' }); setAttempt(value => value + 1) }
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAdminAnimal(session.token, id, { signal: controller.signal })
      .then(animal => { if (current) setState({ status: 'success', animal }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: error.kind === 'notFound' ? 'notFound' : 'error', message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, id, attempt, expire])
  if (state.status === 'loading') return <div className="feedback-panel" role="status">Chargement de la fiche…</div>
  if (state.status !== 'success') return <div className="feedback-panel" role="alert"><h1>{state.status === 'notFound' ? 'Animal introuvable.' : 'La fiche se fait attendre.'}</h1><p>{state.message}</p>{state.status === 'error' && <button className="button button-outline" onClick={reload}>Réessayer</button>} <Link to="/admin/animaux">Retour aux animaux</Link></div>
  return children(state.animal, reload)
}
