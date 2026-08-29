import assert from "node:assert/strict";
import { access } from "node:fs/promises";
import path from "node:path";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

import {
  buildReleaseLock,
  canonicalJson,
  indexUnique,
  loadFoundryInputs,
  readJson,
  repositoryRoot,
  resolveProductForClient,
} from "./manifest-lib.mjs";

const schemaDirectory = path.join(repositoryRoot, "foundry/schemas");
const schemas = {
  client: await readJson(path.join(schemaDirectory, "client-manifest.schema.json")),
  lock: await readJson(path.join(schemaDirectory, "release-lock.schema.json")),
  moduleCatalog: await readJson(path.join(schemaDirectory, "module-catalog.schema.json")),
  product: await readJson(path.join(schemaDirectory, "product-manifest.schema.json")),
};

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

const validators = Object.fromEntries(Object.entries(schemas).map(([name, schema]) => [name, ajv.compile(schema)]));

function assertValid(name, value) {
  const validator = validators[name];
  assert.equal(
    validator(value),
    true,
    `${name} validation failed: ${ajv.errorsText(validator.errors, { separator: "\n" })}`,
  );
}

function assertInvalid(name, value) {
  assert.equal(validators[name](value), false, `${name} fixture unexpectedly passed validation`);
  assert.ok(
    validators[name].errors?.some((error) => error.keyword === "additionalProperties"),
    `${name} negative fixture must fail because unknown properties are forbidden`,
  );
}

const inputs = await loadFoundryInputs();
assertValid("moduleCatalog", inputs.moduleCatalog);

const products = inputs.products.map((entry) => entry.manifest);
const clients = inputs.clients.map((entry) => entry.manifest);
for (const product of products) assertValid("product", product);
for (const client of clients) assertValid("client", client);
for (const entry of inputs.clients) assertValid("lock", entry.releaseLock);

const productsById = indexUnique(products, (product) => product.productId, "product id");
indexUnique(clients, (client) => client.clientId, "client id");
const modulesById = indexUnique(inputs.moduleCatalog.modules, (module) => module.id, "module id");
const allCapabilities = inputs.moduleCatalog.modules.flatMap((module) => module.capabilities);
indexUnique(allCapabilities, (capability) => capability, "capability id");

for (const module of inputs.moduleCatalog.modules) {
  await access(path.join(repositoryRoot, module.path));
}

for (const entry of inputs.products) {
  const product = entry.manifest;
  for (const moduleId of product.requiredModules) {
    assert.ok(modulesById.has(moduleId), `Product ${product.productId} references unknown module: ${moduleId}`);
  }
  for (const capabilityId of product.requiredCapabilities) {
    assert.ok(allCapabilities.includes(capabilityId), `Product ${product.productId} references unknown capability: ${capabilityId}`);
  }
}

for (const entry of inputs.clients) {
  const client = entry.manifest;
  assert.equal(entry.directoryName, client.clientId, `Client directory must match clientId ${client.clientId}`);
  assert.ok(
    client.organization.supportedLocales.includes(client.organization.defaultLocale),
    `Client ${client.clientId} default locale must be included in supported locales`,
  );
  const product = resolveProductForClient(client, productsById);
  const expectedLock = buildReleaseLock({ client, product, moduleCatalog: inputs.moduleCatalog });
  assert.equal(
    canonicalJson(entry.releaseLock),
    canonicalJson(expectedLock),
    `Release lock for ${client.clientId} is stale; run pnpm run generate:lock`,
  );
}

assert.throws(
  () => indexUnique([...products, products[0]], (product) => product.productId, "product id"),
  /Duplicate product id/,
);
assert.throws(
  () => resolveProductForClient({ ...clients[0], product: { id: "wego-missing", version: "0.1.0" } }, productsById),
  /references missing product/,
);
assert.throws(
  () =>
    resolveProductForClient(
      { ...clients[0], product: { ...clients[0].product, version: "99.0.0" } },
      productsById,
    ),
  /requests .* but the manifest is/,
);

assertInvalid(
  "product",
  await readJson(path.join(repositoryRoot, "foundry/fixtures/invalid/product-unknown-field.json")),
);
assertInvalid(
  "client",
  await readJson(path.join(repositoryRoot, "foundry/fixtures/invalid/client-secret-field.json")),
);

console.log(
  `Validated ${products.length} products, ${clients.length} clients, every deterministic lock, negative graph cases, and the module catalog`,
);
