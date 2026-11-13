import test from 'node:test'
import assert from 'node:assert/strict'
import { AdminSessionExpiredError } from '../src/services/adminAuth.js'
import { AdminAnimalError, animalQuery, animalFiltersFromSearchParams, validateAnimal, getAdminAnimals, getAdminAnimal, saveAdminAnimal, deleteAdminAnimal } from '../src/services/adminAnimals.js'

const token = 'x'.repeat(43)
const animal = { id: 12, nom: 'Milo', espece: 'Chat', race: null, age: 0, sexe: 'MALE', etatSante: null, statut: 'DISPONIBLE' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })

test('validation projects seven fields, trims strings and preserves zero/null optionals', () => {
  assert.deepEqual(validateAnimal({ ...animal, nom: ' Milo ', age: '0', race: ' ', etatSante: '', adminId: 999 }), { values: { nom: 'Milo', espece: 'Chat', race: null, age: 0, sexe: 'MALE', etatSante: null, statut: 'DISPONIBLE' }, errors: {} })
})

test('required fields and integer age have field-specific errors', () => {
  assert.deepEqual(Object.keys(validateAnimal({}).errors).sort(), ['age', 'espece', 'nom', 'sexe', 'statut'])
})

for (const age of ['', ' ', -1, '1.5', '1e2', '0x10', 2147483648, null, true]) {
  test(`invalid age is rejected without a request: ${JSON.stringify(age)}`, async () => {
    await assert.rejects(saveAdminAnimal(token, null, { ...animal, age }, { fetchImpl() { assert.fail('must not fetch') } }), error => error instanceof AdminAnimalError && Boolean(error.fields.age))
  })
}

test('Unicode length limits count code points and optional fields also enforce backend limits', () => {
  assert.deepEqual(validateAnimal({ ...animal, nom: '🐾'.repeat(100) }).errors, {})
  for (const [key, max] of [['nom', 100], ['espece', 100], ['race', 100], ['sexe', 20], ['etatSante', 255]]) {
    assert.ok(validateAnimal({ ...animal, [key]: '🐾'.repeat(max + 1) }).errors[key])
  }
})

test('adoption can neither be manufactured nor undone through this form', async () => {
  assert.ok(validateAnimal({ ...animal, statut: 'ADOPTE' }).errors.statut)
  assert.ok(validateAnimal(animal, { originalStatus: 'ADOPTE' }).errors.statut)
  assert.deepEqual(validateAnimal({ ...animal, statut: 'ADOPTE' }, { originalStatus: ' adopte ' }).errors, {})
  await assert.rejects(saveAdminAnimal(token, 12, { ...animal, statut: 'ADOPTE' }, { originalStatus: 'DISPONIBLE', fetchImpl() { assert.fail('must not fetch') } }), error => Boolean(error.fields.statut))
})

test('search combines criteria with URL encoding, excludes unknown and duplicate URL parameters', () => {
  assert.equal(animalQuery({ q: ' Milo & Rex ', espece: ' Chat ', statutAnimal: 'en_soin' }), 'q=Milo+%26+Rex&espece=Chat&statutAnimal=EN_SOIN')
  for (const query of ['q=a&q=b', 'adminId=1', 'statutAnimal=INVALID']) assert.throws(() => animalFiltersFromSearchParams(new URLSearchParams(query)))
})

test('authenticated list search sends bearer without cookies or caching and drops attribution', async () => {
  assert.deepEqual(await getAdminAnimals(token, { filters: { q: 'Milo', espece: 'Chat', statutAnimal: 'DISPONIBLE' }, fetchImpl: async (url, options) => {
    assert.equal(url, '/api/animaux?q=Milo&espece=Chat&statutAnimal=DISPONIBLE')
    assert.equal(options.method, 'GET')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    return response([{ ...animal, adminId: 999, dossierMedical: { private: 'data' } }])
  } }), [animal])
})

test('empty animal list is valid and legacy unknown statuses remain visible', async () => {
  assert.deepEqual(await getAdminAnimals(token, { fetchImpl: async () => response([]) }), [])
  const legacy = { ...animal, statut: 'ANCIEN', sexe: null }
  assert.deepEqual(await getAdminAnimal(token, 12, { fetchImpl: async () => response(legacy) }), legacy)
})

