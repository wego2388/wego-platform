# ADR-0007: Kotlin Multiplatform mobile

- Status: Accepted
- Date: 2026-08-08

## Context

Wego will have distinct Ops and Customer experiences that share reliable networking, storage, sync, and domain primitives.

## Decision

Use Kotlin Multiplatform and Compose Multiplatform with Coroutines/Flow, Ktor Client, Room KMP, DataStore, and platform-native secure storage. Share infrastructure deliberately while keeping app experiences separate.

## Consequences

Offline and idempotency are cross-platform architecture concerns. Platform packaging and adapters are introduced only when executable product slices need them; WEGO-000 validates common/JVM foundations.
