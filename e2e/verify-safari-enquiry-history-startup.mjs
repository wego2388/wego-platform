// Non-PII startup guard on a specifically opted-in, disposable local database.
// Inserts one synthetic historical booking/payment, tests the built backend,
// then deletes ONLY those exact UUIDs. Never an owner/production data tool.
import pg from "pg";
import { randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";

const project = process.env.COMPOSE_PROJECT_NAME;
if (process.env.WEGO_E2E_SEED_CONFIRM !== "yes-this-is-a-disposable-e2e-database" ||
  !["wego-safari-enquiry", "ci-safari-enquiry"].includes(project) ||
  process.env.WEGO_POSTGRES_DB !== "safari_enquiry_e2e" || process.env.WEGO_POSTGRES_PORT !== "55439") {
  throw new Error("Refusing: exact disposable enquiry project/database/port and E2E confirmation are required");
}
const credentialNames = ["SECRET_KEY", "PUBLIC_KEY", "API_KEY", "INTEGRATION_ID", "OWNER_ID", "HMAC_SECRET", "NOTIFICATION_URL", "REDIRECTION_URL"];
if (credentialNames.some((key) => process.env[`TOURS_OPERATOR_PAYMOB_${key}`])) throw new Error("Refusing: this probe must not inherit provider configuration");
const client = new pg.Client({ host: "127.0.0.1", port: 55439, database: "safari_enquiry_e2e", user: "wego_app", password: "wego-local-postgres-only" });
const bookingId = randomUUID();
const paymentId = randomUUID();
await client.connect();
let inserted = false;
try {
  const identity = await client.query("SELECT current_database() AS db");
  if (identity.rows[0].db !== "safari_enquiry_e2e") throw new Error("Unexpected database");
  const counts = await client.query("SELECT (SELECT count(*) FROM wego.tours_operator_booking)::int AS bookings, (SELECT count(*) FROM wego.tours_operator_payment)::int AS payments");
  if (counts.rows[0].bookings !== 0 || counts.rows[0].payments !== 0) throw new Error("Refusing: enquiry fixture already contains commercial records");
  const fixture = await client.query("SELECT s.id, s.tour_id, s.date, s.time_slot FROM wego.tours_operator_tour_slot s JOIN wego.tours_operator_tour t ON t.id=s.tour_id WHERE t.slug='e2e-desert-quad-safari' LIMIT 1");
  if (!fixture.rows.length) throw new Error("Run the explicitly opted-in E2E seed first");
  const slot = fixture.rows[0];
  await client.query("BEGIN");
  await client.query(`INSERT INTO wego.tours_operator_booking
    (id,reference,tour_id,slot_id,tour_date,time_slot,adults_count,children_count,price_adult_eur,total_eur,customer_full_name,customer_phone,customer_nationality,hotel_name,locale,status,created_at)
    VALUES ($1,'STR-2026-9223372',$2,$3,$4,$5,1,0,45.00,45.00,'Synthetic startup fixture','+201000000000','EG','Synthetic hotel','en','NEW',now())`,
  [bookingId, slot.tour_id, slot.id, slot.date, slot.time_slot]);
  await client.query(`INSERT INTO wego.tours_operator_payment
    (id,booking_id,amount_eur,amount_minor_units,currency_code,provider_reference,status,created_at)
    VALUES ($1,$2,45.00,4500,'EUR',$3,'PENDING',now())`, [paymentId, bookingId, `sts-${paymentId}`]);
  await client.query("COMMIT");
  inserted = true;
  const result = spawnSync("docker", ["compose", "--env-file", ".env.safari-tours-sharm.example",
    "-f", "infrastructure/compose/safari-tours-sharm.compose.yaml", "-f", "e2e/compose.safari-enquiry.yaml",
    "run", "--rm", "--no-deps", "backend"], { cwd: new URL("..", import.meta.url), encoding: "utf8", timeout: 60_000, maxBuffer: 2_000_000 });
  if (result.error || result.status === 0 || !`${result.stdout}${result.stderr}`.includes("Payment history exists: retain complete real Paymob configuration")) {
    throw new Error("Built backend did not fail startup with the required payment-history guard");
  }
  console.log("PASS: built non-mock backend refuses disabling real configuration with historical PENDING payment");
} finally {
  // Roll back a failed partial insertion; cleanup is never table-wide.
  await client.query("ROLLBACK");
  if (inserted) {
    await client.query("BEGIN");
    await client.query("DELETE FROM wego.tours_operator_payment WHERE id=$1 AND booking_id=$2", [paymentId, bookingId]);
    await client.query("DELETE FROM wego.tours_operator_booking WHERE id=$1 AND reference='STR-2026-9223372'", [bookingId]);
    await client.query("COMMIT");
    console.log("Cleaned only the two synthetic startup-probe records");
  }
  await client.end();
}
