import test from 'node:test'
import assert from 'node:assert/strict'
import { AdminSessionExpiredError } from '../src/services/adminAuth.js'
import { AdminAnnouncementError, validateAnnouncement, announcementToday, getAdminAnnouncements, getAdminAnnouncement, saveAdminAnnouncement } from '../src/services/adminAnnouncements.js'

const token = 'x'.repeat(43)
const record = { id: 21, titre: 'Une famille pour Milo', description: 'Un chat calme.\nUne nouvelle histoire.', datePublication: '2026-11-08', statut: 'INTERNE', animalId: 12 }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })

test('validation sends only announcement fields, trims outer spaces and preserves internal line breaks', () => {
  assert.deepEqual(validateAnnouncement({ ...record, titre: ' Un titre ', description: '  Ligne 1\nLigne 2  ', animalId: '12', adminId: 999 }).values, { titre: 'Un titre', description: 'Ligne 1\nLigne 2', datePublication: record.datePublication, statut: 'INTERNE', animalId: 12 })
  assert.deepEqual(validateAnnouncement(record).errors, {})
  assert.equal(validateAnnouncement({ ...record, description: ' ' }).values.description, null)
  assert.deepEqual(Object.keys(validateAnnouncement({}).errors).sort(), ['animalId', 'datePublication', 'statut', 'titre'])
})

test('title counts Unicode code points; description enforces MySQL UTF-8 TEXT limit', () => {
  assert.deepEqual(validateAnnouncement({ ...record, titre: '🐾'.repeat(150), description: '🐾'.repeat(16383) + 'abc' }).errors, {})
  assert.ok(validateAnnouncement({ ...record, titre: '🐾'.repeat(151) }).errors.titre)
  assert.ok(validateAnnouncement({ ...record, description: '🐾'.repeat(16384) }).errors.description)
  assert.ok(validateAnnouncement({ ...record, description: {} }).errors.description)
})

test('publication is explicit, and legacy statuses never become public through whitespace normalization', () => {
  assert.deepEqual(validateAnnouncement({ ...record, statut: 'PUBLIEE' }).errors, {})
  assert.ok(validateAnnouncement({ ...record, statut: ' publiee ' }).errors.statut)
  assert.ok(validateAnnouncement({ ...record, statut: ' PUBLIEE ' }, { originalStatus: ' PUBLIEE ' }).errors.statut)
  assert.ok(validateAnnouncement({ ...record, statut: '\u0000PUBLIEE\u0000' }, { originalStatus: '\u0000PUBLIEE\u0000' }).errors.statut)
  assert.deepEqual(validateAnnouncement({ ...record, statut: 'ARCHIVEE' }, { originalStatus: 'ARCHIVEE' }).errors, {})
})

for (const date of ['', '0000-01-01', '0999-12-31', '+10000-01-01', '2026-02-29', '2026-04-31', '2026-1-01']) {
  test(`invalid publication date never submits: ${date}`, async () => {
    await assert.rejects(saveAdminAnnouncement(token, null, { ...record, datePublication: date }, { fetchImpl() { assert.fail('must not fetch') } }), error => Boolean(error.fields.datePublication))
  })
}

test('valid date limits and local today have no timezone shift', () => {
  for (const datePublication of ['1000-01-01', '9999-12-31', '2024-02-29']) assert.deepEqual(validateAnnouncement({ ...record, datePublication }).errors, {})
  assert.equal(announcementToday(new Date(2026, 10, 8, 23, 59)), '2026-11-08')
})

test('animal selection and announcement route IDs must be positive safe integers', async () => {
  const options = { fetchImpl() { assert.fail('must not fetch') } }
  for (const id of ['', '0', '-1', '01', '12/extra', Number.MAX_SAFE_INTEGER + 1]) {
    assert.ok(validateAnnouncement({ ...record, animalId: id }).errors.animalId)
    await assert.rejects(getAdminAnnouncement(token, id, options), error => error.kind === 'notFound')
    await assert.rejects(saveAdminAnnouncement(token, id, record, options), error => error.kind === 'notFound')
  }
})

test('administrator list includes all statuses, drops attribution, and sorts recent first', async () => {
  const published = { ...record, id: 22, statut: 'PUBLIEE' }
  assert.deepEqual(await getAdminAnnouncements(token, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/annonces')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    return response([{ ...record, adminId: 999 }, published])
  } }), [published, record])
  assert.deepEqual(await getAdminAnnouncements(token, { fetchImpl: async () => response([]) }), [])
})

