const statuses = new Set(['DISPONIBLE', 'EN_SOIN', 'ADOPTE'])

export class AnnouncementFilterError extends Error {
  constructor(message = 'Les critères de recherche ne sont pas valides.') {
    super(message)
    this.name = 'AnnouncementFilterError'
  }
}

export function normalizeAnnouncementFilters(filters = {}) {
  const result = {}
  for (const [key, limit] of [['q', 150], ['espece', 100], ['statutAnimal', 50]]) {
    const value = filters[key] ?? ''
    if (typeof value !== 'string') throw new AnnouncementFilterError()
    result[key] = value.trim()
    if ([...result[key]].length > limit) throw new AnnouncementFilterError(`${key === 'q' ? 'La recherche est limitée' : 'Ce filtre est limité'} à ${limit} caractères.`)
  }
  result.statutAnimal = result.statutAnimal.toUpperCase()
  if (result.statutAnimal && !statuses.has(result.statutAnimal)) throw new AnnouncementFilterError('Le statut de l’animal n’est pas valide.')
  return result
}

export function filtersFromSearchParams(params) {
  const values = {}
  for (const [key, value] of params) {
    if (!['q', 'espece', 'statutAnimal'].includes(key) || Object.hasOwn(values, key)) throw new AnnouncementFilterError()
    values[key] = value
  }
  return normalizeAnnouncementFilters(values)
}

export function announcementQuery(filters = {}) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(normalizeAnnouncementFilters(filters))) if (value) params.set(key, value)
  return params.toString()
}

function validDate(date) {
  if (typeof date !== 'string' || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(date)) return false
  const parsed = new Date(`${date}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
}

export function publicationDateLabel(date) {
  if (!validDate(date)) return 'Date non renseignée'
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`))
}

function isAnnouncement(item) {
  return item !== null && typeof item === 'object' && !Array.isArray(item)
    && Number.isSafeInteger(item.id) && item.id > 0
    && Number.isSafeInteger(item.animalId) && item.animalId > 0
    && typeof item.titre === 'string' && item.titre.trim() !== ''
    && (item.description == null || typeof item.description === 'string')
    && validDate(item.datePublication) && item.statut === 'PUBLIEE'
}

/** Public GET only. Never attach a session or retain administrator attribution. */
export async function getAnnouncements({ filters = {}, signal, fetchImpl = globalThis.fetch } = {}) {
  const query = announcementQuery(filters)
  let response
  try {
    response = await fetchImpl(`/api/annonces${query ? `?${query}` : ''}`, {
      method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Impossible de contacter le refuge. Vérifiez votre connexion et réessayez.')
  }
  if (!response.ok) throw new Error('Les annonces sont momentanément indisponibles. Veuillez réessayer.')
  let data
  try { data = await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Les annonces n’ont pas pu être chargées. Veuillez réessayer.')
  }
  if (!Array.isArray(data) || !data.every(isAnnouncement) || new Set(data.map(item => item.id)).size !== data.length) {
    throw new Error('Les annonces n’ont pas pu être chargées. Veuillez réessayer.')
  }
  return data.map(({ id, titre, description, datePublication, animalId }) => ({ id, titre, description: description ?? null, datePublication, animalId }))
    .sort((a, b) => b.datePublication.localeCompare(a.datePublication) || b.id - a.id)
}
