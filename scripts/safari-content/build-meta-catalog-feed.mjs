import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const origin = 'https://safaritourssharm.com';
const esc = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');

/** Hidden by default. Publication changes visibility, never dated seat availability. */
export function metaFeed(items, { visibility = 'staging', reviewed = false } = {}) {
  if (!['staging', 'published'].includes(visibility) || (visibility === 'published' && reviewed !== true)) throw Error('Publication requires explicit reviewed mode');
  if (!items.length || new Set(items.map(item => item.id)).size !== items.length) throw Error('Empty/duplicate catalog');
  const entries = items.map(item => {
    const image = item.images.find(image => image.isCover) ?? item.images[0];
    const page = new URL(item.copy.en.url);
    const img = image && new URL(image.url);
    if (!image || img.origin !== origin || page.origin !== origin || page.search || page.hash || img.search || img.hash) throw Error('Unverified image/canonical URL');
    if (!/^\d+\.\d{2}$/.test(item.price.amount) || Number(item.price.amount) <= 0 || item.price.currencyCode !== 'EUR') throw Error('Invalid trusted price');
    if (!['PER_PERSON', 'PER_UNIT'].includes(item.priceBasis)) throw Error('Unknown price basis');
    if (item.bookingMode !== 'REQUEST_SUBJECT_TO_CONFIRMATION' || item.readinessIssues.length) throw Error('Unready item');
    const basis = item.priceBasis === 'PER_PERSON' ? 'per person' : 'per unit';
    const options = (item.priceOptions ?? []).map(option => `${option.label}: ${option.price.amount} ${option.price.currencyCode} per unit`).join('; ');
    const description = `${item.copy.en.description}\nPrice basis: ${basis}.${options ? ` Options: ${options}.` : ''}${item.pricingNote ? ` ${item.pricingNote}.` : ''}\nCheck the tour page for inclusions, restrictions and the applicable cancellation policy.`;
    if (description.length > 9999) throw Error('Description too long');
    const fields = {
      id: item.id, title: `${item.copy.en.title} — ${basis}`, description,
      link: page.href, image_link: img.href,
      availability: 'available for order', visibility,
      condition: 'new', price: `${item.price.amount} EUR`,
      brand: 'Safari Tours Sharm', product_type: item.category,
      custom_label_0: item.category, custom_label_1: item.bookingMode,
      custom_label_2: item.priceBasis,
    };
    return '<item>\n' + Object.entries(fields).map(([key, value]) => `  <g:${key}>${esc(value)}</g:${key}>`).join('\n') + '\n</item>';
  });
  return `<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0" xmlns:g="http://base.google.com/ns/1.0"><channel>\n<title>Safari Tours Sharm — ${visibility === 'staging' ? 'Hidden catalog review' : 'Reviewed tour catalog'}</title>\n<link>${origin}</link>\n<description>Request-only experiences. Not a stock or booking confirmation authority.</description>\n${entries.join('\n')}\n</channel></rss>\n`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [input, output, mode] = process.argv.slice(2);
  if (!input || !output || !['--sample', '--all-hidden', '--all-visible-reviewed'].includes(mode)) throw Error('Usage: node build-meta-catalog-feed.mjs snapshot.json output.xml --sample|--all-hidden|--all-visible-reviewed');
  const snapshot = JSON.parse(await readFile(resolve(input), 'utf8'));
  if (snapshot.source !== `${origin}/api/v1/tours-operator/tours?locale=en`) throw Error('Unexpected source');
  if (!Number.isFinite(Date.parse(snapshot.capturedAt)) || Math.abs(Date.now() - Date.parse(snapshot.capturedAt)) > 24 * 60 * 60 * 1000) throw Error('Refresh snapshot before import');
  const items = mode === '--sample' ? snapshot.items.slice(0, 1) : snapshot.items;
  const visibility = mode === '--all-visible-reviewed' ? 'published' : 'staging';
  await writeFile(resolve(output), metaFeed(items, { visibility, reviewed: mode === '--all-visible-reviewed' }), { flag: 'wx' });
  console.log(JSON.stringify({ output: resolve(output), count: items.length, visibility, availability: 'available for order', imported: false }));
}
