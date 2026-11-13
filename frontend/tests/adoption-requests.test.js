import assert from 'node:assert/strict'
import test from 'node:test'
import { AdoptionRequestError, localRequestDate, submitAdoptionRequest, validateApplicant } from '../src/services/adoptionRequests.js'

const applicant = { nomDemandeur: '  Test Applicant  ', email: ' test@example.test ', telephone: ' 01 02 03 04 05 ' }
const now = new Date(2026, 10, 8, 0, 15)

test('submits only applicant fields, animal ID and local date without authentication', async () => {
  const calls = []
  const controller = new AbortController()
  const result = await submitAdoptionRequest(11, { ...applicant, adminId: 99, statut: 'ACCEPTEE', dateDemande: '2000-01-01' }, {
    now, signal: controller.signal, fetchImpl: async (...args) => { calls.push(args); return new Response('{"message":"Demande ajoutee"}', { status: 201 }) },
  })
  assert.deepEqual(result, { success: true })
  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], '/api/demandes')
  const request = calls[0][1]
  assert.equal(request.method, 'POST')
  assert.equal(request.credentials, 'omit')
  assert.equal(request.cache, 'no-store')
  assert.equal(request.signal, controller.signal)
  assert.deepEqual(request.headers, { Accept: 'application/json', 'Content-Type': 'application/json' })
  assert.deepEqual(JSON.parse(request.body), { nomDemandeur: 'Test Applicant', email: 'test@example.test', telephone: '01 02 03 04 05', dateDemande: '2026-11-08', animalId: 11 })
})

test('201 is authoritative even without a JSON confirmation body', async () => {
  assert.deepEqual(await submitAdoptionRequest(11, applicant, { now, fetchImpl: async () => new Response('unneeded body', { status: 201 }) }), { success: true })
})

for (const [key, value] of [['nomDemandeur', ' '], ['nomDemandeur', 'x'.repeat(151)], ['email', 'not-an-email'], ['email', 'x'.repeat(245) + '@example.test'], ['telephone', ''], ['telephone', '1'.repeat(31)]]) {
  test(`invalid applicant ${key} is rejected without POST`, async () => {
    let fetched = false
    await assert.rejects(submitAdoptionRequest(11, { ...applicant, [key]: value }, { now, fetchImpl: async () => { fetched = true } }), (error) => error instanceof AdoptionRequestError && Boolean(error.fields[key]))
    assert.equal(fetched, false)
  })
}

test('non-string fields are rejected and unicode limits count characters rather than UTF-16 units', () => {
  assert.equal(Object.keys(validateApplicant({ nomDemandeur: {}, email: null, telephone: 123 }).errors).length, 3)
  assert.deepEqual(validateApplicant({ ...applicant, nomDemandeur: '🐾'.repeat(150) }).errors, {})
})

for (const id of [0, -1, '11', 1.5, Number.MAX_SAFE_INTEGER + 1]) {
  test(`invalid animal ID ${id} is never sent`, async () => {
    let fetched = false
    await assert.rejects(submitAdoptionRequest(id, applicant, { now, fetchImpl: async () => { fetched = true } }), (error) => error.kind === 'notFound')
    assert.equal(fetched, false)
  })
}

for (const [status, kind] of [[400, 'validation'], [404, 'notFound'], [409, 'conflict'], [500, 'uncertain'], [503, 'uncertain'], [200, 'uncertain']]) {
  test(`HTTP ${status} has a safe ${kind} outcome with no retry`, async () => {
    let count = 0
    await assert.rejects(submitAdoptionRequest(11, applicant, { now, fetchImpl: async () => { count++; return new Response('private applicant or server diagnostics', { status }) } }), (error) => {
      assert.equal(error.kind, kind)
      assert.doesNotMatch(error.message, /private|diagnostics|test@example/)
      return true
    })
    assert.equal(count, 1)
  })
}

test('a lost connection after POST is uncertain and does not automatically resubmit', async () => {
  let attempts = 0
  await assert.rejects(submitAdoptionRequest(11, applicant, { now, fetchImpl: async () => { attempts++; throw new TypeError('private diagnostics') } }), (error) => error.kind === 'uncertain')
  assert.equal(attempts, 1)
})

test('explicit cancellation preserves AbortError', async () => {
  const aborted = new DOMException('cancelled', 'AbortError')
  await assert.rejects(submitAdoptionRequest(11, applicant, { now, fetchImpl: async () => { throw aborted } }), (error) => error === aborted)
})

test('request date uses the local calendar at submission time', () => {
  assert.equal(localRequestDate(new Date(2026, 10, 8, 0, 15)), '2026-11-08')
  assert.equal(localRequestDate(new Date(2026, 0, 1, 23, 59)), '2026-01-01')
  assert.throws(() => localRequestDate(new Date('invalid')))
})
