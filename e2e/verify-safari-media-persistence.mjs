// Destructive only to the two explicitly disposable backend/edge containers:
// keeps their named PostgreSQL/media volumes. Never target an owner stack.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { request as httpRequest } from "node:http";
import pg from "pg";

// Same explicitly synthetic fixture as seed.mjs; do not import its executable main.
const E2E_STAFF_EMAIL = "e2e-staff@example.com";
const E2E_STAFF_PASSWORD = "e2e-synthetic-password-123";

const project = "wego-safari-media-final";
if (process.env.WEGO_SAFARI_MEDIA_E2E_CONFIRM !== "yes-this-is-a-disposable-media-stack"
  || process.env.COMPOSE_PROJECT_NAME !== project
  || process.env.WEGO_POSTGRES_DB !== "safari_media_diagnostic"
  || process.env.WEGO_POSTGRES_PORT !== "55440"
  || process.env.WEGO_EDGE_PORT !== "58088") {
  throw new Error("Refusing persistence probe outside the exact owned disposable MEDIA fixture");
}
const origin = "http://127.0.0.1:58088";
// Node fetch does not reliably preserve a supplied Host. Use the real HTTP
// request API so staff-only routes target the exact local staff virtual host.
const request = (path, { method = "GET", headers = {}, body } = {}) => new Promise((resolve, reject) => {
  const outgoing = httpRequest(`${origin}${path}`, {
    // Never reuse a keep-alive socket across deliberate edge replacement.
    method, agent: false, headers: { Host: "staff.localhost", ...headers }, timeout: 15_000,
  }, response => {
    const chunks = []; let length = 0;
    response.on("data", chunk => {
      length += chunk.length;
      if (length > 32 * 1024 * 1024) response.destroy(new Error("Probe response exceeds bounded memory limit"));
      else chunks.push(chunk);
    });
    response.on("error", reject);
    response.on("end", () => resolve({ status: response.statusCode, headers: response.headers, bytes: Buffer.concat(chunks) }));
  });
  outgoing.on("timeout", () => outgoing.destroy(new Error("Probe HTTP timeout")));
  outgoing.on("error", reject);
  outgoing.end(body);
});
const runDocker = args => execFileSync("docker", args, { encoding: "utf8", timeout: 60_000 });
const backend = `${project}-backend-1`;
const edge = `${project}-edge-1`;
for (const [name, service] of [[backend, "backend"], [edge, "edge"], [`${project}-postgres-1`, "postgres"]]) {
  const [container] = JSON.parse(runDocker(["inspect", name]));
  if (container.Config.Labels["com.docker.compose.project"] !== project
    || container.Config.Labels["com.docker.compose.service"] !== service) throw new Error("Fixture container identity mismatch");
}
const database = new pg.Client({
  host: "127.0.0.1", port: 55440, database: "safari_media_diagnostic",
  user: process.env.WEGO_POSTGRES_USER, password: process.env.WEGO_POSTGRES_PASSWORD,
});
await database.connect();
try {
  const { rows: [asset] } = await database.query("SELECT id, sha256, file_size_bytes FROM wego.tours_operator_asset ORDER BY id LIMIT 1");
  if (!asset) throw new Error("Run actual upload browser tests first; no stored asset fixture exists");
  const loginBody = JSON.stringify({ email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD });
  const login = await request("/api/v1/identity/login", {
    method: "POST", headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(loginBody) }, body: loginBody,
  });
  if (login.status !== 200) throw new Error(`Synthetic staff login failed: ${login.status}`);
  const { token } = JSON.parse(login.bytes.toString("utf8"));
  const preview = async () => {
    const response = await request(`/api/v1/tours-operator/staff/media/preview/${asset.id}`, { headers: { Authorization: `Bearer ${token}` } });
    if (response.status !== 200 || response.headers["cache-control"] !== "no-store, private") throw new Error(`Private preview failed: ${response.status}`);
    const bytes = response.bytes;
    if (bytes.length !== Number(asset.file_size_bytes) || createHash("sha256").update(bytes).digest("hex") !== asset.sha256.trim()) {
      throw new Error("Preview bytes differ from trusted immutable asset metadata");
    }
    return createHash("sha256").update(bytes).digest("hex");
  };
  const before = await preview();
  const previousId = JSON.parse(runDocker(["inspect", backend]))[0].Id;
  const compose = ["compose", "-f", "infrastructure/compose/safari-tours-sharm.compose.yaml", "-f", "e2e/compose.safari-media.yaml"];
  runDocker([...compose, "up", "-d", "--no-build", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "50", "backend"]);
  runDocker([...compose, "up", "-d", "--no-build", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "30", "edge"]);
  const [current] = JSON.parse(runDocker(["inspect", backend]));
  if (current.Id === previousId || !current.HostConfig.ReadonlyRootfs) throw new Error("Backend was not recreated with a read-only root");
  const mount = current.Mounts.find(item => item.Destination === "/data/safari-media");
  if (mount?.Type !== "volume" || mount.Name !== `${project}-media` || !mount.RW) throw new Error("Unexpected media persistence boundary");
  const after = await preview();
  if (before !== after) throw new Error("Image bytes changed after container replacement");
  console.log(JSON.stringify({ ok: true, backendRecreated: true, readonlyRoot: true, privateMediaVolume: mount.Name, bytes: Number(asset.file_size_bytes), sha256Unchanged: true }));
} finally {
  await database.end();
}
