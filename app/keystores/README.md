# Keystores

One .jks file per app. Never commit these to git.

| File             | App                    | Alias  | Env Var (store pass)    | Env Var (key pass)     |
|------------------|------------------------|--------|-------------------------|------------------------|
| oneplus7.jks     | OnePlus 7 Wallpapers   | upload | ONEPLUS7_STORE_PASS     | ONEPLUS7_KEY_PASS      |
| oneplus7t.jks    | OnePlus 7T Wallpapers  | upload | ONEPLUS7T_STORE_PASS    | ONEPLUS7T_KEY_PASS     |

## How to add a new keystore
1. Generate: keytool -genkeypair -keystore app/keystores/APPNAME.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
2. Export cert: keytool -export -rfc -keystore app/keystores/APPNAME.jks -alias upload -file app/keystores/APPNAME_upload_cert.pem
3. Add a signingConfig block in build.gradle.kts
4. Add the flavor and point signingConfig to it
5. Upload the .pem to Play Console if resetting key

## Passwords
Store passwords in your password manager — NOT in build.gradle.kts.
Set them as environment variables before building release.
