# Apk-Runner

Inspect, unpack and run Android apps (APK, XAPK, APKM) on **Windows, Intel Macs,
Apple Silicon Macs and Linux**, from one small tool with no dependencies.

The tool is pure Python, with no packages to install (it's about 30 KB). It picks the
best way to run each app on your machine:

| Your computer | How apps run | VM? |
|---|---|---|
| Apple Silicon Mac + [Mac.apk](https://github.com/kksimp/Mac.apk) installed | Natively through Mac.apk | **No** |
| Intel Mac | Android emulator (hardware-accelerated) + scrcpy window | Yes, a lightweight one |
| Windows 10/11 | Android emulator (WHPX acceleration) + scrcpy window | Yes, a lightweight one |
| Linux | Android emulator (KVM) + scrcpy window | Yes, a lightweight one |

> **Why a VM on Intel and Windows?** Most apps, games especially, include native ARM
> code. Apple Silicon is ARM, so Mac.apk runs that code directly. Intel and AMD CPUs
> can't, so ARM code has to be translated, and today only Android system images ship
> that translator. A VM-free x86 runtime is on the [roadmap](#roadmap). Run
> `apkrunner inspect` to see which apps could run without one.

---

## 1. Install

You need **Python 3.9 or newer** ([python.org](https://www.python.org/downloads/);
macOS and Linux usually have it already).

**Option A: single-file release (easiest).** Download `apkrunner.pyz` from the
[Releases page](../../releases/latest), then:

```bash
python apkrunner.pyz --help          # Windows: py apkrunner.pyz --help
```

**Option B: install as a command.**

```bash
pip install https://github.com/gyuv/Apk-Runner/archive/refs/heads/main.zip
apkrunner --help
```

**Option C: from source.**

```bash
git clone https://github.com/gyuv/Apk-Runner && cd Apk-Runner
python -m apkrunner --help
```

The examples below write `apkrunner`. With option A, replace it with `python apkrunner.pyz`.

## 2. Set up a backend to run apps

Check what's available on your machine:

```bash
apkrunner doctor
```

### Apple Silicon Mac (no VM)
Install Mac.apk from its [releases](https://github.com/kksimp/Mac.apk/releases/latest)
into `/Applications`. Then `apkrunner run app.apk` uses it automatically.

### Intel Mac, Windows, Linux (or Apple Silicon without Mac.apk)
1. Install the **Android command-line tools** from
   [developer.android.com/studio](https://developer.android.com/studio#command-line-tools-only)
   (no need for the full Android Studio). Unzip them to
   `<SDK>/cmdline-tools/latest/`, then set `ANDROID_HOME=<SDK>`.
2. Install the emulator, adb and a system image. Use the x86_64 image on Intel/AMD and
   the arm64 image on Apple Silicon. The x86_64 Android 11+ images include ARM
   translation, so ARM-only apps work too:
   ```bash
   sdkmanager "platform-tools" "emulator" "system-images;android-34;google_apis;x86_64"
   avdmanager create avd -n apkrunner -k "system-images;android-34;google_apis;x86_64" -d pixel_6
   ```
3. Turn on hardware acceleration:
   - **Windows:** *Turn Windows features on or off* → enable **Windows Hypervisor Platform**, then reboot.
   - **Intel Mac:** built in (Hypervisor.framework), nothing to do.
   - **Linux:** make sure `/dev/kvm` exists (`sudo apt install qemu-kvm` and add yourself to the `kvm` group).
4. (Recommended) Install **[scrcpy](https://github.com/Genymobile/scrcpy)** so each app
   opens in its own desktop window. Use `brew install scrcpy`, `winget install Genymobile.scrcpy`
   or `apt install scrcpy`.

## 3. Use it

```bash
apkrunner run game.apk               # boot (lite profile), install, launch, open window
apkrunner run app.xapk               # split bundles are installed with all their parts
apkrunner run app.apk --avd apkrunner --full   # choose the AVD, use full resources
apkrunner inspect app.apk            # can it run natively on this machine?
apkrunner inspect --json app.apk     # machine-readable output
apkrunner extract app.apk -o out/    # unpack and decode AndroidManifest.xml to readable XML
apkrunner merge app.xapk -o splits/  # pull the split APKs out of a bundle
```

`run` options:

| Option | Meaning |
|---|---|
| `--backend macapk\|emulator` | Force a backend |
| `--avd NAME` | Which emulator to boot (default: first one found) |
| `--full` | Turn off the lite profile (lite = 2 GB RAM, no audio, no boot animation) |
| `--no-window` | Don't open scrcpy; just install and launch |

An emulator that's already running (or a real phone connected over USB) is reused,
so the second launch takes a few seconds.

### What `inspect` tells you

| Verdict | Meaning |
|---|---|
| `native` | No native code, or native code for your CPU: runnable without translation |
| `needs-translation` | ARM-only native code on an Intel/AMD machine |
| `unsupported` | No native code for any CPU type your machine can run |

## 4. Troubleshooting

| Problem | Fix |
|---|---|
| `No backend available` | Install the emulator (step 2), or set `ANDROID_HOME` |
| `No AVD found` | Run the `avdmanager create avd ...` line from step 2 |
| Emulator very slow | Hardware acceleration is off (step 2.3) |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | Use an Android 11+ x86_64 image (it has ARM translation) |
| App opens but no window appears | Install scrcpy, or open the emulator window |

## Roadmap

1. Inspector, unpacker, bundle merge, multi-backend runner. **(done, v0.2)**
2. Java-only apps without a VM: dex → JVM bytecode on OpenJDK with an Android framework subset, windows through SDL3.
3. Native x86_64 `.so` loading without a VM (ELF loader + bionic shim), with GLES through ANGLE.
4. ARM → x86 translation, so ARM-only apps run without a VM on Intel and Windows.

Steps 2–4 are a large research effort (Mac.apk itself is about 3,000 files). Contributions are welcome.

## Developing

```bash
python -m unittest discover -s tests -t .   # tests
python scripts/build_release.py             # builds dist/apkrunner.pyz
```

To publish a release, push a tag (`git tag v0.2.0 && git push origin v0.2.0`) or run
the **Release** workflow from the Actions tab. It attaches `apkrunner.pyz`, a wheel
and the source archive.

## License

Written from scratch. It doesn't contain code from Mac.apk (AGPL-3.0), which it only
launches as a separate program when that's installed.
