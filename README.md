# Port-SIP

Android VoIP call UI scaffold, package **`com.chatapp.modern`**.

This is an **original, clean-room implementation** of a standard Android voice/video
call interface (Call Screen, Incoming Call Screen, Dialer) plus a swappable call
engine, extracted and adapted from the *structure/feature-set* of the official
Linphone-Android app. **No Linphone source code was copied verbatim** — see the
licensing note below before integrating a real SIP stack.

---

## What was built

| Area | Files | Purpose |
| --- | --- | --- |
| App entry | `PortSipApp.kt`, `MainActivity.kt` | Application + single-activity host |
| Chat list | `ChatsFragment.kt`, `ContactAdapter.kt` | The conversation list; **call button launches the Call Screen** (integration point) |
| Call screen | `ui/call/CallActivity.kt` | Active call: mute, hold, speaker, video, DTMF keypad, hang up |
| Incoming call | `ui/call/IncomingCallActivity.kt` | Accept / decline over the lock screen |
| Dialer | `ui/dialer/DialerActivity.kt` | Numeric keypad → starts an outgoing call |
| Call engine | `engine/Call.kt`, `engine/CallEngine.kt`, `engine/DemoCallEngine.kt` | `CallEngine` interface + demo implementation |
| Model | `model/Contact.kt` | Chat/conversation entry |

Resources:

- Layouts: `res/layout/activity_call.xml`, `activity_incoming_call.xml`,
  `activity_dialer.xml`, `activity_main.xml`, `fragment_chats.xml`, `item_contact.xml`
- Drawables (vector icons + button backgrounds): `res/drawable/*`
- Colors / strings / themes: `res/values/colors.xml`, `strings.xml`, `themes.xml`
- Default config: `assets/linphonerc`, `res/raw/default_provisioning.xml`

---

## Architecture

The UI talks to a single interface, `CallEngine`:

```
Activities/Fragments  ─────▶  CallEngineLocator.engine  ─────▶  CallEngine
                                                                    ▲
                                                       ┌────────────┴───────────┐
                                                       │   DemoCallEngine        │
                                                       │   (simulated; default)  │
                                                       └─────────────────────────┘
                                                       │  Real SIP engine (future)│
                                                       │  e.g. liblinphone        │
                                                       └─────────────────────────┘
```

Because the screens only depend on the `CallEngine` interface, the bundled
`DemoCallEngine` (which simulates ringing → connected locally) makes the whole UI
**usable today without a SIP account**. To go live you replace
`CallEngineLocator.engine` with a real SIP implementation — **no screen changes
required**.

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

All added libraries are standard Material/AndroidX UI components only — no
Glide/Picasso is needed because this scaffold renders a static avatar and does not
fetch contact photos. If you later add remote avatar loading, add Glide/Picasso then.

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
```

---

## Building

Requires JDK 17 and Android Studio (or a Gradle 8.5 distribution). The
`gradle/wrapper/gradle-wrapper.jar` is **not** committed (the sandbox had no
network access to fetch it); Android Studio will regenerate it, or run
`gradle wrapper --gradle-version 8.5` in the project root.

Then open the project in Android Studio and run the `app` configuration on an
emulator/device.

---

## ⚠️ Licensing note (important)

This repository is provided as **original code**. The Linphone Android project is
licensed under **GPLv3**. If you copy Linphone source files (layouts, drawables,
Activites, SDK bindings) into your own project, your combined work must be released
under GPLv3-compatible terms, which typically requires open-sourcing your entire
application.

Two safe paths forward when you want real SIP:

1. **Clean-room UI + your own engine** (recommended here): keep the screens and
   implement `CallEngine` against a separately-licensed SIP stack (e.g. a
   commercial/BSD SDK), or
2. **Call a backend/WebRTC gateway** instead of embedding an SDK on-device.

The bundled `DemoCallEngine` intentionally contains **no** Linphone code, so the
current scaffold carries no GPL obligations.

---

## Files added / modified

Added (all new):

- Root: `settings.gradle`, `build.gradle`, `gradle.properties`, `.gitignore`,
  `gradlew`, `gradle/wrapper/gradle-wrapper.properties`
- `app/build.gradle`, `app/proguard-rules.pro`,
  `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/chatapp/modern/*` (see table above)
- `app/src/main/res/layout/*`, `res/drawable/*`, `res/values/*`,
  `res/raw/default_provisioning.xml`
- `app/src/main/assets/linphonerc`
- `README.md` (this file)

Modified:

- `README.md` (was a stub, now documents the project)
- `.gitignore` (new — no prior file existed)

No existing project files were overwritten; the repository was empty except for the
stub `README.md`.
