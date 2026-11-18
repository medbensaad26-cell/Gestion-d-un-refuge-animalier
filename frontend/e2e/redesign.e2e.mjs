import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createServer } from 'node:http'
import { readFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { chromium } from 'playwright'

// Production-bundle tests only. This static server never proxies to Java.
// Every API response and every mutation below is an isolated in-memory fixture.
const dist = fileURLToPath(new URL('../dist/', import.meta.url))
const artifacts = fileURLToPath(new URL('./artifacts/redesign/', import.meta.url))
const token = 'refuge_ui_test_only_'.padEnd(48, 'x')
const admin = { id: 1, nom: 'Équipe de validation', email: 'admin@example.test' }
const fixtures = [
  { id: 3, nom: 'LunaTest', espece: 'Chat', race: null, age: 4, sexe: 'FEMELLE', statut: 'DISPONIBLE', etatSante: 'Informations de test' },
  { id: 4, nom: 'MiloTest', espece: 'Chat', race: null, age: 1, sexe: 'MALE', statut: 'DISPONIBLE', etatSante: null },
  { id: 5, nom: 'RexTest', espece: 'Chien', race: 'Race de test', age: 5, sexe: 'MALE', statut: 'DISPONIBLE', etatSante: null },
  { id: 6, nom: 'ProfilAdoptéTest', espece: 'Chien', race: null, age: 8, sexe: 'FEMELLE', statut: 'ADOPTE', etatSante: null },
]
const recommendations = [
  { animal: fixtures[0], score: 100, explication: 'Espece : correspond ; Age (ADULTE) : correspond ; Sexe : correspond.' },
  { animal: fixtures[1], score: 50, explication: 'Espece : correspond ; Age (ADULTE) : different ; Sexe : different.' },
  { animal: fixtures[2], score: 30, explication: 'Espece : differente ; Age (ADULTE) : correspond ; Sexe : different.' },
]
const announcements = [{ id: 7, animalId: 3, titre: 'Annonce de validation — LunaTest', description: 'Texte simulé pour vérifier la présentation des publications, sans données réelles.', datePublication: '2026-11-16', statut: 'PUBLIEE' }]
const requests = [{ id: 8, animalId: 3, nomDemandeur: 'Personne de test', email: 'adoption@example.test', telephone: '0100000000', dateDemande: '2026-11-16', statut: 'EN_ATTENTE' }]
let server, browser, base

before(async () => {
  await mkdir(artifacts, { recursive: true })
  server = createServer(async (req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname
    if (pathname.startsWith('/api/')) { res.writeHead(503); res.end('No live API access in browser tests'); return }
    const target = path.resolve(dist, decodeURIComponent(pathname).replace(/^\/+/, '') || 'index.html')
    if (!target.startsWith(path.resolve(dist) + path.sep)) { res.writeHead(403); res.end(); return }
    try {
      let data, ext = path.extname(target)
      try { data = await readFile(target) } catch { data = await readFile(path.join(dist, 'index.html')); ext = '.html' }
      res.writeHead(200, { 'Content-Type': ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.woff2': 'font/woff2' })[ext] || 'text/html' })
      res.end(data)
    } catch { res.writeHead(500); res.end() }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true, channel: process.env.REFUGE_E2E_CHANNEL || (process.platform === 'win32' ? 'msedge' : undefined) })
})
after(async () => { await browser?.close(); if (server) await new Promise(resolve => server.close(resolve)); console.log(`Screenshots with simulated data: ${artifacts}`) })

async function withPage(run, { width = 1440, authenticated = false, failAnimals = false, failAnnouncements = false, empty = false, reducedMotion = 'reduce' } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, serviceWorkers: 'block', reducedMotion })
  const page = await context.newPage()
  page.setDefaultTimeout(8000)
  const crashes = [], unexpected = [], calls = []
  const data = { animals: empty ? [] : structuredClone(fixtures), announcements: structuredClone(announcements), requests: structuredClone(requests), medical: { id: 9, animalId: 3, vaccins: null, traitements: null, observations: 'Contenu médical simulé.', dateMiseAJour: '2026-11-16' } }
  page.on('pageerror', error => crashes.push(error.message))
  if (authenticated) await context.addInitScript(({ token }) => sessionStorage.setItem('refuge.adminSession.v1', JSON.stringify({ token, expiresAt: Date.now() + 3600000 })), { token })
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method(), headers = request.headers()
    const body = request.postDataJSON(), id = Number(url.pathname.split('/')[3])
    calls.push({ path: url.pathname, query: url.search, method, body, headers })
    const reply = (json, status = 200) => route.fulfill({ status, json })
    if (url.pathname === '/api/auth/login') return reply({ token, admin })
    if (url.pathname === '/api/auth/logout') return reply({})
    if (url.pathname === '/api/admin/dashboard') return reply({ admin, animaux: data.animals.length, annonces: data.announcements.length, demandes: data.requests.length })
    if (url.pathname === '/api/matching') return reply(url.search ? recommendations : recommendations.map(r => ({ ...r, score: 0, explication: 'Aucune preference renseignee : score non evalue (0), ordre par identifiant.' })))
    if (/^\/api\/animaux\/\d+\/dossier-medical$/.test(url.pathname)) {
      if (method !== 'GET') data.medical = { ...data.medical, ...body }
      return reply(data.medical)
    }
    if (url.pathname === '/api/animaux' && method === 'GET') {
      if (failAnimals) return reply({ erreur: 'Simulated unavailable catalogue' }, 503)
      return reply(data.animals.filter(a => (!url.searchParams.get('q') || a.nom.toLowerCase().includes(url.searchParams.get('q').toLowerCase())) && (!url.searchParams.get('espece') || a.espece === url.searchParams.get('espece')) && (!url.searchParams.get('statutAnimal') || a.statut === url.searchParams.get('statutAnimal'))))
    }
    if (url.pathname === '/api/animaux' && method === 'POST') { data.animals.push({ ...body, id: 20 }); return reply({}, 201) }
    if (/^\/api\/animaux\/\d+$/.test(url.pathname)) {
      const animal = data.animals.find(a => a.id === id)
      if (!animal) return reply({}, 404)
      if (method === 'PUT') Object.assign(animal, body)
      if (method === 'DELETE') data.animals = data.animals.filter(a => a.id !== id)
      return reply(animal)
    }
    if (url.pathname === '/api/annonces' && method === 'GET') {
      if (failAnnouncements) return reply({}, 503)
      return reply(data.announcements.filter(a => headers.authorization || a.statut === 'PUBLIEE').filter(a => !url.searchParams.get('q') || a.titre.toLowerCase().includes(url.searchParams.get('q').toLowerCase())))
    }
    if (url.pathname === '/api/annonces' && method === 'POST') { data.announcements.push({ id: 21, ...body }); return reply({}, 201) }
    if (/^\/api\/annonces\/\d+$/.test(url.pathname)) {
      const item = data.announcements.find(a => a.id === id)
      if (method === 'PUT') Object.assign(item, body)
      return reply(item)
    }
    if (url.pathname === '/api/demandes' && method === 'GET') return reply(data.requests)
    if (url.pathname === '/api/demandes' && method === 'POST') { data.requests.push({ id: 22, statut: 'EN_ATTENTE', ...body }); return reply({}, 201) }
    if (/^\/api\/demandes\/\d+(\/(accepter|refuser))?$/.test(url.pathname)) {
      const item = data.requests.find(r => r.id === id)
      if (method === 'PATCH') { item.statut = url.pathname.endsWith('accepter') ? 'ACCEPTEE' : 'REFUSEE'; if (item.statut === 'ACCEPTEE') data.animals.find(a => a.id === item.animalId).statut = 'ADOPTE' }
      return reply(item)
    }
    unexpected.push(`${method} ${url.pathname}`)
    return reply({ erreur: 'Unrecognized test request blocked' }, 503)
  })
  try { await run(page, calls, data); assert.deepEqual(crashes, []); assert.deepEqual(unexpected, []) } finally { await context.close() }
}

