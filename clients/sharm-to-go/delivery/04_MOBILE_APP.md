# Phase 4 — customer mobile application

## Dependency gate

- [ ] Phase 1 request API is stable.
- [ ] Website request journey has produced real usability lessons.
- [ ] Owner confirms final store identity before release work, not before normal
  engineering.

## Existing foundation

- [x] Dedicated KMP module exists for Sharm To Go.
- [x] Dedicated Android application module exists.
- [x] Home, experience list/filter and detail screens exist.
- [x] Real logo/design tokens and bilingual content exist.
- [x] Bundled catalog snapshot keeps early read-only builds honest.

## Networking and shared contract

- [ ] Select and document KMP HTTP client, timeout and serialization strategy.
- [ ] Configure environment-specific base URL without committed secrets.
- [ ] Consume the same public catalog schema as web.
- [ ] Consume the same request/create/status schema as web.
- [ ] Define retry, offline, stale-data and error-state behavior.
- [ ] Add correlation/reference visibility for support without exposing internals.

## Request experience

- [ ] Service-bound option/date/party screen.
- [ ] Hotel/pickup/notes screen.
- [ ] Customer contact and consent screen.
- [ ] Review/request submission screen.
- [ ] Result/reference and contextual WhatsApp handoff.
- [ ] `My requests` lookup/status screen without requiring an account at launch.
- [ ] Deep link from shared web/service URLs where platform support exists.

## Quality and release

- [ ] JVM tests for mapping, validation and state reducer/view-model behavior.
- [ ] Compose UI tests for success/error/RTL/large-text states.
- [ ] Android build and host tests.
- [ ] Real device/emulator smoke on supported Android versions.
- [ ] Network security configuration and no cleartext production traffic.
- [ ] Final package id, listing name, icon and privacy disclosure approved.
- [ ] Play Store signing/upload only after explicit owner authorization.
- [ ] iOS build/release path documented; do not claim release without Mac/CI proof.

## Exit gate

- [ ] Mobile creates and retrieves the same real request/reference/status as web
  and ERP, with no copied local booking truth.
