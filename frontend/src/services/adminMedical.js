import { AdminSessionExpiredError } from './adminAuth.js'
import { validAnimalId } from './adminAnimals.js'

export class AdminMedicalError extends Error {
  constructor(kind, message, fields = {}) { super(message); this.name = 'AdminMedicalError'; this.kind = kind; this.fields = fields }
}

export function validMedicalDate(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function medicalDateLabel(value) {
  return validMedicalDate(value) ? new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`)) : 'Non renseignée'
}

export function medicalToday(date = new Date()) {
  const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return validMedicalDate(value) ? value : ''
}

export function validateMedical(input = {}) {
  const values = {}, errors = {}
  for (const key of ['vaccins', 'traitements', 'observations']) {
    const value = input[key]
    if (value != null && typeof value !== 'string') { values[key] = null; errors[key] = 'Renseignez du texte ou laissez ce champ vide.'; continue }
    // Preserve existing spacing and line breaks; an empty field clears to NULL.
    values[key] = value === '' || value == null ? null : value
    if (values[key] != null && new TextEncoder().encode(values[key]).length > 65535) errors[key] = 'Ce texte dépasse la limite de stockage (65 535 octets UTF-8). Raccourcissez-le.'
  }
  values.dateMiseAJour = typeof input.dateMiseAJour === 'string' ? input.dateMiseAJour : ''
  if (!validMedicalDate(values.dateMiseAJour)) errors.dateMiseAJour = 'Choisissez une date valide entre le 01/01/1000 et le 31/12/9999.'
  return { values, errors }
}

function medicalRecord(value, animalId) {
  if (!value || !Number.isSafeInteger(value.id) || value.id <= 0 || !Number.isSafeInteger(value.animalId) || value.animalId !== Number(animalId) || !['vaccins', 'traitements', 'observations'].every(key => value[key] == null || typeof value[key] === 'string') || (value.dateMiseAJour != null && !validMedicalDate(value.dateMiseAJour))) throw new Error('Invalid medical response')
  return { id: value.id, animalId: value.animalId, vaccins: value.vaccins ?? null, traitements: value.traitements ?? null, observations: value.observations ?? null, dateMiseAJour: value.dateMiseAJour ?? null }
}

async function requestMedical(token, animalId, { method = 'GET', body, signal, fetchImpl = globalThis.fetch } = {}) {
  if (!validAnimalId(animalId)) throw new AdminMedicalError('notFound', 'Cet animal est introuvable.')
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{32,512}$/.test(token)) throw new AdminSessionExpiredError()
  const writing = method !== 'GET'
  const uncertain = 'L’enregistrement n’a pas pu être confirmé. Rechargez le dossier pour vérifier les informations conservées avant de réessayer.'
  let response
  try {
    response = await fetchImpl(`/api/animaux/${animalId}/dossier-medical`, { method, headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(writing ? { 'Content-Type': 'application/json' } : {}) }, credentials: 'omit', cache: 'no-store', signal, ...(writing ? { body: JSON.stringify(body) } : {}) })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminMedicalError(writing ? 'uncertain' : 'network', writing ? uncertain : 'Impossible de contacter le refuge. Veuillez réessayer.')
  }
  if (response.status === 401) throw new AdminSessionExpiredError()
  if (response.status === 404) throw new AdminMedicalError('notFound', writing ? 'L’animal ou son dossier n’est plus disponible. Rechargez la fiche avant de continuer.' : 'Aucun dossier médical n’a été trouvé pour cet animal.')
  if (response.status === 400) throw new AdminMedicalError('validation', 'Les informations ont été refusées. Vérifiez les textes et la date avant de réessayer.')
  if (response.status === 409) throw new AdminMedicalError('conflict', 'Le dossier a changé ou existe déjà. Rechargez les informations enregistrées avant de continuer.')
  if (response.status !== (method === 'POST' ? 201 : 200)) throw new AdminMedicalError(writing ? 'uncertain' : 'server', writing ? uncertain : 'Le dossier médical est momentanément indisponible. Veuillez réessayer.')
  try { return medicalRecord(await response.json(), animalId) } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminMedicalError(writing ? 'uncertain' : 'invalid', writing ? uncertain : 'Les informations du dossier médical n’ont pas pu être chargées.')
  }
}

export function getAdminMedical(token, animalId, options) { return requestMedical(token, animalId, options) }

export async function saveAdminMedical(token, animalId, input, { creating = false, ...options } = {}) {
  const { values, errors } = validateMedical(input)
  if (Object.keys(errors).length) throw new AdminMedicalError('validation', 'Vérifiez les informations du dossier médical.', errors)
  return requestMedical(token, animalId, { ...options, method: creating ? 'POST' : 'PUT', body: values })
}
