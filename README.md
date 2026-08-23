# WallpaperApps — Shared Codebase, Many Apps

One Gradle project that builds every Droidates wallpaper app. All the code lives
in **`:core`**; each Play Store app is a thin module that supplies only its own
identity, keys and artwork.

Fix a bug once in `:core` and every app gets it on the next build.

```
WallpaperApps/
  core/           ← 180 shared Kotlin files: screens, viewmodels, repos, DI, utils, resources
  pixel11/        ← Pixel 11 Wallpapers        (2 files + icons)
  iphone18/       ← iPhone 18 Wallpapers       (2 files + icons)
  iphone17/       ← iPhone 17 Wallpapers       (2 files + icons)
  ios27/          ← iOS 27 Wallpapers          (2 files + icons)
  oneplus7/       ← OnePlus Wallpapers         (2 files + icons)
  s25ultra/       ← S25 Ultra Wallpapers       (2 files + icons)
  s26ultra/       ← S26 Ultra Wallpapers       (2 files + icons)
  samsungfold8/   ← Samsung Fold 8 Wallpapers  (2 files + icons)
  xiaomi17/       ← Xiaomi 17 Ultra Wallpapers (2 files + icons)
  app/            ← legacy OnePlus 7/7T flavor module (see "Legacy" below)
```

## Current apps

| Module | App | Application ID | Home collection | Version |
|--------|-----|----------------|-----------------|---------|
| `:pixel11` | Pixel 11 Wallpapers | `com.droidates.pixel11wallpapers` | `Google` | 1.3 |
| `:iphone18` | iPhone 18 Wallpapers | `com.droidates.iphone18wallpapers` | `Apple` | 1.0 |
| `:iphone17` | iPhone 17 Wallpapers | `com.droidates.iphone17wallpapers` | `Apple` | 1.9 |
| `:ios27` | iOS 27 Wallpapers | `com.droidates.ios27wallpapers` | `Apple` | 1.7 |
| `:oneplus7` | OnePlus Wallpapers | `com.droidates.oneplus7Wallpapers` | `OnePlus` | 1.7 |
| `:s25ultra` | S25 Ultra Wallpapers | `com.droidates.s25ultrawallpapers` | `Samsung` | 3.1 |
| `:s26ultra` | S26 Ultra Wallpapers | `com.droidates.s26ultrawallpapers` | `Samsung` | 1.4 |
| `:samsungfold8` | Samsung Fold 8 Wallpapers | `com.droidates.samsungfold8wallpapers` | `Samsung` | 1.0 |
| `:xiaomi17` | Xiaomi 17 Ultra Wallpapers | `com.droidates.xiaomi17ultrawallpapers` | `Xiaomi` | 1.3 |

The three Samsung apps and the three Apple apps share their brand's Firestore
collection — only the per-app sub-collections and the `AppUpdate` doc id differ.

## Getting started (fresh clone)

```bash
git clone https://github.com/Azzy987/WallpaperApps.git
```

Two files are intentionally **not** in the repo and must exist locally before a
build succeeds:

