import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";

import YAML from "yaml";

import { repositoryRoot } from "./manifest-lib.mjs";

async function parseYaml(relativePath) {
  const source = await readFile(path.join(repositoryRoot, relativePath), "utf8");
  const document = YAML.parseDocument(source, { strict: true, version: "1.2" });
  assert.deepEqual(
    document.errors,
    [],
    `${relativePath} is invalid YAML: ${document.errors.map((error) => error.message).join("; ")}`,
  );
  return document.toJS();
}

const workflow = await parseYaml(".github/workflows/ci.yml");
assert.equal(typeof workflow.on, "object", "CI workflow must declare triggers");
assert.equal(typeof workflow.jobs, "object", "CI workflow must declare jobs");

for (const [jobName, job] of Object.entries(workflow.jobs)) {
  assert.ok(Array.isArray(job.steps), `CI job ${jobName} must declare steps`);
  for (const step of job.steps) {
    if (step.uses) {
      assert.match(
        step.uses,
        /^[^@\s]+@[a-f0-9]{40}$/,
        `CI action must use an immutable 40-character SHA: ${step.uses}`,
      );
    }
  }
}

// The historical-payment probe requires a fresh commercial fixture. Request
// conversion deliberately leaves real OFFICE bookings in that disposable DB.
// Never weaken the probe or erase those rows to compensate for wrong ordering.
function assertSafariEnquiryFixtureOrder(steps) {
  const selectors = [
    step => step.name === "Start fresh non-mock Safari enquiry bundle",
    step => step.name === "Seed only the disposable enquiry catalog/staff fixture",
    step => step.run?.includes("tests/safari-enquiry.spec.ts"),
    step => step.run?.includes("node e2e/verify-safari-enquiry-history-startup.mjs"),
    step => step.run?.includes("tests/safari-online-requests.spec.ts"),
    step => step.name === "Stop disposable Safari enquiry bundle",
  ];
  const indices = selectors.map((matches, index) => {
    const found = steps.flatMap((step, i) => matches(step) ? [i] : []);
    assert.equal(found.length, 1, `Safari enquiry fixture step ${index} must occur exactly once`);
    return found[0];
  });
  for (let i = 1; i < indices.length; i++) {
    assert.ok(indices[i] > indices[i - 1], "Safari payment-history guard must run on the fresh fixture before request conversions");
  }
  for (const index of indices.slice(0, -1)) assert.equal(steps[index].if, undefined, "Safari required fixture gates must not be optional");
}
const enquirySteps = workflow.jobs.infrastructure.steps;
assertSafariEnquiryFixtureOrder(enquirySteps);
// Executable regression: the previously failing order must be rejected.
const wrongOrder = structuredClone(enquirySteps);
const historyIndex = wrongOrder.findIndex(step => step.run?.includes("node e2e/verify-safari-enquiry-history-startup.mjs"));
const requestsIndex = wrongOrder.findIndex(step => step.run?.includes("tests/safari-online-requests.spec.ts"));
[wrongOrder[historyIndex], wrongOrder[requestsIndex]] = [wrongOrder[requestsIndex], wrongOrder[historyIndex]];
assert.throws(() => assertSafariEnquiryFixtureOrder(wrongOrder), /before request conversions/);
assert.throws(() => assertSafariEnquiryFixtureOrder(enquirySteps.filter((_, i) => i !== historyIndex)), /exactly once/);
const skippedGuard = structuredClone(enquirySteps);
skippedGuard[historyIndex].if = "false";
assert.throws(() => assertSafariEnquiryFixtureOrder(skippedGuard), /must not be optional/);

const dependabot = await parseYaml(".github/dependabot.yml");
assert.equal(dependabot.version, 2, "Dependabot schema version must be 2");
assert.ok(Array.isArray(dependabot.updates), "Dependabot must declare update sources");
assert.ok(dependabot.updates.length >= 4, "Dependabot must cover the foundation ecosystems");

console.log("Validated GitHub workflow/Dependabot YAML, immutable action pins and Safari fresh-fixture gate ordering");
