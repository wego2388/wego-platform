# Data Modeling Rules

## Ownership and migrations

PostgreSQL is durable truth. Each table has one owning module. Flyway is the only schema creation/change path; startup DDL generation and hand-edited production schemas are forbidden. Released migrations are immutable and failures are forward-fixed.

## Names and keys

- Use lowercase `snake_case`, unquoted identifiers, explicit schemas only when ownership benefit is real.
- Primary keys are stable, opaque, and generated independently of business meaning.
- Natural invariants use unique constraints/indexes in addition to surrogate identifiers.
- Every foreign key states deletion behavior; silent cascades are exceptional.
- Required fields are `NOT NULL`; finite states use checks or deliberately versioned reference data.

## Money

- Kotlin uses `BigDecimal`; PostgreSQL uses consciously selected `NUMERIC(precision, scale)`.
- `Float`, `Double`, and binary floating-point SQL types are forbidden for money.
- Currency is explicit ISO 4217 where values can cross currency boundaries.
- Rounding mode, tax inclusion, exchange-rate source/time, and allocation remainder rules belong to the owning domain.

## Time

- Durable instants use `Instant` and PostgreSQL `TIMESTAMP WITH TIME ZONE` (`timestamptz`).
- APIs emit RFC 3339 instants with offsets and distinguish local date/time concepts.
- Organization timezone is explicit configuration using an IANA zone identifier.
- Local business dates are stored as `date` only when they are genuinely calendar concepts.

## Integrity and concurrency

Database constraints enforce invariants safely expressible in one database. Transactions define atomic business change. Use row locks, compare-and-set/version columns, advisory locks, exclusion constraints, or serializable isolation only when a named concurrency invariant requires them. Tests must exercise conflicting transactions and retries.

Retryable commands carry a stable idempotency key scoped to actor/client operation and persist request fingerprint/outcome where duplicate effects would be harmful.

## Deletion, audit, and privacy

Soft deletion is not a default. Choose retention, anonymization, archival, or deletion according to domain and legal requirements. Audit records are append-oriented and separate from mutable operational descriptions. PII classification and purpose control indexes, logs, exports, retention, and encryption.

## jOOQ

jOOQ code is generated from the Flyway-owned schema and treated as infrastructure. Generated records do not cross into domain APIs. Queries select required columns, state transaction context, and avoid hidden N+1 behavior.

## Client isolation

One database serves one client deployment. Do not add ubiquitous `tenant_id`; ownership/scope columns are modeled only when the client domain itself has organizations, branches, or operational partitions.
