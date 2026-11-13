import test from 'node:test'
import assert from 'node:assert/strict'
import { AdminSessionExpiredError } from '../src/services/adminAuth.js'
import { AdminMedicalError, validMedicalDate, validateMedical, medicalToday, medicalDateLabel, getAdminMedical, saveAdminMedical } from '../src/services/adminMedical.js'

const token = 'x'.repeat(43)
const record = { id: 501, animalId: 12, vaccins: 'Vaccin de test\nRappel à prévoir', traitements: null, observations: '  Texte existant\n\nDeuxième ligne  ', dateMiseAJour: '2026-11-08' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })

test('medical validation preserves whitespace and line breaks, clearing only empty text to null', () => {
  assert.deepEqual(validateMedical({ ...record, traitements: '', adminId: 999 }), { values: { vaccins: record.vaccins, traitements: null, observations: record.observations, dateMiseAJour: record.dateMiseAJour }, errors: {} })
  assert.equal(validateMedical({ ...record, observations: '   ' }).values.observations, '   ')
})

test('all medical texts may be empty but the update date is mandatory', () => {
  assert.deepEqual(validateMedical({ dateMiseAJour: '2026-11-08' }), { values: { vaccins: null, traitements: null, observations: null, dateMiseAJour: '2026-11-08' }, errors: {} })
  assert.ok(validateMedical({}).errors.dateMiseAJour)
})

for (const value of ['', null, '0000-01-01', '0999-12-31', '+10000-01-01', '2026-02-29', '2026-02-30', '2026-04-31', '2026-13-01', '2026-1-01', '2026-11-08T00:00:00Z']) {
  test(`invalid update date never submits: ${value}`, async () => {
    assert.equal(validMedicalDate(value), false)
    await assert.rejects(saveAdminMedical(token, 12, { ...record, dateMiseAJour: value }, { fetchImpl() { assert.fail('must not fetch') } }), error => Boolean(error.fields.dateMiseAJour))
  })
}

test('date limits and leap days are valid; local today and display avoid timezone shifts', () => {
  for (const value of ['1000-01-01', '9999-12-31', '2024-02-29']) assert.equal(validMedicalDate(value), true)
  assert.equal(medicalToday(new Date(2026, 10, 8, 23, 59)), '2026-11-08')
  assert.equal(medicalDateLabel('2026-11-08'), '8 novembre 2026')
  assert.equal(medicalDateLabel(null), 'Non renseignée')
})

test('UTF-8 byte limits enforce MySQL TEXT capacity for every field', () => {
  for (const key of ['vaccins', 'traitements', 'observations']) {
    assert.deepEqual(validateMedical({ ...record, [key]: 'x'.repeat(65535) }).errors, {})
    assert.ok(validateMedical({ ...record, [key]: 'x'.repeat(65536) }).errors[key])
    assert.deepEqual(validateMedical({ ...record, [key]: '🐾'.repeat(16383) + 'abc' }).errors, {})
    assert.ok(validateMedical({ ...record, [key]: '🐾'.repeat(16384) }).errors[key])
    assert.ok(validateMedical({ ...record, [key]: 'é'.repeat(32768) }).errors[key])
    assert.ok(validateMedical({ ...record, [key]: true }).errors[key])
  }
})

test('private GET targets the animal, sends bearer without cookies/cache and projects only record fields', async () => {
  assert.deepEqual(await getAdminMedical(token, 12, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/animaux/12/dossier-medical')
    assert.equal(options.method, 'GET')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.body, undefined)
    return response({ ...record, adminId: 999, password: 'must not escape' })
  } }), record)
})

test('empty automatic medical record and legacy null date remain empty on read', async () => {
  const blank = { ...record, vaccins: null, traitements: null, observations: null, dateMiseAJour: null }
  assert.deepEqual(await getAdminMedical(token, 12, { fetchImpl: async () => response(blank) }), blank)
  assert.deepEqual(await getAdminMedical(token, 12, { fetchImpl: async () => response({ id: record.id, animalId: record.animalId }) }), blank)
})

