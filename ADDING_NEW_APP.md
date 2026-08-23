# How to Add a New App to WallpaperApps

---

## CASE 1 — Same Brand (e.g. OnePlus 7T, OnePlus 8, OnePlus 9 Pro)

Same brand means the Firestore wallpaper collection is the same (e.g. "OnePlus"),
only the app name, icons, AdMob IDs, and billing product change.

### Step 1 — Create the flavor folder

```
app/src/<flavorname>/
  java/com/droidates/wallpapers/config/AppConfig.kt
  res/
    values/strings.xml
    mipmap-mdpi/
    mipmap-hdpi/
    mipmap-xhdpi/
    mipmap-xxhdpi/
    mipmap-xxxhdpi/
    mipmap-anydpi-v26/
    drawable/              ← onboarding images (if different from another flavor)
    drawable-v24/          ← onboarding images
```

Replace `<flavorname>` with a short lowercase name e.g. `oneplus8`, `oneplus9pro`.

### Step 2 — Create AppConfig.kt

Copy `app/src/oneplus7t/java/com/droidates/wallpapers/config/AppConfig.kt`
and change ONLY these values:

```kotlin
const val APP_NAME = "OnePlus 8 Wallpapers"          // new app name
const val TOOLBAR_TITLE = "ONEPLUS 8 WALLPAPERS"

// Firestore — keep same as other OnePlus apps:
const val COLLECTION_HOME = "OnePlus"                 // same collection ✅
const val DOCUMENT_BANNERS_APP = "OnePlusWallpapers"  // same ✅
const val DOCUMENT_USERS_APP = "OnePlusWallpapers"    // same ✅

// Change these to be unique per app:
const val COLLECTION_BANNERS_SUB = "OnePlus8Banners"
const val COLLECTION_USERS_SUB = "OnePlus8AndroidUsers"
const val DOCUMENT_APP_UPDATE = "oneplus8wallpapers"
const val TABLE_FAVORITES = "oneplus8favorites"
const val PREFS_NAME = "oneplus8_wallpapers_prefs"
const val DOWNLOAD_FOLDER_NAME = "OnePlus8Wallpapers"

// AdMob — get new ad unit IDs from AdMob console for this app:
const val AD_INTERSTITIAL_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
const val AD_REWARDED_ID     = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
const val AD_NATIVE_ID       = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

// Billing — create product in Play Console:
const val BILLING_LIFETIME_ID = "oneplus8_lifetime"

// Links — shared across ALL apps, no need to create new pages:
const val URL_PRIVACY_POLICY = "https://www.droidates.com/p/privacy-policy-droidates-wallpaper-apps.html"
const val URL_TERMS_OF_USE   = "https://www.droidates.com/p/terms-of-use-droidates-wallpaper-apps.html"
const val URL_LICENSES       = "https://www.droidates.com/p/licenses-credits-droidates-wallpaper.html"
```

### Step 3 — Create strings.xml (app name only)

`app/src/oneplus8/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">OnePlus 8 Wallpapers</string>
</resources>
```

### Step 4 — Add launcher icons

Drop your icon files into:
```
app/src/oneplus8/res/mipmap-mdpi/ic_launcher.webp
app/src/oneplus8/res/mipmap-mdpi/ic_launcher_round.webp
app/src/oneplus8/res/mipmap-mdpi/ic_launcher_foreground.webp
(repeat for hdpi, xhdpi, xxhdpi, xxxhdpi)
app/src/oneplus8/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/oneplus8/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

### Step 5 — Add onboarding images (if different)

Drop into `app/src/oneplus8/res/drawable/` and `drawable-v24/`:
```
onboarding_screen1.webp
onboarding_screen2.webp
onboarding_screen3.webp
```
If onboarding is the same as another OnePlus app, just copy them from that flavor.

### Step 6 — Generate upload keystore

```bash
keytool -genkeypair -v \
  -keystore app/keystores/oneplus8.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Droidates, OU=Droidates, O=Droidates, L=Jaipur, ST=Rajasthan, C=IN"
```

Export certificate for Play Console:
```bash
keytool -export -rfc \
  -keystore app/keystores/oneplus8.jks \
  -alias upload \
  -file app/keystores/oneplus8_upload_cert.pem
