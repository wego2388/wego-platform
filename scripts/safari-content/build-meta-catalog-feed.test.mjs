import { test } from 'node:test';
import assert from 'node:assert/strict';
import { metaFeed } from './build-meta-catalog-feed.mjs';

const item = { id: 'tour-1', price: { amount: '30.00', currencyCode: 'EUR' }, priceBasis: 'PER_UNIT', images: [{ url: 'https://safaritourssharm.com/images/catalog/buggy.webp', isCover: true }], copy: { en: { title: 'Buggy & Camel', description: 'Request <review>', url: 'https://safaritourssharm.com/en/tour/buggy' } }, category: 'DESERT', bookingMode: 'REQUEST_SUBJECT_TO_CONFIRMATION', readinessIssues: [], priceOptions: [{ label: 'Two-seat buggy', price: { amount: '30.00', currencyCode: 'EUR' } }] };

test('hidden feed preserves EUR price, stable ID and per-unit semantics, escapes XML', () => {
  const xml = metaFeed([item]);
  assert.match(xml, /<g:visibility>staging<\/g:visibility>/);
  assert.match(xml, /<g:availability>available for order<\/g:availability>/);
  assert.match(xml, /<g:price>30.00 EUR<\/g:price>/);
  assert.match(xml, /Buggy &amp; Camel — per unit/);
  assert.match(xml, /Request &lt;review&gt;/);
  assert.match(xml, /Two-seat buggy: 30.00 EUR per unit/);
  assert.doesNotMatch(xml, /in stock|sale_price|quantity_to_sell|aggregate_rating/i);
});

test('rejects empty, duplicate, unready, off-site, PII-query and guessed-price input', () => {
  assert.throws(() => metaFeed([]));
  assert.throws(() => metaFeed([item, item]));
  for (const changed of [{ images: [] }, { readinessIssues: ['NO_IMAGE'] }, { priceBasis: 'UNKNOWN' }, { price: { amount: '0.00', currencyCode: 'EUR' } }, { price: { amount: '30.00', currencyCode: 'EGP' } }, { copy: { en: { ...item.copy.en, url: item.copy.en.url + '?email=private' } } }, { images: [{ url: 'https://other.test/test.webp' }] }]) assert.throws(() => metaFeed([{ ...item, ...changed }]));
});

test('publication is explicit and changes visibility without manufacturing stock or price', () => {
  assert.throws(() => metaFeed([item], { visibility: 'published' }), /explicit reviewed/);
  assert.throws(() => metaFeed([item], { visibility: 'in stock', reviewed: true }));
  const hidden = metaFeed([item]);
  const visible = metaFeed([item], { visibility: 'published', reviewed: true });
  assert.match(visible, /<g:visibility>published<\/g:visibility>/);
  assert.match(visible, /<g:availability>available for order<\/g:availability>/);
  assert.doesNotMatch(visible, /in stock|quantity_to_sell|sale_price/i);
  const fields = xml => xml.match(/<item>[\s\S]*?<\/item>/)[0].replace(/<g:visibility>.*?<\/g:visibility>/, '');
  assert.equal(fields(hidden), fields(visible));
  assert.throws(() => metaFeed([{ ...item, readinessIssues: ['UNVERIFIED_PRICE'] }], { visibility: 'published', reviewed: true }));
});
