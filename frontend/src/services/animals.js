const ANIMALS_ENDPOINT = '/api/animaux'

const messages = {
  network: 'Impossible de contacter le refuge. Vérifiez votre connexion et réessayez.',
  unavailable: 'Les animaux ne peuvent pas être chargés pour le moment. Réessayez dans un instant.',
  invalid: 'Les informations des animaux n’ont pas pu être chargées. Veuillez réessayer.',
}

export class AnimalNotFoundError extends Error {
  constructor() {
    super('Cet animal est introuvable.')
    this.name = 'AnimalNotFoundError'
  }
}

function isAnimal(animal) {
  if (
    animal === null ||
    typeof animal !== 'object' ||
    Array.isArray(animal) ||
    !Number.isSafeInteger(animal.id) ||
    animal.id <= 0 ||
    typeof animal.nom !== 'string' ||
    animal.nom.trim() === '' ||
    typeof animal.espece !== 'string' ||
    animal.espece.trim() === ''
  ) {
    return false
  }

  if (
    animal.age != null &&
    (!Number.isSafeInteger(animal.age) || animal.age < 0)
  ) {
    return false
  }

  return ['race', 'sexe', 'etatSante', 'statut'].every(
    (field) => animal[field] == null || typeof animal[field] === 'string',
  )
}

/** Accepts only this animal's own image route, so a stored value can never load another address. */
export function animalImageUrl(animal) {
  const url = animal?.imageUrl
  return typeof url === 'string' && new RegExp(`^/api/animaux/${animal.id}/image(?:\\?v=\\d+)?$`).test(url) ? url : null
}

function publicAnimal(animal) {
  const imageUrl = animalImageUrl(animal)
  return {
    id: animal.id,
    nom: animal.nom,
    espece: animal.espece,
    race: animal.race ?? null,
    age: animal.age ?? null,
    sexe: animal.sexe ?? null,
    etatSante: animal.etatSante ?? null,
    statut: animal.statut ?? null,
    ...(imageUrl ? { imageUrl } : {}),
  }
}

/** Load only the public animal catalogue through the frontend's /api proxy. */
export async function getAnimals({ signal, fetchImpl = globalThis.fetch } = {}) {
  let response
  try {
    response = await fetchImpl(ANIMALS_ENDPOINT, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      cache: 'no-store',
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error(messages.network)
  }

  if (!response.ok) {
    throw new Error(messages.unavailable)
  }

  let animals
  try {
    animals = await response.json()
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error(messages.invalid)
  }

  if (!Array.isArray(animals) || !animals.every(isAnimal)) {
    throw new Error(messages.invalid)
  }

  return animals.map(publicAnimal)
}

/** Load a single public animal; never fetch administrator-only medical records. */
export async function getAnimal(id, { signal, fetchImpl = globalThis.fetch } = {}) {
  if (!/^[1-9]\d*$/.test(String(id)) || !Number.isSafeInteger(Number(id))) {
    throw new AnimalNotFoundError()
  }
  let response
  try {
    response = await fetchImpl(`${ANIMALS_ENDPOINT}/${id}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      cache: 'no-store',
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error(messages.network)
  }
  if (response.status === 404) throw new AnimalNotFoundError()
  if (!response.ok) throw new Error(messages.unavailable)
  let animal
  try {
    animal = await response.json()
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error(messages.invalid)
  }
  if (!isAnimal(animal) || animal.id !== Number(id)) throw new Error(messages.invalid)
  return publicAnimal(animal)
}
