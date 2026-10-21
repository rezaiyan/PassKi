package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.Fingerprints
import com.alirezaiyan.passki.gradle.ResolvedKeystore
import com.alirezaiyan.passki.gradle.SigningGroups
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Prints the SHA-256 signing fingerprints per variant and the package each variant produces, plus
 * the matching Android WebAuthn origins (`android:apk-key-hash:…`) for server-side origin allow-lists.
 */
@DisableCachingByDefault(because = "Only logs")
public abstract class PasskiFingerprintsTask : PasskiSigningTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    public fun print() {
        val resolved = resolveVariants()
        val rows = resolved.map { variant ->
            val sha256 = when (val keystore = variant.keystore) {
                is ResolvedKeystore.Ok -> keystore.sha256
                is ResolvedKeystore.Skipped -> "(skipped)"
                null -> "(no signing config)"
            }
            listOf(variant.packageName, variant.spec.label, variant.spec.keystore?.path ?: "-", sha256)
        }
        val grouped = SigningGroups.group(requirePackageName(), resolved, extraFingerprints.get())
        val extraRows = extraFingerprints.get().flatMap { extra ->
            grouped.filterValues { extra in it }.keys.map { pkg -> listOf(pkg, "extra", "-", extra) }
        }
        val header = listOf("package", "variant", "keystore", "SHA-256")
        val all = listOf(header) + (rows + extraRows).sortedWith(compareBy({ it[0] }, { it[1] == "extra" }, { it[1] }))
        val widths = (0 until 3).map { column -> all.maxOf { it[column].length } }
        val fingerprints = grouped.values.flatten().distinct().sorted()
        val text = buildString {
            appendLine("passki signing fingerprints:")
            all.forEach { row -> appendLine("  " + (0 until 3).joinToString(" | ") { row[it].padEnd(widths[it]) } + " | " + row[3]) }
            if (fingerprints.isNotEmpty()) {
                appendLine("Android WebAuthn origins (server allow-list):")
                fingerprints.forEach { appendLine("  ${Fingerprints.androidOrigin(it)}") }
            }
        }
        logger.lifecycle(text.trimEnd())
    }
}
