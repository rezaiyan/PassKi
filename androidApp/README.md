# PassKi Android sample

`sample-debug.keystore` is committed on purpose: every checkout signs the debug build with the same key, so the
demo relying party at https://passki.alirezaiyan.com can list one fingerprint in its `assetlinks.json` and
server origins.

| | |
|---|---|
| Store | PKCS12, `androidApp/sample-debug.keystore` |
| Alias / passwords | `passki-sample` / `passki-sample` |
| SHA-256 | `59:E5:92:32:4E:6C:E6:FA:5F:E2:F0:8A:39:18:13:2D:A5:1D:DE:5D:F9:BD:11:F0:5C:E8:23:57:88:E2:06:C0` |
| Origin | `android:apk-key-hash:WeWSMk5s5vpf4vCKORgTLaUd3l35vRHwXOgjV4jiBsA` |

**Never use this key for a release or for your own app.** Its password is public, so anyone can sign an APK
with it. Your app needs its own keystore; `./gradlew passkiFingerprints` prints its fingerprint.

Unattended smoke run: `adb shell am start -n com.alirezaiyan.passki/.MainActivity --ez passkiAutorun true`
and read `adb logcat -s System.out` (`PASSKI_DEMO` lines).
