# Ultron Wear

Wear OS client for academic scheduling and mandatory-attendance tracking.
The watch surface of **Ultron Mark III**, an agent in the SPEDA Mark VI system.

Ultron Wear renders a weekly timetable and maintains a per-teaching-hour
attendance ledger, computing in real time how many further absences a course
permits before the mandatory-attendance threshold is breached. After each
teaching hour the device prompts for an attendance answer, recorded in one tap
from the notification shade.

The application is functional standalone. Backend integration is optional and
degrades cleanly when absent.

---

## Contents

- [Domain model](#domain-model)
- [Architecture](#architecture)
- [Attendance prompt delivery](#attendance-prompt-delivery)
- [Build and configuration](#build-and-configuration)
- [Schedule format](#schedule-format)
- [Performance](#performance)
- [Design system](#design-system)
- [Project layout](#project-layout)
- [Compatibility](#compatibility)
- [Limitations](#limitations)

---

## Domain model

Attendance in the Turkish university system is recorded per **ders saati**
(teaching hour), not per course or per calendar day. A course meeting for three
consecutive hours produces three independent attendance records per week; a
student may attend the first and miss the third, which the register counts as
exactly one absence.

The absence budget is derived as follows:

```
scheduled  = teaching hours the term contains        (holidays excluded)
effective  = scheduled − hours cancelled by the instructor
allowed    = floor(effective × (1 − required_rate))
remaining  = allowed − hours recorded absent
```

Defaults are 14 teaching weeks at a 70% required attendance rate, both
configurable per term.

Three properties of this calculation are non-obvious and are treated as
invariants throughout the codebase:

**Cancellation is not absence.** A cancelled class is one the instructor did not
hold. It leaves the denominator entirely rather than counting against the
student. `CANCELLED` is therefore a distinct state in the storage schema, the
calculation, the user interface and the wire format — never folded into
`ABSENT`.

**Cancellations reduce the absence budget.** Because the threshold is
proportional, a smaller denominator yields a smaller allowance. Cancelling 5
hours of a 42-hour course reduces the budget from 12 to 11. This is
counter-intuitive and correct.

**The budget floors.** 42 hours × 0.30 = 12.6 permits **12** absences, not 13.
Rounding upward would report an absence the student does not have, in the one
calculation where an optimistic error causes a failed course.

The calculation is implemented twice — in `AttendanceCalculator.kt` here and in
`services/academic.py` on the server — because the device must produce a verdict
without network access. The two implementations are required to agree; the
server implementation is covered by 14 unit tests.

---

## Architecture

Single-process application with three entry points into one shared object graph
(`UltronWear.kt`): the main activity, the tile service and the complication
service. Dependencies are constructed by hand; there is no DI container, as the
graph consists of four singletons with no scoping requirements.

| Layer | Components | Responsibility |
|---|---|---|
| `design/` | `ColorMath`, `ThemeEngine`, `UltronType`, `Surfaces` | Design tokens, resolved once per process |
| `data/` | `Course`, `Attendance`, `AttendanceCalculator`, `AttendanceStore`, `ScheduleRepository`, `IgorClient` | Domain model, persistence, transport |
| `presentation/` | `UltronViewModel`, `ScheduleScreen`, `AttendanceScreen`, `AttendanceActivity` | State derivation and rendering |
| `notification/` | `AttendanceMessagingService`, `AttendanceNotifier`, `AttendanceActionReceiver` | Prompt delivery and answer capture |
| `sync/` | `SyncWorker`, `RegisterDeviceWorker`, `FallbackAskScheduler` | Deferred background work |

### Persistence

The attendance ledger is a single JSON document written atomically
(temporary file, then rename) and serialised behind a mutex. A full semester is
approximately 180 records — small enough to hold in memory and rewrite whole on
each mutation. Room was rejected as it would add an annotation processor, a
schema, a migration surface and several hundred kilobytes of dex in order to
index a collection that requires no index.

A ledger that fails to parse is quarantined to `attendance.json.corrupt` rather
than discarded, since it is the sole durable record of course eligibility.

### Schedule resolution

`ScheduleRepository` resolves the timetable in a fixed order:

1. Local cache (`filesDir/schedule.json`) — read first, so the interface renders
   without waiting on the network.
2. Bundled asset — first run, prior to the first successful synchronisation.
3. Empty — reported as such.

The bundled asset ships **empty by design**. An application that displays
fabricated placeholder courses when it has no data is less useful than one that
states it has no data.

A schedule refresh returning zero courses is rejected rather than applied, as an
empty server response is far more likely to indicate a misconfiguration than a
term with no classes.

---

## Attendance prompt delivery

Two independent triggers, with the remote path taking precedence.

**Primary — Firebase Cloud Messaging.** The backend pushes a data-only message
after a teaching hour ends. Data-only is required: a message containing a
`notification` block is rendered by the system tray while the application is
backgrounded and `onMessageReceived` is never invoked, which would leave no
action buttons, no ledger write and no means of answering. Combined with
`android.priority = high`, the handler is guaranteed to run.

**Fallback — local scheduling.** Each upcoming occurrence is armed with a
WorkManager job for 15 minutes after the scheduled end time. If the push
arrives, the message handler cancels the job. If it does not, the device raises
the prompt itself, offline, from its cached schedule.

The fallback exists because the prompt fires precisely when the device is least
likely to be reachable — inside a building, on a captive-portal campus network,
or in Doze. FCM guarantees eventual delivery to a reachable device; an
attendance prompt delivered hours late is answered from memory.

Disable with `FallbackAskScheduler.ENABLED = false` to operate push-only.

### Device registration

The device registers by **Firebase Installation ID**, not by registration token.
`firebase-messaging` 25.1.0 deprecated `getToken()`, `deleteToken()` and
`onNewToken()`; the SDK marks the callback *"Use onRegistered(String)
instead"*. The corresponding server SDKs deprecated `Message(token=…)` in favour
of `Message(fid=…)`. Registration tokens remain functional, but a new
integration built on them would require migration during the deprecation window.

### Answer capture

Answers are recorded from notification actions rather than by launching an
activity. On a wrist-worn device this is the difference between a single tap on
an already-raised arm and a cold Compose start. `AttendanceActionReceiver` uses
`goAsync()` so the process is not eligible for termination between the tap and
the durable write.

Record identity is `(slotId, date)`. Re-answering overwrites; conflicts between
device and server resolve last-write-wins on a device-supplied timestamp.
Ingestion is idempotent, as the device re-sends records whose upload failed.

---

## Build and configuration

### Requirements

- JDK 21
- Android SDK 36
- A Wear OS 4+ target (API 33+)

### `local.properties`

```properties
sdk.dir=C:\\Users\\<user>\\AppData\\Local\\Android\\Sdk

# Optional. Omit both for an offline-only build.
IGOR_BASE_URL=https://<backend-host>
IGOR_API_KEY=<api-key>
```

Injected into `BuildConfig` at build time. Git-ignored; no credential is
committed or embedded in source.

### Firebase (optional)

Place `google-services.json` in `app/`. The Gradle script detects the file and
applies the `google-services` plugin conditionally:

```kotlin
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) { apply(plugin = /* google-services */) }
```

The plugin fails the build when the file is absent, so applying it
unconditionally would prevent anyone from compiling the project before a
Firebase project exists. The messaging SDK remains on the classpath regardless —
it is the plugin, not the library, that requires the configuration — so sources
compile identically and the runtime simply finds no `FirebaseApp`.

### Building

```bash
./gradlew :app:assembleRelease
```

Release output is approximately 5.3 MB with R8 full mode enabled.

### Loading a schedule

With a backend: `PUT /academic/schedule`.
Without: copy `docs/courses.sample.json` over `app/src/main/assets/courses.json`.

---

## Schedule format

```json
{
  "term": {
    "start_date": "2026-09-21",
    "total_weeks": 14,
    "required_rate": 0.70,
    "holidays": ["2026-10-29"]
  },
  "courses": [
    {
      "id": "phys101_mon_0900",
      "code": "PHYS101",
      "name": "Fizik I",
      "instructor": "Dr. R. Wilson",
      "roomNumber": "C-310",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00",
      "endTime": "09:50"
    }
  ]
}
```

| Field | Constraint |
|---|---|
| `term.start_date` | The **Monday of week 1**. All week arithmetic counts from this date. |
| `id` | Unique per teaching hour, and **stable**. This is the key the ledger joins on; renaming it orphans that slot's recorded history. |
| `code` | Shared by every hour of one subject. This grouping defines the attendance budget. Two distinct courses must never share a code. |
| `dayOfWeek` | `java.time.DayOfWeek` name, uppercase. |
| `startTime` / `endTime` | `HH:MM`, 24-hour. |

One entry per teaching hour. A three-hour course is three entries.

---

## Performance

The predecessor implementation held a clock in composition state, updated it
every 60 seconds, and read it inside every course card. All thirteen cards were
therefore invalidated once per minute for the lifetime of the screen. The
per-card status calculation was memoised on that same timestamp, so the cache
never produced a hit.

Current implementation:

**Minimal derived state.** `UltronViewModel` reduces the clock to an
active-slot identifier and a next-slot identifier. A card compares its own
identifier and receives a boolean, so a minute boundary invalidates at most the
two cards whose status actually changed.

**Draw-phase state reads.** Lecture progress is passed as `() -> Float` and
invoked inside `Modifier.drawBehind`. The read is deferred to the draw phase,
so an advancing progress bar costs a redraw rather than a recomposition and
layout pass.

**Aligned, bounded ticking.** The clock sleeps to the next wall-clock minute
rather than a flat 60-second interval, so status transitions occur on the minute
instead of up to 59 seconds late.

**Baseline profile.** `app/src/main/baseline-prof.txt` marks the startup path
for ahead-of-time compilation, removing JIT warm-up from the first frames.

**Build configuration.** R8 in full mode; `androidx.wear:wear` removed as unused
(it also pinned `androidx.fragment` to a 2019 release that failed
`lintVitalRelease`).

**Shared repositories.** The tile and complication services read the same
repository instance as the activity. The predecessor constructed a fresh
repository per callback, re-parsing the schedule from assets on every tile
refresh.

> The baseline profile is hand-written from the known startup path rather than
> captured from a device. A profile generated by the Baseline Profile Gradle
> Plugin against target hardware would be strictly better; see
> [Limitations](#limitations).

---

## Design system

Ultron Wear implements the SPEDA (Heartbreaker) design language, ported from the
shared Android design system. `ThemeEngine` expands a single accent colour into
the complete palette by hue-rotating a fixed token table while preserving
saturation and lightness. Ultron's accent is `#8a93a6` (hue ≈ 221°), so
structural tokens resolve to a cool blue-slate range.

The palette is resolved once per process and distributed via
`staticCompositionLocalOf`, as it cannot change at runtime.

Two deliberate deviations from the parent design system, both documented at
their definitions:

**No backdrop blur.** The reference material specifies
`backdrop-filter: blur(28px) saturate(140%)`. On the target SoC this forces an
offscreen composition layer and a per-frame render effect within a scrolling
list, in order to blur a pure-black background that contains nothing to refract.
Retained instead are the occluding fill, the milky tint and the 1px rim light —
the blur-less fallback the parent design system already specifies for contexts
where nested backdrop roots cancel blur.

**No ambient gradient.** The parent mobile port already flattens the body
gradient to `#000000` because an OLED pixel at true black is unlit. On a device
with an order of magnitude less battery capacity, the argument is stronger.

Typography retains the two-family split: **Rajdhani** for interface chrome
(uppercase tracked labels, day headers, numerals) and **Inter** for content
requiring sustained reading (course names, prompt copy). Sizes are re-derived
for a 1.5-inch display rather than scaled by a constant from the reference ramp.

---

## Project layout

```
app/src/main/
├── assets/courses.json                 Empty fallback; real data from backend
├── baseline-prof.txt                   AOT startup profile
├── java/com/spedatox/ultroncore/
│   ├── UltronWear.kt                   Application, object graph
│   ├── design/
│   │   ├── ColorMath.kt                Accent → palette (port of theme.ts)
│   │   ├── BaseTokens.kt               Structural token table
│   │   ├── ThemeEngine.kt              Palette resolution, once per process
│   │   ├── UltronPalette.kt            Resolved tokens
│   │   ├── UltronType.kt               Rajdhani + Inter ramp
│   │   └── Surfaces.kt                 Glass material, accent edge, etched seam
│   ├── data/
│   │   ├── Course.kt                   One teaching hour
│   │   ├── Attendance.kt               Ledger model, term, risk
│   │   ├── AttendanceCalculator.kt     Occurrence expansion, budget calculation
│   │   ├── AttendanceStore.kt          Atomic JSON ledger
│   │   ├── ScheduleRepository.kt       Cache → asset → empty resolution
│   │   ├── ScheduleWire.kt             Transport DTOs
│   │   └── IgorClient.kt               REST transport
│   ├── presentation/
│   │   ├── MainActivity.kt             Host, navigation
│   │   ├── UltronViewModel.kt          Derived state
│   │   ├── ScheduleScreen.kt
│   │   ├── AttendanceScreen.kt
│   │   ├── AttendanceActivity.kt       Full-screen prompt
│   │   └── components/
│   ├── notification/                   FCM receiver, notifier, action receiver
│   ├── sync/                           WorkManager: sync, registration, fallback
│   ├── tile/                           Tile provider
│   └── complication/                   Complication data source
└── res/font/                           Rajdhani, Inter
```

---

## Compatibility

| Component | Version |
|---|---|
| Kotlin | 2.3.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| compileSdk / targetSdk | 36 |
| minSdk | 33 (Wear OS 4) |
| Compose BOM | 2026.06.01 |
| Wear Compose | 1.6.2 (Material 3) |
| Firebase BoM | 34.16.0 |
| WorkManager | 2.10.5 |

`androidx.wear.compose:compose-material` is superseded by `compose-material3`;
this project targets the latter.

AGP is held on the 8.x line rather than 9.x. AGP 9 is a build-system release —
keep-rule source sets, built-in Kotlin, a Gradle 9 baseline — affecting nothing
the device executes. Every dependency that does affect runtime behaviour is
pinned to current stable.

---

## Limitations

- **Not validated on hardware.** Both build variants compile and the release
  build passes `lintVitalRelease` under R8 full mode, but the application has
  not been executed on a physical device. The performance characteristics
  described above are structural — recomposition scope is bounded by
  construction — and are not backed by a captured frame trace.
- **Baseline profile is hand-authored.** See [Performance](#performance).
- **Attendance calculation is duplicated** between client and server by
  necessity. Only the server implementation carries automated tests; the Kotlin
  implementation is currently verified by inspection against the same
  specification.
- **No instrumented tests.** Neither UI nor persistence has automated coverage
  on-device.

---

## License

MIT. See [LICENSE](LICENSE).