```

### Step 7 — Add to build.gradle.kts

Open `app/build.gradle.kts` and add TWO blocks:

**In signingConfigs:**
```kotlin
create("oneplus8") {
    storeFile = file("keystores/oneplus8.jks")
    storePassword = System.getenv("ONEPLUS8_STORE_PASS") ?: ""
    keyAlias = "upload"
    keyPassword = System.getenv("ONEPLUS8_KEY_PASS") ?: ""
}
```

**In productFlavors:**
```kotlin
create("oneplus8") {
    dimension = "app"
    applicationId = "com.droidates.oneplus8wallpapers"  // ← from Play Console
    versionCode = 1
    versionName = "1.0"
    signingConfig = signingConfigs.getByName("oneplus8")
    manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
}
```

### Step 8 — Register app in Firebase

Since you use the same Firebase project:
1. Go to Firebase Console → Project Settings → Add App
2. Package name: `com.droidates.oneplus8wallpapers`
3. Download `google-services.json` — but since it's the same project,
   the existing `google-services.json` in `app/` already works.
   Just make sure the new package name is registered there.

### Step 9 — Sync and build

```bash
./gradlew bundleOneplus8Release
```

---

## CASE 2 — Different Brand (e.g. Samsung S25, iPhone 17, Xiaomi)

Different brand means different Firestore wallpaper collection.
Everything is the same as Case 1 EXCEPT you change more values in AppConfig.

### What changes compared to Case 1:

```kotlin
const val APP_NAME = "Samsung S25 Wallpapers"
const val TOOLBAR_TITLE = "SAMSUNG S25 WALLPAPERS"

// Firestore — different collection for a different brand:
const val CATEGORY_BRAND_NAME = "Samsung"
const val COLLECTION_HOME = "Samsung"              // ← different

// Banners and Users — different document namespace:
const val DOCUMENT_BANNERS_APP = "SamsungWallpapers"    // ← different
const val DOCUMENT_USERS_APP = "SamsungWallpapers"      // ← different
const val COLLECTION_BANNERS_SUB = "SamsungS25Banners"
const val COLLECTION_USERS_SUB = "SamsungS25AndroidUsers"

// Everything else same as Case 1 (unique per app)
const val DOCUMENT_APP_UPDATE = "samsungs25wallpapers"
const val TABLE_FAVORITES = "samsungs25favorites"
const val PREFS_NAME = "samsungs25_wallpapers_prefs"
const val BILLING_LIFETIME_ID = "samsungs25_lifetime"
```

All other steps (icons, onboarding, keystore, build.gradle.kts) are identical to Case 1.

---

## CASE 3 — Updating an Existing App (Lost Keystore)

If you lost the upload key for an app already on Play Console:

1. Generate a new keystore (Step 6 above)
2. Export the certificate (Step 6 above)
3. Go to Play Console → App → Setup → App integrity
4. Click "Request key upgrade" and upload the `.pem` certificate file
5. Google resets your key within a few days
6. Once reset, add the keystore to `app/keystores/` and wire it up in
   `build.gradle.kts` signingConfigs as shown in Step 7

---

## Quick Reference — What Changes Per App

| What                         | File                              | Same brand? | Diff brand? |
|------------------------------|-----------------------------------|-------------|-------------|
| App name                     | AppConfig.kt + strings.xml        | Change      | Change      |
| Application ID               | build.gradle.kts productFlavors   | Change      | Change      |
| AdMob App ID                 | build.gradle.kts manifestPlaceholders | Change  | Change      |
| AdMob ad unit IDs            | AppConfig.kt                      | Change      | Change      |
| Billing product ID           | AppConfig.kt                      | Change      | Change      |
| Privacy/Terms/Licenses URLs  | AppConfig.kt                      | Change      | Change      |
| Launcher icons               | flavor/res/mipmap-*/              | Change      | Change      |
| Onboarding images            | flavor/res/drawable/              | Optional    | Usually yes |
| Upload keystore              | keystores/ + signingConfigs       | New file    | New file    |
| Firestore home collection    | AppConfig.kt COLLECTION_HOME      | Same ✅     | Change      |
| Firestore banners/users doc  | AppConfig.kt DOCUMENT_*_APP       | Same ✅     | Change      |
| Shared code (screens/logic)  | src/main/                         | Never touch | Never touch |
