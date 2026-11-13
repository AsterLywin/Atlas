# Atlas

**English** | [فارسی](#فارسی)

Atlas is a manga / manhwa / manhua / webtoon / comic reader for **Android,
Windows, Linux and macOS** — one app that aims to be better than Kotatsu,
Mihon and Suwayomi combined: Kotatsu's built-in sources, Mihon's reader and
library, Suwayomi's server, sync and auto-download.

> **Source v1.** This repository is the *source* skeleton, not a finished app:
> the three-part structure exists and compiles, the Android app is the Mihon
> base renamed to Atlas, the desktop app opens a library. Kotatsu parsers,
> Suwayomi sync / auto-download, the full reader, trackers, backup and the
> Play Store release are **phase 2** and are not in this version.

## Structure

```
Atlas/
├── atlas-core/      Shared Kotlin: Source / Book / Chapter / DownloadState
│                    contracts and an in-memory library store.
├── atlas-android/   Android app (Mihon v0.21.3 base, renamed to Atlas).
│                    applicationId com.atlas.reader.
└── atlas-desktop/   Compose Multiplatform skeleton (Windows / Linux / macOS),
                    same atlas-core, minimal library UI.
```

## Build

```sh
./gradlew :atlas-android:app:compileDebugKotlin
./gradlew :atlas-core:build
./gradlew :atlas-desktop:compileKotlinDesktop
```

Mihon needs **Gradle 9.8.2 and a Java 21 JVM** (the Metro plugin requires
JVM 21). APK builds (`assembleDebug` / `assembleRelease`) run only on
explicit request — never as part of a routine check.

VM build notes (the sandbox blocks loopback TCP for dual-stack JVMs, so every
JVM Gradle forks must run IPv4-only):

- Run Gradle through the launcher directly, with
  `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"` and
  `JAVA_HOME=~/tools/jdk-21-ipv4` — a copy of Temurin 21 whose `bin/java`
  is a shim that prepends `-Djava.net.preferIPv4Stack=true` (the real binary
  lives beside it as `java.real`, so `java.home` still resolves to the copy).
  A plain `-D` flag on the Gradle command line does **not** reach the
  Gradle worker daemons (SqlDelight codegen fails with
  "Never received a connection from Gradle Worker Daemon").
- Dependencies resolve through the local mirror `http://127.0.0.1:18080`
  (`~/tools/relay/mirror.py`); it also proxies JitPack.
- Toolchain pins, relative to upstream Mihon v0.21.3: `compileSdk 37 → 36`
  and `targetSdk 36` (no android-37 platform in the public SDK repository
  yet), `ndk 29.0.14206865 → 28.2.13676358` (the tree has no native code).
  One source line depends on the newer SDK:
  `DelegateLastClassLoaderCompat.kt` uses the literal `37` instead of
  `Build.VERSION_CODES.CINNAMON_BUN` (API 37, same value).

## Attribution & license

Atlas is **GPL-3.0** (see `LICENSE`). It is built from open-source work:

- **Mihon** (Apache-2.0, `mihonapp/mihon`, v0.21.3) — the Android base in
  `atlas-android/`; its Apache license is kept at `atlas-android/LICENSE`.
- **Kotatsu** (GPL-3.0, `KotatsuApp/Kotatsu`, archived) and its live fork
  **Kotatsu-Redo** — the 1200+ built-in parsers Atlas plans to adopt in
  phase 2. Kotatsu code is why the whole app is GPL-3.0.
- **Suwayomi-Server** (MPL-2.0, `Suwayomi/Suwayomi-Server`) — the server,
  sync and auto-download model for phase 2.

## Known gaps in source v1 (to fix before any release)

- The donation screen and the Patreon / OpenCollective links still point at
  Mihon's channels; Atlas has no donation channels yet.
- `google-services.json` is still Mihon's Firebase config and tracker OAuth
  (AniList etc.) still uses Mihon's clients — Atlas needs its own.
- `fastlane/` metadata is still Mihon's store listing.
- Old deep-link schemes (`tachiyomi://`, `mihon://`) are kept as aliases so
  extension-repo links keep working.
- Non-English locales still say "Mihon" in a few strings; only the base
  (English) strings were rebranded.

---

## فارسی

**اطلس** یک برنامه‌ی خواندن مانگا / مانهوا / مانهوا / وب‌تون / کمیک برای
**اندروید، ویندوز، لینوکس و مک** است — یک برنامه که می‌خواهد از کوتاتسو،
میهون و سووایومی با هم بهتر باشد: سورس‌های داخلی کوتاتسو، خواننده و کتابخانه‌ی
میهون، سرور و همگام‌سازی و دانلود خودکار سووایومی.

> **نسخه‌ی اول سورس.** این مخزن اسکلت *سورس* است، نه یک برنامه‌ی کامل:
> ساختار سه‌بخشی وجود دارد و کامپایل می‌شود، برنامه‌ی اندروید همان میهون است
> که به اطلس تغییر نام داده، و برنامه‌ی دسکتاپ کتابخانه را باز می‌کند. پارسرهای
> کوتاتسو، همگام‌سازی و دانلود خودکار سووایومی، خواننده‌ی کامل، ترکرها، پشتیبان
> و انتشار در پلی‌استور **فاز ۲** هستند و در این نسخه نیستند.

### ساختار

```
Atlas/
├── atlas-core/      کوتین مشترک: قراردادهای Source و Book و Chapter و DownloadState
│                    و یک کتابخانه‌ی درون‌حافظه‌ای.
├── atlas-android/   برنامه‌ی اندروید (بر پایه‌ی Mihon v0.21.3، تغییر نام به Atlas).
│                    applicationId برابر com.atlas.reader است.
└── atlas-desktop/   اسکلت Compose Multiplatform (ویندوز / لینوکس / مک)،
                    با همان atlas-core و یک رابط کاربری ساده‌ی کتابخانه.
```

### ساخت

```sh
./gradlew :atlas-android:app:compileDebugKotlin
./gradlew :atlas-core:build
./gradlew :atlas-desktop:compileKotlinDesktop
```

میهون به **Gradle 9.8.2 و JVM جاوا ۲۱** نیاز دارد (پلاگین Metro به JVM 21
نیاز دارد). ساخت APK (‏`assembleDebug` / `assembleRelease`‏) فقط با درخواست
صریح انجام می‌شود — هرگز به‌عنوان بخشی از یک بررسی معمولی.

### انتساب و مجوز

اطلس **GPL-3.0** است (فایل `LICENSE` را ببینید) و بر پایه‌ی این کارهای
متن‌باز ساخته شده است:

- **میهون** (Apache-2.0، ‏`mihonapp/mihon`‏، نسخه‌ی 0.21.3) — پایه‌ی اندروید
  در `atlas-android/`؛ مجوز آپاچی آن در `atlas-android/LICENSE` نگه داشته شده است.
- **کوتاتسو** (GPL-3.0، ‏`KotatsuApp/Kotatsu`‏، آرشیو شده) و فورک زنده‌ی آن
  **Kotatsu-Redo** — بیش از ۱۲۰۰ پارسر داخلی که اطلس در فاز ۲ می‌خواهد
  استفاده کند. کد کوتاتسو دلیل GPL-3.0 بودن کل برنامه است.
- **Suwayomi-Server** (MPL-2.0، ‏`Suwayomi/Suwayomi-Server`‏) — مدل سرور،
  همگام‌سازی و دانلود خودکار برای فاز ۲.
