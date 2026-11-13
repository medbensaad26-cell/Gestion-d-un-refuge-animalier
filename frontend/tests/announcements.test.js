import assert from 'node:assert/strict'
import test from 'node:test'
import { AnnouncementFilterError, announcementQuery, filtersFromSearchParams, getAnnouncements, publicationDateLabel } from '../src/services/announcements.js'

const fixture = { id: 2, titre: 'Adoptez Mimi', description: 'Une chatte calme.', datePublication: '2026-11-07', statut: 'PUBLIEE', animalId: 11, adminId: 1 }
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

test('public announcements use GET without authentication and retain only display fields', async () => {
  const controller = new AbortController()
  const calls = []
  const items = await getAnnouncements({ signal: controller.signal, fetchImpl: async (...args) => { calls.push(args); return response([fixture]) } })
  assert.deepEqual(calls, [['/api/annonces', { method: 'GET', headers: { Accept: 'application/json' }, credentials: 'omit', cache: 'no-store', signal: controller.signal }]])
  assert.deepEqual(items, [{ id: 2, titre: 'Adoptez Mimi', description: 'Une chatte calme.', datePublication: '2026-11-07', animalId: 11 }])
})

test('encodes combined search criteria and never sends administrator options', async () => {
  let url
  await getAnnouncements({ filters: { q: '  chat & câlin  ', espece: ' Chat ', statutAnimal: 'en_soin', adminId: '1', statut: 'INTERNE' }, fetchImpl: async (value) => { url = value; return response([]) } })
  const parsed = new URL(url, 'http://localhost')
  assert.equal(parsed.pathname, '/api/annonces')
  assert.deepEqual([...parsed.searchParams], [['q', 'chat & câlin'], ['espece', 'Chat'], ['statutAnimal', 'EN_SOIN']])
})

test('an empty response is preserved without invented announcements', async () => {
  assert.deepEqual(await getAnnouncements({ fetchImpl: async () => response([]) }), [])
  assert.equal(announcementQuery({ q: ' ', espece: '', statutAnimal: '' }), '')
})

test('sorts newest publication dates first, breaks ties by ID, and preserves missing descriptions', async () => {
  const records = [fixture, { ...fixture, id: 3, description: null, datePublication: '2026-11-08' }, { ...fixture, id: 4, datePublication: '2026-11-08' }]
  const result = await getAnnouncements({ fetchImpl: async () => response(records) })
  assert.deepEqual(result.map(row => row.id), [4, 3, 2])
  assert.equal(result[1].description, null)
  assert.equal(records[0].id, 2)
})

for (const body of [null, {}, [null], [{ ...fixture, statut: 'INTERNE' }], [{ ...fixture, statut: 'publiee' }], [{ ...fixture, animalId: -1 }], [{ ...fixture, datePublication: '2026-02-30' }], [{ ...fixture, titre: '' }], [{ ...fixture, description: {} }], [fixture, fixture]]) {
  test(`rejects incompatible or nonpublic data ${JSON.stringify(body)}`, async () => {
    await assert.rejects(getAnnouncements({ fetchImpl: async () => response(body) }), /annonces/)
  })
}

for (const filters of [{ q: 'x'.repeat(151) }, { espece: 'x'.repeat(101) }, { statutAnimal: 'UNKNOWN' }, { q: 123 }]) {
  test(`invalid filters ${JSON.stringify(filters)} do not send a request`, async () => {
    let fetched = false
    await assert.rejects(getAnnouncements({ filters, fetchImpl: async () => { fetched = true } }), AnnouncementFilterError)
    assert.equal(fetched, false)
  })
}

test('URL filters reject duplicate or unsupported parameters and normalize valid criteria', () => {
  for (const query of ['q=a&q=b', 'adminId=1', 'statut=INTERNE', 'statutAnimal=UNKNOWN']) {
    assert.throws(() => filtersFromSearchParams(new URLSearchParams(query)), AnnouncementFilterError)
  }
  assert.deepEqual(filtersFromSearchParams(new URLSearchParams('q=Mimi&espece=Chat&statutAnimal=disponible')), { q: 'Mimi', espece: 'Chat', statutAnimal: 'DISPONIBLE' })
})

test('HTTP and JSON errors do not expose response content', async () => {
  for (const status of [400, 401, 500]) {
    await assert.rejects(getAnnouncements({ fetchImpl: async () => new Response('private diagnostics', { status }) }), (error) => {
      assert.match(error.message, /réessayer/)
      assert.doesNotMatch(error.message, /private|diagnostics/)
      return true
    })
  }
  await assert.rejects(getAnnouncements({ fetchImpl: async () => new Response('private malformed JSON') }), /annonces/)
})

test('network errors are friendly and cancellation is preserved through fetch and JSON parsing', async () => {
  await assert.rejects(getAnnouncements({ fetchImpl: async () => { throw new Error('private network diagnostics') } }), /connexion/)
  const aborted = new DOMException('cancelled', 'AbortError')
  for (const fetchImpl of [async () => { throw aborted }, async () => ({ ok: true, json: async () => { throw aborted } })]) {
    await assert.rejects(getAnnouncements({ fetchImpl }), (error) => error === aborted)
  }
})

test('publication dates are formatted in French without timezone shifts', () => {
  assert.equal(publicationDateLabel('2026-11-07'), '7 novembre 2026')
  assert.equal(publicationDateLabel('2026-02-30'), 'Date non renseignée')
})