async function open(page, route) {
  await page.goto(base + route)
  await page.locator('h1').first().waitFor()
  await page.evaluate(() => document.fonts.ready)
}
async function noOverflow(page) {
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `Horizontal overflow on ${page.url()}`)
}
async function capture(page, name, { fullPage = true } = {}) {
  await page.locator('img').evaluateAll(images => images.forEach(img => { img.loading = 'eager' }))
  await page.evaluate(async () => { await Promise.all([...document.images].map(img => img.decode().catch(() => {}))); const marker = document.createElement('div'); marker.id = 'test-capture-label'; marker.textContent = 'APERÇU DE VALIDATION · DONNÉES SIMULÉES'; marker.style.cssText = 'position:fixed;z-index:9999;right:12px;bottom:12px;padding:7px 11px;border-radius:5px;background:#193d32;color:white;font:10px sans-serif'; document.body.append(marker) })
  await page.screenshot({ path: path.join(artifacts, `${name}.png`), fullPage })
  await page.locator('#test-capture-label').evaluate(element => element.remove())
}

test('catalogue filters combine species, name and status, then reset to the original API order', async () => withPage(async page => {
  await open(page, '/'); await page.locator('.animal-card').first().waitFor()
  assert.deepEqual(await page.locator('.animal-card h3').allTextContents(), fixtures.map(a => a.nom))
  assert.equal(await page.locator('.available-spotlight>a').count(), 3)
  await page.getByRole('button', { name: 'Les chats', exact: true }).click()
  await page.getByRole('searchbox', { name: 'Rechercher un animal' }).fill('luna')
  await page.getByRole('combobox', { name: 'Statut de l’animal', exact: true }).selectOption('DISPONIBLE')
  assert.deepEqual(await page.locator('.animal-card h3').allTextContents(), ['LunaTest'])
  await page.getByRole('searchbox', { name: 'Rechercher un animal' }).fill('inexistant')
  await page.getByRole('heading', { name: 'Pas encore de rencontre avec ces critères.' }).waitFor()
  await page.getByRole('button', { name: 'Réinitialiser les filtres' }).click()
  assert.equal(await page.locator('.animal-card').count(), 4)
  await page.getByRole('button', { name: /Les chiens Découvrir les chiens disponibles/ }).click()
  assert.deepEqual(await page.locator('.animal-card h3').allTextContents(), ['RexTest'])
  await page.getByRole('button', { name: 'Réinitialiser les filtres' }).click()
  await page.evaluate(() => window.scrollTo(0, 0)); await capture(page, 'home-desktop')
}))

