# PDF Image Cropper

Native Android app (Kotlin, Jetpack Compose, Material 3, MVVM) that imports a PDF, lets you draw
**multiple finger-drawn crop rectangles per page**, and exports **every crop as its own JPG/PNG**
into `Pictures/PDF Image Cropper/`. Everything runs on-device: no internet permission, no analytics, no ads.

## How it works
- **Rendering:** `PdfRenderer`. Thumbnails are small (240 px wide, LRU-cached); the editor renders one page at a time.
- **Crops** are stored as normalized page coordinates (0..1), so they are independent of screen size, zoom and DPI.
  Export renders *only the cropped region* straight from the PDF at the chosen DPI (no screenshot of the screen).
- **Export** is sequential (one bitmap alive at a time), saved through `MediaStore` (scoped storage, no permission on Android 10+).
  Android 8–9 asks for the legacy storage permission at export time.
- **Sessions** (crops + selected pages) are saved per PDF and restored when you reopen it from Recent PDFs.

## Get the APK from GitHub Actions (no computer needed)
1. Push this folder to a GitHub repository.
2. Open the **Actions** tab → **Android Build** → latest run (or *Run workflow*).
3. When it finishes, scroll to **Artifacts** and download `pdf-image-cropper-debug`, unzip, install the `.apk`.

The debug APK needs no signing keys. If a build fails, the log shows the exact Gradle/Kotlin error; test reports are uploaded as an artifact.

## Release build (optional)
Add these repository secrets: `KEYSTORE_BASE64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
When `KEYSTORE_BASE64` exists the workflow also builds a signed release APK (`pdf-image-cropper-release` artifact).
Locally: `export KEYSTORE_PATH=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=...` then `gradle :app:assembleRelease`.
Never commit keystores or passwords (`.gitignore` already excludes `*.jks`).

## Local build
Requires JDK 17 and Android SDK 35. The Gradle wrapper jar is not included; run `gradle wrapper --gradle-version 8.9` once, or just use `gradle`.
`gradle :app:testDebugUnitTest :app:assembleDebug`

## Using it
Import PDF → tap a page → drag a rectangle around each photo (drag handles to resize, drag inside the active crop to move;
pinch to zoom, two fingers to pan) → **Review & convert** → pick format/quality/resolution → **CONVERT ALL**.

## Not implemented yet (be aware)
- Freeform (lasso) crop and rotate-preview.
- WorkManager / foreground-service conversion: export runs in the app's coroutine scope and is cancellable, but is not guaranteed to survive the app being killed.
- Instrumented (on-device) tests for PdfRenderer and gallery saving; current unit tests cover geometry, zoom mapping, sizing, filenames and persistence.
- Custom numeric resolution (presets only).
