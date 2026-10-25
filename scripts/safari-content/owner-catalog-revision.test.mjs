import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import test from 'node:test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const read = (path) => readFileSync(resolve(root, path), 'utf8')
const correction = JSON.parse(read('clients/safari-tours-sharm/marketing/owner-corrections/2026-10-25_catalog-revision-part2.json'))
const migration = read('platform/application/src/main/resources/db/migration/data/V37__tours_operator_owner_catalog_revision_part2.sql')
const fixtures = JSON.parse(read('platform/apps/safari-tours-sharm/src/test/resources/catalog/v36-owner-confirmation-content.json'))
const drafts = Object.fromEntries(['en', 'ar', 'ru', 'it'].map((locale) => [locale,
  JSON.parse(read(`clients/safari-tours-sharm/content-research/drafts/tour-content-drafts.${locale}.json`)),
]))
const content = (locale, slug) => drafts[locale].tours.find((tour) => tour.slug === slug).content

test('review source, pending SQL and four localized drafts describe the same unique changes', () => {
  const embedded = JSON.parse(migration.match(/\$patches\$(.*?)\$patches\$/s)[1])
  assert.deepEqual(embedded, correction.contentPatches)
  const keys = correction.contentPatches.map((patch) => `${patch.slug}/${patch.locale}/${patch.field}`)
  assert.equal(new Set(keys).size, keys.length)
  for (const patch of correction.contentPatches) {
    assert.deepEqual(content(patch.locale, patch.slug)[patch.field], patch.after, `${patch.slug}/${patch.locale}/${patch.field}`)
    const baseline = fixtures.find((entry) => entry.slug === patch.slug && entry.locale === patch.locale)
    if (baseline) assert.deepEqual(baseline.content[patch.field], patch.before, `V36 baseline ${patch.slug}/${patch.locale}/${patch.field}`)
  }
})

test('beginner course has no advanced prerequisite or blanket medical exemption in any locale', () => {
  const forbidden = {
    en: ['An Open Water certification is required.', 'Not suitable for beginners.', 'Designed for certified divers', 'No medical questionnaire'],
    ar: ['شهادة Open Water مطلوبة.', 'غير مناسبة للمبتدئين.', 'لا يحتاج استبيان', 'لا يحتاج كشف طبي'],
    ru: ['Требуется сертификат Open Water.', 'Не подходит для начинающих.', 'Курс рассчитан на дайверов с сертификатом'],
    it: ['È richiesto il brevetto Open Water.', 'Non adatto ai principianti.', 'Pensato per subacquei già brevettati'],
  }
  const requiredMedical = {
    en: ['medical questionnaire is required', 'Physician approval may be required'],
    ar: ['يلزم استكمال استبيان طبي', 'قد تُطلب موافقة طبيب'],
    ru: ['Необходимо заполнить медицинскую анкету', 'может потребоваться разрешение врача'],
    it: ['È obbligatorio compilare un questionario medico', 'potrebbe essere necessaria l’approvazione di un medico'],
  }
  for (const locale of Object.keys(drafts)) {
    const course = JSON.stringify(content(locale, 'diving-course'))
    assert.ok(course.includes('PADI Open Water Diver'))
    for (const phrase of forbidden[locale]) assert.ok(!course.includes(phrase), `${locale}: stale or unsafe phrase ${phrase}`)
    for (const phrase of requiredMedical[locale]) assert.ok(course.includes(phrase), `${locale}: missing ${phrase}`)
  }
})

test('lunch resolution, optional tips, transfer separation and single dolphin show remain explicit', () => {
  assert.deepEqual(correction.mealsPending, [])
  const checks = {
    en: ['Lunch in Dahab', 'Optional tips', 'from the agreed meeting time with the driver', 'One show daily at 14:30.'],
    ar: ['غداء في دهب', 'اختيارية وليست إلزامية', 'من موعد مقابلة السائق المتفق عليه', 'عرض واحد يوميًا الساعة 14:30.'],
    ru: ['Обед в Дахабе', 'по желанию, не обязательны', 'с согласованного времени встречи с водителем', 'Одно шоу ежедневно в 14:30.'],
    it: ['Pranzo a Dahab', 'facoltative', 'dall’orario concordato per l’incontro con l’autista', 'Un solo spettacolo al giorno alle 14:30.'],
  }
  for (const [locale, terms] of Object.entries(checks)) {
    assert.ok(JSON.stringify(content(locale, 'st-catherine-dahab').includes).includes(terms[0]))
    assert.ok(JSON.stringify(content(locale, 'speed-boat-adventure').excludes).includes(terms[1]))
    const transfer = content(locale, 'sharm-airport-transfer')
    assert.deepEqual(transfer.excludes, [])
    assert.ok(JSON.stringify(transfer.knowBeforeYouGo).includes(terms[2]))
    assert.ok(content(locale, 'dolphin-show').knowBeforeYouGo.includes(terms[3]))
  }
  assert.deepEqual(correction.tourPatches.map((patch) => patch.field), ['name_en', 'duration_text'])
  assert.equal(correction.status, 'PREPARED_NOT_DEPLOYED')
  assert.ok(correction.notImplemented.some((line) => line.includes('Child-age pricing')))
})
