package com.alirezaiyan.passki

import com.alirezaiyan.passki.internal.doctor.plural

/**
 * Diagnoses passkey setup for a relying party on this device: platform support, app
 * configuration and the well-known association files (`assetlinks.json` on Android,
 * `apple-app-site-association` on iOS).
 *
 * Use it in debug builds only: it performs network requests.
 *
 * ```kotlin
 * val report = PasskeyDoctor.run("example.com")
 * if (report.failures.isNotEmpty() || report.warnings.isNotEmpty()) println(report)
 * ```
 *
 * Supported on Android and iOS. On other platforms the report holds a single
 * [DoctorStatus.Skipped] check.
 */
public expect object PasskeyDoctor {
    /**
     * Runs every check for [rpId] (a bare domain such as `example.com`, no scheme or path)
     * and returns the results. Never throws for setup problems; they are reported as checks.
     */
    public suspend fun run(rpId: String): DoctorReport
}

/**
 * Result of [PasskeyDoctor.run]: one [DoctorCheck] per diagnosed item, in the order they ran.
 *
 * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
 */
public class DoctorReport @InternalPasskiApi constructor(
    /** The relying party ID the checks ran against. */
    public val rpId: String,
    /** Every check that ran, in order. */
    public val checks: List<DoctorCheck>,
) {
    /**
     * True when no check has status [DoctorStatus.Fail]. Warnings and skipped checks don't count, so a healthy
     * report can still contain [warnings] or [DoctorStatus.Skipped] checks that were not verified; look at
     * [failures] and [warnings] (or log the report) rather than relying on this flag alone.
     */
    public val isHealthy: Boolean get() = checks.none { it.status == DoctorStatus.Fail }

    /** Checks with status [DoctorStatus.Fail], in order. Passkeys won't work until each is fixed. */
    public val failures: List<DoctorCheck> get() = checks.filter { it.status == DoctorStatus.Fail }

    /** Checks with status [DoctorStatus.Warn], in order. Likely to cause trouble but not blocking. */
    public val warnings: List<DoctorCheck> get() = checks.filter { it.status == DoctorStatus.Warn }

    /**
     * Multi-line, human-readable summary; safe to log (contains no secrets). Example:
     *
     * ```
     * PassKi doctor for example.com: 1 failure, 0 warnings
     * [PASS] DEV-03 Platform support - Android 14 (API 34)
     * [FAIL] AND-03 assetlinks.json - fingerprint C9:64:... missing
     *        fix: add it to https://example.com/.well-known/assetlinks.json
     * ```
     *
     * Markers are plain ASCII (`[PASS]`, `[WARN]`, `[FAIL]`, `[SKIP]`) so the output survives any log sink.
     * The exact format is for humans and may change; don't parse it, read [checks] instead.
     */
    override fun toString(): String {
        return buildString {
            append("PassKi doctor for ").append(rpId).append(": ")
            append(plural(failures.size, "failure")).append(", ").append(plural(warnings.size, "warning"))
            checks.forEach { check ->
                append('\n').append(check.toString())
                check.fixHint?.let { hint ->
                    hint.lines().forEachIndexed { index, line ->
                        append('\n').append(if (index == 0) "$HINT_INDENT$HINT_LABEL" else "$HINT_INDENT$HINT_CONTINUATION")
                        append(line)
                    }
                }
            }
        }
    }

    private companion object {
        const val HINT_INDENT = "       "
        const val HINT_LABEL = "fix: "
        const val HINT_CONTINUATION = "     "
    }
}

/**
 * One diagnosed item of a [DoctorReport].
 *
 * Instances are created by PassKi; the constructor is internal API so fields can be added without breaking binaries.
 */
public class DoctorCheck @InternalPasskiApi constructor(
    /** Short name of what was checked, e.g. `assetlinks.json`. */
    public val name: String,
    /** Outcome of the check. */
    public val status: DoctorStatus,
    /** What was found, in one human-readable sentence. */
    public val message: String,
    /** How to fix a [DoctorStatus.Fail] or [DoctorStatus.Warn]; may span several lines. Null when there's nothing to do. */
    public val fixHint: String?,
    /**
     * Stable ID from PassKi's setup check catalog (e.g. `AND-01`, `IOS-02`), shared with the Gradle plugin's
     * `passkiCheck` and the setup docs (`docs/checks/<id>.md`). Null for checks without a catalog entry.
     */
    public val id: String?,
) {
    @InternalPasskiApi
    public constructor(name: String, status: DoctorStatus, message: String, fixHint: String?) :
        this(name, status, message, fixHint, id = null)

    /** One line: status marker, [id] (when set), [name] and [message]. [fixHint] is left out; see [DoctorReport.toString]. */
    override fun toString(): String = if (id == null) "${status.marker} $name - $message" else "${status.marker} $id $name - $message"
}

/**
 * Outcome of a [DoctorCheck].
 *
 * New statuses may be added in minor releases; keep an `else` branch in exhaustive `when`s.
 */
public enum class DoctorStatus {
    /** Configured correctly. */
    Pass,

    /** Works, but something is likely to cause trouble (e.g. a stale CDN copy). */
    Warn,

    /** Misconfigured; passkeys will not work until it is fixed. */
    Fail,

    /** Could not be checked on this platform or in this state. */
    Skipped,
}

private val DoctorStatus.marker: String
    get() = when (this) {
        DoctorStatus.Pass -> "[PASS]"
        DoctorStatus.Warn -> "[WARN]"
        DoctorStatus.Fail -> "[FAIL]"
        DoctorStatus.Skipped -> "[SKIP]"
    }
