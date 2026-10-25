import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const origin = 'https://safaritourssharm.com';
const locales = ['en', 'ar', 'ru', 'it'];
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const caveats = {
  en: 'Booking request only. Our team checks availability before confirming. WhatsApp is for enquiries.',
  ar: 'طلب حجز وليس تأكيدًا فوريًا. يراجع فريقنا التوافر قبل التأكيد. واتساب للاستفسارات.',
  ru: 'Заявка на бронирование, не мгновенное подтверждение. Наша команда проверит наличие мест. WhatsApp — для вопросов.',
  it: 'Richiesta di prenotazione, non conferma immediata. Il nostro team verifica la disponibilità. WhatsApp per informazioni.',
};

/** Review data, NOT an inventory feed. No inferred stock, sale prices or reviews. */
export function normalizeTour(tour, contents, images) {
  if (!tour.isActive || !tour.id || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(tour.slug)) throw Error('Invalid active tour');
  if (!['PER_PERSON', 'PER_UNIT'].includes(tour.priceBasis)) throw Error('Unrecognized price basis');
  if (!/^\d+\.\d{2}$/.test(tour.priceAdult?.amount) || Number(tour.priceAdult.amount) <= 0 || tour.priceAdult.currencyCode !== 'EUR') throw Error('Unverified price/currency');
  const copy = Object.fromEntries(locales.map(locale => {
    const content = contents[locale];
    if (content?.tourId !== tour.id || content.slug !== tour.slug || content.servedLocale !== locale || !content.name || !content.shortDescription) throw Error(`Missing translated source: ${tour.slug}/${locale}`);
    return [locale, {
      title: content.name,
      description: `${content.shortDescription}\n\n${caveats[locale]}`,
      url: `${origin}/${locale}/tour/${tour.slug}`,
      includes: content.includes,
      excludes: content.excludes,
      knowBeforeYouGo: content.knowBeforeYouGo,
      meetingPoint: content.meetingPoint,
      facts: content.facts,
    }];
  }));
  return {
    id: tour.id, slug: tour.slug, category: tour.category,
    durationText: tour.durationText, price: tour.priceAdult,
    priceBasis: tour.priceBasis, priceOptions: tour.priceOptions,
    pricingNote: tour.pricingNote, childPrice: tour.priceChild,
    cancellationPolicy: tour.cancellationPolicy,
    bookingMode: 'REQUEST_SUBJECT_TO_CONFIRMATION',
    inventoryStatus: 'NOT_EXPORTED', images, copy,
    readinessIssues: images.length ? [] : ['NO_VERIFIED_PUBLISHED_IMAGE'],
  };
}

async function json(path) {
  const res = await fetch(origin + path, { signal: AbortSignal.timeout(20000), redirect: 'error' });
  if (!res.ok) throw Error(`Public catalog read failed: ${res.status}`);
  return res.json();
}

async function imageUrl(path) {
  if (!path || !/^\/(?:media\/tours|images\/catalog)\//.test(path)) return null;
  const url = new URL(path, origin);
  if (url.origin !== origin || url.search || url.hash) return null;
  const res = await fetch(url, { method: 'HEAD', signal: AbortSignal.timeout(15000), redirect: 'error' });
  return res.ok && /^image\//.test(res.headers.get('content-type') ?? '') ? url.href : null;
}

export async function exportCatalog() {
  const curated = JSON.parse(await readFile(resolve(root, 'web/apps/safari-tours-sharm-site/app/content/curatedMedia.json'), 'utf8'));
  const tours = await json('/api/v1/tours-operator/tours?locale=en');
  if (!Array.isArray(tours) || !tours.length || new Set(tours.map(t => t.id)).size !== tours.length) throw Error('Catalog count/identity failure');
  const items = [];
  // One tour at a time: at most four public reads, no staff API or booking data.
  for (const tour of tours.filter(t => t.isActive)) {
    const contents = Object.fromEntries(await Promise.all(locales.map(async locale => [locale, await json(`/api/v1/tours-operator/tours/by-slug/content?slug=${encodeURIComponent(tour.slug)}&locale=${locale}`)])));
    const candidates = contents.en.media?.length ? contents.en.media : curated.filter(i => i.tour === tour.slug);
    const images = [];
    for (const candidate of candidates) {
      const url = await imageUrl(candidate.path);
      if (url && !images.some(i => i.url === url)) images.push({ url, isCover: candidate.isCover, alt: candidate.alt });
    }
    items.push(normalizeTour(tour, contents, images));
  }
  const capturedAt = new Date().toISOString();
  const output = resolve(root, `clients/safari-tours-sharm/marketing/social-catalog/${capturedAt.replaceAll(':', '-')}.json`);
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify({
    capturedAt, source: origin + '/api/v1/tours-operator/tours?locale=en',
    purpose: 'Review/copy source, not a published provider feed or stock authority',
    publicationStatus: 'NOT_IMPORTED',
    note: 'Refresh commercial facts before import; do not map static capacity to available stock. Readiness issues block publication.',
    count: items.length, items,
  }, null, 2) + '\n', { flag: 'wx' });
  console.log(JSON.stringify({ output, count: items.length, withVerifiedImages: items.filter(t => t.images.length).length, perUnit: items.filter(t => t.priceBasis === 'PER_UNIT').length }));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv[2] !== '--write') throw Error('Use --write to export a dated public-data review snapshot. No remote writes occur.');
  await exportCatalog();
}
