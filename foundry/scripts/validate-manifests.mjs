import assert from "node:assert/strict";
import { access, readFile, readdir } from "node:fs/promises";
import path from "node:path";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import YAML from "yaml";

import {
  buildReleaseLock,
  buildReleasePlan,
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
  releasePlan: await readJson(path.join(schemaDirectory, "release-plan.schema.json")),
  releaseProfiles: await readJson(path.join(schemaDirectory, "release-profile-catalog.schema.json")),
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
assertValid("releaseProfiles", inputs.releaseProfiles);

const products = inputs.products.map((entry) => entry.manifest);
const clients = inputs.clients.map((entry) => entry.manifest);
for (const product of products) assertValid("product", product);
for (const client of clients) assertValid("client", client);
for (const entry of inputs.clients) assertValid("lock", entry.releaseLock);
for (const entry of inputs.clients) assertValid("releasePlan", entry.releasePlan);

const productsById = indexUnique(products, (product) => product.productId, "product id");
indexUnique(clients, (client) => client.clientId, "client id");
const modulesById = indexUnique(inputs.moduleCatalog.modules, (module) => module.id, "module id");
const allCapabilities = inputs.moduleCatalog.modules.flatMap((module) => module.capabilities);
indexUnique(allCapabilities, (capability) => capability, "capability id");
const profilesByProduct = indexUnique(inputs.releaseProfiles.profiles, (profile) => profile.productId, "release profile product id");

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

  const profile = profilesByProduct.get(product.productId);
  assert.ok(profile, `Client ${client.clientId} has no executable release profile for ${product.productId}`);
  assert.deepEqual(
    [...profile.backend.includedModules].sort(),
    [...expectedLock.modules.map((module) => module.id)].sort(),
    `Backend profile for ${product.productId} does not match its release lock modules`,
  );
  const expectedPlan = buildReleasePlan({
    client,
    releaseLock: expectedLock,
    releaseProfileCatalog: inputs.releaseProfiles,
    profile,
  });
  assert.equal(
    canonicalJson(entry.releasePlan),
    canonicalJson(expectedPlan),
    `Release plan for ${client.clientId} is stale; run pnpm run generate:release`,
  );
}

const clientProductIds = new Set(clients.map((client) => client.product.id));
assert.deepEqual(
  [...profilesByProduct.keys()].sort(),
  [...clientProductIds].sort(),
  "Executable release profiles must map exactly to the client products in this repository",
);

const settingsText = await readFile(path.join(repositoryRoot, "settings.gradle.kts"), "utf8");
const modulePathsById = new Map(inputs.moduleCatalog.modules.map((module) => [module.id, module.path]));
const composePaths = new Set();
const backendProjects = new Set();
const webArtifactPaths = new Set();

