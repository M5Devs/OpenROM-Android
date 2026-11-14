# OpenROM-Android — Agent Guide

## What this repo is
Native Kotlin Android app for OpenROM ROM conversion toolkit.
Companion to https://github.com/M5Devs/OpenROM (Flutter/Dart desktop).
Shares NO Dart code — only JSON specs and binary tool contracts.

Package: `com.m5dev.openrom` · min SDK 26 · target/compile SDK 35 ·
Kotlin 2.0 · Material 3 + ViewBinding (not Compose) · single Activity +
Navigation Component · GPL v3.

## Key contracts (must stay in sync with desktop repo)
- `plugin.json` schema → `PluginManifest.kt` (schemaVersion = 2)
- `manifest.json` format → `BinsManifest.kt`
- Format names (CHD/CSO/etc) → `FormatDetector.kt`
- Store API → `https://openrom-store.pages.dev/api/plugins.json`

## Android binary tools (arm64-v8a only)
Available: chdman, ecm, unecm, maxcso, xdelta3
NOT available on Android: nodtool, saturn-patcher, xgdtool, nkit
FormatDetector MUST hide routes requiring unavailable tools.

## Storage rules
NEVER use java.io.File for user-picked files.
ALWAYS use SAF (Storage Access Framework) for input/output.
Binaries live in context.filesDir/bins/ — never external storage.
Plugin files live in context.filesDir/plugins/<id>/

## Security rules (PluginInstaller)
1. Zip-Slip check on every archive entry
2. SHA-256 verify BEFORE extraction
3. Max 4096 entries, 512MB total, 256MB per entry
4. Atomic swap with .backup rollback

## Build & test
```bash
./gradlew assembleDebug     # APK, must be warning-free
./gradlew test              # JVM unit tests
./gradlew lint              # must report 0 errors
```

The SDK location is read from `local.properties` (`sdk.dir`) or `ANDROID_HOME`.

## Branch policy
Feature branches: feat/<name>
Fix branches: fix/<name>
Always push branch + open PR. Never push to main directly.