| File | Why it is absent | What to do |
|------|------------------|------------|
| `local.properties` | machine-specific SDK path | Android Studio writes it on first open, or add `sdk.dir=/Users/<you>/Library/Android/sdk` |
| `<module>/keystore.properties` | signing passwords | Only needed for `bundleRelease` — see [Signing](#signing) |

`google-services.json` **is** committed at the project root by design (see
[Firebase](#firebase--one-project-one-file-many-apps)); it holds client config
only, the same values that ship inside every released APK.

## Build

```bash
./gradlew :pixel11:assembleDebug
```

```bash
./gradlew :iphone18:installDebug
```

Release AAB for the Play Console (one app at a time — each has its own keystore,
application ID and listing):

```bash
./gradlew :pixel11:bundleRelease
```

## What lives where

Each app module contains **exactly two Kotlin files**:

| File | Purpose |
|------|---------|
| `<App>Spec.kt` | Every per-app value — brand, Firestore paths, ad IDs, billing ID |
| `WallpaperApp.kt` | `@HiltAndroidApp` entry point; installs the spec |

Plus `build.gradle.kts`, `AndroidManifest.xml` (application + AdMob App ID only),
`res/values/strings.xml` (`app_name`) and launcher icons.

## Firebase — one project, one file, many apps

Every app lives in the Firebase project **`wallpaper-apps-cad2c`**, and a single
`google-services.json` at the **project root** serves all modules. Each module
copies it in at build time, so one download updates every app.

Adding an app still means registering its package in the Firebase console —
Firebase mints a distinct `mobilesdk_app_id` per package, and a package that was
never registered is missing from the file, which fails the build with
*"No matching client found"*. That registration is all Android Studio's
"which app?" prompt is asking for; it does not mean separate files or projects.

Everything else — all 180 files — is shared from `:core`.

### How per-app values reach shared code

`:core` never hardcodes a brand. Each app installs its spec at startup:

```kotlin
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(Pixel11Spec)
        super.onCreate()
    }
}
```

Shared code keeps reading `AppConfig.COLLECTION_HOME`, `AppConfig.AD_BANNER_ID`
and so on; the values come from whichever app is running. Compare
[Pixel11Spec.kt](pixel11/src/main/java/com/droidates/pixel11wallpapers/Pixel11Spec.kt)
against [IPhone18Spec.kt](iphone18/src/main/java/com/droidates/iphone18wallpapers/IPhone18Spec.kt)
— same shape, different brand, and that is the entire difference between the apps.

## Adding another app

See **[NEW_APP_GUIDE.md](NEW_APP_GUIDE.md)**. Short version: copy an app module,
change the values in its spec, add one line to `settings.gradle.kts`.

## Signing

Each app signs with its own keystore. Create `<module>/keystore.properties`
(git-ignored):

```properties
storeFile=/Users/Azam/keystores/pixel11.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

The build reads it automatically. Without the file, debug builds still work and
release builds are simply unsigned.

## Legacy `:app` module

`:app` is the older product-flavor module for OnePlus 7 / 7T. It is **not** part
of the shared-core structure and currently does not build — its manifest
references `@string/default_notification_channel_id`, which its `strings.xml`
never defined (a pre-existing gap, not caused by the restructure). It was left
untouched deliberately. Migrate those two apps into their own modules when you
need them, following the new-app guide.

## Re-engagement notifications

An on-device reminder posts one rotating message every ~3 days, deep-linking into
the matching category. No server and no Firebase Blaze — it runs entirely in the app.

- 20 messages live in `core/.../notifications/EngagementMessages.kt`, covering all
  five main categories from the admin panel (`mainCategories` in
  `wallpaper-dashboard-zenith/src/lib/firebase.ts`). Edit freely; only
  `categoryName` is load-bearing — it must match a `Categories` doc **exactly**.
- Subcategories are tabs *inside* a category screen, not separate destinations, so
  every notification deep-links to the parent category. Messages written about a
  subcategory set `subcategoryHint`, which appends "Open the <tab> tab" to the body
  so the copy matches where the tap actually lands.
- Scheduling is in `EngagementNotificationWorker`. `INTERVAL_DAYS` sets the cadence.
- Users who opened the app within the last 2 days are skipped, so it never nags
  someone already active.
- WorkManager batches background work to save battery, so treat the interval as
  "roughly every N days", not an exact clock.

### FCM

The FCM **receiver service** (`FCMService`) and its manifest entries were removed —
no push had been sent in months and reminders are now generated on-device.

Topic *subscriptions* were deliberately kept (`all_users` plus a per-app topic named
after `documentAppUpdate`, e.g. `pixel11wallpapers`). They cost nothing and keep
installs reachable. Re-enabling push means restoring a `FirebaseMessagingService`
subclass and its manifest `<service>` entry — the dependency is still in `:core`.