for (const profile of inputs.releaseProfiles.profiles) {
  assert.ok(!composePaths.has(profile.compose.path), `Compose bundle is shared by multiple products: ${profile.compose.path}`);
  composePaths.add(profile.compose.path);
  assert.ok(!backendProjects.has(profile.backend.gradleProject), `Backend project is shared by multiple products: ${profile.backend.gradleProject}`);
  backendProjects.add(profile.backend.gradleProject);

  for (const webArtifact of [profile.publicSite, profile.staffApp]) {
    assert.ok(!webArtifactPaths.has(webArtifact.path), `Web artifact path is shared by multiple release surfaces: ${webArtifact.path}`);
    webArtifactPaths.add(webArtifact.path);
  }

  const requiredPaths = [
    profile.backend.projectPath,
    profile.backend.dockerfile,
    profile.publicSite.path,
    profile.publicSite.dockerfile,
    profile.staffApp.path,
    profile.staffApp.dockerfile,
    profile.apiContract.path,
    profile.compose.path,
    profile.compose.environmentExample,
  ];
  await Promise.all(requiredPaths.map((relativePath) => access(path.join(repositoryRoot, relativePath))));

  // The release profile is executable policy, not descriptive metadata. Keep its
  // migration and isolation declarations tied to the exact SQL artifacts that
  // the backend build stages; a plan must fail closed when either drifts.
  assert.equal(
    profile.backend.migrationFiles.length,
    profile.backend.migrationVersions.length,
    `${profile.productId} migration files and versions must have one-to-one cardinality`,
  );
  const migrationTexts = await Promise.all(
    profile.backend.migrationFiles.map(async (relativePath, index) => {
      const fileName = path.basename(relativePath);
      const version = fileName.match(/^V([0-9]+)__/i)?.[1];
      assert.equal(version, profile.backend.migrationVersions[index], `${profile.productId} migration ${relativePath} has unexpected version`);
      return readFile(path.join(repositoryRoot, relativePath), "utf8");
    }),
  );
  const migrationSql = migrationTexts.join("\n");
  for (const prefix of profile.dataIsolation.allowedTablePrefixes) {
    assert.ok(
      migrationSql.includes(prefix),
      `${profile.productId} declares table prefix ${prefix} absent from selected migration SQL`,
    );
  }
  for (const prefix of profile.dataIsolation.allowedPermissionPrefixes) {
    assert.ok(
      migrationSql.includes(prefix),
      `${profile.productId} declares permission prefix ${prefix} absent from selected migration SQL`,
    );
  }
  const declaredTablePrefixes = profile.dataIsolation.allowedTablePrefixes;
  const selectedTables = [
    ...migrationSql.matchAll(/(?:CREATE|ALTER|INSERT\s+INTO|UPDATE)\s+wego\.([a-z0-9_]+)/gi),
  ].map((match) => match[1]);
  for (const tableName of selectedTables) {
    assert.ok(
      declaredTablePrefixes.some((prefix) => tableName.startsWith(prefix)),
      `${profile.productId} selected migration references undeclared table ${tableName}`,
    );
  }
  const selectedPermissionCodes = [...migrationSql.matchAll(/\('([^']+:[^']+)'\s*,/g)].map((match) => match[1]);
  for (const permissionCode of selectedPermissionCodes) {
    assert.ok(
      profile.dataIsolation.allowedPermissionPrefixes.some((prefix) => permissionCode.startsWith(prefix)),
      `${profile.productId} selected migration references undeclared permission ${permissionCode}`,
    );
  }

  assert.ok(
    settingsText.includes(`include("${profile.backend.gradleProject}")`),
    `Gradle settings do not include ${profile.backend.gradleProject}`,
  );
  assert.ok(
    profile.backend.jar.startsWith(`${profile.backend.projectPath}/build/libs/`),
    `Backend jar for ${profile.productId} must be emitted by its declared project`,
  );

  const backendBuildPath = path.join(repositoryRoot, profile.backend.projectPath, "build.gradle.kts");
  const backendBuild = await readFile(backendBuildPath, "utf8");
  const expectedMigrationNames = new Set(profile.backend.migrationFiles.map((relativePath) => path.basename(relativePath)));
  let stagedMigrationNames;
  if (backendBuild.includes("src/main/resources/db/migration/*.sql")) {
    const migrationDirectory = path.join(repositoryRoot, profile.backend.projectPath, "src/main/resources/db/migration");
    stagedMigrationNames = new Set(
      (await readdir(migrationDirectory)).filter((fileName) => /^V[0-9]+__.*\.sql$/i.test(fileName)),
    );
  } else {
    const migrationListName = profile.productId === "wego-divers" ? "diversReleaseMigrations" : "selectedDdlMigrations";
    const listBlock = backendBuild.match(new RegExp(`val\\s+${migrationListName}\\s*=\\s*listOf\\(([^)]*)\\)`, "s"))?.[1] ?? "";
    stagedMigrationNames = new Set(listBlock.match(/V[0-9]+__[^"/]+\.sql/gi) ?? []);
    // Safari stages its data (DML) migrations through separate Sync sources
    // from the shared db/migration/data directory.
    if (profile.productId === "wego-tours-operator") {
      const sharedBlock = backendBuild.match(/val\s+selectedSharedDdlMigrations\s*=\s*listOf\((.*?)\)\s*val\s+selectedDdlMigrations/s)?.[1] ?? "";
      stagedMigrationNames = new Set(sharedBlock.match(/V[0-9]+__[^"/]+\.sql/gi) ?? []);
      stagedMigrationNames.add("V3__identity_administration.sql");
      for (const dataMigration of backendBuild.match(/data\/V[0-9]+__[^"/]+\.sql/gi) ?? []) {
        stagedMigrationNames.add(dataMigration.slice("data/".length));
      }
    }
  }
  assert.deepEqual(
    [...stagedMigrationNames].sort(),
    [...expectedMigrationNames].sort(),
    `${profile.productId} executable build migration set differs from its release profile`,
  );
  for (const relativeMigrationPath of profile.backend.migrationFiles) {
    const migrationFileName = path.basename(relativeMigrationPath);
    const belongsToBackendProject = relativeMigrationPath.startsWith(`${profile.backend.projectPath}/`);
    assert.ok(
      belongsToBackendProject
        ? backendBuild.includes("src/main/resources/db/migration")
        : backendBuild.includes(migrationFileName),
      `${profile.productId} build does not stage declared migration ${relativeMigrationPath}`,
    );
  }
  const declaredSourceRoots = [...backendBuild.matchAll(/"([^"\n]+\/src\/main\/kotlin)"/g)]
    .map((match) => path.resolve(path.dirname(backendBuildPath), match[1]));
  for (const moduleId of profile.backend.includedModules) {
    const modulePath = modulePathsById.get(moduleId);
    assert.ok(modulePath, `Release profile ${profile.productId} references unknown module ${moduleId}`);
    const expectedSourceRoot = path.join(repositoryRoot, modulePath, "src/main/kotlin");
    assert.ok(
      declaredSourceRoots.includes(expectedSourceRoot),
      `${profile.backend.gradleProject} does not compile declared lock module ${moduleId}`,
    );
  }
  const declaredProductRoots = declaredSourceRoots.filter((sourceRoot) => sourceRoot.startsWith(path.join(repositoryRoot, "products")));
  const expectedProductRoots = profile.backend.includedModules
    .filter((moduleId) => moduleId.startsWith("product."))
    .map((moduleId) => path.join(repositoryRoot, modulePathsById.get(moduleId), "src/main/kotlin"));
  assert.deepEqual(
    [...declaredProductRoots].sort(),
    [...expectedProductRoots].sort(),
    `${profile.backend.gradleProject} compiles an undeclared product or omits a declared one`,
  );

  for (const webArtifact of [profile.publicSite, profile.staffApp]) {
    const packageJson = await readJson(path.join(repositoryRoot, webArtifact.path, "package.json"));
    assert.equal(packageJson.name, webArtifact.packageName, `${webArtifact.path} package name does not match its release profile`);
  }

  const compose = YAML.parse(await readFile(path.join(repositoryRoot, profile.compose.path), "utf8"));
  assert.deepEqual(
    Object.keys(compose.services).sort(),
    [...profile.compose.services].sort(),
    `${profile.compose.path} services differ from its release profile`,
  );
  const composeDockerfiles = Object.values(compose.services)
    .map((service) => service?.build?.dockerfile)
    .filter(Boolean);
  for (const dockerfile of [profile.backend.dockerfile, profile.publicSite.dockerfile, profile.staffApp.dockerfile]) {
    assert.ok(composeDockerfiles.includes(dockerfile), `${profile.compose.path} does not build ${dockerfile}`);
  }

  const contract = await readFile(path.join(repositoryRoot, profile.apiContract.path), "utf8");
  for (const prefix of profile.apiContract.ownedPathPrefixes) {
    assert.ok(contract.includes(`  ${prefix}`), `${profile.apiContract.path} has no path under ${prefix}`);
  }
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
assertInvalid("releaseProfiles", {
  ...inputs.releaseProfiles,
  profiles: [{ ...inputs.releaseProfiles.profiles[0], secret: "must-never-be-declared-here" }],
});
assertInvalid("releasePlan", {
  ...inputs.clients[0].releasePlan,
  secret: "must-never-be-generated-here",
});

console.log(
  `Validated ${products.length} products, ${clients.length} clients, every deterministic lock and release plan, negative graph cases, and the module catalog`,
);
