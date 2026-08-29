import { writeFile } from "node:fs/promises";

import {
  buildReleaseLock,
  discoverCompositionFiles,
  indexUnique,
  paths,
  readJson,
  resolveProductForClient,
} from "./manifest-lib.mjs";

const [files, moduleCatalog] = await Promise.all([discoverCompositionFiles(), readJson(paths.moduleCatalog)]);
const products = await Promise.all(files.products.map(async (file) => readJson(file.manifestPath)));
const productsById = indexUnique(products, (product) => product.productId, "product id");

for (const file of files.clients) {
  const client = await readJson(file.manifestPath);
  const product = resolveProductForClient(client, productsById);
  const releaseLock = buildReleaseLock({ client, product, moduleCatalog });
  await writeFile(file.lockPath, `${JSON.stringify(releaseLock, null, 2)}\n`, "utf8");
  console.log(`Generated ${file.lockPath}`);
}
