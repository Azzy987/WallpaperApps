# Adding a New Wallpaper App

Every app shares `:core`. Creating a new one means writing its identity — never
copying screens or logic.

Example: adding **OnePlus 16 Wallpapers** (`com.droidates.oneplus16wallpapers`).

---

## 1. Copy an existing app module

Pick the one whose brand is closest — `iphone18` for an Apple app, `pixel11`
otherwise.

```bash
rsync -a --exclude 'build/' pixel11/ oneplus16/
```

Rename the package directory and rewrite the id:

```bash
mv oneplus16/src/main/java/com/droidates/pixel11wallpapers oneplus16/src/main/java/com/droidates/oneplus16wallpapers
grep -rl "pixel11wallpapers" oneplus16 | grep -v google-services.json | while read f; do LC_ALL=C sed -i '' 's/pixel11wallpapers/oneplus16wallpapers/g' "$f"; done
```

Also update the `rootProject.file("pixel11/keystore.properties")` path near the
top of `oneplus16/build.gradle.kts`.

## 2. Register the module

In `settings.gradle.kts`:

```kotlin
include(":oneplus16")
```

## 3. Write the spec

Rename `Pixel11Spec.kt` → `OnePlus16Spec.kt` and set every value. This file is
the app.

| Field | Same brand as source | New brand |
|-------|---------------------|-----------|
| `appName`, `toolbarTitle` | change | change |
| `homeSectionTitle`, `onboardingTagline`, `deviceFamilyName` | change | change |
| `categoryBrandName`, `collectionHome` | keep | **change** (`OnePlus`) |
| `documentDevicesBrand` / `Secondary` | keep | **change** |
| `seriesPrefixRanges` | keep | **change** — see below |
| `documentBannersApp`, `documentUsersApp` | keep (brand-level) | **change** |
| `collectionBannersSub`, `collectionUsersSub` | **change** | change |
| `documentAppUpdate` | **change** | change |
| `prefsName`, `downloadFolderName`, `favoritesPrefsKey` | **change** | change |
| `billingLifetimeId`, prices | **change** | change |
| `adBannerId` / `adInterstitialId` / `adRewardedId` | **change** | change |

Then update `WallpaperApp.kt` to install the new spec object.

> **`seriesPrefixRanges` is a Firestore range, `[first, second)`.** Take the
> brand's label prefix and bump its last character: `"OnePlus" to "OnePlut"`.
> Get it wrong and the series filter chips silently come back empty — the app
> still builds and runs.

> **Never reuse another app's `documentAppUpdate`.** It drives the force-update
> wall; sharing it locks one app's users out when the other ships.

## 4. Per-app files

- `res/values/strings.xml` → `app_name`
- `AndroidManifest.xml` → `android:name` of the Application class and the AdMob
  `APPLICATION_ID`
- `res/mipmap-*/` → launcher icons
- `res/drawable/` + `drawable-v24/` → `onboarding_screen1/2/3.webp` if this app
  needs its own artwork (otherwise it inherits `:core`'s)

## 5. Firebase

All apps share the project **`wallpaper-apps-cad2c`**, and there is **one**
`google-services.json` at the **project root** that serves every module.

1. Firebase console → project `wallpaper-apps-cad2c` → Add app → Android →
   `com.droidates.oneplus16wallpapers`
2. Download `google-services.json` and replace the one at the **project root**
   (not in the module) — the download always contains every registered app
3. Add debug + release SHA-1 (needed for Google Sign-In)

Each module's `build.gradle.kts` copies the root file into itself at configuration
time, so one download updates every app. The per-module copies are generated and
git-ignored.

> **You cannot skip step 1 for a new app.** Firebase mints a distinct
> `mobilesdk_app_id` per package, so a package that was never registered simply is
> not in the file, and the Gradle plugin fails with *"No matching client found"*.
> Registering is what Android Studio's "which app?" prompt is asking about —
> it does not mean each app needs its own file or its own Firebase project.

## 6. AdMob

Create a **new AdMob app**. Never ship another app's live IDs — it misattributes
revenue and risks a policy strike.

> Test ads apply to the **banner only**, and only on a **debug build running on an
> emulator** (`AdEnvironment.shouldUseTestAds`). Interstitial and rewarded always
> use live IDs, including debug on a real phone. Add your device's test ID to
> `MobileAdsInitializer.kt` before testing on hardware.

## 7. Firestore data

The app builds and runs without this, but shows empty tabs:

- `Categories` → doc with `categoryType: "brand"`, `name: "<Brand>"`, `thumbnail`
  — the Categories tab shows this app's brand category first, then the shared
  `categoryType: "main"` categories
- `<Brand>` collection with wallpapers, each carrying a `series` value that
  matches `seriesPrefixRanges`
- `Devices/<Brand>` → device list for the filter chips
- `AppUpdate/<documentAppUpdate>` → `version`, `mandatoryUpdate`, `message`

## 8. Build and verify

```bash
./gradlew :oneplus16:assembleDebug
```

Then check: app name and icon, onboarding wording, Home loads the right
collection, Categories shows the brand category, series chips populate, favorites
persist, paywall price, share text names the right app.

## 9. Release

```bash
keytool -genkeypair -v -keystore ~/keystores/oneplus16.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Droidates, OU=Droidates, O=Droidates, L=Jaipur, ST=Rajasthan, C=IN"
```

Create `oneplus16/keystore.properties` (git-ignored) with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`, then:

```bash
./gradlew :oneplus16:bundleRelease
```

Upload `oneplus16/build/outputs/bundle/release/oneplus16-release.aab`.

Each app also needs its own Play Console listing, data-safety form and content
rating — the answers can be copied from a sibling app, since the code is shared.

---

## Changing shared behaviour

Anything that should apply to **all** apps goes in `:core`. Edit it once, rebuild,
done — no per-app copies, no `SHARED_CHANGES.md` porting.

If a change should apply to only one app, it belongs in that app's spec. If the
spec can't express it, add a new field to `AppSpec` (every app must then supply a
value) rather than branching on brand names inside shared code.
