import { writeFile } from "node:fs/promises";

import { buildReleaseLock, loadReleaseInputs, paths } from "./manifest-lib.mjs";

const releaseLock = buildReleaseLock(await loadReleaseInputs());
await writeFile(paths.lock, `${JSON.stringify(releaseLock, null, 2)}\n`, "utf8");
console.log(`Generated ${paths.lock}`);
