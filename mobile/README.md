# Wego mobile workspace

Wego Ops and Wego Customer are separate product experiences. They share only
deliberate KMP infrastructure and domain primitives through `mobile/shared`.

`mobile/shared` and `mobile/apps/customer` are Kotlin Multiplatform libraries
targeting `jvm`, `androidTarget` (AGP 9's `com.android.kotlin.multiplatform.library`
plugin — the root `build.gradle.kts` declares it `apply false` for exactly this
reason), and `iosArm64`/`iosSimulatorArm64`. `mobile/apps/customer-android` is
the real, separate Android application module (`com.android.application`) that
consumes `WegoCustomerRoot()` from `:mobile:apps:customer` — Compose
Multiplatform apps keep the final platform application module distinct from
the KMP library modules; AGP 9 does not allow `com.android.application`
directly on a `kotlin.multiplatform` module.

A real debug APK builds today:

```bash
./gradlew :mobile:apps:customer-android:assembleDebug
```

iOS common/shared source compiles (`compileKotlinIosSimulatorArm64`) and is
klib-verified on Linux, but an actual `.app`/simulator run needs Xcode on a
Mac — `kotlin.native.ignoreDisabledTargets=true` (in `gradle.properties`) lets
iOS test/run tasks skip cleanly on this host instead of failing the build.

Run the executable foundation gate with JDK 25:

```bash
./gradlew \
  :mobile:shared:check \
  :mobile:apps:ops:check \
  :mobile:apps:customer:check \
  :mobile:apps:customer-android:check
```

The offline types define stable command identity, origin, dependency ordering,
and a Flow-based queue port. They do not claim durable offline support. Room
KMP, DataStore, Ktor, background execution, native secure storage, and platform
targets remain deferred until a real product slice can test their adapters and
failure semantics.
