import assert from 'node:assert/strict'
import test from 'node:test'
import { AnimalNotFoundError, getAnimal } from '../src/services/animals.js'

const fixture = { id: 11, nom: 'Milo', espece: 'Chat', race: 'Européen', age: 2, sexe: 'Mâle', etatSante: 'Bon', statut: 'DISPONIBLE', adminId: 1 }
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

test('loads the requested detail with public GET, cancellation and no credentials', async () => {
  const controller = new AbortController()
  const calls = []
  const animal = await getAnimal('11', { signal: controller.signal, fetchImpl: async (...args) => { calls.push(args); return response(fixture) } })
  assert.deepEqual(calls, [['/api/animaux/11', { method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal: controller.signal }]])
  assert.equal(animal.nom, fixture.nom)
  assert.equal(animal.etatSante, fixture.etatSante)
  assert.equal('adminId' in animal, false)
})

for (const id of ['abc', '0', '-1', '1.5', '11/dossier-medical', '9007199254740992']) {
  test(`invalid detail ID ${id} never sends an API request`, async () => {
    let fetched = false
    await assert.rejects(getAnimal(id, { fetchImpl: async () => { fetched = true } }), AnimalNotFoundError)
    assert.equal(fetched, false)
  })
}

test('a missing animal is distinguished from a temporary server failure', async () => {
  await assert.rejects(getAnimal('11', { fetchImpl: async () => response({ private: 'diagnostic' }, 404) }), AnimalNotFoundError)
  await assert.rejects(getAnimal('11', { fetchImpl: async () => response({ private: 'diagnostic' }, 500) }), (error) => {
    assert.equal(error instanceof AnimalNotFoundError, false)
    assert.doesNotMatch(error.message, /private|diagnostic/)
    return true
  })
})

for (const body of [null, [], { ...fixture, id: 12 }, { ...fixture, age: -1 }, { ...fixture, race: {} }]) {
  test(`invalid or mismatched detail data ${JSON.stringify(body)} is rejected`, async () => {
    await assert.rejects(getAnimal('11', { fetchImpl: async () => response(body) }), /informations des animaux/)
  })
}

test('unknown optional details remain unknown without invented content', async () => {
  const animal = await getAnimal('11', { fetchImpl: async () => response({ id: 11, nom: 'Milo', espece: 'Chat' }) })
  assert.deepEqual(animal, { id: 11, nom: 'Milo', espece: 'Chat', race: null, age: null, sexe: null, etatSante: null, statut: null })
})

test('detail network and JSON failures use safe messages', async () => {
  await assert.rejects(getAnimal('11', { fetchImpl: async () => { throw new Error('private diagnostic') } }), /connexion/)
  await assert.rejects(getAnimal('11', { fetchImpl: async () => new Response('private malformed data') }), (error) => {
    assert.doesNotMatch(error.message, /private|malformed/)
    return true
  })
})

test('detail fetch and JSON parsing preserve AbortError', async () => {
  const aborted = new DOMException('cancelled', 'AbortError')
  for (const fetchImpl of [async () => { throw aborted }, async () => ({ ok: true, json: async () => { throw aborted } })]) {
    await assert.rejects(getAnimal('11', { fetchImpl }), (error) => error === aborted)
  }
})
