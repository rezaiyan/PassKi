package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.Fingerprints
import com.alirezaiyan.passki.gradle.ResolvedKeystore
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Prints the SHA-256 signing fingerprints per build type, plus the matching Android WebAuthn
 * origins (`android:apk-key-hash:…`) for server-side origin allow-lists.
 */
@DisableCachingByDefault(because = "Only logs")
public abstract class PasskiFingerprintsTask : PasskiSigningTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    public fun print() {
        val rows = mutableListOf<Triple<String, String, String>>()
        val fingerprints = mutableListOf<String>()
        resolveKeystores().forEach { resolved ->
            when (resolved) {
                is ResolvedKeystore.Ok -> {
                    rows += Triple(resolved.spec.label, resolved.spec.path, resolved.sha256)
                    fingerprints += resolved.sha256
                }
                is ResolvedKeystore.Skipped -> rows += Triple(resolved.spec.label, resolved.spec.path, "(skipped)")
            }
        }
        extraFingerprints.get().forEach {
            rows += Triple("extra", "-", it)
            fingerprints += it
        }

        val header = Triple("variant/buildType", "keystore", "SHA-256")
        val w1 = (rows.map { it.first.length } + header.first.length).max()
        val w2 = (rows.map { it.second.length } + header.second.length).max()
        val text = buildString {
            appendLine("passki signing fingerprints${packageName.orNull?.let { " for $it" } ?: ""}:")
            (listOf(header) + rows).forEach { (a, b, c) -> appendLine("  ${a.padEnd(w1)} | ${b.padEnd(w2)} | $c") }
            if (fingerprints.isNotEmpty()) {
                appendLine("Android WebAuthn origins (server allow-list):")
                fingerprints.distinct().forEach { appendLine("  ${Fingerprints.androidOrigin(it)}") }
            }
        }
        logger.lifecycle(text.trimEnd())
    }
}