test('Chat + Adulte + Femelle displays exactly LunaTest 100, MiloTest 50, RexTest 30 from the API', async () => withPage(async (page, calls) => {
  await open(page, '/trouver-compagnon')
  await page.getByRole('radio', { name: 'Un chat', exact: true }).check()
  await page.getByRole('radio', { name: /^Adulte/ }).check()
  await page.getByRole('radio', { name: 'Femelle', exact: true }).check()
  await page.getByRole('button', { name: 'Voir mes recommandations' }).click()
  await page.locator('.matching-card').first().waitFor()
  assert.deepEqual(await page.locator('.matching-card h3').allTextContents(), ['LunaTest', 'MiloTest', 'RexTest'])
  assert.deepEqual(await page.locator('.matching-score strong').allTextContents(), ['100 / 100', '50 / 100', '30 / 100'])
  assert.equal(calls.find(c => c.path === '/api/matching').query, '?espece=Chat&categorieAge=ADULTE&sexe=FEMELLE')
  await capture(page, 'matching-desktop-results')
  await page.locator('.matching-results').evaluate(element => element.scrollIntoView({ block: 'start' }))
  await capture(page, 'matching-results-viewport', { fullPage: false })
}))

test('published announcements keep their query behavior and public profile links', async () => withPage(async (page, calls) => {
  await open(page, '/annonces'); await page.locator('.announcement-card').waitFor()
  await page.getByRole('searchbox', { name: 'Rechercher', exact: true }).fill('LunaTest')
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click()
  await page.locator('.announcement-card').waitFor()
  assert.ok(calls.some(c => c.path === '/api/annonces' && c.query === '?q=LunaTest'))
  await capture(page, 'announcements-desktop')
  await page.getByRole('link', { name: /Voir l’animal de l’annonce/ }).click()
  await page.getByRole('heading', { level: 1, name: 'LunaTest' }).waitFor()
  assert.equal(await page.getByRole('link', { name: /Faire une demande d’adoption/ }).getAttribute('href'), '/animaux/3/demande-adoption')
  await capture(page, 'profile-desktop')
}))

