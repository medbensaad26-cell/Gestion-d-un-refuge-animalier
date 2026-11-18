import test from 'node:test'
import assert from 'node:assert/strict'
import { getAnimal, getAnimals } from '../src/services/animals.js'
import { AdminSessionExpiredError } from '../src/services/adminAuth.js'
import { AdminAnimalError, ANIMAL_IMAGE_MAX_BYTES, deleteAnimalImage, getAdminAnimal, saveAdminAnimal, uploadAnimalImage, validateAnimalImage } from '../src/services/adminAnimals.js'

const token = 'x'.repeat(43)
const animal = { id: 12, nom: 'Milo', espece: 'Chat', race: null, age: 2, sexe: 'MALE', etatSante: null, statut: 'DISPONIBLE' }
const json = (data, status = 200) => new Response(JSON.stringify(data), { status })
const photo = (type = 'image/png', size = 4) => new File([new Uint8Array(size)], 'photo', { type })

test('public animals keep their own image route', async () => {
  const imageUrl = '/api/animaux/12/image?v=1763481234567'
  assert.deepEqual(await getAnimals({ fetchImpl: async () => json([{ ...animal, imageUrl }]) }), [{ ...animal, imageUrl }])
  assert.equal((await getAnimal(12, { fetchImpl: async () => json({ ...animal, imageUrl: '/api/animaux/12/image' }) })).imageUrl, '/api/animaux/12/image')
})

test('animals without a photo keep exactly the previous shape', async () => {
  assert.deepEqual(await getAnimals({ fetchImpl: async () => json([animal]) }), [animal])
  assert.equal('imageUrl' in (await getAnimal(12, { fetchImpl: async () => json(animal) })), false)
})

for (const imageUrl of ['https://example.test/a.png', 'javascript:alert(1)', '/api/animaux/9/image', '/api/animaux/12/image?v=x', '//example.test/api/animaux/12/image', 42, null]) {
  test(`unsafe image value is ignored without hiding the animal: ${imageUrl}`, async () => {
    assert.deepEqual(await getAnimals({ fetchImpl: async () => json([{ ...animal, imageUrl }]) }), [animal])
  })
}

test('admin records keep a valid image route', async () => {
  const imageUrl = '/api/animaux/12/image?v=5'
  assert.equal((await getAdminAnimal(token, 12, { fetchImpl: async () => json({ ...animal, imageUrl }) })).imageUrl, imageUrl)
})

test('only supported, non-empty images within the limit are accepted', () => {
  for (const type of ['image/jpeg', 'image/png', 'image/webp']) assert.equal(validateAnimalImage(photo(type)), '')
  for (const type of ['image/svg+xml', 'image/gif', 'text/html', '']) assert.ok(validateAnimalImage(photo(type)))
  assert.ok(validateAnimalImage(null))
  assert.ok(validateAnimalImage(photo('image/png', 0)))
  assert.equal(validateAnimalImage({ type: 'image/png', size: ANIMAL_IMAGE_MAX_BYTES }), '')
  assert.ok(validateAnimalImage({ type: 'image/png', size: ANIMAL_IMAGE_MAX_BYTES + 1 }))
})

test('creating an animal returns its new ID when the server provides one', async () => {
  assert.deepEqual(await saveAdminAnimal(token, null, animal, { fetchImpl: async () => json({ message: 'Animal ajoute', id: 31 }, 201) }), { success: true, id: 31 })
  for (const body of [{ id: '31' }, { id: 0 }, {}]) assert.deepEqual(await saveAdminAnimal(token, null, animal, { fetchImpl: async () => json(body, 201) }), { success: true })
})

test('upload sends the raw file with its type and the administrator session', async () => {
  const file = photo('image/webp')
  assert.deepEqual(await uploadAnimalImage(token, 12, file, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/animaux/12/image')
    assert.equal(options.method, 'PUT')
    assert.equal(options.body, file)
    assert.equal(options.headers['Content-Type'], 'image/webp')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    return json({ imageUrl: '/api/animaux/12/image?v=1' })
  } }), { success: true })
})

test('removal targets only the selected animal and sends no body', async () => {
  assert.deepEqual(await deleteAnimalImage(token, 12, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/animaux/12/image')
    assert.equal(options.method, 'DELETE')
    assert.equal(options.body, undefined)
    assert.equal(options.headers['Content-Type'], undefined)
    return json({})
  } }), { success: true })
})

test('invalid files, IDs and tokens never reach the server', async () => {
  const fetchImpl = () => assert.fail('must not fetch')
  await assert.rejects(uploadAnimalImage(token, 12, photo('image/svg+xml'), { fetchImpl }), error => error.kind === 'validation')
  await assert.rejects(uploadAnimalImage(token, '1/../2', photo(), { fetchImpl }), error => error.kind === 'notFound')
  await assert.rejects(uploadAnimalImage('forged', 12, photo(), { fetchImpl }), AdminSessionExpiredError)
})

test('server outcomes become clear messages without exposing response content', async () => {
  for (const [status, kind] of [[400, 'validation'], [413, 'validation'], [415, 'validation'], [404, 'notFound'], [500, 'uncertain']]) {
    await assert.rejects(uploadAnimalImage(token, 12, photo(), { fetchImpl: async () => json({ erreur: 'PRIVATE PATH' }, status) }), error => error instanceof AdminAnimalError && error.kind === kind && !error.message.includes('PRIVATE'))
  }
  await assert.rejects(uploadAnimalImage(token, 12, photo(), { fetchImpl: async () => json({}, 401) }), AdminSessionExpiredError)
  await assert.rejects(deleteAnimalImage(token, 12, { fetchImpl: async () => { throw Error('PRIVATE HOST') } }), error => error.kind === 'uncertain' && !error.message.includes('PRIVATE'))
  const abort = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(uploadAnimalImage(token, 12, photo(), { fetchImpl: async () => { throw abort } }), error => error === abort)
})
