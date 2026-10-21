package com.alirezaiyan.passki.gradle.tasks

import com.alirezaiyan.passki.gradle.PasskiConfigException
import com.alirezaiyan.passki.gradle.WellKnownVerifier
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Fetched
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Problem
import com.alirezaiyan.passki.gradle.WellKnownVerifier.Severity
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches `https://<rpId>/.well-known/assetlinks.json` (and `apple-app-site-association` when Apple
 * is configured), checks them and the optional entitlements file against the build, and fails on
 * any error. Network-dependent: never wired into `check`.
 */
@DisableCachingByDefault(because = "Checks remote state")
public abstract class PasskiVerifyTask : PasskiSigningTask() {
    @get:Input
    @get:Optional
    public abstract val rpId: Property<String>

    /** `<TeamID>.<bundleId>` entries; empty skips the Apple checks. */
    @get:Input
    public abstract val appleAppIds: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val entitlementsFile: RegularFileProperty

    /** Overrides `https://<rpId>` (Gradle property `passki.verify.baseUrl`); intended for tests. */
    @get:Input
    @get:Optional
    public abstract val baseUrl: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    public fun verify() {
        val rp = rpId.orNull ?: throw PasskiConfigException("passkiVerify needs passki { rpId = \"example.com\" }")
        val expected = WellKnownVerifier.Expected(
            rpId = rp,
            fingerprintsByPackage = fingerprintsByPackage(resolveVariants()),
            appleAppIds = appleAppIds.get(),
        )
        val base = (baseUrl.orNull ?: "https://$rp").trimEnd('/')
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build()

        val problems = mutableListOf<Problem>()
        fun fetch(file: String): Fetched? {
            val url = "$base/.well-known/$file"
            return try {
                val request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                Fetched(
                    status = response.statusCode(),
                    contentType = response.headers().firstValue("Content-Type").orElse(null),
                    body = response.body(),
                    redirected = response.statusCode() in 300..399,
                )
            } catch (e: Exception) {
                problems += Problem("Could not fetch $url: ${e.message ?: e.javaClass.simpleName}", "Check that $url is reachable")
                null
            }
        }

        val assetLinks = fetch(PasskiWellKnownTask.ASSET_LINKS)
        val aasa = if (expected.appleAppIds.isEmpty()) null else fetch(PasskiWellKnownTask.AASA)
        val entitlements = entitlementsFile.orNull?.asFile?.readText()
        problems += WellKnownVerifier.verify(expected, assetLinks, aasa, entitlements)
        if (expected.appleAppIds.isEmpty()) {
            logger.lifecycle("passki: apple { teamId; bundleId } not configured — skipping apple-app-site-association checks")
        }

        val (errors, warnings) = problems.partition { it.severity == Severity.ERROR }
        warnings.forEach { logger.warn("passki: warning: ${it.message}\n  fix: ${it.fix}") }
        if (errors.isNotEmpty()) {
            throw GradleException(
                errors.joinToString("\n", prefix = "passkiVerify found ${errors.size} problem(s):\n") { "- ${it.message}\n  fix: ${it.fix}" },
            )
        }
        logger.lifecycle("passki: well-known files OK for $rp")
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
