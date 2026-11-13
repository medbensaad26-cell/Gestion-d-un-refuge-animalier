export const ADMIN_SESSION_KEY = 'refuge.adminSession.v1'
export const ADMIN_SESSION_DURATION = 2 * 60 * 60 * 1000

export class AdminSessionExpiredError extends Error {
  constructor() { super('Votre session a expiré. Veuillez vous reconnecter.'); this.name = 'AdminSessionExpiredError' }
}

function validToken(token) { return typeof token === 'string' && /^[A-Za-z0-9_-]{32,512}$/.test(token) }
function validAdmin(admin) { return admin && Number.isSafeInteger(admin.id) && admin.id > 0 && typeof admin.nom === 'string' && admin.nom.trim() !== '' && typeof admin.email === 'string' && admin.email.trim() !== '' }
function publicAdmin(admin) { return { id: admin.id, nom: admin.nom, email: admin.email } }

export function clearAdminSession(storage) {
  try { (storage ?? globalThis.sessionStorage)?.removeItem(ADMIN_SESSION_KEY) } catch { /* Memory-only sessions still work when browser storage is blocked. */ }
}

export function storeAdminSession(session, storage) {
  try { (storage ?? globalThis.sessionStorage)?.setItem(ADMIN_SESSION_KEY, JSON.stringify({ token: session.token, expiresAt: session.expiresAt })) } catch { /* No persistent fallback. */ }
}

export function restoreAdminSession(storage, now = Date.now()) {
  try {
    const value = JSON.parse((storage ?? globalThis.sessionStorage)?.getItem(ADMIN_SESSION_KEY) ?? 'null')
    if (value && validToken(value.token) && Number.isFinite(value.expiresAt) && value.expiresAt > now && value.expiresAt <= now + ADMIN_SESSION_DURATION) {
      return { token: value.token, expiresAt: value.expiresAt }
    }
  } catch { /* Invalid/unavailable storage never grants access. */ }
  clearAdminSession(storage)
  return null
}

export async function loginAdmin(email, password, { signal, fetchImpl = globalThis.fetch, now = Date.now() } = {}) {
  if (typeof email !== 'string' || !email.trim() || typeof password !== 'string' || !password.trim()) throw new Error('Renseignez votre e-mail et votre mot de passe.')
  let response
  try {
    response = await fetchImpl('/api/auth/login', { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, credentials: 'omit', cache: 'no-store', signal, body: JSON.stringify({ email: email.trim(), motDePasse: password }) })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Impossible de contacter le refuge. Réessayez dans un instant.')
  }
  if (response.status === 401) throw new Error('E-mail ou mot de passe incorrect.')
  if (!response.ok) throw new Error('La connexion est momentanément indisponible. Veuillez réessayer.')
  let data
  try { data = await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('La connexion n’a pas pu être confirmée. Veuillez réessayer.')
  }
  if (!validToken(data?.token) || !validAdmin(data?.admin)) throw new Error('La connexion n’a pas pu être confirmée. Veuillez réessayer.')
  return { token: data.token, admin: publicAdmin(data.admin), expiresAt: now + ADMIN_SESSION_DURATION }
}

export async function logoutAdmin(token, { fetchImpl = globalThis.fetch } = {}) {
  if (!validToken(token)) return
  let response
  try { response = await fetchImpl('/api/auth/logout', { method: 'POST', headers: { Accept: 'application/json', Authorization: `Bearer ${token}` }, credentials: 'omit', cache: 'no-store' }) } catch {
    throw new Error('Déconnexion de cet onglet effectuée. Sa confirmation par le refuge est momentanément indisponible.')
  }
  if (response.status !== 200 && response.status !== 401) throw new Error('Déconnexion de cet onglet effectuée. Sa confirmation par le refuge est momentanément indisponible.')
}

export async function getAdminDashboard(token, { signal, fetchImpl = globalThis.fetch } = {}) {
  if (!validToken(token)) throw new AdminSessionExpiredError()
  let response
  try { response = await fetchImpl('/api/admin/dashboard', { method: 'GET', headers: { Accept: 'application/json', Authorization: `Bearer ${token}` }, credentials: 'omit', cache: 'no-store', signal }) } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Impossible de vérifier votre accès. Vérifiez la connexion et réessayez.')
  }
  if (response.status === 401) throw new AdminSessionExpiredError()
  if (!response.ok) throw new Error('Le tableau de bord est momentanément indisponible. Veuillez réessayer.')
  let data
  try { data = await response.json() } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new Error('Le tableau de bord n’a pas pu être chargé. Veuillez réessayer.')
  }
  if (!validAdmin(data?.admin) || !['animaux', 'annonces', 'demandes'].every(key => Number.isSafeInteger(data[key]) && data[key] >= 0)) throw new Error('Le tableau de bord n’a pas pu être chargé. Veuillez réessayer.')
  return { admin: publicAdmin(data.admin), animaux: data.animaux, annonces: data.annonces, demandes: data.demandes }
}
