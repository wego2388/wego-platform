# Offline Sync Architecture

Offline is a durable command and state architecture for the future Wego Ops and Wego Customer apps. WEGO-000 creates only shared primitives and boundaries.

```text
Compose UI → repository → Room KMP → local outbox → sync engine → OpenAPI
                                              │
                                  stable idempotency key
```

## Local write path

A user action creates a local command envelope and, where allowed, an optimistic local projection in one Room transaction. The envelope contains command type/version, stable command ID, idempotency key, actor/device context, creation instant, payload, and dependency ordering metadata. Secrets and reusable credentials are excluded.

## Sync behavior

The sync engine sends bounded batches, preserves dependency order, retries transient failures with jitter, and distinguishes accepted, already-applied, conflicted, permanently rejected, and authentication-required outcomes. The server persists idempotency before returning a successful mutation response.

## Conflict policy

Conflict behavior is domain-specific. Append-only observations may merge; scarce inventory, payments, approvals, scheduling, and destructive changes generally require server authority and explicit resolution. Last-write-wins is never a platform default.

## Read synchronization

Server changes use versioned cursors or change tokens with deterministic pagination. Tombstones/retention ensure deletions converge. A full re-sync is bounded, observable, and does not duplicate pending commands.

## Security and observability

Room contains only data needed for the offline role and is protected by platform facilities where justified. Tokens use native secure storage. Logout/revocation defines local-data cleanup. Sync logs use correlation IDs and redact payload PII.

## Deferred decisions

Room schema, Ktor transport, DataStore settings, background execution policies, server change feed, conflict UX, and per-command offline eligibility require real product use cases. An offline banner without these mechanisms does not qualify as offline support.
