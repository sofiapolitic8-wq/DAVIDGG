# R.SHMap

Open-source **keyboard + mouse mapper for Android touch games** (think BlueStacks keymapping / GG Mouse Pro / Panda Mouse Pro / Octopus). Plug a USB-OTG or Bluetooth keyboard and mouse into your phone and play with them. Kotlin, classic Views (no Compose), no root, **no internet permission, no ads, no analytics**.

> ⚠️ **Fair-play notice.** Some online games forbid or detect input-mapping tools. Use at your own risk; prefer single-player or custom rooms. R.SHMap only converts keyboard/mouse input into normal touches. It contains **no** anti-cheat bypass, root hiding, memory access, aimbot or recoil scripts, and PRs adding such things are rejected.

<!-- Add screenshots to docs/ and reference them here, e.g. ![Main screen](docs/screenshot-main.png) -->
<!-- Build badge (replace OWNER/REPO): [![Build](https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/build.yml) -->

## Features
- **Raw mouse look** via `View.requestPointerCapture()` (no OS acceleration, no screen-edge limit). Camera finger auto-lifts and re-centres before it reaches the edge, carrying leftover movement.
- Separate X/Y sensitivity, **ADS multiplier**, sub-pixel accumulator, EMA / **1-Euro** smoothing (0–100 %), optional acceleration (off = 1:1), invert Y, dead-zone, input batched per display frame (Choreographer + historical samples).
- Elements: Tap, Hold, Toggle, **Joystick** (WASD/arrows, 8 directions, ramp up/down, walk/sprint modifier), Mouse-look, Fire (auto-burst), Scope/ADS (hold or toggle), Scroll-wheel taps, **Macros** (taps/waits, loop on hold), Swipe.
- **Two injection backends**: Accessibility `dispatchGesture` with continued strokes (default, zero setup) and **Shizuku** (true multi-touch MotionEvents, lowest latency). Each virtual finger has a stable pointer id.
- **Per-game profiles** (auto-switch on foreground app), JSON import/export, templates: BGMI, PUBG Mobile, Free Fire (+MAX), COD Mobile, Minecraft PE, Genshin Impact, generic FPS / MOBA / racing. Coordinates are relative (0..1).
- **Overlay editor** over the live game: drag, resize, press-to-bind, grid snap, undo/redo, opacity, test mode (shows touch points). Optional crosshair and key hints, floating bubble.
- Foreground service, hot-plug aware, rotation/screen-off safe, **never leaves a touch stuck** (everything is released on Game Mode off, screen off, errors).
- Dark UI, English + Hindi.

## Build
Requirements: JDK 17, Android SDK 34, Gradle 8.5.
```bash
./gradlew :core:test :app:assembleDebug :app:assembleRelease
```
APKs: `app/build/outputs/apk/{debug,release}/`. Release is signed with the debug key unless you set `RSHMAP_KEYSTORE` (path to a `.jks`), `RSHMAP_KEYSTORE_PASSWORD`, `RSHMAP_KEY_ALIAS`, `RSHMAP_KEY_PASSWORD`.
**CI:** push to GitHub; the *Build* workflow runs tests + lint, builds both APKs and uploads them as the `RSHMap-apks` artifact. Pushing a tag such as `v1.0.0` also creates a GitHub Release. To sign releases with your own key add the repository secrets `RSHMAP_KEYSTORE_BASE64` (base64 of your `.jks`), `RSHMAP_KEYSTORE_PASSWORD`, `RSHMAP_KEY_ALIAS` and `RSHMAP_KEY_PASSWORD`.

## Install & first run
1. Install the APK (allow "install unknown apps").
2. Open R.SHMap → **Setup**: grant *Display over other apps*, enable the *Accessibility service*, notifications, battery exemption. Shizuku is optional.
3. Plug in keyboard + mouse. Check **Diagnostics** – both should be listed.
4. Pick a game → choose a template → **Edit layout on the game** and nudge widgets onto your HUD → **Save** → **Play**.
5. **F1** (configurable) or the bubble toggles Game Mode so you can release the mouse.

### Permission quirks per brand
- **Android 13+ (all brands):** if the accessibility switch is greyed out: Settings → Apps → R.SHMap → ⋮ → *Allow restricted settings*.
- **Samsung:** Battery → *Unrestricted*; remove from *Sleeping apps*.
- **Xiaomi / Redmi / Poco (MIUI/HyperOS):** enable *Autostart*, *Display pop-up windows while running in background*, Battery saver → *No restrictions*.
- **Oppo / Realme / OnePlus:** allow *Auto-launch* and *Background activity*; lock the app in recents.
- **Vivo / iQOO:** enable *Autostart* and *High background power consumption*.

### Shizuku backend
1. Install Shizuku and start it (Wireless debugging on Android 11+, or `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`).
2. In R.SHMap → Setup → *Shizuku* → grant permission. Settings → backend = *Auto* or *Shizuku*.
3. Diagnostics should show `Backend in use: Shizuku`. Shizuku must be restarted after a phone reboot (unless rooted).

### Making a profile
Game list → tap game → pick template → *Apply* → *Edit layout on the game*. In the editor: **+** adds an element, drag to move, drag the white handle to resize, **Bind** (or double-tap) then press a key / click a mouse button / scroll the wheel. **Opts** has per-type settings (burst, toggle, ramp, macro steps…). **Test** shows the touch points while you play.

## Troubleshooting
See the in-app *About → FAQ*. Typical: mouse cursor visible → toggle Game Mode off/on; camera too fast → lower sensitivity / raise smoothing; game pauses when Game Mode starts → the overlay takes window focus (known limitation, see below).

## Architecture
`core/` pure Kotlin (engine, filters, profile model, JSON, templates, unit tests) · `app/` Android: `input/InputHub` (HandlerThread + lock-free ring buffer + Choreographer) → `MappingEngine` → `TouchInjector` (`AccessibilityInjector` | `ShizukuInjector`). Overlays: `CaptureView`, `EditorView`, `BubbleView`. Service: `GameService`.

## Known limitations
- **Not yet verified on a physical device** – the engine is unit-tested in CI, but real-world behaviour per phone/ROM/game still needs testing. Please open issues.
- Accessibility backend latency ≈ one 32 ms chunk + IPC; Android also limits concurrent strokes. Use Shizuku for lowest latency.
- The focusable capture overlay takes window focus; a few games pause on focus loss.
- Fallback mode (no pointer capture) is limited by screen edges and may block injected touches on some ROMs.
- Hidden-API reflection in the Shizuku service is best-effort across Android versions.

## Roadmap
PC-streaming mode · gamepad mapping · cloud/community profile sharing (opt-in) · per-element sensitivity curves · macro recorder · more templates · tablet/DeX layouts.

---

## 🇮🇳 Hinglish section
**R.SHMap kya hai?** Ek free, open-source app jisse aap phone me OTG ya Bluetooth keyboard + mouse laga kar touch games (BGMI, Free Fire, COD Mobile, Minecraft PE, Genshin...) khel sakte ho. Root nahi chahiye, internet permission bhi nahi.

**Build kaise karein:** GitHub par push karo → *Actions* tab → latest run → *Artifacts* se `RSHMap-apks` download karo. Local build ke liye JDK 17 + Android SDK 34 + Gradle 8.5 chahiye: seedha `./gradlew assembleDebug` (wrapper jar repo me already hai).

**Install / setup:** APK install karo → app kholo → **Setup** me *Display over other apps*, *Accessibility service*, notification aur battery exemption allow karo. Android 13+ me accessibility grey ho to *App info → ⋮ → Allow restricted settings*.

**Profile banana:** Game list me game choose karo → template apply karo → **Edit layout on the game** → buttons ko apne HUD par drag/resize karo → **Bind** dabake keyboard key ya mouse button dabao → Save → Play. **F1** ya bubble se Game Mode on/off hota hai.

**Shizuku (optional):** kam latency chahiye to Shizuku start karo, app me permission do, Settings me backend *Auto/Shizuku* rakho.

**Dhyan rakho:** Online games me input tools ban ho sakte hain. Single-player ya custom room me use karo. Is app me koi cheat/aimbot/anti-cheat bypass nahi hai.

**Problem aaye to:** About → FAQ dekho (Samsung/Xiaomi/Oppo/Vivo ki battery & autostart settings wahin likhi hain).

## License
MIT – see [LICENSE](LICENSE).
