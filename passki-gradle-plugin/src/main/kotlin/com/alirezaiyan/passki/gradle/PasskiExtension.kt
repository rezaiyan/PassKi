package com.alirezaiyan.passki.gradle

import org.gradle.api.Action
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
 *     apple { teamId = "ABCDE12345"; bundleId = "com.example.app" }
 * }
 * ```
 */
public abstract class PasskiExtension @Inject constructor(objects: ObjectFactory) {
    /** Relying Party ID, e.g. "example.com". Required by passkiVerify. */
    public abstract val rpId: Property<String>

    /** Android app identity; package and signing fingerprints are read from the Android build by default. */
    @get:Nested
    public val android: AndroidConfig = objects.newInstance(AndroidConfig::class.java)

    /** Apple app identity used to build `apple-app-site-association`. */
    @get:Nested
    public val apple: AppleConfig = objects.newInstance(AppleConfig::class.java)

    /** Configures [android]. */
    public fun android(action: Action<AndroidConfig>): Unit = action.execute(android)

    /** Configures [apple]. */
    public fun apple(action: Action<AppleConfig>): Unit = action.execute(apple)

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

        /** Optional .entitlements file; passkiVerify checks it lists webcredentials:<rpId>. */
        public abstract val entitlementsFile: RegularFileProperty
    }
}