for (const data of [null, {}, [animal, animal], [{ ...animal, age: -1 }], [{ ...animal, nom: '' }], [{ ...animal, id: '12' }]]) {
  test(`malformed list response is rejected: ${JSON.stringify(data)}`, async () => {
    await assert.rejects(getAdminAnimals(token, { fetchImpl: async () => response(data) }), AdminAnimalError)
  })
}

test('detail rejects a mismatched record instead of editing the wrong animal', async () => {
  await assert.rejects(getAdminAnimal(token, 13, { fetchImpl: async () => response(animal) }), error => error.kind === 'invalid')
})

test('invalid route IDs do not issue requests for detail, edit or delete', async () => {
  const options = { fetchImpl() { assert.fail('must not fetch') } }
  for (const id of ['0', '-1', '12/extra', '1e2', '01', Number.MAX_SAFE_INTEGER + 1]) {
    await assert.rejects(getAdminAnimal(token, id, options), error => error.kind === 'notFound')
    await assert.rejects(saveAdminAnimal(token, id, animal, options), error => error.kind === 'notFound')
    await assert.rejects(deleteAdminAnimal(token, id, options), error => error.kind === 'notFound')
  }
})

test('create and edit use the correct method and never submit identity/admin/medical fields', async () => {
  for (const id of [null, 12]) {
    let calls = 0
    assert.deepEqual(await saveAdminAnimal(token, id, { ...animal, adminId: 999, dossierMedical: {} }, { fetchImpl: async (url, options) => {
      calls++
      assert.equal(url, id == null ? '/api/animaux' : '/api/animaux/12')
      assert.equal(options.method, id == null ? 'POST' : 'PUT')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.headers['Content-Type'], 'application/json')
      assert.deepEqual(JSON.parse(options.body), validateAnimal(animal).values)
      return response({}, id == null ? 201 : 200)
    } }), { success: true })
    assert.equal(calls, 1)
  }
})

test('delete targets only the selected ID with no body and confirms only HTTP200', async () => {
  assert.deepEqual(await deleteAdminAnimal(token, 12, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/animaux/12')
    assert.equal(options.method, 'DELETE')
    assert.equal(options.body, undefined)
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    return response({})
  } }), { success: true })
})

test('all admin operations turn HTTP401 into session expiry', async () => {
  const options = { fetchImpl: async () => response({}, 401) }
  for (const operation of [() => getAdminAnimals(token, options), () => getAdminAnimal(token, 12, options), () => saveAdminAnimal(token, 12, animal, options), () => deleteAdminAnimal(token, 12, options)]) await assert.rejects(operation(), AdminSessionExpiredError)
  await assert.rejects(getAdminAnimals('forged', { fetchImpl() { assert.fail('must not fetch') } }), AdminSessionExpiredError)
})

test('server validation/not-found/conflict statuses remain useful without leaking response content', async () => {
  for (const [status, kind] of [[400, 'validation'], [404, 'notFound'], [409, 'conflict']]) {
    await assert.rejects(deleteAdminAnimal(token, 12, { fetchImpl: async () => response({ erreur: 'PRIVATE SQL' }, status) }), error => error.kind === kind && !error.message.includes('PRIVATE'))
  }
})

test('unknown write outcomes never retry automatically or claim failure before checking persistence', async () => {
  for (const operation of [options => saveAdminAnimal(token, null, animal, options), options => saveAdminAnimal(token, 12, animal, options), options => deleteAdminAnimal(token, 12, options)]) {
    for (const outcome of ['network', 500, 202]) {
      let calls = 0
      await assert.rejects(operation({ fetchImpl: async () => { calls++; if (outcome === 'network') throw Error('PRIVATE HOST'); return response({}, outcome) } }), error => error.kind === 'uncertain' && error.message.includes('Vérifiez la liste'))
      assert.equal(calls, 1)
    }
  }
})

test('read network/server/JSON failures stay generic and aborts propagate', async () => {
  await assert.rejects(getAdminAnimals(token, { fetchImpl: async () => { throw Error('PRIVATE HOST') } }), error => error.kind === 'network' && !error.message.includes('PRIVATE'))
  await assert.rejects(getAdminAnimals(token, { fetchImpl: async () => response({}, 500) }), error => error.kind === 'server')
  await assert.rejects(getAdminAnimal(token, 12, { fetchImpl: async () => new Response('{broken', { status: 200 }) }), error => error.kind === 'invalid')
  const abort = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(saveAdminAnimal(token, null, animal, { fetchImpl: async () => { throw abort } }), error => error === abort)
})
