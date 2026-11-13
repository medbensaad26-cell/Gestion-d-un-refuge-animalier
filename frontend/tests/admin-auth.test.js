import test from 'node:test'
import assert from 'node:assert/strict'
import { ADMIN_SESSION_KEY, ADMIN_SESSION_DURATION, AdminSessionExpiredError, clearAdminSession, storeAdminSession, restoreAdminSession, loginAdmin, logoutAdmin, getAdminDashboard } from '../src/services/adminAuth.js'

const token = 'x'.repeat(43)
const admin = { id: 1, nom: 'Admin test', email: 'admin@example.test' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function storage() {
  const entries = new Map()
  return { getItem: key => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key) }
}

test('login sends the backend field names, preserves password, and projects public identity', async () => {
  const result = await loginAdmin(' admin@example.test ', ' test password ', { now: 100, fetchImpl: async (url, options) => {
    assert.equal(url, '/api/auth/login')
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.headers.Authorization, undefined)
    assert.deepEqual(JSON.parse(options.body), { email: 'admin@example.test', motDePasse: ' test password ' })
    return response({ token, admin: { ...admin, motDePasse: 'must not escape' } })
  } })
  assert.deepEqual(result, { token, admin, expiresAt: 100 + ADMIN_SESSION_DURATION })
})

test('session persistence includes only token and expiry and restores without identity', () => {
  const local = storage()
  storeAdminSession({ token, expiresAt: 1000, admin, password: 'never persist' }, local)
  assert.deepEqual(JSON.parse(local.getItem(ADMIN_SESSION_KEY)), { token, expiresAt: 1000 })
  assert.deepEqual(restoreAdminSession(local, 500), { token, expiresAt: 1000 })
  clearAdminSession(local)
  assert.equal(local.getItem(ADMIN_SESSION_KEY), null)
})

for (const value of ['{bad', 'null', '{}', JSON.stringify({ token: 'bad', expiresAt: 1000 }), JSON.stringify({ token, expiresAt: 500 }), JSON.stringify({ token, expiresAt: ADMIN_SESSION_DURATION + 501 })]) {
  test(`invalid or expired stored session is removed: ${value.slice(0, 35)}`, () => {
    const local = storage()
    local.setItem(ADMIN_SESSION_KEY, value)
    assert.equal(restoreAdminSession(local, 500), null)
    assert.equal(local.getItem(ADMIN_SESSION_KEY), null)
  })
}

test('unavailable browser storage has no persistent fallback and never grants access', () => {
  const blocked = { getItem() { throw Error('blocked') }, setItem() { throw Error('blocked') }, removeItem() { throw Error('blocked') } }
  assert.doesNotThrow(() => storeAdminSession({ token, expiresAt: 1000 }, blocked))
  assert.doesNotThrow(() => clearAdminSession(blocked))
  assert.equal(restoreAdminSession(blocked, 500), null)
})

test('blank credentials do not send a request', async () => {
  await assert.rejects(loginAdmin(' ', 'test', { fetchImpl() { assert.fail('must not fetch') } }), /Renseignez/)
})

test('invalid credentials use a generic message without exposing server content', async () => {
  await assert.rejects(loginAdmin('a@b.test', 'test', { fetchImpl: async () => response({ erreur: 'private SQL detail' }, 401) }), /^Error: E-mail ou mot de passe incorrect\.$/)
})

for (const payload of [{ token: 'bad', admin }, { token, admin: { ...admin, id: 0 } }, { token }, null]) {
  test(`invalid login payload cannot establish a session: ${JSON.stringify(payload)}`, async () => {
    await assert.rejects(loginAdmin('a@b.test', 'test', { fetchImpl: async () => response(payload) }), /confirmée/)
  })
}

test('dashboard is authenticated, includes zero counts, and projects only public summary fields', async () => {
  assert.deepEqual(await getAdminDashboard(token, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/admin/dashboard')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.credentials, 'omit')
    assert.equal(options.cache, 'no-store')
    return response({ admin: { ...admin, motDePasse: 'private' }, animaux: 0, annonces: 2, demandes: 3, applicants: ['private'] })
  } }), { admin, animaux: 0, annonces: 2, demandes: 3 })
})

test('dashboard rejects invalid tokens before fetch and turns HTTP401 into session expiry', async () => {
  await assert.rejects(getAdminDashboard('forged', { fetchImpl() { assert.fail('must not fetch') } }), AdminSessionExpiredError)
  await assert.rejects(getAdminDashboard(token, { fetchImpl: async () => response({}, 401) }), AdminSessionExpiredError)
})

for (const invalid of [-1, 0.5, '2', null, Number.MAX_SAFE_INTEGER + 1]) {
  test(`dashboard rejects malformed counts: ${invalid}`, async () => {
    await assert.rejects(getAdminDashboard(token, { fetchImpl: async () => response({ admin, animaux: invalid, annonces: 0, demandes: 0 }) }), /chargé/)
  })
}

test('network errors and HTTP failures remain generic; aborts propagate', async () => {
  await assert.rejects(getAdminDashboard(token, { fetchImpl: async () => { throw Error('private host detail') } }), /Impossible de vérifier/)
  await assert.rejects(getAdminDashboard(token, { fetchImpl: async () => response({ erreur: 'private detail' }, 500) }), /momentanément indisponible/)
  const aborted = new DOMException('Cancelled', 'AbortError')
  await assert.rejects(loginAdmin('a@b.test', 'test', { fetchImpl: async () => { throw aborted } }), error => error === aborted)
  await assert.rejects(getAdminDashboard(token, { fetchImpl: async () => { throw aborted } }), error => error === aborted)
})

test('logout revokes server token and accepts an already expired session', async () => {
  for (const status of [200, 401]) await logoutAdmin(token, { fetchImpl: async (url, options) => {
    assert.equal(url, '/api/auth/logout')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Authorization, `Bearer ${token}`)
    assert.equal(options.cache, 'no-store')
    return response({}, status)
  } })
})

test('failed remote logout clearly distinguishes local logout from server confirmation', async () => {
  for (const fetchImpl of [async () => { throw Error('offline') }, async () => response({}, 500)]) {
    await assert.rejects(logoutAdmin(token, { fetchImpl }), /Déconnexion de cet onglet effectuée.*confirmation/)
  }
})
