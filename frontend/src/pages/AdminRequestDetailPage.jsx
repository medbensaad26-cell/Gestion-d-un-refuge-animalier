import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAdminSession } from '../context/AdminSessionContext.js'
import { AdminSessionExpiredError } from '../services/adminAuth.js'
import { getAdminRequest, decideAdminRequest, requestStatus } from '../services/adminRequests.js'
import { getAdminAnimal } from '../services/adminAnimals.js'
import { getAnimalStatus } from '../utils/animalLabels.js'
import { publicationDateLabel as requestDateLabel } from '../services/announcements.js'

function RequestReview({ initial, reload }) {
  const { session, expire } = useAdminSession()
  const [data, setData] = useState(initial)
  const [intent, setIntent] = useState(null)
  const [state, setState] = useState({ phase: 'idle', message: '' })
  const active = useRef(true), lock = useRef(false), operation = useRef(null), feedback = useRef(null), confirmation = useRef(null)
  const { request, animal } = data
  const pending = request.statut === 'EN_ATTENTE'
  const busy = ['checking', 'sending'].includes(state.phase)
  const blocked = busy || ['conflict', 'uncertain', 'notFound', 'validation', 'success'].includes(state.phase)
  const canAccept = pending && animal.statut === 'DISPONIBLE'
  useEffect(() => { active.current = true; return () => { active.current = false; operation.current?.abort() } }, [])
  useEffect(() => { if (intent) confirmation.current?.focus() }, [intent])
  useEffect(() => { if (state.message) feedback.current?.focus() }, [state])
  async function confirm() {
    if (lock.current || blocked || !pending || !intent || (intent === 'accepter' && !canAccept)) return
    lock.current = true
    const controller = new AbortController()
    operation.current = controller
    let sent = false
    setState({ phase: 'checking', message: '' })
    try {
      const latest = await getAdminRequest(session.token, request.id, { signal: controller.signal })
      const latestAnimal = await getAdminAnimal(session.token, latest.animalId, { signal: controller.signal })
      if (!active.current) return
      const changed = ['animalId', 'nomDemandeur', 'email', 'telephone', 'dateDemande'].some(key => latest[key] !== request[key])
      setData({ request: latest, animal: latestAnimal })
      if (changed || latest.statut !== 'EN_ATTENTE' || (intent === 'accepter' && latestAnimal.statut !== 'DISPONIBLE')) {
        setIntent(null)
        setState({ phase: 'conflict', message: 'La demande ou la situation de l’animal a changé. Aucune décision n’a été envoyée. Rechargez les informations avant de continuer.' })
        return
      }
      sent = true
      setState({ phase: 'sending', message: '' })
      const result = await decideAdminRequest(session.token, request.id, intent, { signal: controller.signal })
      if (!active.current) return
      setData({ request: { ...latest, statut: result.statut }, animal: intent === 'accepter' ? { ...latestAnimal, statut: 'ADOPTE' } : latestAnimal })
      setIntent(null)
      setState({ phase: 'success', message: intent === 'accepter' ? 'La demande a bien été acceptée. L’animal est maintenant adopté.' : 'La demande a bien été refusée. Cette décision ne modifie pas le statut de l’animal.' })
    } catch (error) {
      if (!active.current || controller.signal.aborted) return
      if (error instanceof AdminSessionExpiredError) expire(session.token)
      else if (!sent && !['notFound', 'conflict'].includes(error.kind)) setState({ phase: 'preflightError', message: 'Impossible de vérifier la situation actuelle. Aucune décision n’a été envoyée. Vous pouvez réessayer la confirmation.' })
      else setState({ phase: error.kind ?? 'uncertain', message: error.message })
    } finally { lock.current = false }
  }
  return <><p className="request-detail-date">Demande n° {request.id} · Reçue le {requestDateLabel(request.dateDemande)}</p><span className={`request-status request-status-${request.statut}`}>{requestStatus(request.statut)}</span>
    {state.message && <div className={state.phase === 'success' ? 'admin-notice request-feedback' : 'admin-error request-feedback'} ref={feedback} tabIndex={-1} role={state.phase === 'success' ? 'status' : 'alert'}><p>{state.message}</p>{!busy && state.phase !== 'preflightError' && <button className="button button-outline" type="button" onClick={reload}>Actualiser la demande</button>}</div>}
    <section className="request-applicant" aria-labelledby="request-applicant-title"><h2 id="request-applicant-title">Le demandeur</h2><dl><div><dt>Nom</dt><dd>{request.nomDemandeur}</dd></div><div><dt>Adresse e-mail</dt><dd>{request.email}</dd></div><div><dt>Téléphone</dt><dd>{request.telephone || 'Non renseigné'}</dd></div></dl></section>
    <section className="request-animal" aria-label="Animal concerné"><div><p className="eyebrow">Animal concerné · Fiche n° {animal.id}</p><h2>{animal.nom}</h2><p>{animal.espece} · {getAnimalStatus(animal.statut).label}</p></div><Link to={`/admin/animaux/${animal.id}/modifier`}>Consulter la fiche de l’animal →</Link></section>
    {!pending && <p className="adoption-note">Cette demande est déjà traitée. Sa décision est conservée dans l’historique.</p>}
    {pending && <section className="request-decision" aria-labelledby="request-decision-title"><h2 id="request-decision-title">Examiner la demande</h2><p>Vérifiez les coordonnées et l’animal avant de confirmer votre décision.</p>{!canAccept && <p className="adoption-note">L’animal n’est pas disponible à l’adoption. L’acceptation est bloquée ; la demande peut encore être refusée.</p>}
      {!intent && <div className="adoption-actions"><button className="button button-primary" disabled={blocked || !canAccept} onClick={() => setIntent('accepter')}>Accepter la demande</button><button className="button button-outline" disabled={blocked} onClick={() => setIntent('refuser')}>Refuser la demande</button></div>}
      {intent && <div className="request-confirmation" ref={confirmation} tabIndex={-1} role="region" aria-labelledby="request-confirmation-title"><h3 id="request-confirmation-title">{intent === 'accepter' ? 'Confirmer l’acceptation ?' : 'Confirmer le refus ?'}</h3><p>Demande n° {request.id} · {request.nomDemandeur} · {animal.nom} (fiche n° {animal.id}).</p><p>{intent === 'accepter' ? 'L’acceptation enregistre cette adoption et fait passer l’animal au statut « Adopté ». La décision ne pourra plus être modifiée depuis cet écran.' : 'Le refus clôt cette demande sans changer le statut de l’animal. La décision ne pourra plus être modifiée depuis cet écran.'}</p><div className="adoption-actions"><button className={`button ${intent === 'accepter' ? 'button-primary' : 'button-danger'}`} disabled={blocked} onClick={confirm}>{busy ? state.phase === 'checking' ? 'Vérification…' : 'Enregistrement…' : intent === 'accepter' ? 'Confirmer l’acceptation' : 'Confirmer le refus'}</button><button type="button" className="text-button" disabled={blocked} onClick={() => { setIntent(null); setState({ phase: 'idle', message: '' }) }}>Annuler la décision</button></div></div>}
      {busy && <p className="adoption-progress" role="status">{state.phase === 'checking' ? 'Vérification de la demande et de l’animal.' : 'Enregistrement de la décision en cours.'}</p>}
    </section>}
    {state.phase === 'idle' && <button className="text-button request-refresh" onClick={reload}>Actualiser la demande</button>}
  </>
}

