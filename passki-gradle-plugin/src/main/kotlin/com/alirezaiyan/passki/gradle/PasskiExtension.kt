package com.alirezaiyan.passki.gradle

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import javax.inject.Inject

/**
 * The `passki { }` extension: describes the relying party and the apps that may use its passkeys.
 *
 * ```
 * passki {
 *     rpId = "example.com"
 *     android { extraFingerprints.add("AB:CD:...") }
 *     apple {
 *         teamId = "ABCDE12345"            // optional: read from xcodebuild when unset
 *         bundleId = "com.example.app"     // optional: read from xcodebuild when unset
 *         xcodeProject = file("../iosApp/iosApp.xcodeproj")              // optional: discovered
 *         xcconfig = file("../iosApp/Configuration/Config.xcconfig")    // optional: discovered
 *     }
 *     server { configUrl = "https://example.com/passki/server-config" } // optional, for passkiCheck
 * }
 * ```
 *
 * To try passkeys before you have a domain or a server, call [useDemoServer] instead of setting [rpId].
 */
public abstract class PasskiExtension @Inject constructor(objects: ObjectFactory) {
    /** Relying Party ID: the bare domain that serves your `.well-known` files, e.g. "example.com". */
    public abstract val rpId: Property<String>

    /** Set by [useDemoServer]: the demo relying party's base URL. */
    internal val demoServerUrl: Property<String> = objects.property(String::class.java)

    /**
     * Uses the hosted PassKi demo relying party (https://passki.alirezaiyan.com) instead of your own domain and
     * server, for evaluation only. Sets [rpId] to `passki.alirezaiyan.com` and [ServerConfig.configUrl] to its
     * server config. `passkiSetup` then registers this app with the demo server for 7 days (it prints exactly what
     * it sends: package names, signing SHA-256 fingerprints and `TEAM.bundle` IDs, all public data) instead of
     * asking you to upload files. Pair it with `PasskiDemoServer` from `passki-demo` in the app.
     *
     * Going to production: replace this call with `rpId = "your.domain"`.
     */
    public fun useDemoServer() {
        rpId.set(DEMO_RP_ID)
        demoServerUrl.set(DEMO_URL)
        server.configUrl.set("$DEMO_URL/api/demo/server-config")
        apple.developerMode.set(true)
    }

    /** Android app identity; package and signing fingerprints are read from the Android build by default. */
    @get:Nested
    public val android: AndroidConfig = objects.newInstance(AndroidConfig::class.java)

    /** Apple app identity used to build `apple-app-site-association`. */
    @get:Nested
    public val apple: AppleConfig = objects.newInstance(AppleConfig::class.java)

    /** Your WebAuthn server, checked by passkiCheck (SRV-01..03). */
    @get:Nested
    public val server: ServerConfig = objects.newInstance(ServerConfig::class.java)

    /** Configures [android]. */
    public fun android(action: Action<AndroidConfig>): Unit = action.execute(android)

    /** Configures [apple]. */
    public fun apple(action: Action<AppleConfig>): Unit = action.execute(apple)

    /** Configures [server]. */
    public fun server(action: Action<ServerConfig>): Unit = action.execute(server)

    internal companion object {
        const val DEMO_RP_ID: String = "passki.alirezaiyan.com"
        const val DEMO_URL: String = "https://$DEMO_RP_ID"
    }

    /** Android settings for `assetlinks.json`. */
    public abstract class AndroidConfig {
        /** Overrides the base applicationId read from the Android build; `applicationIdSuffix`es still apply. */
        public abstract val packageName: Property<String>

        /**
         * Fingerprints not derivable from local keystores (e.g. Play App Signing). Colon-hex SHA-256.
         * Added to the packages of non-debuggable variants (all packages if every variant is debuggable).
         */
        public abstract val extraFingerprints: ListProperty<String>
    }

    /** Apple settings for `apple-app-site-association`. */
    public abstract class AppleConfig {
        /** Apple Developer Team ID, e.g. "ABCDE12345". */
        public abstract val teamId: Property<String>

        /** App bundle identifier, e.g. "com.example.app". */
        public abstract val bundleId: Property<String>

        /**
         * Optional .entitlements file; passkiVerify/passkiCheck check it lists webcredentials:<rpId> and passkiSetup
         * writes it. Defaults to the file Xcode's `CODE_SIGN_ENTITLEMENTS` names, else `<project>/<name>/<name>.entitlements`.
         */
        public abstract val entitlementsFile: RegularFileProperty

        /** The `.xcodeproj`; discovered under the root project (e.g. `iosApp/iosApp.xcodeproj`) when unset. */
        public abstract val xcodeProject: DirectoryProperty

        /** Xcode scheme for `xcodebuild -showBuildSettings`; defaults to the project's name. */
        public abstract val scheme: Property<String>

        /**
         * Base `.xcconfig` that passkiSetup writes `CODE_SIGN_ENTITLEMENTS` into (never project.pbxproj);
         * defaults to `Configuration/Config.xcconfig` next to the Xcode project (the KMP template layout).
         */
        public abstract val xcconfig: RegularFileProperty

        /** Also add `webcredentials:<rpId>?mode=developer` (bypasses Apple's CDN on development builds). Default true. */
        public abstract val developerMode: Property<Boolean>
    }

    /** WebAuthn server settings. */
    public abstract class ServerConfig {
        /**
         * URL answering GET with `{"rpId": "...", "origins": [...]}` (see build/passki/server-config.json),
         * or with WebAuthn registration options. Optional.
         */
        public abstract val configUrl: Property<String>
    }
}
