import { writeFile } from "node:fs/promises";

import {
  buildReleasePlan,
  discoverCompositionFiles,
  indexUnique,
  paths,
  readJson,
} from "./manifest-lib.mjs";

const [files, releaseProfiles] = await Promise.all([
  discoverCompositionFiles(),
  readJson(paths.releaseProfiles),
]);
const profilesByProduct = indexUnique(releaseProfiles.profiles, (profile) => profile.productId, "release profile product id");

for (const file of files.clients) {
  const [client, releaseLock] = await Promise.all([readJson(file.manifestPath), readJson(file.lockPath)]);
  const profile = profilesByProduct.get(releaseLock.product.id);
  if (!profile) {
    throw new Error(`No executable release profile exists for ${releaseLock.product.id}`);
  }
  const releasePlan = buildReleasePlan({ client, releaseLock, releaseProfileCatalog: releaseProfiles, profile });
  await writeFile(file.planPath, `${JSON.stringify(releasePlan, null, 2)}\n`, "utf8");
  console.log(`Generated ${file.planPath}`);
}
