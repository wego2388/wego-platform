// Seeds one synthetic staff user directly in Postgres for the Playwright
// E2E run — never through a test-only backend endpoint (none exists, and
// the brief this packet implements explicitly forbids adding one reachable
// in production). AdminBootstrapRunner deliberately requires an interactive
// TTY and is WEGO-001's own reviewed decision, out of scope to touch or
// route around here — this script is a fully out-of-process alternative,
// the same shape as the Kotlin integration tests' own raw jOOQ user seeds,
// just from outside the JVM. Idempotent: safe to run more than once against
// the same database.
import bcrypt from "bcryptjs";
import pg from "pg";
import { randomUUID } from "node:crypto";

export const E2E_STAFF_EMAIL = "e2e-staff@example.com";
export const E2E_STAFF_PASSWORD = "e2e-synthetic-password-123";

const REQUIRED_CONFIRMATION = "yes-this-is-a-disposable-e2e-database";

async function main() {
  // This script upserts a known-password `platform-admin` account by
  // email. It only ever dials 127.0.0.1, but that alone doesn't rule out a
  // port-forward/SSH tunnel to a real database pointed at localhost with
  // matching WEGO_POSTGRES_* values — an explicit, easy-to-grep opt-in is
  // the cheap guard against that, not just "it can only reach localhost."
  if (process.env.WEGO_E2E_SEED_CONFIRM !== REQUIRED_CONFIRMATION) {
    console.error(
      `Refusing to seed: set WEGO_E2E_SEED_CONFIRM=${REQUIRED_CONFIRMATION} to confirm the target database is a disposable E2E/CI instance, never production.`,
    );
    process.exit(1);
  }

  const client = new pg.Client({
    host: "127.0.0.1",
    port: Number(process.env.WEGO_POSTGRES_PORT ?? 55432),
    user: process.env.WEGO_POSTGRES_USER ?? "wego_app",
    password: process.env.WEGO_POSTGRES_PASSWORD ?? "wego-local-postgres-only",
    database: process.env.WEGO_POSTGRES_DB ?? "wego",
  });
  await client.connect();

  try {
    // Spring Security's DelegatingPasswordEncoder prefixes the stored hash
    // with the encoding id in braces; "bcrypt" is its default id, and its
    // BCryptPasswordEncoder accepts bcryptjs's $2a$ hashes directly.
    const passwordHash = `{bcrypt}${bcrypt.hashSync(E2E_STAFF_PASSWORD, 10)}`;
    const userId = randomUUID();

    await client.query(
      `INSERT INTO wego.identity_user (id, email, password_hash, status, created_at, failed_login_count)
       VALUES ($1, $2, $3, 'ACTIVE', now(), 0)
       ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE', failed_login_count = 0, locked_until = NULL
       RETURNING id`,
      [userId, E2E_STAFF_EMAIL, passwordHash],
    );

    const { rows } = await client.query(`SELECT id FROM wego.identity_user WHERE email = $1`, [E2E_STAFF_EMAIL]);
    const resolvedUserId = rows[0].id;

    // platform-admin already holds every permission this lifecycle needs
    // (offering:manage/view, booking:create/view/cancel/payment-update/refund)
    // — reusing it here keeps this a lifecycle proof, not a second copy of
    // the permission-boundary proofs DiversHttpTest already covers.
    await client.query(
      `INSERT INTO wego.identity_user_role (user_id, role_code)
       VALUES ($1, 'platform-admin')
       ON CONFLICT (user_id, role_code) DO NOTHING`,
      [resolvedUserId],
    );

    console.log(`Seeded E2E staff user ${E2E_STAFF_EMAIL} (id=${resolvedUserId}).`);

    // Fills the offerings list's first page with 50 old, low-priority rows
    // so the offering the E2E test creates through the UI lands on page 2 —
    // a real proof that "Next" reaches content a caller could otherwise
    // never see, not just that the button exists. Ordered by starts_on
    // (see JooqOfferingRepository), so these old dates always sort first.
    // Seeded CLOSED (not ACTIVE): the bookings page's create-booking
    // dropdown only lists ACTIVE offerings, and padding rows must not
    // crowd out the real "E2E Lifecycle Trip" the test creates and later
    // books against.
    const { rows: countRows } = await client.query(`SELECT count(*)::int AS count FROM wego.divers_offering`);
    if (countRows[0].count < 50) {
      for (let i = 0; i < 50; i += 1) {
        await client.query(
          `INSERT INTO wego.divers_offering
             (id, offering_type, title, starts_on, pricing_basis, unit_price, currency_code, status, created_by_user_id, created_at, closed_at)
           VALUES ($1, 'DIVE_TRIP', $2, $3, 'PER_PARTICIPANT', 10.00, 'EUR', 'CLOSED', $4, now(), now())`,
          [randomUUID(), `E2E Pagination Padding ${i}`, `2020-01-${String((i % 28) + 1).padStart(2, "0")}`, resolvedUserId],
        );
      }
      console.log("Seeded 50 padding offerings for the pagination step.");
    } else {
      console.log("Padding offerings already present; skipped.");
    }
  } finally {
    await client.end();
  }
}

// Only seeds when run directly (`node seed.mjs` / `pnpm run seed`) — the
// test spec imports E2E_STAFF_EMAIL/PASSWORD from this same module and
// must not trigger a database write as a side effect of that import.
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
