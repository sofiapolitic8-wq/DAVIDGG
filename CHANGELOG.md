# Changelog

## Unreleased
- Fix: `MappingEngine` did not compile (`else ->` directly after an `if` branch inside `when`).
- Fix: replaced `catch (_: ...)` (not valid before Kotlin 2.x) so the project builds on Kotlin 1.9.
- CI: separate release job, optional keystore via `RSHMAP_KEYSTORE_BASE64`, concurrency + read-only default permissions.
- Repo: `.gitattributes`, PR template, Dependabot, `dataExtractionRules`, cleaner `.gitignore`.

## 1.0.0
- Initial release: keyboard + mouse mapping for touch games.
- Pointer-capture mouse look with sub-pixel accumulator, EMA / 1-Euro smoothing, ADS sensitivity, camera auto-recenter.
- 8-direction joystick with ramp and walk/sprint modifier, fire (auto-burst), scope, scroll, macro, swipe, tap/hold/toggle.
- Accessibility backend (continued strokes) and optional Shizuku backend (true multi-touch).
- Overlay editor with grid, undo/redo, opacity, test mode; crosshair + key hints; floating bubble.
- Per-game profiles with auto-switch, JSON import/export, 9 built-in templates.
- English + Hindi UI.
