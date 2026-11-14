# OpenROM-Android

Native Kotlin Android companion to
[OpenROM](https://github.com/M5Devs/OpenROM), the universal ROM toolkit.
It reimplements the desktop app's core contracts — format detection,
conversion jobs, the `.orplug` plugin system and the binary tool manifest — for
Android, sharing no Dart or Flutter code.

License: **GPL v3**.

## What it does

Pick a ROM image, see which formats it can become, and convert it. The heavy
lifting is done by the same command-line tools the desktop app ships
(`chdman`, `ecm`, `unecm`, `maxcso`, `xdelta3`), executed as separate processes
with the ROM never leaving the device.

```
ISO  → CHD · CSO · ECM
BIN  → CHD · ECM
CUE  → CHD
GDI  → CHD
CDI  → CHD
IMG  → CHD
CHD  → ISO · BIN/CUE
CSO  → ISO
ZSO  → ISO
ECM  → ISO · BIN
```

Only routes whose tools publish an `android/arm64-v8a` build are offered.
`nodtool`, `saturn-patcher` and `xgdtool` have no Android build, so the
routes that need them (RVZ, WIA, WBFS, GCZ) are hidden rather than shown and
then failing. Plugins can add those routes back.

## Architecture

```
MainActivity ── Navigation Component ── ConvertFragment / PluginsFragment / SettingsFragment
                        │
        ┌───────────────┼────────────────┬──────────────┐
     core/            plugin/          storage/        ui/
  FormatDetector   PluginManifest    SafHelper      ConvertFragment
  ProcessRunner    PluginInstaller   UriResolver    ProgressSheet
  ConversionEngine PluginExecutor                   FormatPickerSheet
  ConversionCoordinator PluginRegistry
  BinaryManager / BinsManifest
```

- **core** — pure detection logic (no Android types) plus the process
  execution and job orchestration around it.
- **plugin** — `.orplug` archive parsing, hardening and execution.
- **storage** — SAF helpers. User-picked files are never touched through
  `java.io.File`; only binaries and plugins in app-private storage are.
- **ui** — Material 3 with ViewBinding.

## Security notes

The plugin installer is the main untrusted-input boundary and is hardened
accordingly, with unit tests for each rule:

| Threat | Mitigation |
| --- | --- |
| Zip-Slip | every entry's resolved path is checked against the staging root |
| Zip bomb | max 4096 entries, 512 MB total, 256 MB per entry, checked from the central directory before writing |
| Symlink escape | symlink entries are rejected outright |
| Tampered archive | SHA-256 verified **before** any byte is extracted |
| Tampered binary | per-binary SHA-256 verified after extraction |
| Broken upgrade | extract to staging → atomic rename, with a `.backup` for rollback |
| Route hijacking | a plugin cannot shadow a built-in route unless it declares `override: true` |

Binaries are also checksummed against `assets/manifest.json` on every launch;
a binary that already matches its digest is never rewritten.

## Building

```bash
./gradlew assembleDebug   # debug APK
./gradlew test            # 61 JVM unit tests
./gradlew lint            # 0 errors
```

The Android SDK is located via `local.properties` (`sdk.dir=`) or
`$ANDROID_HOME`. Java 17 is required.

### Release signing

`assembleRelease` reads the keystore from the environment, and stays unsigned
when none is present so local builds still work:

```
OPENROM_KEYSTORE_PATH / OPENROM_KEYSTORE_PASSWORD
OPENROM_KEY_ALIAS     / OPENROM_KEY_PASSWORD
```

Pushing a `v*` tag builds, signs and attaches the APK to a GitHub Release.

## Tests

| Suite | Covers |
| --- | --- |
| `FormatDetectorTest` | extension and `content://` URI detection, `.nkit.iso` ordering, Android tool gating, plugin-merged formats, route precedence |
| `BinsManifestTest` | manifest parsing, `android/arm64-v8a` resolution, exclusion of tools with no Android build |
| `PluginManifestTest` | schema v2 parsing, v1 normalisation, validation rules |
| `PluginInstallerTest` | zip-slip, entry/total size limits, checksum mismatch, atomic replacement |
| `ProcessRunnerTest` | argv passing (no shell), progress parsing, timeouts, output streaming |

## Credits

ROMeo, the mascot, and the format specifications come from the
[OpenROM](https://github.com/M5Devs/OpenROM) desktop project (GPL v3).
