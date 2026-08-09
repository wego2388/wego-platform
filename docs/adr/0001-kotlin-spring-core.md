# ADR-0001: Kotlin and Spring core

- Status: Accepted
- Date: 2026-08-08

## Context

Wego needs a long-lived, transactional business platform with mature security, database, testing, and operations support.

## Decision

Use Kotlin on JDK 25 LTS with Spring Boot and Spring Security. Pin supported versions and build with the Gradle wrapper.

## Consequences

Kotlin domain modeling and Spring's production ecosystem are available. JVM/toolchain upgrades are deliberate. Domain code remains framework-independent even though application assembly uses Spring.
