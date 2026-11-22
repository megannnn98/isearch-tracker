# Contributing

Issues and pull requests are welcome.

## Build and test

Requires JDK 17 and the Android SDK (`tracker/local.properties` with `sdk.dir=...`).

```
cd tracker
./gradlew testDebugUnitTest assembleDebug
```

## Guidelines

- Keep decisions about connection and button logic in `tracker/app/src/main/kotlin/dev/isearch/tracker/core/` (plain Kotlin, unit-tested). Android classes stay thin.
- Do not state that a behaviour works on a phone unless it was tried on a phone. Record results in `docs/TEST_REPORT.md`.
- Protocol facts (UUIDs, packet format) must come from the fob or the analysis in `docs/REVERSE_ENGINEERING.md`, not guesses.
- One topic per pull request, squashed into one commit. Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`).
- Do not commit third-party APKs or decompiled sources.
