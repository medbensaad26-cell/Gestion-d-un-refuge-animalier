import { AdminSessionExpiredError } from './adminAuth.js'
import { validAnimalId } from './adminAnimals.js'

export class AdminAnnouncementError extends Error {
  constructor(kind, message, fields = {}) { super(message); this.name = 'AdminAnnouncementError'; this.kind = kind; this.fields = fields }
}

export function validPublicationDate(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function announcementToday(date = new Date()) {
  const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return validPublicationDate(value) ? value : ''
}

export function validateAnnouncement(input = {}, { originalStatus = null } = {}) {
  const values = {}, errors = {}
  values.titre = typeof input.titre === 'string' ? input.titre.trim() : ''
  if (!values.titre) errors.titre = 'Renseignez le titre de l’annonce.'
  else if ([...values.titre].length > 150) errors.titre = 'Utilisez au maximum 150 caractères.'
  values.description = typeof input.description === 'string' ? input.description.trim() || null : null
  if (input.description != null && typeof input.description !== 'string') errors.description = 'Renseignez du texte ou laissez ce champ vide.'
  else if (values.description && new TextEncoder().encode(values.description).length > 65535) errors.description = 'La description dépasse la limite de stockage. Raccourcissez-la (65 535 octets UTF-8 maximum).'
  values.datePublication = typeof input.datePublication === 'string' ? input.datePublication : ''
  if (!validPublicationDate(values.datePublication)) errors.datePublication = 'Choisissez une date valide entre le 01/01/1000 et le 31/12/9999.'
  values.statut = typeof input.statut === 'string' ? input.statut : ''
  const canonical = ['INTERNE', 'PUBLIEE'].includes(values.statut)
  // Account for Java String.trim() too: a legacy private value must not become PUBLIEE on save.
  const trimmedStatus = values.statut.trim()
  let start = 0, end = trimmedStatus.length
  while (start < end && trimmedStatus.charCodeAt(start) <= 32) start++
  while (end > start && trimmedStatus.charCodeAt(end - 1) <= 32) end--
  const normalizedStatus = trimmedStatus.slice(start, end)
  if (!normalizedStatus || [...values.statut].length > 50 || (!canonical && (values.statut !== originalStatus || normalizedStatus === 'PUBLIEE'))) errors.statut = 'Choisissez explicitement « Non publiée » ou « Publiée ».'
  else values.statut = normalizedStatus
  values.animalId = Number(input.animalId)
  if (!validAnimalId(input.animalId)) errors.animalId = 'Choisissez l’animal concerné.'
  return { values, errors }
}

function announcementRecord(value) {
  if (!value || !Number.isSafeInteger(value.id) || value.id <= 0 || !Number.isSafeInteger(value.animalId) || value.animalId <= 0 || typeof value.titre !== 'string' || !value.titre.trim() || (value.description != null && typeof value.description !== 'string') || !validPublicationDate(value.datePublication) || typeof value.statut !== 'string' || !value.statut.trim()) throw new Error('Invalid announcement response')
  return { id: value.id, titre: value.titre, description: value.description ?? null, datePublication: value.datePublication, statut: value.statut, animalId: value.animalId }
}

async function request(token, path, { method = 'GET', body, signal, fetchImpl = globalThis.fetch } = {}) {
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{32,512}$/.test(token)) throw new AdminSessionExpiredError()
  const writing = method !== 'GET'
  const uncertain = 'L’enregistrement n’a pas pu être confirmé. Vérifiez la liste des annonces avant de réessayer pour éviter une publication en double.'
  let response
  try {
    response = await fetchImpl(path, { method, headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(writing ? { 'Content-Type': 'application/json' } : {}) }, credentials: 'omit', cache: 'no-store', signal, ...(writing ? { body: JSON.stringify(body) } : {}) })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminAnnouncementError(writing ? 'uncertain' : 'network', writing ? uncertain : 'Impossible de contacter le refuge. Veuillez réessayer.')
  }
  if (response.status === 401) throw new AdminSessionExpiredError()
  if (response.status === 404) throw new AdminAnnouncementError('notFound', 'Cette annonce est introuvable ou a été supprimée.')
  if (response.status === 400) throw new AdminAnnouncementError('validation', 'Les informations ont été refusées. Vérifiez les champs avant de réessayer.')
  if (response.status === 409) throw new AdminAnnouncementError('conflict', 'L’annonce ne peut pas être enregistrée. Rechargez les informations et vérifiez l’animal choisi.')
  if (response.status !== (method === 'POST' ? 201 : 200)) throw new AdminAnnouncementError(writing ? 'uncertain' : 'server', writing ? uncertain : 'Les annonces sont momentanément indisponibles. Veuillez réessayer.')
  if (method === 'POST') return { created: true }
  try { return await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminAnnouncementError(writing ? 'uncertain' : 'invalid', writing ? uncertain : 'Les annonces n’ont pas pu être chargées.')
  }
}

export async function getAdminAnnouncements(token, options) {
  const data = await request(token, '/api/annonces', options)
  try {
    if (!Array.isArray(data) || new Set(data.map(item => item?.id)).size !== data.length) throw new Error('Invalid list')
    return data.map(announcementRecord).sort((a, b) => b.datePublication.localeCompare(a.datePublication) || b.id - a.id)
  } catch { throw new AdminAnnouncementError('invalid', 'La liste des annonces n’a pas pu être chargée.') }
}

export async function getAdminAnnouncement(token, id, options) {
  if (!validAnimalId(id)) throw new AdminAnnouncementError('notFound', 'Cette annonce est introuvable.')
  const data = await request(token, `/api/annonces/${id}`, options)
  try {
    const record = announcementRecord(data)
    if (record.id !== Number(id)) throw new Error('Wrong announcement')
    return record
  } catch { throw new AdminAnnouncementError('invalid', 'L’annonce n’a pas pu être chargée.') }
}

export async function saveAdminAnnouncement(token, id, input, { originalStatus, ...options } = {}) {
  if (id != null && !validAnimalId(id)) throw new AdminAnnouncementError('notFound', 'Cette annonce est introuvable.')
  const { values, errors } = validateAnnouncement(input, { originalStatus })
  if (Object.keys(errors).length) throw new AdminAnnouncementError('validation', 'Vérifiez les informations de l’annonce.', errors)
  const data = await request(token, id == null ? '/api/annonces' : `/api/annonces/${id}`, { ...options, method: id == null ? 'POST' : 'PUT', body: values })
  if (id == null) return data
  try {
    const record = announcementRecord(data)
    if (record.id !== Number(id) || record.animalId !== values.animalId || record.statut !== values.statut) throw new Error('Unexpected announcement state')
    return record
  } catch { throw new AdminAnnouncementError('uncertain', 'L’enregistrement n’a pas pu être confirmé. Rechargez l’annonce pour vérifier les informations enregistrées.') }
}
