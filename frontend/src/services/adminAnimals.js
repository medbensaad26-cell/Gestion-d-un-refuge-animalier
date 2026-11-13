import { AdminSessionExpiredError } from './adminAuth.js'
// Animals and announcements use the same validated backend catalogue criteria.
import { announcementQuery as animalQuery, normalizeAnnouncementFilters as normalizeAnimalFilters, filtersFromSearchParams as animalFiltersFromSearchParams } from './announcements.js'
export { animalQuery, normalizeAnimalFilters, animalFiltersFromSearchParams }

export class AdminAnimalError extends Error {
  constructor(kind, message, fields = {}) { super(message); this.name = 'AdminAnimalError'; this.kind = kind; this.fields = fields }
}

export function validAnimalId(value) { return /^[1-9]\d*$/.test(String(value)) && Number.isSafeInteger(Number(value)) }

export function validateAnimal(input = {}, { originalStatus = null } = {}) {
  const values = {}, errors = {}
  for (const [key, label, limit, required] of [['nom', 'le nom', 100, true], ['espece', 'l’espèce', 100, true], ['race', 'la race', 100, false], ['sexe', 'le sexe', 20, true], ['etatSante', 'l’état de santé', 255, false], ['statut', 'le statut', 50, true]]) {
    const text = typeof input[key] === 'string' ? input[key].trim() : ''
    values[key] = text || (required ? '' : null)
    if (required && !text) errors[key] = `Renseignez ${label}.`
    else if ([...text].length > limit) errors[key] = `Utilisez au maximum ${limit} caractères.`
  }
  const age = String(input.age ?? '').trim()
  if (!/^\d+$/.test(age) || !Number.isSafeInteger(Number(age)) || Number(age) > 2147483647) errors.age = 'Renseignez un âge entier positif ou nul.'
  values.age = Number(age)
  if (!['DISPONIBLE', 'EN_SOIN', 'ADOPTE'].includes(values.statut)) errors.statut = 'Choisissez un statut valide.'
  else if (originalStatus?.trim().toUpperCase() === 'ADOPTE' && values.statut !== 'ADOPTE') errors.statut = 'Un animal adopté conserve son statut.'
  else if (originalStatus?.trim().toUpperCase() !== 'ADOPTE' && values.statut === 'ADOPTE') errors.statut = 'Une adoption se confirme en acceptant une demande.'
  return { values, errors }
}

function animalRecord(item) {
  if (!item || !validAnimalId(item.id) || typeof item.id !== 'number' || typeof item.nom !== 'string' || !item.nom.trim() || typeof item.espece !== 'string' || !item.espece.trim() || !Number.isInteger(item.age) || item.age < 0 || item.age > 2147483647 || !['race', 'sexe', 'etatSante', 'statut'].every(key => item[key] == null || typeof item[key] === 'string')) throw new AdminAnimalError('invalid', 'La fiche de l’animal n’a pas pu être chargée.')
  return { id: item.id, nom: item.nom, espece: item.espece, race: item.race ?? null, age: item.age, sexe: item.sexe ?? null, etatSante: item.etatSante ?? null, statut: item.statut ?? null }
}

async function request(token, path, { method = 'GET', body, signal, fetchImpl = globalThis.fetch } = {}) {
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{32,512}$/.test(token)) throw new AdminSessionExpiredError()
  const writing = method !== 'GET'
  let response
  try {
    response = await fetchImpl(path, { method, headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(writing && body ? { 'Content-Type': 'application/json' } : {}) }, credentials: 'omit', cache: 'no-store', signal, ...(body ? { body: JSON.stringify(body) } : {}) })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminAnimalError(writing ? 'uncertain' : 'network', writing ? 'L’opération n’a pas pu être confirmée. Vérifiez la liste des animaux avant de réessayer pour éviter une opération en double.' : 'Impossible de contacter le refuge. Veuillez réessayer.')
  }
  if (response.status === 401) throw new AdminSessionExpiredError()
  if (response.status === 404) throw new AdminAnimalError('notFound', 'Cet animal est introuvable ou a été supprimé.')
  if (response.status === 400) throw new AdminAnimalError('validation', 'Les informations ont été refusées. Vérifiez les champs avant de réessayer.')
  if (response.status === 409) throw new AdminAnimalError('conflict', method === 'DELETE' ? 'Suppression impossible : un animal adopté ou lié à des annonces ou demandes doit être conservé.' : 'La situation de l’animal empêche cette opération. Rechargez sa fiche ; une adoption doit être confirmée depuis les demandes.')
  const expected = method === 'POST' ? 201 : 200
  if (response.status !== expected) throw new AdminAnimalError(writing ? 'uncertain' : 'server', writing ? 'L’opération n’a pas pu être confirmée. Vérifiez la liste des animaux avant de réessayer.' : 'Les animaux sont momentanément indisponibles. Veuillez réessayer.')
  if (writing) return { success: true }
  try { return await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdminAnimalError('invalid', 'Les informations des animaux n’ont pas pu être chargées.')
  }
}

export async function getAdminAnimals(token, { filters = {}, ...options } = {}) {
  const query = animalQuery(filters)
  const data = await request(token, `/api/animaux${query ? `?${query}` : ''}`, options)
  if (!Array.isArray(data) || new Set(data.map(item => item?.id)).size !== data.length) throw new AdminAnimalError('invalid', 'La liste des animaux n’a pas pu être chargée.')
  return data.map(animalRecord)
}

export async function getAdminAnimal(token, id, options) {
  if (!validAnimalId(id)) throw new AdminAnimalError('notFound', 'Cet animal est introuvable.')
  const animal = animalRecord(await request(token, `/api/animaux/${id}`, options))
  if (animal.id !== Number(id)) throw new AdminAnimalError('invalid', 'La fiche de l’animal n’a pas pu être chargée.')
  return animal
}

export async function saveAdminAnimal(token, id, input, { originalStatus, ...options } = {}) {
  if (id != null && !validAnimalId(id)) throw new AdminAnimalError('notFound', 'Cet animal est introuvable.')
  const { values, errors } = validateAnimal(input, { originalStatus })
  if (Object.keys(errors).length) throw new AdminAnimalError('validation', 'Vérifiez les informations de l’animal.', errors)
  return request(token, id == null ? '/api/animaux' : `/api/animaux/${id}`, { ...options, method: id == null ? 'POST' : 'PUT', body: values })
}

export async function deleteAdminAnimal(token, id, options) {
  if (!validAnimalId(id)) throw new AdminAnimalError('notFound', 'Cet animal est introuvable.')
  return request(token, `/api/animaux/${id}`, { ...options, method: 'DELETE' })
}
