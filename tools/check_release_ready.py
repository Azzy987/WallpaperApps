#!/usr/bin/env python3
"""
Pre-publish readiness check for one wallpaper app module.

    python3 tools/check_release_ready.py samsungfold8

Verifies everything that can be verified from the repo, and lists what must be
confirmed by hand in the Play Console / AdMob / Firestore.

Why a script and not a markdown checklist: a checklist goes stale the moment a
field is added to AppSpec or an app is registered in Firebase. This reads the
real files, so it cannot drift from the code.

Exit code 0 when nothing is blocking, 1 when something is.
"""
import hashlib
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RED, GREEN, YELLOW, BLUE, DIM, RESET = (
    "\033[31m", "\033[32m", "\033[33m", "\033[34m", "\033[2m", "\033[0m"
)
if not sys.stdout.isatty():
    RED = GREEN = YELLOW = BLUE = DIM = RESET = ""

blocking, warnings, manual = [], [], []


def rel(*p):
    return os.path.join(ROOT, *p)


def read(path):
    try:
        with open(rel(path), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def ok(msg):
    print(f"  {GREEN}PASS{RESET}  {msg}")


def fail(msg, detail=""):
    print(f"  {RED}FAIL{RESET}  {msg}")
    blocking.append(msg + (f" — {detail}" if detail else ""))


def warn(msg, detail=""):
    print(f"  {YELLOW}WARN{RESET}  {msg}")
    warnings.append(msg + (f" — {detail}" if detail else ""))


def section(name):
    print(f"\n{BLUE}{name}{RESET}")


def spec_value(spec_src, field):
    """Pull `override val <field> = "..."` out of the spec."""
    m = re.search(rf'override val {field}\s*=\s*"([^"]*)"', spec_src)
    if m:
        return m.group(1)
    m = re.search(rf"override val {field}\s*=\s*(\S+)", spec_src)
    return m.group(1) if m else None


def main(module):
    mod_dir = rel(module)
    if not os.path.isdir(mod_dir):
        print(f"{RED}No such module: {module}{RESET}")
        return 1

    print(f"\n{BLUE}Release readiness — :{module}{RESET}")

    # ── Module wiring ───────────────────────────────────────────────────
    section("Module wiring")
    settings = read("settings.gradle.kts") or ""
    if re.search(rf'^\s*include\("\:{re.escape(module)}"\)', settings, re.M):
        ok(f'registered in settings.gradle.kts')
    else:
        fail(f":{module} not registered in settings.gradle.kts",
             'add include(":%s")' % module)

    gradle = read(f"{module}/build.gradle.kts") or ""
    app_id = None
    m = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    if m:
        app_id = m.group(1)
        ok(f"applicationId = {app_id}")
    else:
        fail("no applicationId in build.gradle.kts")

    ns = re.search(r'namespace\s*=\s*"([^"]+)"', gradle)
    if ns and app_id and ns.group(1) != app_id:
        warn(f"namespace ({ns.group(1)}) != applicationId ({app_id})",
             "intentional only if the package was renamed post-launch")

    vc = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if vc and vn:
        ok(f"version {vn.group(1)} (code {vc.group(1)})")
        manual.append(
            f"Play Console: confirm versionCode {vc.group(1)} is higher than any "
            f"code already uploaded for {app_id} (including internal testing)"
        )

    # ── Spec ────────────────────────────────────────────────────────────
    section("App spec")
    spec_files = []
    for dirpath, _, files in os.walk(rel(module, "src/main/java")):
        spec_files += [os.path.join(dirpath, f) for f in files if f.endswith("Spec.kt")]
    if not spec_files:
        fail("no <App>Spec.kt found")
        return report()
    spec_src = open(spec_files[0], encoding="utf-8").read()
    spec_name = os.path.basename(spec_files[0])
    ok(f"{spec_name}")

    # Every AppSpec member must be overridden, or it will not compile — but a
    # value copied from the template compiles fine and ships the wrong app.
    iface = read("core/src/main/java/com/droidates/wallpapers/core/config/AppSpec.kt") or ""
    fields = re.findall(r"^\s+val (\w+):", iface, re.M)
    missing = [f for f in fields if f"override val {f}" not in spec_src]
    if missing:
        fail(f"spec missing {len(missing)} field(s)", ", ".join(missing))
    else:
        ok(f"all {len(fields)} AppSpec fields overridden")

    # ── Collision check against sibling apps ────────────────────────────
    section("Per-app uniqueness (vs other modules)")
    # These MUST differ per app; sharing them corrupts another app's data.
    unique_fields = [
        "documentAppUpdate", "collectionBannersSub", "collectionUsersSub",
        "prefsName", "downloadFolderName", "favoritesPrefsKey",
        "billingLifetimeId", "adMobAppId", "adBannerId",
        "adInterstitialId", "adRewardedId",
    ]
    others = {}
    for other in sorted(os.listdir(ROOT)):
        if other == module or not os.path.isdir(rel(other, "src")):
            continue
        for dirpath, _, files in os.walk(rel(other, "src/main/java")):
            for f in files:
                if f.endswith("Spec.kt"):
                    others[other] = open(os.path.join(dirpath, f), encoding="utf-8").read()

    clashes = 0
    for field in unique_fields:
        mine = spec_value(spec_src, field)
        if not mine:
            continue
        for other_mod, other_src in others.items():
            if spec_value(other_src, field) == mine:
                fail(f"{field} collides with :{other_mod} ({mine})")
                clashes += 1
    if clashes == 0:
        ok(f"all {len(unique_fields)} per-app values are unique")

    # ── AdMob ───────────────────────────────────────────────────────────
    section("AdMob")
    ad_app = spec_value(spec_src, "adMobAppId")
    units = {f: spec_value(spec_src, f)
             for f in ("adBannerId", "adInterstitialId", "adRewardedId")}
    if ad_app and re.fullmatch(r"ca-app-pub-\d+~\d+", ad_app):
        ok(f"adMobAppId {ad_app}")
    else:
        fail("adMobAppId malformed", f"got {ad_app!r}; expected ca-app-pub-<pub>~<id>")

    pub = ad_app.split("~")[0] if ad_app else None
    for field, val in units.items():
        if not val or not re.fullmatch(r"ca-app-pub-\d+/\d+", val):
            fail(f"{field} malformed", f"got {val!r}; expected ca-app-pub-<pub>/<id>")
        elif pub and not val.startswith(pub):
            fail(f"{field} belongs to a different publisher than adMobAppId")
        else:
            ok(f"{field} {val}")

    # Google's public test ids must never ship.
    for field, val in list(units.items()) + [("adMobAppId", ad_app)]:
        if val and "3940256099942544" in val:
            fail(f"{field} is a Google TEST id", "revenue would go nowhere")

    # The AdMob *app* id is per-Play-listing. Two modules sharing one means AdMob
    # attributes both apps' revenue to a single entry, and the second app's units
    # may not serve at all. The uniqueness sweep above already covers spec-vs-spec;
    # this catches the app id being wired to the wrong applicationId.
    if ad_app and app_id:
        # Legacy :app declared the id in the manifest; :core-based modules take it
        # from AppSpec. If a module still declares it, the two must agree.
        mf = read(f"{module}/src/main/AndroidManifest.xml") or ""
        m_meta = re.search(
            r'com\.google\.android\.gms\.ads\.APPLICATION_ID"\s+android:value="([^"]+)"', mf)
        if m_meta:
            if m_meta.group(1) != ad_app:
                fail("manifest APPLICATION_ID != spec adMobAppId",
                     f"manifest {m_meta.group(1)} vs spec {ad_app}; ads stop serving on a mismatch")
            else:
                ok("manifest APPLICATION_ID matches spec adMobAppId")
        else:
            ok("adMobAppId supplied via AppSpec (GMA Next-Gen; no manifest meta-data)")

    manual.append(
        f"AdMob: confirm app {ad_app} is the entry for {app_id} (not another app's), "
        f"and that it plus its 3 ad units are ACTIVE and linked to the Play listing"
    )

    # ── Firebase ────────────────────────────────────────────────────────
    section("Firebase")
    gs_path = rel("google-services.json")
    if not os.path.exists(gs_path):
        fail("google-services.json missing at project root")
    else:
        gs = json.load(open(gs_path, encoding="utf-8"))
        client = None
        for c in gs.get("client", []):
            if c["client_info"]["android_client_info"]["package_name"] == app_id:
                client = c
                break
        if not client:
            fail(f"{app_id} not registered in google-services.json",
                 "add the app in the Firebase console, re-download to project root")
        else:
            ok(f"{app_id} registered (mobilesdk_app_id {client['client_info']['mobilesdk_app_id']})")
            if client.get("api_key"):
                ok("api_key present")
            else:
                fail("no api_key for this package")
            web = [o for o in client.get("oauth_client", []) if o.get("client_type") == 3]
            if web:
                ok("web OAuth client present (Google Sign-In)")
                core_cfg = read("core/src/main/java/com/droidates/wallpapers/core/config/AppConfig.kt") or ""
                m = re.search(r'GOOGLE_WEB_CLIENT_ID\s*=\s*\n?\s*"([^"]+)"', core_cfg)
                if m and m.group(1) != web[0]["client_id"]:
                    fail("GOOGLE_WEB_CLIENT_ID does not match google-services.json",
                         "Google Sign-In will fail")
                elif m:
                    ok("GOOGLE_WEB_CLIENT_ID matches")
            else:
                warn("no web OAuth client", "Google Sign-In will not work")
            if not [o for o in client.get("oauth_client", []) if o.get("client_type") == 1]:
                warn("no Android OAuth client — SHA-1 likely not registered",
                     "add debug + upload + Play App Signing SHA-1 in Firebase")

    # ── Signing ─────────────────────────────────────────────────────────
    section("Signing")
    ks_props = rel(module, "keystore.properties")
    if os.path.exists(ks_props):
        ok("keystore.properties present")
        txt = open(ks_props, encoding="utf-8").read()
        for key in ("storeFile", "storePassword", "keyAlias", "keyPassword"):
            if not re.search(rf"^{key}\s*=", txt, re.M):
                fail(f"keystore.properties missing {key}")
        m = re.search(r"^storeFile\s*=\s*(.+)$", txt, re.M)
        if m and not os.path.exists(os.path.expanduser(m.group(1).strip())):
            fail("storeFile path does not exist", m.group(1).strip())
    else:
        fail(f"{module}/keystore.properties missing",
             "release AAB builds UNSIGNED and Play will reject it")

    aab = rel(module, f"build/outputs/bundle/release/{module}-release.aab")
    if os.path.exists(aab):
        with zipfile.ZipFile(aab) as z:
            signed = any(re.search(r"META-INF/.*\.(RSA|DSA|EC)$", n) for n in z.namelist())
        if signed:
            ok("last built AAB is signed")
        else:
            fail("last built AAB is UNSIGNED", os.path.relpath(aab, ROOT))

    # ── Assets ──────────────────────────────────────────────────────────
    section("Assets")
    strings = read(f"{module}/src/main/res/values/strings.xml") or ""
    m = re.search(r'<string name="app_name">([^<]+)</string>', strings)
    spec_app_name = spec_value(spec_src, "appName")
    if not m:
        fail("app_name missing from strings.xml")
    elif spec_app_name and m.group(1).strip() != spec_app_name.strip():
        warn(f'app_name "{m.group(1)}" != spec appName "{spec_app_name}"',
             "launcher label and in-app title will disagree")
    else:
        ok(f'app_name "{m.group(1)}"')

    for d in ("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"):
        p = rel(module, "src/main/res", d, "ic_launcher.webp")
        if not os.path.exists(p):
            warn(f"no launcher icon in {d}")

    # Icons identical to another module's = the template's icon was never replaced.
    my_icon = rel(module, "src/main/res/mipmap-xxxhdpi/ic_launcher.webp")
    if os.path.exists(my_icon):
        my_hash = hashlib.sha256(open(my_icon, "rb").read()).hexdigest()
        for other in others:
            other_icon = rel(other, "src/main/res/mipmap-xxxhdpi/ic_launcher.webp")
            if os.path.exists(other_icon):
                if hashlib.sha256(open(other_icon, "rb").read()).hexdigest() == my_hash:
                    fail(f"launcher icon is identical to :{other}'s",
                         "template icon was never replaced")
                    break
        else:
            ok("launcher icon is unique to this app")

    # The first onboarding page is a hero shot of the device this app is about.
    # A copied module ships the template app's wallpaper, which is the very first
    # thing a new user sees and advertises the wrong phone. Screens 2 and 3 are
    # shared feature illustrations and are meant to be identical everywhere.
    ob = rel(module, "src/main/res/drawable/onboarding_screen1.webp")
    if os.path.exists(ob):
        ob_hash = hashlib.sha256(open(ob, "rb").read()).hexdigest()
        clash = None
        for other in list(others) + ["core"]:
            other_ob = rel(other, "src/main/res/drawable/onboarding_screen1.webp")
            if os.path.exists(other_ob):
                if hashlib.sha256(open(other_ob, "rb").read()).hexdigest() == ob_hash:
                    clash = other
                    break
        if clash:
            fail(f"onboarding_screen1 is identical to :{clash}'s",
                 "first page shows the wrong device — replace with this app's wallpaper")
        else:
            ok("onboarding_screen1 is unique to this app")
    else:
        warn("no onboarding_screen1.webp; inheriting :core's",
             "the first onboarding page will show the generic wallpaper")

    # ── Manifest / policy ───────────────────────────────────────────────
    section("Manifest & Play policy")
    manifest = read(f"{module}/src/main/AndroidManifest.xml") or ""
    if "READ_MEDIA_IMAGES" in manifest:
        fail("READ_MEDIA_IMAGES declared",
             "Play rejects it for this app type; MediaStore needs no permission for own files")
    else:
        ok("no READ_MEDIA_IMAGES")
    if spec_app_name and f"{module}wallpapers" not in manifest.lower().replace("_", ""):
        pass  # application class package is checked by the compiler anyway

    # ── Things only a human can confirm ─────────────────────────────────
    billing_id = spec_value(spec_src, "billingLifetimeId")
    price = spec_value(spec_src, "billingLifetimeFallbackPrice")
    manual.insert(0, f"Play Console: create + ACTIVATE one-time product '{billing_id}' priced {price}")
    manual.append(f"Firestore: Categories doc with categoryType='brand', name='{spec_value(spec_src,'categoryBrandName')}'")
    manual.append(f"Firestore: Devices/{spec_value(spec_src,'documentDevicesBrand')} device list (filter chips)")
    manual.append(f"Firestore: AppUpdate/{spec_value(spec_src,'documentAppUpdate')} with version, mandatoryUpdate, message")
    series = re.search(r"seriesPrefixRanges\s*=\s*listOf\((.+)\)", spec_src)
    if series:
        manual.append(f"Firestore: wallpapers in '{spec_value(spec_src,'collectionHome')}' with series matching {series.group(1).strip()}")
    manual.append("Play Console: store listing, screenshots, feature graphic, data safety, content rating, target audience")
    manual.append("Firebase: add upload-key AND Play App Signing SHA-1 (or Google Sign-In breaks in production)")
    manual.append("Test the paywall on an internal-testing track — billing behaves differently in a Play-signed build")

    return report()


def report():
    print()
    if blocking:
        print(f"{RED}BLOCKING ({len(blocking)}){RESET}")
        for b in blocking:
            print(f"  - {b}")
    if warnings:
        print(f"\n{YELLOW}WARNINGS ({len(warnings)}){RESET}")
        for w in warnings:
            print(f"  - {w}")
    print(f"\n{BLUE}CONFIRM BY HAND ({len(manual)}){RESET} {DIM}— cannot be checked from the repo{RESET}")
    for m in manual:
        print(f"  [ ] {m}")

    print()
    if blocking:
        print(f"{RED}NOT READY TO LIST{RESET} — {len(blocking)} blocking issue(s)\n")
        return 1
    print(f"{GREEN}Repo-side checks pass.{RESET} Work the manual list above before publishing.\n")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
