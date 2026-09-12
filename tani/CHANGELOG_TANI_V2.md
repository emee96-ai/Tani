# TANI v2 — Build workflow consolidation

## Changes
- Consolidated GitHub Actions into one workflow: `.github/workflows/build-apk.yml`.
- Removed duplicate `.github/workflows/ci.yml`.
- Switched build execution from a manually selected Gradle binary to the repository Gradle Wrapper:
  - `./gradlew lintDebug testDebugUnitTest`
  - `./gradlew :app:assembleDebug`
- Added Gradle Wrapper validation.
- Kept `gradle/actions/setup-gradle@v4` only for cache/configuration, without pinning a second Gradle version.
- Preserved artifact output as `tani-debug-apk`.

The Gradle version is now controlled only by `tani/gradle/wrapper/gradle-wrapper.properties`.
