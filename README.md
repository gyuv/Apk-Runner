# Apk-Runner

Run Android apps on **Windows, Intel Macs, Apple Silicon Macs and Linux** as ordinary
desktop processes: no emulator, no virtual machine, no Android booting underneath.
Inspired by the approach of [kksimp/Mac.apk](https://github.com/kksimp/Mac.apk)
(Apple Silicon only), redesigned to be cross-platform.

> **Status: early foundation (v0.1).** Only the APK inspector works today. It can't run apps yet.

## Why native is easy on Apple Silicon and hard elsewhere

An APK contains Java/Kotlin bytecode (`.dex`), which is portable, and often native
libraries (`lib/<abi>/*.so`), which are not. Apple Silicon and most phones are ARM64, so
Mac.apk can execute the ARM code directly. On an x86 PC that code needs a translator.

So every APK falls into one of three cases on a given host:

| Verdict | Meaning | x86 Windows / Intel Mac |
|---|---|---|
| `native` | No native libs, or libs for this CPU (`x86_64`) | Runs natively once the runtime exists |
| `needs-translation` | ARM-only native libs (most games) | Needs the ARM→x86 translator (phase 4) |
| `unsupported` | No usable ABI at all | – |

## Try it (works now)

```bash
python -m apkrunner inspect app.apk            # also .xapk / .apkm bundles
python -m apkrunner inspect --arch x86_64 app.apk
python -m apkrunner inspect --json app.apk
```

Requires only Python 3.9+, with no third-party packages.

## Architecture (planned)

```
APK ─► unpacker ─► dex → JVM bytecode ─► HotSpot/OpenJDK (all OSes)
                 │                        └─ Android framework reimplementation (Java)
                 └► .so loader (bionic shim)
                       ├─ host ABI matches  → load directly (ELF loader on Win/mac)
                       └─ ARM on x86        → dynamic binary translator
Graphics: GLES → ANGLE (D3D11 on Windows, Metal on macOS, Vulkan/GL on Linux)
Audio / input / windows: SDL3 (one portable layer instead of per-OS bridges)
```

Choosing SDL3 plus ANGLE for the host layer, instead of writing Cocoa bridges by hand,
keeps the runtime small and gives one codebase for all three OSes.

## Roadmap

1. **Inspector** (done): manifest, ABIs, split bundles, per-host verdict.
2. **Unpacker and installer**: merge split APKs, extract resources, run dex→class conversion.
3. **Java-only apps**: framework subset on OpenJDK, a window through SDL3, input, and Canvas rendering.
4. **Native libs**: ELF loader plus bionic shim on Windows and macOS, and GLES through ANGLE.
5. **ARM→x86 translation**: dynamic binary translator for ARM-only apps. This is the hardest part.

## License note

The code here is written from scratch. Copying code from Mac.apk would make this
project AGPL-3.0 as well.
