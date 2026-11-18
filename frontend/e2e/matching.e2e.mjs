import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import os from 'node:os'
import { mkdir } from 'node:fs/promises'
import { chromium } from 'playwright'

// Tests the actual production React bundle. This server has NO /api proxy or database access.
const dist = fileURLToPath(new URL('../dist/', import.meta.url))
const artifacts = process.env.REFUGE_E2E_ARTIFACTS || path.join(os.tmpdir(), `refuge-matching-ui-${Date.now()}`)
let server, browser, base
const explanation = 'Espece : correspond ; Age (ADULTE) : correspond ; Sexe : correspond.'
const recommendations = [
  { animal: { id: 3, nom: 'Luna', espece: 'Chien', age: 4, sexe: 'FEMELLE', statut: 'DISPONIBLE' }, score: 100, explication: explanation },
  { animal: { id: 4, nom: 'Nino', espece: 'Chien', age: 8, sexe: 'FEMELLE', statut: 'DISPONIBLE' }, score: 70, explication: 'Espece : correspond ; Age (ADULTE) : different ; Sexe : correspond.' },
  { animal: { id: 5, nom: 'Mimi', espece: 'Chat', age: 4, sexe: 'FEMELLE', statut: 'DISPONIBLE' }, score: 50, explication: 'Espece : differente ; Age (ADULTE) : correspond ; Sexe : correspond.' },
]
const neutral = recommendations.map(r => ({ ...r, score: 0, explication: 'Aucune preference renseignee : score non evalue (0), ordre par identifiant.' }))
const fixtureAnimal = id => ({ ...recommendations.find(r => r.animal.id === Number(id))?.animal, race: null, etatSante: null })

before(async () => {
  await mkdir(artifacts, { recursive: true })
  server = createServer(async (req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname
    if (pathname.startsWith('/api/')) { res.writeHead(503, { 'Content-Type': 'application/json' }); res.end('{"erreur":"Unmocked API blocked"}'); return }
    try {
      const relative = decodeURIComponent(pathname).replace(/^\/+/, '')
      const target = path.resolve(dist, relative || 'index.html')
      if (!target.startsWith(path.resolve(dist) + path.sep)) { res.writeHead(403); res.end(); return }
      let data
      try { data = await readFile(target) } catch { data = await readFile(path.join(dist, 'index.html')) }
      const extension = path.extname(target)
      res.writeHead(200, { 'Content-Type': ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.woff2': 'font/woff2' })[extension] || 'text/html' })
      res.end(data)
    } catch { res.writeHead(500); res.end('Static test server error') }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true, channel: process.env.REFUGE_E2E_CHANNEL || (process.platform === 'win32' ? 'msedge' : undefined) })
})

after(async () => { await browser?.close(); await new Promise(resolve => server.close(resolve)); console.log(`Browser screenshots: ${artifacts}`) })

async function withPage(run, { matching, width = 1440 } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, serviceWorkers: 'block' })
  const page = await context.newPage()
  const calls = [], violations = [], crashes = []
  page.on('pageerror', error => crashes.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url())
    calls.push({ path: url.pathname, query: url.search, method: request.method(), headers: request.headers() })
    if (request.method() !== 'GET') { violations.push('Unexpected write request'); await route.fulfill({ status: 405, json: { erreur: 'Writes forbidden in UI tests' } }); return }
    if (url.pathname === '/api/matching' && matching) { await matching(route, url); return }
    if (url.pathname === '/api/animaux') { await route.fulfill({ json: recommendations.map(r => fixtureAnimal(r.animal.id)) }); return }
    if (/^\/api\/animaux\/\d+$/.test(url.pathname)) { await route.fulfill({ json: fixtureAnimal(url.pathname.split('/').at(-1)) }); return }
    if (url.pathname === '/api/annonces') { await route.fulfill({ json: [] }); return }
    violations.push('Unexpected API read: ' + url.pathname)
    await route.fulfill({ status: 503, json: { erreur: 'Unexpected test request blocked' } })
  })
  try { await run(page, calls); assert.deepEqual(violations, []); assert.deepEqual(crashes, []) } finally { await context.close() }
}

const waitFor = async (page, selector) => { await page.locator(selector).first().waitFor({ state: 'visible' }) }
async function chooseAll(page) {
  await page.getByRole('radio', { name: 'Un chien', exact: true }).check()
  await page.getByRole('radio', { name: /^Adulte/ }).check()
  await page.getByRole('radio', { name: 'Femelle', exact: true }).check()
}
const submit = page => page.getByRole('button', { name: 'Voir mes recommandations', exact: true }).click()
const results = page => page.locator('.matching-card')

test('homepage call-to-action and public navigation open the real French questionnaire', async () => withPage(async (page, calls) => {
  await page.goto(base)
  await page.getByRole('link', { name: 'Trouvez votre compagnon idéal', exact: true }).click()
  assert.equal(new URL(page.url()).pathname, '/trouver-compagnon')
  await page.getByRole('heading', { level: 1, name: /Trouvez votre compagnon idéal/ }).waitFor()
  assert.equal(await page.locator('form').getByRole('group').count(), 3)
  assert.equal(await page.getByRole('radio', { name: 'Sans préférence', exact: true }).count(), 3)
  assert.equal(await page.getByRole('link', { name: 'Trouver un compagnon', exact: true }).getAttribute('aria-current'), 'page')
  assert.equal(calls.filter(c => c.path === '/api/matching').length, 0)
  assert.match(await page.title(), /compagnon idéal/)
}))