test('adoption validation, availability reread, single submission and confirmation still work', async () => withPage(async (page, calls) => {
  await open(page, '/animaux/3/demande-adoption'); await page.getByRole('heading', { name: 'Vos coordonnées', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Envoyer ma demande' }).click()
  assert.equal(await page.locator('#adoption-nomDemandeur').getAttribute('aria-invalid'), 'true')
  assert.equal(calls.filter(c => c.method === 'POST').length, 0)
  await page.getByLabel('Nom complet').fill('Personne de validation')
  await page.getByLabel('Adresse e-mail').fill('validation@example.test')
  await page.getByLabel('Téléphone').fill('0100000000')
  await capture(page, 'adoption-form-desktop')
  await page.getByRole('button', { name: 'Envoyer ma demande' }).click()
  await page.getByRole('heading', { name: 'Votre demande est envoyée.' }).waitFor()
  assert.equal(calls.filter(c => c.method === 'POST' && c.path === '/api/demandes').length, 1)
  assert.equal(calls.filter(c => c.path === '/api/animaux/3').length, 2)
  await capture(page, 'adoption-confirmation')
}))

test('admin login, original server counters, logout and protected redirects still work', async () => withPage(async (page, calls) => {
  await open(page, '/admin'); await page.getByRole('button', { name: 'Se connecter', exact: true }).waitFor()
  assert.equal(new URL(page.url()).pathname, '/admin/connexion')
  await capture(page, 'login-desktop')
  await page.getByLabel('Adresse e-mail').fill(admin.email)
  await page.getByLabel('Mot de passe', { exact: true }).fill('fixture-test-only')
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click()
  await page.getByRole('heading', { name: 'Tableau de bord', exact: true }).waitFor()
  assert.deepEqual(await page.locator('.dashboard-count').allTextContents(), ['4', '1', '1'])
  await capture(page, 'admin-dashboard-desktop')
  await page.getByRole('button', { name: 'Se déconnecter' }).click()
  await page.getByRole('button', { name: 'Se connecter', exact: true }).waitFor()
  assert.ok(calls.some(c => c.path === '/api/auth/logout' && c.method === 'POST'))
}))

test('animal creation, update, medical editing and explicit deletion still use existing contracts', async () => withPage(async (page, calls) => {
  await open(page, '/admin/animaux/nouveau')
  await page.getByLabel('Nom', { exact: true }).fill('AnimalInterfaceTest')
  await page.getByLabel('Espèce', { exact: true }).fill('Chat')
  await page.getByLabel('Âge en années').fill('3')
  await page.getByLabel('Sexe', { exact: true }).fill('FEMELLE')
  await page.getByRole('button', { name: 'Ajouter l’animal', exact: true }).click()
  await page.getByRole('heading', { name: 'Les animaux', exact: true }).waitFor()
  assert.ok(calls.some(c => c.method === 'POST' && c.path === '/api/animaux' && c.body.nom === 'AnimalInterfaceTest'))
  await open(page, '/admin/animaux/3/modifier')
  await page.getByLabel('Race (facultatif)').fill('Valeur simulée')
  await page.getByRole('button', { name: 'Enregistrer les modifications' }).click()
  await page.getByRole('heading', { name: 'Les animaux', exact: true }).waitFor()
  assert.ok(calls.some(c => c.method === 'PUT' && c.path === '/api/animaux/3' && c.body.race === 'Valeur simulée'))
  await capture(page, 'admin-animals-desktop')
  await open(page, '/admin/animaux/3/dossier-medical')
  await page.getByRole('button', { name: 'Modifier le dossier' }).click()
  await page.getByLabel('Observations (facultatif)').fill('Observation simulée de validation.')
  await page.getByRole('button', { name: 'Enregistrer le dossier' }).click()
  await page.getByText('Le dossier médical a bien été enregistré.').waitFor()
  await capture(page, 'admin-medical-desktop')
  await open(page, '/admin/animaux/20/supprimer')
  await page.getByRole('button', { name: 'Confirmer la suppression' }).click()
  await page.getByRole('heading', { name: 'Les animaux', exact: true }).waitFor()
  assert.ok(calls.some(c => c.method === 'DELETE' && c.path === '/api/animaux/20'))
}, { authenticated: true }))

test('announcement creation, publication, editing and withdrawal preserve existing contracts', async () => withPage(async (page, calls) => {
  await open(page, '/admin/annonces/7/modifier')
  await page.getByLabel('Titre', { exact: true }).fill('Titre modifié en mémoire')
  await page.getByRole('button', { name: 'Publier l’annonce', exact: true }).click()
  await page.getByText(/L’annonce a bien été/).waitFor()
  assert.ok(calls.some(c => c.path === '/api/annonces/7' && c.method === 'PUT' && c.body.titre === 'Titre modifié en mémoire'))
  await open(page, '/admin/annonces/7/modifier')
  await page.getByLabel('Visibilité', { exact: true }).selectOption('INTERNE')
  await page.getByRole('button', { name: 'Retirer du site et enregistrer', exact: true }).click()
  await page.getByRole('heading', { name: 'Annonce enregistrée.' }).waitFor()
  assert.ok(calls.some(c => c.path === '/api/annonces/7' && c.method === 'PUT' && c.body.statut === 'INTERNE'))
  await open(page, '/admin/annonces/nouvelle')
  await page.getByLabel('Animal concerné', { exact: true }).selectOption('4')
  await page.getByLabel('Titre', { exact: true }).fill('Nouvelle annonce de test')
  await page.getByRole('button', { name: 'Enregistrer sans publier', exact: true }).click()
  await page.getByRole('heading', { name: 'Annonce enregistrée.' }).waitFor()
  assert.ok(calls.some(c => c.path === '/api/annonces' && c.method === 'POST' && c.body.statut === 'INTERNE'))
  await open(page, '/admin/annonces')
  await page.locator('.admin-announcement-card').first().waitFor()
  await capture(page, 'admin-announcements-desktop')
}, { authenticated: true }))

for (const decision of ['accepter', 'refuser']) {
  test(`request review keeps the explicit ${decision} confirmation flow`, async () => withPage(async (page, calls) => {
    await open(page, '/admin/demandes/8')
    await page.getByRole('button', { name: decision === 'accepter' ? 'Accepter la demande' : 'Refuser la demande', exact: true }).click()
    assert.equal(calls.filter(c => c.method === 'PATCH').length, 0)
    await page.getByRole('button', { name: decision === 'accepter' ? 'Confirmer l’acceptation' : 'Confirmer le refus', exact: true }).click()
    await page.getByText(decision === 'accepter' ? /La demande a bien été acceptée/ : /La demande a bien été refusée/).waitFor()
    assert.equal(calls.filter(c => c.path === `/api/demandes/8/${decision}` && c.method === 'PATCH').length, 1)
    await capture(page, `admin-request-${decision}`)
  }, { authenticated: true }))
}

const publicRoutes = ['/', '/annonces', '/trouver-compagnon', '/animaux/3', '/animaux/3/demande-adoption', '/admin/connexion', '/route-inconnue']
const adminRoutes = ['/admin', '/admin/animaux', '/admin/animaux/nouveau', '/admin/animaux/3/modifier', '/admin/animaux/3/supprimer', '/admin/animaux/3/dossier-medical', '/admin/animaux/3/annonce', '/admin/annonces', '/admin/annonces/nouvelle', '/admin/annonces/7/modifier', '/admin/demandes', '/admin/demandes/8', '/admin/inconnue']
for (const width of [320, 390, 768, 1024, 1440]) {
  test(`all public and administrator routes load without overflow at ${width}px`, async () => withPage(async page => {
    for (const route of [...publicRoutes, ...adminRoutes]) {
      await open(page, route)
      // Wait for asynchronous route content, not only the protected shell.
      await page.locator('.loading-spinner').waitFor({ state: 'hidden' }).catch(() => {})
      await noOverflow(page)
      if (width === 390 && ['/', '/animaux/3', '/admin', '/admin/animaux'].includes(route)) await capture(page, `mobile-${route.replaceAll('/', '-').replace(/^-/, '') || 'home'}`)
    }
  }, { width, authenticated: true }))
}

test('mobile navigation opens, closes with Escape, restores focus and navigates', async () => withPage(async page => {
  await open(page, '/')
  const toggle = page.getByRole('button', { name: 'Ouvrir le menu' })
  await toggle.click(); assert.equal(await page.getByRole('button', { name: 'Fermer le menu' }).getAttribute('aria-expanded'), 'true')
  await page.keyboard.press('Escape')
  assert.equal(await toggle.evaluate(element => element === document.activeElement), true)
  await toggle.click()
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Annonces', exact: true }).click()
  await page.getByRole('heading', { name: 'Une histoire à partager.' }).waitFor()
  assert.equal(await toggle.getAttribute('aria-expanded'), 'false')
}, { width: 390 }))

for (const width of [320, 390, 768]) {
  test(`unauthenticated login form and password control remain usable at ${width}px`, async () => withPage(async page => {
    await open(page, '/admin/connexion')
    await page.getByLabel('Adresse e-mail').fill('admin@example.test')
    const password = page.getByLabel('Mot de passe', { exact: true })
    await password.fill('fixture-test-only')
    await page.getByRole('button', { name: 'Afficher le mot de passe', exact: true }).click()
    assert.equal(await password.getAttribute('type'), 'text')
    await page.getByRole('button', { name: 'Masquer le mot de passe', exact: true }).click()
    assert.equal(await password.getAttribute('type'), 'password')
    await noOverflow(page)
    if (width === 390) await capture(page, 'login-mobile')
  }, { width }))
}

test('local photos and fonts load, photographs stay illustrative, reduced motion disables effects', async () => withPage(async page => {
  await open(page, '/'); await capture(page, 'home-review', { fullPage: false })
  assert.equal(await page.locator('img').evaluateAll(images => images.every(img => img.complete && img.naturalWidth > 0 && img.alt.includes('illustration'))), true)
  assert.equal(await page.evaluate(() => document.fonts.check('400 16px "DM Sans"') && document.fonts.check('400 30px "DM Serif Display"')), true)
  assert.equal(await page.locator('.hero-floating-paw').evaluate(element => getComputedStyle(element).animationName), 'none')
  assert.equal(await page.locator('.reveal').evaluateAll(elements => elements.every(element => getComputedStyle(element).opacity === '1')), true)
}))

test('offline catalogue and announcements expose safe retry actions', async () => withPage(async page => {
  await open(page, '/')
  await page.getByRole('heading', { name: 'Les animaux se font un peu attendre.' }).waitFor()
  await page.getByRole('heading', { name: 'Les annonces se font attendre.' }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Réessayer', exact: true }).isVisible(), true)
}, { failAnimals: true, failAnnouncements: true }))

test('empty catalogue never substitutes stock-photo animals or invented statistics', async () => withPage(async page => {
  await open(page, '/')
  await page.getByRole('heading', { name: 'La liste est encore toute calme.' }).waitFor()
  assert.equal(await page.locator('.animal-card, .available-spotlight').count(), 0)
}, { empty: true }))
