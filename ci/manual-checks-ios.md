# iOS manual check (before a release)

The iOS passkey sheet and Face ID can't be tapped by CI, so this run is done by hand on the simulator. It takes about
10 minutes. Every result also appears in the app's **Lab** tab, under **Activity**, with ✓ or ✗.

## Before you start

1. Use Xcode 26 and an **iOS 26** iPhone simulator (signals need iOS 26).
2. In the Simulator menu, turn on **Features > Face ID > Enrolled**.
3. Pull `main`. Make sure `iosApp/Configuration/Local.xcconfig` exists and sets `TEAM_ID` (copy
   `Local.xcconfig.example` if it doesn't).
4. Run `./gradlew passkiSetup`. For the maintainer's team it answers "already permanently listed"; for another team it
   registers the app for 7 days.
5. Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on the simulator. Don't change the team in
   **Signing & Capabilities**: it comes from `Local.xcconfig`. If `git status` shows `project.pbxproj` changed
   afterwards, restore it with `git checkout -- iosApp/iosApp.xcodeproj/project.pbxproj`.
6. If the simulator's **Settings > Developer** shows **Associated Domains Development**, turn it on.

When the simulator asks for Face ID, choose **Features > Face ID > Matching Face** (⌥⌘M).

## Checks

| # | Do this | Expect |
|---|---|---|
| 1 | Launch the app | No red "Setup: … problem" banner at the top. If one appears, tap it and take a screenshot of the Setup tab |
| 2 | **Setup** tab → **Run setup check** | Every check passes; IOS-04 is skipped (the app can't read its own entitlements) |
| 3 | **Demo** tab: type a username (e.g. `ios-check`), tap **Create an account**, then **Continue** in the sheet, then Matching Face | "You're signed in", and a **Verified by the server** card with **User verified: Yes** |
| 4 | Under **Try next**, tap **Derive a key** twice | Activity shows two `PRF ✓` lines with the same first 8 bytes |
| 5 | **Sign out**, then **Sign in with a passkey**, choose the passkey, Matching Face | Signed in again; the card shows the sign count |
| 6 | **Sign out**, tap the **Username** field. If no bar appears above the keyboard, turn off **I/O > Keyboard > Connect Hardware Keyboard** (⌘K). Tap **Passwords** in that bar, Matching Face, pick the passkey | Signed in (autofill) |
| 7 | **Sign out**, **Sign in with a passkey**, then close the sheet without choosing | No error is shown, nothing new appears in Activity (a cancel is not an error), and the button can be tapped again |
| 8 | **Lab** tab → **Device only**. If a sheet appears, choose the passkey and use Matching Face | `Sign in (device only) ✓` |
| 9 | **Lab** → **Passkey or password**, choose the passkey, Matching Face | `Sign in or password (passkey) ✓` |
| 10 | **Lab** → **Rename user** | `Signal user details ✓`. The passkey's name in the next sheet changes to the username |
| 11 | **Lab** → **Forget last passkey**, then **Device only** | `Signal unknown ✓`, then `Sign in (device only) ✗ [NoCredentials]` |
| 12 | **Lab** → **Upgrade** | `Upgrade ✗ [NotSupported]`. This is expected: iOS only allows it right after a password AutoFill sign-in |

## Report back

Send a screenshot of **Lab > Activity** (scroll to include all lines), plus the number of any step that didn't match.
Also note the iOS and Xcode versions you used.
