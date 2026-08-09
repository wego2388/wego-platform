import assert from "node:assert/strict";
import { access } from "node:fs/promises";
import path from "node:path";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

import {
  buildReleaseLock,
  canonicalJson,
  loadReleaseInputs,
  paths,
  readJson,
  repositoryRoot,
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

const validators = Object.fromEntries(
  Object.entries(schemas).map(([name, schema]) => [name, ajv.compile(schema)]),
);

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

const inputs = await loadReleaseInputs();
const releaseLock = await readJson(paths.lock);

assertValid("client", inputs.client);
assertValid("product", inputs.product);
assertValid("moduleCatalog", inputs.moduleCatalog);
assertValid("lock", releaseLock);

const moduleIds = inputs.moduleCatalog.modules.map((module) => module.id);
assert.equal(new Set(moduleIds).size, moduleIds.length, "Module IDs must be unique");

const capabilityIds = inputs.moduleCatalog.modules.flatMap((module) => module.capabilities);
assert.equal(new Set(capabilityIds).size, capabilityIds.length, "Capability IDs must be unique");

for (const module of inputs.moduleCatalog.modules) {
  await access(path.join(repositoryRoot, module.path));
}

assert.equal(inputs.client.product.id, inputs.product.productId, "Client must reference this product");
assert.equal(
  inputs.client.product.version,
  inputs.product.version,
  "Client product version must match the product manifest",
);
assert.ok(
  inputs.client.organization.supportedLocales.includes(inputs.client.organization.defaultLocale),
  "Default locale must be included in supported locales",
);

for (const moduleId of inputs.product.requiredModules) {
  assert.ok(moduleIds.includes(moduleId), `Product references unknown module: ${moduleId}`);
}
for (const capabilityId of inputs.product.requiredCapabilities) {
  assert.ok(capabilityIds.includes(capabilityId), `Product references unknown capability: ${capabilityId}`);
}

const expectedLock = buildReleaseLock(inputs);
assert.equal(
  canonicalJson(releaseLock),
  canonicalJson(expectedLock),
  "Release lock is stale; run pnpm run generate:lock",
);

assertInvalid(
  "product",
  await readJson(path.join(repositoryRoot, "foundry/fixtures/invalid/product-unknown-field.json")),
);
assertInvalid(
  "client",
  await readJson(path.join(repositoryRoot, "foundry/fixtures/invalid/client-secret-field.json")),
);

console.log("Validated product/client composition, module catalog, negative fixtures, and deterministic lock");
