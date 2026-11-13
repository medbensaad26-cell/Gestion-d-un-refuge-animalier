import test from 'node:test'
import assert from 'node:assert/strict'
import { AdminSessionExpiredError } from '../src/services/adminAuth.js'
import { AdminRequestError, getAdminRequests, getAdminRequest, decideAdminRequest, requestStatus } from '../src/services/adminRequests.js'

const token = 'x'.repeat(43)
const record = { id: 41, nomDemandeur: 'Camille Démo', email: 'camille@example.test', telephone: '0102030405', dateDemande: '2026-11-08', statut: 'EN_ATTENTE', animalId: 12 }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })

test('private list sends bearer without cookies/cache and projects applicant data without admin attribution', async () => {
  assert.deepEqual(await getAdminRequests(token, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/demandes')
    assert.equal(options.method, 'GET')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    return response([{ ...record, adminId: 999, privateExtra: 'must not escape' }])
  } }), [record])
})

test('list includes all valid statuses and sorts by most recent date and ID', async () => {
  const accepted = { ...record, id: 42, statut: 'ACCEPTEE' }, refused = { ...record, id: 43, dateDemande: '2026-11-07', statut: 'REFUSEE' }
  assert.deepEqual(await getAdminRequests(token, { fetchImpl: async () => response([record, refused, accepted]) }), [accepted, record, refused])
  assert.deepEqual(await getAdminRequests(token, { fetchImpl: async () => response([]) }), [])
  assert.equal(requestStatus('EN_ATTENTE'), 'En attente')
  assert.equal(requestStatus('ACCEPTEE'), 'Acceptée')
  assert.equal(requestStatus('REFUSEE'), 'Refusée')
  assert.equal(requestStatus('OTHER'), 'Statut inconnu')
})

test('detail uses the request ID and preserves a legacy absent phone as unknown', async () => {
  const { telephone: _phone, ...legacy } = record
  assert.deepEqual(await getAdminRequest(token, 41, { fetchImpl: async (url) => { assert.equal(url, '/api/demandes/41'); return response(legacy) } }), { ...record, telephone: null })
  await assert.rejects(getAdminRequest(token, 42, { fetchImpl: async () => response(record) }), error => error.kind === 'invalid')
})

for (const data of [null, {}, [record, record], [{ ...record, id: '41' }], [{ ...record, animalId: 0 }], [{ ...record, nomDemandeur: '' }], [{ ...record, email: null }], [{ ...record, telephone: {} }], [{ ...record, dateDemande: '2026-02-30' }], [{ ...record, statut: 'ADOPTE' }]]) {
  test(`malformed private request list is rejected: ${JSON.stringify(data).slice(0, 55)}`, async () => {
    await assert.rejects(getAdminRequests(token, { fetchImpl: async () => response(data) }), AdminRequestError)
  })
}

test('invalid route IDs or decision names never issue a request', async () => {
  const options = { fetchImpl() { assert.fail('must not fetch') } }
  for (const id of ['0', '-1', '01', '41/accepter', Number.MAX_SAFE_INTEGER + 1]) {
    await assert.rejects(getAdminRequest(token, id, options), error => error.kind === 'notFound')
    await assert.rejects(decideAdminRequest(token, id, 'accepter', options), error => error.kind === 'notFound')
  }
  for (const decision of ['ACCEPTEE', 'annuler', '', null]) await assert.rejects(decideAdminRequest(token, 41, decision, options), error => error.kind === 'validation')
})

test('decisions use exactly one PATCH to the explicit action with no PII, status or administrator body', async () => {
  for (const [decision, statut] of [['accepter', 'ACCEPTEE'], ['refuser', 'REFUSEE']]) {
    let calls = 0
    const receipt = await decideAdminRequest(token, 41, decision, { fetchImpl: async (url, options) => {
      calls++
      assert.equal(url, `/api/demandes/41/${decision}`)
      assert.equal(options.method, 'PATCH')
      assert.equal(options.body, undefined)
      assert.equal(options.headers.Authorization, `Bearer ${token}`)
      assert.equal(options.credentials, 'omit')
      assert.equal(options.cache, 'no-store')
      return response({ id: 41, statut, adminId: 999, message: 'must not escape' })
    } })
    assert.deepEqual(receipt, { id: 41, statut })
    assert.equal(calls, 1)
  }
})

test('all private operations expire the session on 401 and invalid local tokens never fetch', async () => {
  const options = { fetchImpl: async () => response({}, 401) }
  for (const operation of [() => getAdminRequests(token, options), () => getAdminRequest(token, 41, options), () => decideAdminRequest(token, 41, 'accepter', options)]) await assert.rejects(operation(), AdminSessionExpiredError)
  await assert.rejects(getAdminRequests('forged', { fetchImpl() { assert.fail('must not fetch') } }), AdminSessionExpiredError)
})

test('404/409/400 block the decision with useful generic errors, never leaking server details', async () => {
  for (const [status, kind] of [[404, 'notFound'], [409, 'conflict'], [400, 'validation']]) {
    let calls = 0
    await assert.rejects(decideAdminRequest(token, 41, 'refuser', { fetchImpl: async () => { calls++; return response({ erreur: 'PRIVATE SQL' }, status) } }), error => error.kind === kind && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
})

for (const receipt of [null, {}, { id: 42, statut: 'ACCEPTEE' }, { id: '41', statut: 'ACCEPTEE' }, { id: 41, statut: 'REFUSEE' }]) {
  test(`an inconsistent receipt never confirms acceptance: ${JSON.stringify(receipt)}`, async () => {
    await assert.rejects(decideAdminRequest(token, 41, 'accepter', { fetchImpl: async () => response(receipt) }), error => error.kind === 'uncertain')
  })
}

test('lost, malformed and unexpected decision responses never auto-retry or claim success', async () => {
  for (const outcome of ['network', 'invalid-json', 500, 202, 204]) {
    let calls = 0
    await assert.rejects(decideAdminRequest(token, 41, 'accepter', { fetchImpl: async () => {
      calls++
      if (outcome === 'network') throw Error('PRIVATE HOST')
      if (outcome === 'invalid-json') return new Response('{broken', { status: 200 })
      return outcome === 204 ? new Response(null, { status: 204 }) : response({}, outcome)
    } }), error => error.kind === 'uncertain' && error.message.includes('Rechargez la demande') && !error.message.includes('PRIVATE'))
    assert.equal(calls, 1)
  }
})

test('read errors stay generic and cancellation propagates through fetch and receipt parsing', async () => {
  await assert.rejects(getAdminRequests(token, { fetchImpl: async () => { throw Error('PRIVATE HOST') } }), error => error.kind === 'network' && !error.message.includes('PRIVATE'))
  await assert.rejects(getAdminRequests(token, { fetchImpl: async () => response({}, 500) }), error => error.kind === 'server')
  await assert.rejects(getAdminRequest(token, 41, { fetchImpl: async () => new Response('{broken', { status: 200 }) }), error => error.kind === 'invalid')
  const abort = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(getAdminRequests(token, { fetchImpl: async () => { throw abort } }), error => error === abort)
  await assert.rejects(decideAdminRequest(token, 41, 'accepter', { fetchImpl: async () => ({ status: 200, json: async () => { throw abort } }) }), error => error === abort)
})
