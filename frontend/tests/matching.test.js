import test from 'node:test'
import assert from 'node:assert/strict'
import { getMatchingRecommendations, hasMatchingPreferences, matchingQuery, MatchingPreferenceError, normalizeMatchingPreferences } from '../src/services/matching.js'

const first = { animal: { id: 3, nom: 'Luna', espece: 'Chien', age: 4, sexe: 'FEMELLE', statut: 'DISPONIBLE' }, score: 100, explication: 'Espece : correspond ; Age (ADULTE) : correspond ; Sexe : correspond.' }
const second = { animal: { ...first.animal, id: 4, nom: 'Nino', age: 8 }, score: 70, explication: 'Espece : correspond ; Age (ADULTE) : different ; Sexe : correspond.' }
const preferences = { espece: 'Chien', categorieAge: 'ADULTE', sexe: 'FEMELLE' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })

test('uses the real anonymous matching contract with GET, exact query names and cancellation', async () => {
  const calls = [], controller = new AbortController()
  const results = await getMatchingRecommendations({ preferences, signal: controller.signal, fetchImpl: async (...args) => { calls.push(args); return response([first, second]) } })
  assert.deepEqual(calls, [['/api/matching?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE', {
    method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal: controller.signal,
  }]])
  assert.deepEqual(results, [first, second])
})

test('normalizes French case, accents and sex aliases exactly to accepted backend values', () => {
  assert.deepEqual(normalizeMatchingPreferences({ espece: ' cHiEn ', categorieAge: ' adulte ', sexe: ' mâle ' }), { espece: 'Chien', categorieAge: 'ADULTE', sexe: 'MALE' })
  assert.equal(normalizeMatchingPreferences({ sexe: ' f ' }).sexe, 'FEMELLE')
  assert.equal(matchingQuery({ espece: 'chat' }), 'espece=Chat')
})

test('all blank preferences are omitted and an unevaluated zero is preserved without inventing a score', async () => {
  let url
  const neutral = { ...first, score: 0, explication: 'Aucune preference renseignee : score non evalue (0), ordre par identifiant.' }
  assert.equal(hasMatchingPreferences({ espece: ' ', categorieAge: '', sexe: '' }), false)
  assert.equal(matchingQuery({}), '')
  const results = await getMatchingRecommendations({ preferences: { espece: ' ' }, fetchImpl: async value => { url = value; return response([neutral]) } })
  assert.equal(url, '/api/matching')
  assert.equal(results[0].score, 0)
})

test('extra client fields cannot influence status, limit, weights or administrator identity', async () => {
  let url
  await getMatchingRecommendations({ preferences: { ...preferences, adminId: 1, statutAnimal: 'ADOPTE', limit: 10, score: 100 }, fetchImpl: async value => { url = value; return response([]) } })
  assert.equal(url, '/api/matching?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE')
})

for (const [field, value] of [['espece', 'Lapin'], ['categorieAge', 'BABY'], ['sexe', 'INCONNU'], ['espece', 1], ['sexe', {}], ['categorieAge', true], ['espece', ' '.repeat(33)], ['categorieAge', 'x'.repeat(33)], ['sexe', 'x'.repeat(33)]]) {
  test(`invalid preference ${field} ${JSON.stringify(value)} is blocked before fetching`, async () => {
    let fetched = false
    await assert.rejects(getMatchingRecommendations({ preferences: { [field]: value }, fetchImpl: async () => { fetched = true } }), error => error instanceof MatchingPreferenceError && error.field === field)
    assert.equal(fetched, false)
  })
}

test('empty results are successful and response projections drop unrelated or private fields', async () => {
  assert.deepEqual(await getMatchingRecommendations({ fetchImpl: async () => response([]) }), [])
  const extra = { ...first, token: 'not-for-display', animal: { ...first.animal, adminId: 99, etatSante: 'not-for-display', observations: 'not-for-display' } }
  assert.deepEqual(await getMatchingRecommendations({ preferences, fetchImpl: async () => response([extra]) }), [first])
})

test('server scores and explanations stay authoritative, including zero-score alternatives', async () => {
  const zero = { ...first, score: 0, explication: 'Espece : differente.' }
  assert.deepEqual(await getMatchingRecommendations({ preferences, fetchImpl: async () => response([zero]) }), [zero])
})

test('a legacy negative integer age and unrecognized sex remain unknown rather than fabricated', async () => {
  const unknown = { ...first, animal: { ...first.animal, age: -1, sexe: '' }, score: 0 }
  assert.deepEqual(await getMatchingRecommendations({ preferences, fetchImpl: async () => response([unknown]) }), [unknown])
})

const invalidResponses = [
  null, {}, [null], [first, first], [first, second, { ...second, animal: { ...second.animal, id: 5 } }, { ...second, animal: { ...second.animal, id: 6 } }],
  [{ ...first, animal: { ...first.animal, statut: 'ADOPTE' } }], [{ ...first, animal: { ...first.animal, statut: 'EN_SOIN' } }],
  [{ ...first, animal: { ...first.animal, statut: 'disponible' } }], [{ ...first, animal: { ...first.animal, id: 0 } }],
  [{ ...first, animal: { ...first.animal, age: 1.5 } }], [{ ...first, animal: { ...first.animal, sexe: null } }],
  [{ ...first, animal: { ...first.animal, nom: '' } }], [{ ...first, score: 101 }], [{ ...first, score: -1 }],
  [{ ...first, score: '100' }], [{ ...first, explication: '' }], [second, first],
  [{ ...first, score: 70 }, { ...second, animal: { ...second.animal, id: 2 } }],
]
for (const [index, data] of invalidResponses.entries()) {
  test(`rejects invalid, unavailable, duplicate or incorrectly ranked results ${index + 1}`, async () => {
    await assert.rejects(getMatchingRecommendations({ preferences, fetchImpl: async () => response(data) }), /recommandations reçues/)
  })
}

test('nonzero scores with no preferences are rejected instead of displaying invented compatibility', async () => {
  await assert.rejects(getMatchingRecommendations({ fetchImpl: async () => response([first]) }), /recommandations reçues/)
})

for (const status of [201, 204, 400, 401, 404, 405, 500, 503]) {
  test(`HTTP ${status} produces a safe error without retry or exposing server content`, async () => {
    let attempts = 0
    await assert.rejects(getMatchingRecommendations({ preferences, fetchImpl: async () => { attempts++; return new Response(status === 204 ? null : 'private diagnostic', { status }) } }), error => {
      assert.doesNotMatch(error.message, /private|diagnostic/)
      return true
    })
    assert.equal(attempts, 1)
  })
}

test('network and malformed JSON errors are readable and never expose internal data', async () => {
  await assert.rejects(getMatchingRecommendations({ fetchImpl: async () => { throw new Error('private network detail') } }), /connexion/)
  await assert.rejects(getMatchingRecommendations({ fetchImpl: async () => new Response('private malformed data') }), error => {
    assert.match(error.message, /recommandations reçues/)
    assert.doesNotMatch(error.message, /private|malformed/)
    return true
  })
})

test('cancellation propagates during both fetch and JSON parsing', async () => {
  const error = new DOMException('cancelled', 'AbortError')
  for (const fetchImpl of [async () => { throw error }, async () => ({ status: 200, json: async () => { throw error } })]) {
    await assert.rejects(getMatchingRecommendations({ fetchImpl }), failure => failure === error)
  }
})