function RequestLoader({ id }) {
  const { session, expire } = useAdminSession()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ status: 'loading' })
  function reload() { setState({ status: 'loading' }); setAttempt(value => value + 1) }
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    getAdminRequest(session.token, id, { signal: controller.signal })
      .then(async request => ({ request, animal: await getAdminAnimal(session.token, request.animalId, { signal: controller.signal }) }))
      .then(data => { if (current) setState({ status: 'success', data }) })
      .catch(error => {
        if (!current || controller.signal.aborted) return
        if (error instanceof AdminSessionExpiredError) expire(session.token)
        else setState({ status: error.kind === 'notFound' ? 'notFound' : 'error', message: error.message })
      })
    return () => { current = false; controller.abort() }
  }, [session.token, id, attempt, expire])
  if (state.status === 'loading') return <div className="feedback-panel" role="status">Chargement de la demande…</div>
  if (state.status !== 'success') return <div className="feedback-panel" role="alert"><h2>{state.status === 'notFound' ? 'Demande ou animal introuvable.' : 'La demande se fait attendre.'}</h2><p>{state.message}</p>{state.status === 'error' && <button className="button button-outline" onClick={reload}>Réessayer</button>}</div>
  return <RequestReview initial={state.data} reload={reload} />
}

export default function AdminRequestDetailPage() {
  const { id } = useParams()
  useEffect(() => { const previous = document.title; document.title = 'Examiner une demande · Le Refuge'; return () => { document.title = previous } }, [])
  return <section className="animal-editor request-detail"><Link className="admin-back-link" to="/admin/demandes">← Retour aux demandes</Link><p className="eyebrow">Accompagner les adoptions</p><h1 className="animal-editor-title">Examiner une demande</h1><RequestLoader key={id} id={id} /></section>
}