test('questionnaire sends exact public GET parameters, renders backend scores and focus, and links both existing flows', async () => withPage(async (page, calls) => {
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page)
  await waitFor(page, '.matching-card')
  assert.equal(await results(page).count(), 3)
  assert.deepEqual((await page.locator('.matching-card h3').allTextContents()), ['Luna', 'Nino', 'Mimi'])
  const call = calls.find(c => c.path === '/api/matching')
  assert.equal(call.query, '?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE'); assert.equal(call.method, 'GET')
  assert.equal(call.headers.authorization, undefined); assert.equal(call.headers.cookie, undefined)
  assert.deepEqual(await page.locator('.matching-score strong').allTextContents(), ['100 / 100', '70 / 100', '50 / 100'])
  assert.match(await page.locator('.matching-explanation').first().textContent(), /Espece : correspond/)
  assert.equal(await page.locator('#matching-results-title').evaluate(el => el === document.activeElement), true)
  assert.equal(await page.getByRole('link', { name: 'Voir la fiche de Luna', exact: true }).getAttribute('href'), '/animaux/3')
  assert.equal(await page.getByRole('link', { name: /Faire une demande pour Luna/ }).getAttribute('href'), '/animaux/3/demande-adoption')
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
  await page.screenshot({ path: path.join(artifacts, 'matching-desktop-results.png'), fullPage: true })
}, { matching: route => route.fulfill({ json: recommendations }) }))

test('no preferences request has no query and displays neutral cards, never a genuine zero score', async () => withPage(async (page, calls) => {
  await page.goto(base + '/trouver-compagnon'); await submit(page); await waitFor(page, '.matching-card')
  assert.equal(calls.find(c => c.path === '/api/matching').query, '')
  assert.equal(await page.getByText('Score non évalué', { exact: true }).count(), 3)
  assert.equal(await page.locator('.matching-score').count(), 0)
  assert.match(await page.locator('.matching-results-description').textContent(), /aucune préférence/)
}, { matching: route => route.fulfill({ json: neutral }) }))

test('zero with supplied preferences remains meaningful preference agreement without adoption claims', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page); await waitFor(page, '.matching-card')
  assert.equal(await page.locator('.matching-score strong').textContent(), '0 / 100')
  assert.match(await page.locator('.matching-score').textContent(), /Accord avec vos préférences/)
}, { matching: route => route.fulfill({ json: [{ ...recommendations[0], score: 0, explication: 'Espece : differente ; Age (ADULTE) : different ; Sexe : different.' }] }) }))

test('empty availability is a successful empty state without fabricated results', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon'); await submit(page)
  await page.getByRole('heading', { name: 'Aucun animal disponible pour le moment.' }).waitFor()
  assert.equal(await results(page).count(), 0); assert.equal(await page.locator('.matching-score').count(), 0)
}, { matching: route => route.fulfill({ json: [] }) }))

test('server failure is safe, can retry, and never automatically repeats or exposes diagnostics', async () => {
  let count = 0
  await withPage(async (page, calls) => {
    await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page)
    await page.getByRole('alert').waitFor(); assert.doesNotMatch(await page.locator('body').textContent(), /PRIVATE DIAGNOSTIC/)
    assert.equal(calls.filter(c => c.path === '/api/matching').length, 1)
    await page.getByRole('button', { name: 'Réessayer les recommandations' }).click(); await waitFor(page, '.matching-card')
    assert.equal(calls.filter(c => c.path === '/api/matching').length, 2)
  }, { matching: route => ++count === 1 ? route.fulfill({ status: 500, json: { erreur: 'PRIVATE DIAGNOSTIC' } }) : route.fulfill({ json: recommendations }) })
})

test('network failure displays a readable retry message', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon'); await submit(page); await page.getByRole('alert').waitFor()
  assert.match(await page.getByRole('alert').textContent(), /connexion/)
}, { matching: route => route.abort('failed') }))

test('invalid or unavailable API records never become recommendations', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page); await page.getByRole('alert').waitFor()
  assert.match(await page.getByRole('alert').textContent(), /incomplètes/)
  assert.equal(await results(page).count(), 0)
}, { matching: route => route.fulfill({ json: [{ ...recommendations[0], animal: { ...recommendations[0].animal, statut: 'ADOPTE' } }] }) }))

