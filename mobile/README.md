# Wego mobile workspace

Wego Ops and Wego Customer are separate product experiences. They share only
deliberate KMP infrastructure and domain primitives through `mobile/shared`.
The WEGO-000 JVM target proves common-source and Compose compilation; it is not
an Android, iOS, or desktop release artifact.

Run the executable foundation gate with JDK 25:

```bash
./gradlew \
  :mobile:shared:check \
  :mobile:apps:ops:check \
  :mobile:apps:customer:check
```

The offline types define stable command identity, origin, dependency ordering,
and a Flow-based queue port. They do not claim durable offline support. Room
KMP, DataStore, Ktor, background execution, native secure storage, and platform
targets remain deferred until a real product slice can test their adapters and
failure semantics.
