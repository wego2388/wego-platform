import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeTour } from './export-social-catalog.mjs';

const tour = { id: 'tour-1', slug: 'test-tour', isActive: true, priceBasis: 'PER_UNIT', priceAdult: { amount: '30.00', currencyCode: 'EUR' }, priceOptions: [{ code: 'buggy', seatsPerUnit: 2, price: { amount: '30.00', currencyCode: 'EUR' } }], cancellationPolicy: 'NON_REFUNDABLE' };
const content = Object.fromEntries(['en', 'ar', 'ru', 'it'].map(locale => [locale, { tourId: tour.id, slug: tour.slug, servedLocale: locale, name: 'Tour', shortDescription: 'Approved copy' }]));

test('preserves per-unit pricing, exceptions and trusted ID without invented stock', () => {
  const result = normalizeTour(tour, content, [{ url: 'https://safaritourssharm.com/images/catalog/test.webp' }]);
  assert.equal(result.priceBasis, 'PER_UNIT');
  assert.deepEqual(result.priceOptions, tour.priceOptions);
  assert.equal(result.cancellationPolicy, 'NON_REFUNDABLE');
  assert.equal(result.inventoryStatus, 'NOT_EXPORTED');
  assert.equal(result.bookingMode, 'REQUEST_SUBJECT_TO_CONFIRMATION');
  assert.equal(Object.keys(result.copy).length, 4);
  assert.equal(result.copy.ar.url, 'https://safaritourssharm.com/ar/tour/test-tour');
  assert.deepEqual(result.readinessIssues, []);
});

test('missing published images are a readiness issue, not legacy URL fallback', () => {
  assert.deepEqual(normalizeTour(tour, content, []).readinessIssues, ['NO_VERIFIED_PUBLISHED_IMAGE']);
});

test('rejects guessed price, currency, inactive tours and path injection', () => {
  for (const change of [{ priceAdult: { amount: '0.00', currencyCode: 'EUR' } }, { priceAdult: { amount: '30.00', currencyCode: 'EGP' } }, { isActive: false }, { slug: '../private?email=x' }, { priceBasis: 'UNKNOWN' }]) {
    assert.throws(() => normalizeTour({ ...tour, ...change }, content, []));
  }
});

test('rejects fallback locale or content belonging to a different tour', () => {
  assert.throws(() => normalizeTour(tour, { ...content, ar: { ...content.ar, servedLocale: 'en' } }, []));
  assert.throws(() => normalizeTour(tour, { ...content, en: { ...content.en, tourId: 'other-tour' } }, []));
});
