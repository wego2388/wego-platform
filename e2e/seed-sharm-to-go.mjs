// Seeds one synthetic Sharm To Go platform-admin directly in Postgres, the
// same out-of-process pattern as seed.mjs (AdminBootstrapRunner requires a
// real interactive TTY on purpose — this script is a deliberate, confirmed
// alternative for CI, never a route past it in production). Idempotent:
// safe to run more than once against the same database. Sharm To Go shares
// the same wego.identity_user/_user_role schema and platform-admin role
// code as every other product on platform/kernel/identity — confirmed
// against V2__identity_foundation.sql / V4__identity_administration.sql.
import bcrypt from "bcryptjs";
import pg from "pg";
import { randomUUID } from "node:crypto";

export const STG_E2E_STAFF_EMAIL = "stg-e2e-staff@example.com";
export const STG_E2E_STAFF_PASSWORD = "stg-e2e-synthetic-password-123";

const REQUIRED_CONFIRMATION = "yes-this-is-a-disposable-e2e-database";

async function main() {
  if (process.env.WEGO_E2E_SEED_CONFIRM !== REQUIRED_CONFIRMATION) {
    console.error(
      `Refusing to seed: set WEGO_E2E_SEED_CONFIRM=${REQUIRED_CONFIRMATION} to confirm the target database is a disposable E2E/CI instance, never production.`,
    );
    process.exit(1);
  }

  const client = new pg.Client({
    host: "127.0.0.1",
    port: Number(process.env.STG_POSTGRES_PORT ?? 55432),
    user: process.env.STG_POSTGRES_USER ?? "sharmtogo",
    password: process.env.STG_POSTGRES_PASSWORD ?? "sharmtogo-local-postgres-only",
    database: process.env.STG_POSTGRES_DB ?? "sharmtogo",
  });
  await client.connect();

  try {
    const passwordHash = `{bcrypt}${bcrypt.hashSync(STG_E2E_STAFF_PASSWORD, 10)}`;
    const userId = randomUUID();

    await client.query(
      `INSERT INTO wego.identity_user (id, email, password_hash, status, created_at, failed_login_count)
       VALUES ($1, $2, $3, 'ACTIVE', now(), 0)
       ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE', failed_login_count = 0, locked_until = NULL
       RETURNING id`,
      [userId, STG_E2E_STAFF_EMAIL, passwordHash],
    );

    const { rows } = await client.query(`SELECT id FROM wego.identity_user WHERE email = $1`, [STG_E2E_STAFF_EMAIL]);
    const resolvedUserId = rows[0].id;

    // platform-admin already holds service:manage/view and every other
    // permission the catalog importer and accessibility run need.
    await client.query(
      `INSERT INTO wego.identity_user_role (user_id, role_code)
       VALUES ($1, 'platform-admin')
       ON CONFLICT (user_id, role_code) DO NOTHING`,
      [resolvedUserId],
    );

    console.log(`Seeded Sharm To Go E2E staff user ${STG_E2E_STAFF_EMAIL} (id=${resolvedUserId}).`);
  } finally {
    await client.end();
  }
}

// Only seeds when run directly — a future spec importing the exported
// credentials must not trigger a database write as a side effect.
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
