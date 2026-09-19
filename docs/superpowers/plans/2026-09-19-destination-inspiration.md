# Destination Inspiration Implementation Plan

> **For agentic workers:** Execute this plan in the current isolated worktree. The referenced superpowers execution skills are not installed; use direct implementation with per-task verification. Steps use checkbox syntax for tracking.

**Goal:** Add offline city inspiration cards and travel guide details for 天津、济南、青岛、大同、洛阳.

**Architecture:** Bundle a five-city JSON catalogue and licensed photos independently of the ticket source. Compose reads this catalogue synchronously without network and adds a destination page before the existing train-detail page; direct train navigation remains available.

**Tech Stack:** Kotlin, Jetpack Compose, Gson (existing core dependency), JUnit, Compose instrumentation, Pencil.

## Global Constraints

- minSdk 26; compileSdk/targetSdk 36; no Google Play Services.
- Five cities only; every other city retains its ticket workflow.
- No weather, hotel prices, return tickets, city comparison or global trip-day filter.
- Day plans are editorial suggestions, independent of departure-date ranges.
- Source URLs, check dates, image attribution/license and modification notice remain accessible.
- Do not clear preferences or run test resets on the personal device.
- Work on codex/destination-inspiration, baseline 08784f8, preserve backup.

### Task 1: Verified offline content

**Files:** core/src/main/kotlin/cn/traintrip/core/DestinationGuide.kt; core/src/main/resources/destination_guides.json; app/src/main/assets/destinations/*.jpg; core/src/test/kotlin/cn/traintrip/core/DestinationGuideTest.kt; docs/verification/destination-inspiration/sources.json.

**Interfaces:** `DestinationGuides.find(cityId: String): DestinationGuide?`, `DestinationGuides.all: List<DestinationGuide>`. Guide contains cityId, name, tagline, tags, suggestedDays, pace, season, arrivalAdvice, experiences (id/name/reason/duration/location), foods (name/description), plans (days/title/stops/note), source metadata and photo metadata.

- [x] Read 5 city source pages and retain revision/time/URL; use only supported sights and foods.
- [x] Download 5 Commons photos with verified creator/license and original file URL; resize JPEG to at most 960px, record derivative.
- [x] Add catalogue loader with safe empty fallback, exact city IDs and no network dependency.
- [x] Test five-city coverage, unknown city fallback, safe missing/corrupt resource, required attribution fields, valid plan references, local JPEG existence.
- [x] Run `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test --offline`.

### Task 2: Cards, destination screen and navigation

**Files:** app/src/main/kotlin/cn/traintrip/app/ui/DestinationGuideScreen.kt; ResultsScreen.kt; TrainTripApp.kt; AppViewModel.kt.

**Interfaces:** `DestinationGuideScreen(cityName, guide, onBack, onTrains, onSource)`. Result adds `onGuide` callback. `showCity` remains direct train navigation; `showDestination` and `showDestinationTrains` set explicit return target.

- [x] Cards show photo, reason, tags and days above ticket summary. Add visible “了解目的地” and “查看车次”; unknown guide retains original card behavior.
- [x] Detail uses LazyColumn with stable section keys, saveable 1/2-day selection and fixed bottom train button; use dynamic text heights and wrapping tags.
- [x] Show 3 experiences, foods, itinerary, seasonal/arrival advice and an attribution/source dialog with clickable HTTPS URLs.
- [x] Add DESTINATION route; saveable page key includes cityId for city-specific scroll position; back behavior tested for both entry paths.
- [x] Keep old ticket source, selection, recheck and handoff unchanged.

### Task 3: Design and user-flow verification

**Files:** design/train-trip-v1.pen; design/README.md; app/src/androidTest/kotlin/cn/traintrip/app/DestinationGuideTest.kt; app/build.gradle.kts; docs/verification/2026-09-19-destination-inspiration.md; README.md.

- [x] Create editable inspiration card and full guide plus unavailable-content state in worktree Pencil file; render, verify bounds, export references and OCR.
- [x] Build offline sample tests without real ticket requests, verify navigation, day switch, fallback, attribution and 1.3x font/compact width.
- [x] Run existing TimeRangePicker and UI polish tests on a temporary emulator only; copy screenshot evidence and inspect it.
- [x] Build `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`; record warnings and exit status.
- [x] Bump version to 0.2.0 / code 4, preserve previous APKs, copy final artifact and verify SHA-256 and signing certificate.
- [x] Record actual validation, limitations and rollback; commit scoped changes; send completion notification via punk-12 with artifact location.

Spec coverage review: five-city scope, both navigation paths, unknown city, offline failure, attribution, large font and ticket isolation mapped above. No new dependencies or live ticket load are required.
