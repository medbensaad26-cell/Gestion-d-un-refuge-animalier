export class AdoptionRequestError extends Error {
  constructor(kind, message, fields = {}) {
    super(message)
    this.name = 'AdoptionRequestError'
    this.kind = kind
    this.fields = fields
  }
}

export function validateApplicant(input = {}) {
  const values = {}
  const errors = {}
  const labels = { nomDemandeur: 'votre nom', email: 'votre adresse e-mail', telephone: 'votre numéro de téléphone' }
  for (const [key, maximum] of [['nomDemandeur', 150], ['email', 255], ['telephone', 30]]) {
    values[key] = typeof input[key] === 'string' ? input[key].trim() : ''
    if (!values[key]) errors[key] = `Renseignez ${labels[key]}.`
    else if ([...values[key]].length > maximum) errors[key] = `Utilisez au maximum ${maximum} caractères.`
  }
  if (values.email && !/^[^\s@]+@[^\s@]+$/.test(values.email)) errors.email = 'Renseignez une adresse e-mail valide.'
  return { values, errors }
}

export function localRequestDate(date = new Date()) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime()) || date.getFullYear() < 1000 || date.getFullYear() > 9999) throw new Error('Date de demande invalide.')
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** One explicit public POST per call, without credentials or automatic retries. */
export async function submitAdoptionRequest(animalId, applicant, { signal, fetchImpl = globalThis.fetch, now = new Date() } = {}) {
  const { values, errors } = validateApplicant(applicant)
  if (Object.keys(errors).length) throw new AdoptionRequestError('validation', 'Vérifiez vos coordonnées avant l’envoi.', errors)
  if (!Number.isSafeInteger(animalId) || animalId <= 0) throw new AdoptionRequestError('notFound', 'Cet animal est introuvable.')
  const payload = { ...values, dateDemande: localRequestDate(now), animalId }
  let response
  try {
    response = await fetchImpl('/api/demandes', {
      method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      credentials: 'omit', cache: 'no-store', signal, body: JSON.stringify(payload),
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new AdoptionRequestError('uncertain', 'L’envoi n’a pas pu être confirmé. Vérifiez sa réception auprès du refuge avant de réessayer pour éviter une demande en double.')
  }
  // HTTP 201 confirms persistence; the response body contains no information needed by this page.
  if (response.status === 201) return { success: true }
  if (response.status === 400) throw new AdoptionRequestError('validation', 'Les coordonnées ont été refusées. Vérifiez-les avant de renvoyer la demande.')
  if (response.status === 404) throw new AdoptionRequestError('notFound', 'Cet animal n’est plus disponible dans le catalogue.')
  if (response.status === 409) throw new AdoptionRequestError('conflict', 'La demande ne peut pas être enregistrée pour le moment. Actualisez la disponibilité de l’animal avant de réessayer.')
  throw new AdoptionRequestError('uncertain', 'L’envoi n’a pas pu être confirmé. Vérifiez sa réception auprès du refuge avant de réessayer pour éviter une demande en double.')
}
