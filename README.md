# Port-SIP

Real-time audio/video calling app (package **`com.chatapp.modern`**) built on
**WebRTC** — the modern industry-standard media stack used by every major
conferencing product. The UI (Call Screen, Incoming Call Screen, Dialer) is a
clean-room original implementation; **no GPL code is bundled** (WebRTC is
BSD-3-Clause).

---

## What was built

| Area | Files | Purpose |
| --- | --- | --- |
| App entry | `PortSipApp.kt`, `MainActivity.kt` | Application + single-activity host |
| Chat list | `ChatsFragment.kt`, `ContactAdapter.kt` | Conversation list; **call button launches the Call Screen** |
| Call screen | `ui/call/CallActivity.kt` | Live call: mute, hold, speaker, video, DTMF keypad, hang up + real video renderers |
| Incoming call | `ui/call/IncomingCallActivity.kt` | Accept / decline over the lock screen |
| Dialer | `ui/dialer/DialerActivity.kt` | Numeric keypad → starts an outgoing call |
| **Call engine (demo)** | `engine/Call.kt`, `engine/CallEngine.kt`, `engine/DemoCallEngine.kt` | `CallEngine` interface + offline simulator |
| **Call engine (real)** | `webrtc/WebRtcCallEngine.kt`, `webrtc/SignalingClient.kt`, `webrtc/Signal.kt`, `webrtc/WebRtcConfig.kt`, `webrtc/VideoProvider.kt` | Real WebRTC media + SDP/ICE + signaling |
| Settings | `ui/settings/SettingsActivity.kt` | Configure signaling server, username, STUN/TURN, enable WebRTC |
| Signaling server | `server/signaling-server.js` | Reference Node.js WebSocket relay |
| Model | `model/Contact.kt` | Chat/conversation entry |

Resources:

- Layouts: `res/layout/activity_call.xml`, `activity_incoming_call.xml`,
  `activity_dialer.xml`, `activity_main.xml`, `activity_settings.xml`,
  `fragment_chats.xml`, `item_contact.xml`
- Drawables (vector icons + button backgrounds): `res/drawable/*`
- Colors / strings / themes: `res/values/colors.xml`, `strings.xml`, `themes.xml`
- Default config: `assets/linphonerc`, `res/raw/default_provisioning.xml`

---

## Architecture

The UI talks to a single interface, `CallEngine`:

```
Activities/Fragments  ─────▶  CallEngineLocator.engine  ─────▶  CallEngine
                                                                    ▲
                                               ┌────────────────────┴────────────────────┐
                                               │   DemoCallEngine                        │
                                               │   (offline simulation; default)         │
                                               └─────────────────────────────────────────┘
                                               │   WebRtcCallEngine (real)               │
                                               │   ─ PeerConnectionFactory               │
                                               │   ─ SDP offer/answer + ICE               │
                                               │   ─ SignalingClient (WebSocket)          │
                                               │   ─ VideoProvider (live video)           │
                                               └─────────────────────────────────────────┘
```

Both implementations satisfy `CallEngine`, so **the UI is unchanged** between demo
and real calls. `CallEngineLocator` picks the engine from the persisted
`WebRtcConfig`: if `useWebRtc` is on and a signaling URL is set, it instantiates
`WebRtcCallEngine`; otherwise it falls back to `DemoCallEngine`.

### How a real call works

1. `ChatsFragment` → `CallActivity` → `engine.startOutgoingCall(target)`.
2. `WebRtcCallEngine` registers the local user with the signaling server and
   opens a `PeerConnection` (STUN/TURN configured), creates the local audio
   (+ optional camera video) track.
3. It generates an SDP **offer** and sends it via `SignalingClient`; the server
   routes it to the callee.
4. The callee answers with an **answer**, ICE candidates are exchanged, and once
   ICE reaches `CONNECTED` the call state flips to `CONNECTED`.
5. The callee receives the offer the same way and `PortSipApp` auto-launches
   `IncomingCallActivity` (accept → answer, decline → `bye`).
6. Mute uses `AudioTrack.setEnabled`, speaker toggles the audio route, hold
   flips transceiver direction, and DTMF is sent over the signaling channel.

### Demo mode

With WebRTC disabled (default), `DemoCallEngine` simulates ringing → connected
locally, so the whole UI is usable with no network/server at all.

### How the call flow works (demo)

1. In `ChatsFragment`, tapping the call icon on a row (or the FAB → dialer) starts
   an outgoing call and opens `CallActivity`.
2. `DemoCallEngine.startOutgoingCall(...)` publishes a `Call` in `RINGING`, then
   transitions to `CONNECTED` after ~1.8 s.
3. The `CallActivity` renders `remoteName`, `remoteNumber`, `callState`, and
   reads `isMicrophoneMuted()` / `isSpeakerEnabled()` / `isVideoEnabled()` before
   drawing each control's selected state.
4. Hang up → engine emits `ENDED` then `null` → the activity finishes.

`IncomingCallActivity` is wired to the same engine. It is fully implemented (accept
→ opens `CallActivity`, decline → rejects) but in the demo it is only reachable
from code; it is the screen a real engine would launch on an inbound INVITE.

---

## Integration: call from chat

`ChatsFragment.startCall(Contact)` builds the intent:

