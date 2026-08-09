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

const dependabot = await parseYaml(".github/dependabot.yml");
assert.equal(dependabot.version, 2, "Dependabot schema version must be 2");
assert.ok(Array.isArray(dependabot.updates), "Dependabot must declare update sources");
assert.ok(dependabot.updates.length >= 4, "Dependabot must cover the foundation ecosystems");

console.log("Validated GitHub workflow/Dependabot YAML and immutable action pins");