for (const data of [null, [], { ...record, id: 0 }, { ...record, animalId: 13 }, { ...record, animalId: '12' }, { ...record, vaccins: {} }, { ...record, dateMiseAJour: '2026-02-30' }]) {
  test(`malformed or wrong-animal record cannot be displayed: ${JSON.stringify(data).slice(0, 65)}`, async () => {
    await assert.rejects(getAdminMedical(token, 12, { fetchImpl: async () => response(data) }), error => error.kind === 'invalid')
  })
}

test('invalid animal IDs never issue medical reads or writes', async () => {
  const options = { fetchImpl() { assert.fail('must not fetch') } }
  for (const id of ['0', '-1', '01', '12/extra', Number.MAX_SAFE_INTEGER + 1]) {
    await assert.rejects(getAdminMedical(token, id, options), error => error.kind === 'notFound')
    await assert.rejects(saveAdminMedical(token, id, record, options), error => error.kind === 'notFound')
  }
})

test('update uses PUT while only an explicit create uses POST; route identifies the animal', async () => {
  for (const creating of [false, true]) {
    let calls = 0
    assert.deepEqual(await saveAdminMedical(token, 12, { ...record, animalId: 999, adminId: 99 }, { creating, fetchImpl: async (url, options) => {
      calls++
      assert.equal(url, '/api/animaux/12/dossier-medical')
      assert.equal(options.method, creating ? 'POST' : 'PUT')
      assert.equal(options.headers['Content-Type'], 'application/json')
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.credentials, 'omit')
      assert.equal(options.cache, 'no-store')
      assert.deepEqual(JSON.parse(options.body), validateMedical(record).values)
      return response(record, creating ? 201 : 200)
    } }), record)
    assert.equal(calls, 1)
  }
})

test('HTTP401 expires the session for reads and writes, and invalid local tokens never fetch', async () => {
  await assert.rejects(getAdminMedical(token, 12, { fetchImpl: async () => response({}, 401) }), AdminSessionExpiredError)
  await assert.rejects(saveAdminMedical(token, 12, record, { fetchImpl: async () => response({}, 401) }), AdminSessionExpiredError)
  await assert.rejects(getAdminMedical('invalid', 12, { fetchImpl() { assert.fail('must not fetch') } }), AdminSessionExpiredError)
})

test('HTTP400/404/409 give useful generic errors and never silently create on failed PUT', async () => {
  for (const [status, kind] of [[400, 'validation'], [404, 'notFound'], [409, 'conflict']]) {
    let calls = 0
    await assert.rejects(saveAdminMedical(token, 12, record, { fetchImpl: async (_url, options) => { calls++; assert.equal(options.method, 'PUT'); return response({ erreur: 'PRIVATE SQL' }, status) } }), error => error.kind === kind && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
})

test('unknown or malformed write outcomes block retry pending a read and never retry automatically', async () => {
  for (const outcome of ['network', 'invalid-json', 'wrong-animal', 500, 202, 201]) {
    let calls = 0
    await assert.rejects(saveAdminMedical(token, 12, record, { fetchImpl: async () => {
      calls++
      if (outcome === 'network') throw Error('PRIVATE HOST')
      if (outcome === 'invalid-json') return new Response('{broken', { status: 200 })
      if (outcome === 'wrong-animal') return response({ ...record, animalId: 99 })
      return response({}, outcome)
    } }), error => error.kind === 'uncertain' && error.message.includes('Rechargez le dossier') && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
})

test('read failures are generic and aborts propagate through fetch and JSON parsing', async () => {
  await assert.rejects(getAdminMedical(token, 12, { fetchImpl: async () => response({}, 500) }), error => error.kind === 'server')
  await assert.rejects(getAdminMedical(token, 12, { fetchImpl: async () => { throw Error('PRIVATE HOST') } }), error => error.kind === 'network' && !error.message.includes('PRIVATE'))
  const abort = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(getAdminMedical(token, 12, { fetchImpl: async () => { throw abort } }), error => error === abort)
  await assert.rejects(saveAdminMedical(token, 12, record, { fetchImpl: async () => ({ status: 200, json: async () => { throw abort } }) }), error => error === abort)
  assert.ok(new AdminMedicalError('validation', 'test') instanceof Error)
})