test('loading is announced, duplicate submissions are blocked, and reset cancels the pending response', async () => {
  let finish
  const held = new Promise(resolve => { finish = resolve })
  await withPage(async (page, calls) => {
    await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page)
    await page.getByRole('heading', { name: 'Nous cherchons vos compagnons…' }).waitFor()
    assert.equal(await page.locator('.matching-results').getAttribute('aria-busy'), 'true')
    const button = page.getByRole('button', { name: 'Recherche en cours…' })
    assert.equal(await button.isDisabled(), true)
    await button.evaluate(el => el.click())
    assert.equal(calls.filter(c => c.path === '/api/matching').length, 1)
    await page.getByRole('button', { name: 'Réinitialiser', exact: true }).click()
    finish(); await page.getByText('Choisissez ce qui vous tient à cœur,', { exact: false }).waitFor()
    assert.equal(await results(page).count(), 0)
    assert.equal(await page.getByRole('radio', { name: 'Sans préférence', exact: true }).filter({ visible: true }).count(), 3)
    assert.equal(await page.locator('input[type=radio]:checked').evaluateAll(inputs => inputs.every(input => input.value === '')), true)
  }, { matching: async route => { await held; await route.fulfill({ json: recommendations }).catch(() => {}) } })
})

test('editing preferences clears previous results instead of relabelling stale scores', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page); await waitFor(page, '.matching-card')
  await page.getByRole('radio', { name: 'Un chat', exact: true }).check()
  assert.equal(await results(page).count(), 0)
  assert.equal(await page.locator('.matching-results').getAttribute('aria-busy'), 'false')
}, { matching: route => route.fulfill({ json: recommendations }) }))

test('a superseded in-flight request cannot replace results for newly submitted preferences', async () => {
  let firstRoute, count = 0
  const newer = { ...recommendations[2], score: 100, explication: 'Espece : correspond.' }
  await withPage(async page => {
    await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page)
    await page.getByRole('heading', { name: 'Nous cherchons vos compagnons…' }).waitFor()
    await page.getByRole('radio', { name: 'Un chat', exact: true }).check()
    await submit(page); await waitFor(page, '.matching-card')
    assert.deepEqual(await page.locator('.matching-card h3').allTextContents(), ['Mimi'])
    await firstRoute?.fulfill({ json: recommendations }).catch(() => {})
    assert.deepEqual(await page.locator('.matching-card h3').allTextContents(), ['Mimi'])
  }, { matching: route => { count++; if (count === 1) { firstRoute = route; return } return route.fulfill({ json: [newer] }) } })
})

test('leaving the questionnaire cancels pending matching without updating the destination page', async () => {
  let pending
  await withPage(async page => {
    await page.goto(base + '/trouver-compagnon'); await submit(page)
    await page.getByRole('heading', { name: 'Nous cherchons vos compagnons…' }).waitFor()
    await page.getByRole('link', { name: 'Accueil', exact: true }).first().click()
    await page.getByRole('heading', { level: 1, name: /Une rencontre/ }).waitFor()
    await pending?.fulfill({ json: neutral }).catch(() => {})
    assert.equal(await page.locator('.matching-results').count(), 0)
  }, { matching: route => { pending = route } })
})

test('native questionnaire radios support keyboard arrows and visible focus', async () => withPage(async page => {
  await page.goto(base + '/trouver-compagnon')
  await page.getByRole('radio', { name: 'Un chien', exact: true }).focus()
  await page.keyboard.press('ArrowRight')
  assert.equal(await page.getByRole('radio', { name: 'Un chat', exact: true }).isChecked(), true)
  assert.equal(await page.getByRole('radio', { name: 'Un chat', exact: true }).evaluate(el => getComputedStyle(el.closest('label')).outlineStyle), 'solid')
  await page.getByRole('button', { name: 'Réinitialiser', exact: true }).click()
  assert.equal(await page.locator('input[name=espece]').first().evaluate(el => el === document.activeElement), true)
}))

test('profile and adoption navigation use existing routes and never submit a request', async () => withPage(async (page, calls) => {
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page); await waitFor(page, '.matching-card')
  await page.getByRole('link', { name: 'Voir la fiche de Luna', exact: true }).click()
  await page.getByRole('heading', { level: 1, name: 'Luna' }).waitFor(); assert.equal(new URL(page.url()).pathname, '/animaux/3')
  await page.goto(base + '/trouver-compagnon'); await chooseAll(page); await submit(page); await waitFor(page, '.matching-card')
  await page.getByRole('link', { name: /Faire une demande pour Luna/ }).click()
  await page.getByRole('heading', { name: 'Vos coordonnées', exact: true }).waitFor()
  assert.equal(new URL(page.url()).pathname, '/animaux/3/demande-adoption')
  assert.equal(calls.some(c => c.path === '/api/demandes' || c.method !== 'GET'), false)
}, { matching: route => route.fulfill({ json: recommendations }) }))

for (const width of [320, 390, 768]) {
  test(`questionnaire and results remain readable without horizontal overflow at ${width}px`, async () => withPage(async page => {
    await page.goto(base + '/trouver-compagnon')
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await chooseAll(page); await submit(page); await waitFor(page, '.matching-card')
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    assert.equal(await results(page).count(), 3)
    if (width === 390) {
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }))
      await page.screenshot({ path: path.join(artifacts, 'matching-mobile-results.png'), fullPage: true })
    }
  }, { width, matching: route => route.fulfill({ json: recommendations }) }))
}
