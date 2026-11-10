# Contributing

Thanks for helping! Quick rules:

1. **Fair play first.** PRs adding anti-cheat bypass, root hiding, memory reading, aimbots, recoil scripts that read game state or emulator spoofing will be closed.
2. **Keep it light.** No Compose, no analytics/ads/Firebase, no network permission, no heavy libraries. Release APK must stay under 6 MB.
3. **Hot path = zero allocation.** Code reachable from `InputHub.drain`, `MappingEngine.onKey/onMouseDelta/onFrame` must not allocate.
4. **Engine logic lives in `core/`** (pure Kotlin) with a JUnit test. Run `./gradlew :core:test`.
5. Run `./gradlew :app:lintDebug :app:assembleDebug` before opening a PR; add strings in both `values/` and `values-hi/`.

Dev setup: JDK 17, Android SDK 34, Gradle 8.5 (wrapper included: use `./gradlew`).
