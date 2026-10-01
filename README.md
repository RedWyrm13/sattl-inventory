# SATTL Lab Inventory Kiosk

An offline Android tablet app that tracks the SATTL lab's equipment and who has it.
The full build spec is `SPEC.md` (kept alongside this repository by the lab manager).

> **Status:** milestone 1 of 6 is done (project skeleton, database, first-time setup,
> login, Change PIN, auto-logout). The step-by-step guide for non-developers (installing
> Android Studio, sideloading the APK, app pinning, updating without data loss, signing
> key storage) will be written here as part of milestone 6.

## Building

Requirements: Android Studio (or the Android SDK command-line tools) and **JDK 17**.

Gradle must run on JDK 17. Android Studio does this automatically. From a terminal, point
`JAVA_HOME` at a JDK 17 first, for example on Ubuntu:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew testDebugUnitTest   # run all unit tests
./gradlew assembleDebug       # build app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/java/org/sattl/inventory/
  SattlApp.kt            Application class; creates the database, repositories, session
  MainActivity.kt        The single activity; touch tracking for auto-logout
  data/entity/           Room tables: Item, User, Checkout, AppSettings (spec §4)
  data/dao/              Room queries
  data/db/               Database, type converters, migrations, extra SQL constraints
  data/repo/             Business rules; the UI only talks to repositories
  security/              PIN hashing (PBKDF2), PIN rules, recovery code (spec §8)
  session/               Who is logged in; inactivity auto-logout (rule 6.14)
  ui/                    Compose screens, one folder per screen, plus shared components
app/schemas/             Room schema snapshots. Commit these; migrations need them.
app/src/test/            JUnit + Robolectric tests (run on the computer, no tablet needed)
```

## Rules for maintainers

- Business rules are commented with the spec section they implement, e.g. `rule 6.14`.
- Never use a destructive database migration. See `data/db/Migrations.kt` for how to
  change the schema without losing data.
- The app must never request the `INTERNET` permission. The manifest actively strips it.
