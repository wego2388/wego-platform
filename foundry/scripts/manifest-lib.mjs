import { createHash } from "node:crypto";
import { access, readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));

export const repositoryRoot = path.resolve(scriptDirectory, "../..");

export const paths = Object.freeze({
  clientsDirectory: path.join(repositoryRoot, "clients"),
  moduleCatalog: path.join(repositoryRoot, "foundry/catalog/modules.json"),
  productsDirectory: path.join(repositoryRoot, "products"),
});

export async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, "utf8"));
}

export function canonicalJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }

  if (value !== null && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(",")}}`;
  }

  return JSON.stringify(value);
}

export function sha256(value) {
  return createHash("sha256").update(canonicalJson(value)).digest("hex");
}

async function manifestDirectories(parentDirectory, manifestName) {
  const entries = (await readdir(parentDirectory, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory() && !entry.name.startsWith("."))
    .sort((left, right) => left.name.localeCompare(right.name));

  if (entries.length === 0) {
    throw new Error(`No composition directories found under ${path.relative(repositoryRoot, parentDirectory)}`);
  }

  return Promise.all(
    entries.map(async (entry) => {
      const directory = path.join(parentDirectory, entry.name);
      const manifestPath = path.join(directory, manifestName);
      await access(manifestPath);
      return { directory, directoryName: entry.name, manifestPath };
    }),
  );
}

export async function discoverCompositionFiles() {
  const [clientDirectories, productDirectories] = await Promise.all([
    manifestDirectories(paths.clientsDirectory, "client.manifest.json"),
    manifestDirectories(paths.productsDirectory, "product.manifest.json"),
  ]);

  return {
    clients: clientDirectories.map((entry) => ({ ...entry, lockPath: path.join(entry.directory, "release.lock.json") })),
    products: productDirectories,
  };
}

export async function loadFoundryInputs() {
  const [files, moduleCatalog] = await Promise.all([discoverCompositionFiles(), readJson(paths.moduleCatalog)]);
  const [clients, products] = await Promise.all([
    Promise.all(
      files.clients.map(async (file) => ({
        ...file,
        manifest: await readJson(file.manifestPath),
        releaseLock: await readJson(file.lockPath),
      })),
    ),
    Promise.all(files.products.map(async (file) => ({ ...file, manifest: await readJson(file.manifestPath) }))),
  ]);

  return { clients, products, moduleCatalog };
}

export function indexUnique(items, keyOf, label) {
  const index = new Map();
  for (const item of items) {
    const key = keyOf(item);
    if (index.has(key)) {
      throw new Error(`Duplicate ${label}: ${key}`);
    }
    index.set(key, item);
  }
  return index;
}

export function resolveProductForClient(client, productsById) {
  const product = productsById.get(client.product.id);
  if (!product) {
    throw new Error(`Client ${client.clientId} references missing product ${client.product.id}`);
  }
  if (client.product.version !== product.version) {
    throw new Error(
      `Client ${client.clientId} requests ${client.product.id}@${client.product.version}, but the manifest is ${product.version}`,
    );
  }
  return product;
}

export function buildReleaseLock({ client, product, moduleCatalog }) {
  const modulesById = indexUnique(moduleCatalog.modules, (module) => module.id, "module id");
  const modules = product.requiredModules
    .map((moduleId) => {
      const module = modulesById.get(moduleId);
      if (!module) {
        throw new Error(`Product ${product.productId} references unknown module: ${moduleId}`);
      }
      return { id: module.id, version: module.version };
    })
    .sort((left, right) => left.id.localeCompare(right.id));

  return {
    schemaVersion: 1,
    clientId: client.clientId,
    platformVersion: product.platformVersion,
    product: {
      id: product.productId,
      version: product.version,
    },
    contractVersions: {
      httpApi: "v1",
    },
    modules,
    manifestDigests: {
      client: sha256(client),
      product: sha256(product),
      moduleCatalog: sha256(moduleCatalog),
    },
  };
}
