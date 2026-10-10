// Publishes a handful of already-APPROVED catalog services so the
// accessibility suite's experience-detail/request pages have real
// published data to render, not just an empty-catalog state. Reads the
// importer's own state file (clients/sharm-to-go/scripts/import_catalog.py,
// schema v2) rather than hardcoding ids, and prints the first published
// service's id on its own final line so the CI step can capture it
// straight into WEGO_STG_E2E_SERVICE_ID without a second parsing step.
import { readFileSync } from "node:fs";

const apiBase = process.env.STG_API_BASE;
const email = process.env.STG_ADMIN_EMAIL;
const password = process.env.STG_ADMIN_PASSWORD;
const stateFile = process.env.STG_IMPORT_STATE_FILE;
if (!apiBase || !email || !password || !stateFile) {
  console.error("Set STG_API_BASE, STG_ADMIN_EMAIL, STG_ADMIN_PASSWORD, STG_IMPORT_STATE_FILE.");
  process.exit(1);
}

const SAMPLE_REFS = [
  "super-safari-adventure",
  "ras-mohamed-white-island-boat",
  "mount-sinai-st-catherine",
  "sharm-airport-transfer",
  "turkish-bath",
];

async function main() {
  const loginResponse = await fetch(`${apiBase}/api/v1/identity/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!loginResponse.ok) throw new Error(`login failed: HTTP ${loginResponse.status}`);
  const { token } = await loginResponse.json();

  const state = JSON.parse(readFileSync(stateFile, "utf8"));
  const services = state.services ?? state; // tolerate a pre-v2 flat state file too

  let firstPublishedId = null;
  for (const ref of SAMPLE_REFS) {
    const entry = services[ref];
    if (!entry) {
      console.error(`  skip ${ref}: not present in state file`);
      continue;
    }
    const serviceId = entry.serviceId ?? entry.id;
    const response = await fetch(`${apiBase}/api/v1/travel-marketplace/services/${serviceId}/publish`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) {
      console.error(`  FAILED to publish ${ref} (${serviceId}): HTTP ${response.status}`);
      continue;
    }
    console.error(`  published ${ref} -> ${serviceId}`);
    if (!firstPublishedId) firstPublishedId = serviceId;
  }

  if (!firstPublishedId) throw new Error("no sample service could be published");
  console.log(firstPublishedId);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