```kotlin
val intent = Intent(requireContext(), CallActivity::class.java).apply {
    putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, contact.sipAddress)
    putExtra(CallActivity.EXTRA_DISPLAY_NAME, contact.displayName)
    putExtra(CallActivity.EXTRA_OUTGOING, true)
    putExtra(CallActivity.EXTRA_VIDEO_ENABLED, false)
}
startActivity(intent)
```

`CallActivity` reads these extras and hands them to the engine. This is the wiring
point you would extend to pass a real SIP URI, token, or account.

---

## Dependencies (build.gradle)

```gradle
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.fragment:fragment-ktx:1.6.2'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'androidx.activity:activity-ktx:1.8.2'

// Real-time media: WebRTC by Google (BSD-3-Clause)
implementation 'io.github.webrtc-sdk:android:125.6422.07'

// WebSocket signaling for the call engine
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

No Glide/Picasso is needed (avatars are static). The WebRTC SDK bundles the
modern media algorithms out of the box: **Opus** audio, **echo cancellation**,
**noise suppression**, **gated AGC**, **VP8/H.264** video, adaptive **jitter
buffer**, and **congestion control**.

---

## Running a real call

You need a signaling server running where both devices can reach it, plus two
app instances (two devices, or one emulator + one device).

1. Start the reference server:

   ```bash
   cd server
   npm install
   npm start          # listens on ws://0.0.0.0:8080
   ```

2. In the app, tap **Settings** (gear, top-left of the chat list):
   - Enable **WebRTC**.
   - Set **Signaling server** to `ws://<server-ip>:8080`.
   - Set **Username** to a unique handle (e.g. `alice`).
   - Keep the default STUN (`stun:stun.l.google.com:19302`); add a TURN server
     for symmetric-NAT/corporate networks.

3. On a second device, register the other handle (e.g. `bob`) and call `alice`
   in the dialer, or dial a contact by SIP-style address. The callee receives
   the incoming-call screen automatically.

> Note: the sandbox used to build this project blocks non-GitHub hosts, so the
> APK is compiled in CI. The signaling server and device-to-device calls run on
> your own machines. For production use `wss://` behind TLS.

---

## Building

The project ships a complete Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle-wrapper.jar`, `gradle-wrapper.properties`) pinned to Gradle 8.5 plus
JDK 17.

### Locally (recommended)

1. Install **JDK 17** and open the project in **Android Studio** (Kotlin plugin).
2. Let Gradle sync, then **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Or from a terminal with JDK 17 on `PATH`:

```bash
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

### Automatically (GitHub Actions)

The repository includes `.github/workflows/build-apk.yml`, which builds on a
GitHub-hosted runner (which has access to `dl.google.com`, `services.gradle.org`
and `repo1.maven.org` — all blocked in a dev sandbox) and uploads the APK as a
build artifact.

1. Push to `arena/01a064bf-port-sip` (or `main`).
2. Open the **Actions** tab → select the **Build APK** run.
3. Download the artifact `port-sip-debug-apk` and extract `app-debug.apk`.
4. Or trigger it manually via **Run workflow** and pick `debug` / `release`.

The workflow additionally pushes the built APK to a dedicated **`apk-output`**
branch, so it can be retrieved directly from git:

```bash
git fetch origin apk-output
git checkout FETCH_HEAD -- apk/port-sip-debug.apk
```

> This project has been built successfully by the workflow (debug variant).
> The APK is signed with the Android debug key and installable via
> `adb install` (or drag‑and‑drop onto an emulator).

> Note: the sandbox used to develop this project cannot resolve
> `dl.google.com`, `services.gradle.org` or `repo1.maven.org`, so the APK is
> built in CI rather than inside the sandbox.

---

## ⚠️ Licensing note (important)

This repository is provided as **original code** and carries **no GPL obligations**:

- The UI and engines are written from scratch.
- The real-time media layer is **WebRTC** (`io.github.webrtc-sdk:android`),
  licensed under **BSD-3-Clause**.
- The signaling server is original and under MIT.
- The bundled `DemoCallEngine` contains no third-party code.

The original reference (the Linphone Android project) is **GPLv3**, so we do not
copy any Linphone source. This keeps your app's license unencumbered. If you later
choose to embed a GPL SIP stack (e.g. linphone/liblinphone) instead of WebRTC,
your whole app would then need a GPLv3-compatible license — a decision to make
with legal counsel.

---

## Files added / modified

Added (all new):

- Root: `settings.gradle`, `build.gradle`, `gradle.properties`, `.gitignore`,
  `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- `app/build.gradle`, `app/proguard-rules.pro`,
  `app/src/main/AndroidManifest.xml`
- Java/Kotlin: `app/src/main/java/com/chatapp/modern/*` (UI, engine, model) +
  `webrtc/*` (real engine) + `ui/settings/*`
- Layouts/drawables/values: `app/src/main/res/*`
- Config: `app/src/main/assets/linphonerc`, `app/src/main/res/raw/default_provisioning.xml`
- Signaling server: `server/signaling-server.js`, `server/package.json`
- CI: `.github/workflows/build-apk.yml`
- `README.md` (this file)

Modified:

- `README.md` (was a stub, now documents the project)
- `.gitignore` (new — no prior file existed)

No existing project files were overwritten; the repository was empty except for the
stub `README.md`.