for (const data of [null, {}, [record, record], [{ ...record, animalId: '12' }], [{ ...record, titre: '' }], [{ ...record, datePublication: '2026-02-30' }]]) {
  test(`malformed announcement list is rejected: ${JSON.stringify(data).slice(0, 50)}`, async () => {
    await assert.rejects(getAdminAnnouncements(token, { fetchImpl: async () => response(data) }), AdminAnnouncementError)
  })
}

test('detail accepts private status/omitted description and rejects a mismatched ID', async () => {
  const { description: _description, ...withoutDescription } = record
  assert.deepEqual(await getAdminAnnouncement(token, 21, { fetchImpl: async () => response(withoutDescription) }), { ...record, description: null })
  await assert.rejects(getAdminAnnouncement(token, 22, { fetchImpl: async () => response(record) }), error => error.kind === 'invalid')
})

test('create uses POST201, update uses PUT200, and identity always comes from route/session', async () => {
  for (const id of [null, 21]) {
    let calls = 0
    const result = await saveAdminAnnouncement(token, id, { ...record, adminId: 999 }, { fetchImpl: async (url, options) => {
      calls++
      assert.equal(url, id == null ? '/api/annonces' : '/api/annonces/21')
      assert.equal(options.method, id == null ? 'POST' : 'PUT')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.deepEqual(JSON.parse(options.body), validateAnnouncement(record).values)
      return response(id == null ? { message: 'Annonce ajoutee' } : record, id == null ? 201 : 200)
    } })
    assert.deepEqual(result, id == null ? { created: true } : record)
    assert.equal(calls, 1)
  }
})

test('all admin operations expire on HTTP401 and forged local tokens never fetch', async () => {
  const options = { fetchImpl: async () => response({}, 401) }
  for (const operation of [() => getAdminAnnouncements(token, options), () => getAdminAnnouncement(token, 21, options), () => saveAdminAnnouncement(token, null, record, options)]) await assert.rejects(operation(), AdminSessionExpiredError)
  await assert.rejects(getAdminAnnouncements('forged', { fetchImpl() { assert.fail('must not fetch') } }), AdminSessionExpiredError)
})

test('400/404/409 keep useful errors without exposing server content or converting PUT to POST', async () => {
  for (const [status, kind] of [[400, 'validation'], [404, 'notFound'], [409, 'conflict']]) {
    let calls = 0
    await assert.rejects(saveAdminAnnouncement(token, 21, record, { fetchImpl: async (_url, options) => { calls++; assert.equal(options.method, 'PUT'); return response({ erreur: 'PRIVATE SQL' }, status) } }), error => error.kind === kind && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
})

test('lost/unexpected write responses never retry or claim a confirmed publication', async () => {
  for (const id of [null, 21]) for (const outcome of ['network', 500, 202]) {
    let calls = 0
    await assert.rejects(saveAdminAnnouncement(token, id, record, { fetchImpl: async () => { calls++; if (outcome === 'network') throw Error('PRIVATE HOST'); return response({}, outcome) } }), error => error.kind === 'uncertain' && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
  for (const data of [{ ...record, id: 99 }, { ...record, animalId: 99 }, { ...record, statut: 'PUBLIEE' }, null]) await assert.rejects(saveAdminAnnouncement(token, 21, record, { fetchImpl: async () => response(data) }), error => error.kind === 'uncertain')
})

test('read network/JSON errors stay generic and fetch/JSON aborts propagate', async () => {
  await assert.rejects(getAdminAnnouncements(token, { fetchImpl: async () => { throw Error('PRIVATE HOST') } }), error => error.kind === 'network' && !error.message.includes('PRIVATE'))
  await assert.rejects(getAdminAnnouncement(token, 21, { fetchImpl: async () => new Response('{broken', { status: 200 }) }), error => error.kind === 'invalid')
  const abort = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(saveAdminAnnouncement(token, null, record, { fetchImpl: async () => { throw abort } }), error => error === abort)
  await assert.rejects(getAdminAnnouncement(token, 21, { fetchImpl: async () => ({ status: 200, json: async () => { throw abort } }) }), error => error === abort)
})
