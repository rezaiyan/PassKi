package com.alirezaiyan.passki.gradle

import java.io.File
import java.io.Serializable

/**
 * A signing keystore captured from the Android DSL at configuration time.
 *
 * Plain serializable value so tasks stay configuration-cache compatible. The password is carried
 * for reading the certificate but is never a tracked task input (see [identity]).
 */
public data class KeystoreSpec(
    /** Where this keystore is used, e.g. `release` or `free/debug`. */
    val label: String,
    val path: String,
    val password: String?,
    val alias: String?,
    val type: String?,
) : Serializable {
    /** Tracked identity: everything but the password and the (separately tracked) file. */
    val identity: String get() = "$label|$alias|${type.orEmpty()}"

    override fun toString(): String = "KeystoreSpec(label=$label, path=$path, alias=$alias, type=$type)"

    internal val file: File get() = File(path)

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** A keystore whose certificate fingerprint was resolved, or why it was skipped. */
internal sealed class ResolvedKeystore {
    abstract val spec: KeystoreSpec

    data class Ok(override val spec: KeystoreSpec, val sha256: String) : ResolvedKeystore()

    data class Skipped(override val spec: KeystoreSpec, val reason: String) : ResolvedKeystore()

    companion object {
        /** Missing files and incomplete specs are skipped; unreadable keystores throw [PasskiConfigException]. */
        fun of(spec: KeystoreSpec): ResolvedKeystore = when {
            !spec.file.isFile -> Skipped(spec, "keystore not found: ${spec.path}")
            spec.password == null -> Skipped(spec, "no storePassword configured for ${spec.path}")
            spec.alias == null -> Skipped(spec, "no keyAlias configured for ${spec.path}")
            else -> Ok(spec, Fingerprints.sha256(spec.file, spec.password, spec.alias, spec.type))
        }
    }
}
