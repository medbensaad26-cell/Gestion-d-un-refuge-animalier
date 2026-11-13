import { AdminSessionExpiredError } from './adminAuth.js'
import { validAnimalId } from './adminAnimals.js'

export class AdminRequestError extends Error {
  constructor(kind, message) { super(message); this.name = 'AdminRequestError'; this.kind = kind }
}

export function requestStatus(value) {
  return ({ EN_ATTENTE: 'En attente', ACCEPTEE: 'Acceptée', REFUSEE: 'Refusée' })[value] ?? 'Statut inconnu'
}

function validDate(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

function requestRecord(value) {
  if (!value || !Number.isSafeInteger(value.id) || value.id <= 0 || !Number.isSafeInteger(value.animalId) || value.animalId <= 0 || typeof value.nomDemandeur !== 'string' || !value.nomDemandeur.trim() || typeof value.email !== 'string' || !value.email.trim() || (value.telephone != null && typeof value.telephone !== 'string') || !validDate(value.dateDemande) || !['EN_ATTENTE', 'ACCEPTEE', 'REFUSEE'].includes(value.statut)) throw new Error('Invalid request response')
  return { id: value.id, nomDemandeur: value.nomDemandeur, email: value.email, telephone: value.telephone ?? null, dateDemande: value.dateDemande, statut: value.statut, animalId: value.animalId }
}

async function request(token, path, { method = 'GET', signal, fetchImpl = globalThis.fetch } = {}) {
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{32,512}$/.test(token)) throw new AdminSessionExpiredError()
  const writing = method === 'PATCH'
  const uncertain = 'La décision n’a pas pu être confirmée. Rechargez la demande pour vérifier son statut avant toute nouvelle action.'
  let response
  try { response = await fetchImpl(path, { method, headers: { Accept: 'application/json', Authorization: `Bearer ${token}` }, credentials: 'omit', cache: 'no-store', signal }) } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminRequestError(writing ? 'uncertain' : 'network', writing ? uncertain : 'Impossible de contacter le refuge. Veuillez réessayer.')
  }
  if (response.status === 401) throw new AdminSessionExpiredError()
  if (response.status === 404) throw new AdminRequestError('notFound', 'Cette demande est introuvable ou a été supprimée.')
  if (response.status === 409) throw new AdminRequestError('conflict', 'La demande a déjà été traitée ou l’animal ne peut plus être adopté. Rechargez la demande avant de continuer.')
  if (response.status === 400) throw new AdminRequestError('validation', 'La décision a été refusée. Rechargez la demande avant de continuer.')
  if (response.status !== 200) throw new AdminRequestError(writing ? 'uncertain' : 'server', writing ? uncertain : 'Les demandes sont momentanément indisponibles. Veuillez réessayer.')
  try { return await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminRequestError(writing ? 'uncertain' : 'invalid', writing ? uncertain : 'Les informations de la demande n’ont pas pu être chargées.')
  }
}

export async function getAdminRequests(token, options) {
  const data = await request(token, '/api/demandes', options)
  try {
    if (!Array.isArray(data) || new Set(data.map(item => item?.id)).size !== data.length) throw new Error('Invalid list')
    return data.map(requestRecord).sort((a, b) => b.dateDemande.localeCompare(a.dateDemande) || b.id - a.id)
  } catch { throw new AdminRequestError('invalid', 'La liste des demandes n’a pas pu être chargée.') }
}

export async function getAdminRequest(token, id, options) {
  if (!validAnimalId(id)) throw new AdminRequestError('notFound', 'Cette demande est introuvable.')
  const data = await request(token, `/api/demandes/${id}`, options)
  try {
    const record = requestRecord(data)
    if (record.id !== Number(id)) throw new Error('Wrong request')
    return record
  } catch { throw new AdminRequestError('invalid', 'Les informations de la demande n’ont pas pu être chargées.') }
}

export async function decideAdminRequest(token, id, decision, options) {
  if (!validAnimalId(id)) throw new AdminRequestError('notFound', 'Cette demande est introuvable.')
  if (!['accepter', 'refuser'].includes(decision)) throw new AdminRequestError('validation', 'Choisissez une décision valide.')
  const data = await request(token, `/api/demandes/${id}/${decision}`, { ...options, method: 'PATCH' })
  const expected = decision === 'accepter' ? 'ACCEPTEE' : 'REFUSEE'
  if (!data || data.id !== Number(id) || data.statut !== expected) throw new AdminRequestError('uncertain', 'La décision n’a pas pu être confirmée. Rechargez la demande pour vérifier son statut avant toute nouvelle action.')
  return { id: data.id, statut: data.statut }
}
