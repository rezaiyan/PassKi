# Consumer fixtures

Apps that use PassKi the way the README tells people to: from `mavenLocal()`, with the oldest toolchains PassKi
supports. CI publishes PassKi to Maven Local and builds them, so a dependency or Kotlin upgrade that would lock out
existing apps fails the build instead of a user's first build.

| Fixture | Toolchain | What it proves |
|---|---|---|
| `kmp/` | Kotlin 2.2.21 (the oldest supported) | iOS and JVM apps can read PassKi's libraries; iOS tests run, which loads the Swift bridge |
| `android/` | AGP 9.1.1 with its built-in Kotlin, `compileSdk` 35 | Android apps don't need PassKi's Kotlin or `compileSdk`; the README's "try it in your app" code compiles; `passkiSetup --preview` works |

Run locally:

```sh
./gradlew publishToMavenLocal && ./gradlew -p passki-gradle-plugin publishToMavenLocal
./gradlew -p ci/consumers/kmp iosSimulatorArm64Test jvmTest
./gradlew -p ci/consumers/android assembleDebug passkiSetup --preview
```

Both read the PassKi version from the root `gradle.properties` (`VERSION_NAME`).
