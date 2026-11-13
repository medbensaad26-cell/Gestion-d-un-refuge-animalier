import assert from 'node:assert/strict'
import test from 'node:test'
import { getAnimals } from '../src/services/animals.js'

const animalFixture = {
  id: 11,
  nom: 'Milo',
  espece: 'Chat',
  race: 'Européen',
  age: 2,
  sexe: 'Mâle',
  etatSante: 'Bon',
  statut: 'DISPONIBLE',
  adminId: 1,
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

test('requests the relative public endpoint with GET and no authentication', async () => {
  const controller = new AbortController()
  const calls = []
  const fetchImpl = async (...arguments_) => {
    calls.push(arguments_)
    return jsonResponse([animalFixture])
  }

  const animals = await getAnimals({ signal: controller.signal, fetchImpl })

  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], '/api/animaux')
  assert.deepEqual(calls[0][1], {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'omit',
    cache: 'no-store',
    signal: controller.signal,
  })
  assert.equal(animals.length, 1)
  assert.equal(animals[0].nom, animalFixture.nom)
  assert.equal(animals[0].age, animalFixture.age)
  assert.equal(animals[0].statut, animalFixture.statut)
  assert.equal('adminId' in animals[0], false)
  assert.notEqual(animals[0], animalFixture)
})

test('returns an empty array without substituting demonstration animals', async () => {
  assert.deepEqual(await getAnimals({ fetchImpl: async () => jsonResponse([]) }), [])
})

test('keeps missing optional animal information unknown', async () => {
  const animals = await getAnimals({
    fetchImpl: async () => jsonResponse([{ id: 4, nom: 'Luna', espece: 'Chien' }]),
  })

  assert.deepEqual(animals, [{
    id: 4,
    nom: 'Luna',
    espece: 'Chien',
    race: null,
    age: null,
    sexe: null,
    etatSante: null,
    statut: null,
  }])
})

for (const status of [404, 500, 503]) {
  test(`shows a friendly message for HTTP ${status} without exposing response details`, async () => {
    await assert.rejects(
      getAnimals({ fetchImpl: async () => new Response('private server diagnostic', { status }) }),
      (error) => {
        assert.match(error.message, /Réessayez/)
        assert.doesNotMatch(error.message, /private|diagnostic|404|500|503/)
        return true
      },
    )
  })
}

test('network failures produce a connection message without low-level details', async () => {
  await assert.rejects(
    getAnimals({ fetchImpl: async () => { throw new TypeError('internal network diagnostic') } }),
    (error) => {
      assert.match(error.message, /connexion/)
      assert.doesNotMatch(error.message, /internal|diagnostic/)
      return true
    },
  )
})

test('rejects malformed JSON without exposing its content', async () => {
  await assert.rejects(
    getAnimals({ fetchImpl: async () => new Response('{invalid private response') }),
    (error) => {
      assert.match(error.message, /informations des animaux/)
      assert.doesNotMatch(error.message, /invalid private response/)
      return true
    },
  )
})

for (const body of [null, { animaux: [] }, [null], [{ ...animalFixture, id: -1 }],
  [{ ...animalFixture, nom: '' }], [{ ...animalFixture, age: 1.5 }],
  [{ ...animalFixture, espece: {} }], [{ ...animalFixture, race: {} }]]) {
  test(`rejects incompatible catalogue data ${JSON.stringify(body)}`, async () => {
    await assert.rejects(
      getAnimals({ fetchImpl: async () => jsonResponse(body) }),
      /informations des animaux/,
    )
  })
}

test('preserves fetch cancellation so an unmounted page does not show an error', async () => {
  const aborted = new DOMException('Cancelled by the page', 'AbortError')
  await assert.rejects(
    getAnimals({ fetchImpl: async () => { throw aborted } }),
    (error) => error === aborted,
  )
})

test('preserves cancellation while reading JSON', async () => {
  const aborted = new DOMException('Cancelled while reading', 'AbortError')
  await assert.rejects(
    getAnimals({ fetchImpl: async () => ({ ok: true, json: async () => { throw aborted } }) }),
    (error) => error === aborted,
  )
})
