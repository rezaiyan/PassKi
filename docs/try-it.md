# Try PassKi

Two ways to see PassKi work before you set up your own domain and server: run the sample app, or add PassKi to your
app against the hosted demo server. To ship with your own server, follow the [quickstart](quickstart.md).

Paths below use the same Gradle tasks as the quickstart. Every task prints what it changes and what it sends. Add
`--preview` to see that before anything happens.

## Run the sample

You need a device that can hold a passkey:

- **Android 9+:** a phone, or an emulator with a Google Play system image. Sign in to a Google account and set a
  screen lock.
- **iOS 16+:** an iPhone, or a simulator with **Features → Face ID → Enrolled**.

Then:

1. Clone this repository and open it in Android Studio.
2. **Android:** run `androidApp`. There is nothing to set up in the project: the demo server already trusts the
   sample's debug key.
3. **iOS:** copy `iosApp/Configuration/Local.xcconfig.example` to `iosApp/Configuration/Local.xcconfig`, and set
   `TEAM_ID` to your Apple Developer Team ID. Git ignores this file.
4. **iOS:** run `./gradlew passkiSetup`. It registers your build of the sample with the demo server. Then run `iosApp`
   from Xcode.

When the app starts, it checks its setup. If something is wrong, a banner names the problem, and tapping it opens the
fix. CI follows this path on every push to `main`: a fresh clone builds the Android sample, then runs
`passkiSetup --preview` and `passkiCheck` against the demo server.

### What's in the sample

`androidApp`, `iosApp` and `shared/` make up the Compose Multiplatform sample shown in the README. It runs every
PassKi call against the demo server, and the server checks every response. It has three tabs:

<table>
  <tr>
    <th>Demo</th>
    <th>Lab</th>
    <th>Setup</th>
  </tr>
  <tr>
    <td><img src="media/android-signed-in.png" width="240" alt="Demo tab after signing in: You're signed in, and a Verified by the server card with credential, sign count, user verified, synced, origin and AAGUID"></td>
    <td><img src="media/android-lab.png" width="240" alt="Lab tab: grouped flows for sign in (device only, passkey or password, autofill in a dialog), create (register with PRF, upgrade) and extensions and signals"></td>
    <td><img src="media/android-setup-check.png" width="240" alt="Setup tab: Ready for passkeys, 8 passed, with a checklist of catalog IDs"></td>
  </tr>
  <tr>
    <td valign="top"><sub>A real sign-in screen built with <code>PasskeyUsernameField</code> and <code>PasskeySignInButton</code>. After a sign-in it shows what the server checked: credential, sign count, user verification, backup state, origin and AAGUID.</sub></td>
    <td valign="top"><sub>Every other flow, one tap each: device-only sign-in, passkey or password, autofill in a dialog, register with PRF, upgrade, PRF key derivation and signals, with a result per row and an activity log.</sub></td>
    <td valign="top"><sub><b>Run setup check</b> runs <code>PasskeyDoctor</code> and the server checks SRV-01/02 as a checklist with catalog IDs, plus what this device supports.</sub></td>
  </tr>
</table>

- **Android: nothing to set up.** The debug build is signed with the committed `androidApp/sample-debug.keystore`,
  and the demo server already lists that key. See [`androidApp/README.md`](../androidApp/README.md).

  > [!CAUTION]
  > That key is public. Never use it for a release or for your own app.
- **iOS: one value to set,** your Apple Developer Team ID, in a file git ignores:

  ```sh
  cp iosApp/Configuration/Local.xcconfig.example iosApp/Configuration/Local.xcconfig  # then set TEAM_ID
  ./gradlew passkiSetup   # registers TEAM_ID.com.alirezaiyan.passki.PassKi.TEAM_ID with the demo server
  ```

  Your Team ID is added to the end of the sample's bundle ID, because a bundle ID can belong to only one team. The
  sample's plugin block is `passki { useDemoServer() }`, so `passkiSetup` reads both values from Xcode, shows what it
  sends, and registers the app with the demo server for 7 days. You don't edit any tracked file. Without
  `Local.xcconfig`, the app still builds for the simulator, but passkey calls fail with `Misconfigured` (IOS-02) because
  iOS can't link the app to the demo domain. Don't pick a team in Xcode's **Signing & Capabilities** tab: Xcode would
  write it into `project.pbxproj`, and CI rejects that.
- **Setup problems show at launch.** The app runs the setup check when it starts. If a check fails, a banner names it
  (for example `Setup: 1 problem (IOS-02)`), and tapping the banner opens the Setup tab with the fix.
- **Every call is traced.** The sample sets `PassKi.logLevel = PasskiLogLevel.Debug`: read it with
  `adb logcat -s PassKi` ([Logging](logging.md)).
- **Automatic test run.** Start the app with the autorun flag. It first runs the steps that need no taps
  (capabilities, setup check, server options, device-only sign-in with an unknown passkey, signals, upgrade), then the
  ones that open a system sheet. It logs each result as a `PASSKI_DEMO` line.
  - Android: `adb shell am start -n com.alirezaiyan.passki/.MainActivity --ez passkiAutorun true`, then read
    `adb logcat -s System.out`.
  - iOS: `xcrun simctl launch --console <device> <your bundle ID> -passkiAutorun`. With the example `Local.xcconfig`,
    the bundle ID is `com.alirezaiyan.passki.PassKi.<TEAM_ID>`.

## Try it in your app

The demo server at [passki.alirezaiyan.com](https://passki.alirezaiyan.com) creates real challenges and checks every
response. Use it to try PassKi, not in production: it deletes its users after 24 hours.

1. Add `passki` and the demo server client:

   ```kotlin
   commonMain.dependencies {
       implementation("com.alirezaiyan.passki:passki:0.1.0-alpha05")
       implementation("com.alirezaiyan.passki:passki-demo:0.1.0-alpha05")
   }
   ```

2. In your Android app module, apply the Gradle plugin and point it at the demo server:

   ```kotlin
   plugins {
       id("com.alirezaiyan.passki") version "0.1.0-alpha05"
   }

   passki {
       useDemoServer() // instead of rpId = "your.domain"
   }
   ```

   The plugin also sets up your iOS app. It finds the Xcode project in `iosApp/`, where the KMP template puts it. If
   yours is somewhere else, set `passki { apple { xcodeProject = ... } }`; see [all options](setup.md#all-options).

3. Run `./gradlew passkiSetup`. It does two things:
   - It adds the demo domain to your iOS app's associated domains.
   - It registers your app with the demo server for 7 days.

   It prints exactly what it sends: package names, signing key fingerprints and Apple app IDs. These are all public.
   Run it with `--preview` first to see the output without sending anything.

4. Create a passkey, then sign in with it. The demo server provides the options and checks the result:

   ```kotlin
   val passkeys = Passkeys()
   val demo = PasskiDemoServer()
   passkeys.register(demo.registrationOptions("alice"))
       .onSuccess { println(demo.verifyRegistration(it.json)) } // verified by server ✓ signCount 0 …
   passkeys.signIn(demo.authenticationOptions())
       .onSuccess { println(demo.verifyAuthentication(it.json)) }
   ```

5. Optional: run `./gradlew passkiCheck` to check the whole setup.

On Android, Google caches which apps a domain trusts. A newly registered app can take a few minutes to work. Check
AND-04 in `passkiCheck` passes once Google has picked it up.

Moving to your own server: replace `useDemoServer()` with `rpId = "your.domain"`, and `PasskiDemoServer` with calls to
your own server ([quickstart](quickstart.md)). The PassKi calls stay the same.
