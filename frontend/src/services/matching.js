export class MatchingPreferenceError extends Error {
  constructor(field) { super('Choisissez une préférence proposée ou « Sans préférence ».'); this.name = 'MatchingPreferenceError'; this.field = field }
}

const choices = {
  espece: { CHIEN: 'Chien', CHAT: 'Chat' },
  categorieAge: { JEUNE: 'JEUNE', ADULTE: 'ADULTE', SENIOR: 'SENIOR' },
  sexe: { MALE: 'MALE', FEMELLE: 'FEMELLE', M: 'MALE', F: 'FEMELLE' },
}

export function normalizeMatchingPreferences(input = {}) {
  const result = {}
  for (const key of Object.keys(choices)) {
    const raw = input[key] ?? ''
    if (typeof raw !== 'string' || [...raw].length > 32) throw new MatchingPreferenceError(key)
    const value = raw.trim().normalize('NFD').replace(/\p{M}+/gu, '').toUpperCase()
    if (value && !choices[key][value]) throw new MatchingPreferenceError(key)
    result[key] = value ? choices[key][value] : ''
  }
  return result
}

export function hasMatchingPreferences(input) {
  return Object.values(normalizeMatchingPreferences(input)).some(Boolean)
}

export function matchingQuery(input) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(normalizeMatchingPreferences(input))) if (value) params.set(key, value)
  return params.toString()
}

function publicRecommendation(value, evaluated) {
  const a = value?.animal
  if (!a || !Number.isSafeInteger(a.id) || a.id <= 0 || typeof a.nom !== 'string' || !a.nom.trim()
    || typeof a.espece !== 'string' || !a.espece.trim() || !Number.isInteger(a.age) || a.age < -2147483648 || a.age > 2147483647
    || typeof a.sexe !== 'string' || a.statut !== 'DISPONIBLE'
    || !Number.isInteger(value.score) || value.score < 0 || value.score > 100 || (!evaluated && value.score !== 0)
    || typeof value.explication !== 'string' || !value.explication.trim()) throw new Error('Invalid matching response')
  return { animal: { id: a.id, nom: a.nom, espece: a.espece, age: a.age, sexe: a.sexe, statut: a.statut }, score: value.score, explication: value.explication }
}

/** Anonymous read-only request through the existing Vite /api proxy. Never recalculates scores. */
export async function getMatchingRecommendations({ preferences = {}, signal, fetchImpl = globalThis.fetch } = {}) {
  const query = matchingQuery(preferences)
  const evaluated = hasMatchingPreferences(preferences)
  let response
  try {
    response = await fetchImpl(`/api/matching${query ? `?${query}` : ''}`, {
      method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Impossible de contacter le refuge. Vérifiez votre connexion et réessayez.')
  }
  if (response.status === 400) throw new Error('Ces préférences ne sont pas reconnues. Choisissez les options proposées et réessayez.')
  if (response.status !== 200) throw new Error('Les recommandations sont momentanément indisponibles. Réessayez dans un instant.')
  try {
    const data = await response.json()
    if (!Array.isArray(data) || data.length > 3) throw new Error('Invalid matching list')
    const results = data.map(value => publicRecommendation(value, evaluated))
    if (new Set(results.map(r => r.animal.id)).size !== results.length) throw new Error('Duplicate animal')
    for (let i = 1; i < results.length; i++) {
      if (results[i].score > results[i - 1].score || (results[i].score === results[i - 1].score && results[i].animal.id < results[i - 1].animal.id)) throw new Error('Invalid ranking')
    }
    return results
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Les recommandations reçues sont incomplètes. Veuillez réessayer.')
  }
}
