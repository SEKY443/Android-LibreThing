package io.github.seky443.librething.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A GitHub release newer than what's currently installed. [version] has no leading "v" (the
 * repo's tags are inconsistent about that prefix, so it's stripped before comparison and
 * before display, rather than carried through as-is). [changelog] is the release's raw
 * Markdown body, blank if GitHub returned none -- shown as-is rather than rendered, since a
 * plain-text approximation of Markdown reads fine for the short bullet-list changelogs this
 * project publishes. */
data class UpdateInfo(val version: String, val releaseUrl: String, val changelog: String)

/**
 * Checks SEKY443/Android-LibreThing's GitHub releases for a version newer than the one
 * installed -- see [io.github.seky443.librething.data.AppPreferences.autoCheckForUpdatesEnabled].
 * A plain unauthenticated call to GitHub's public REST API (60 requests/hour per IP; the caller
 * is expected to throttle actual invocations via
 * [io.github.seky443.librething.data.SettingsRepository.lastUpdateCheckAtMillis], so this is
 * nowhere near that ceiling in practice).
 */
object UpdateChecker {
    private const val RELEASES_API_URL = "https://api.github.com/repos/SEKY443/Android-LibreThing/releases/latest"

    /** Where "view the release" hands off to -- the releases list itself, not necessarily the
     * exact matched release's own [UpdateInfo.releaseUrl], since that's a reasonable fallback
     * if a response somehow carries no usable URL of its own. */
    const val RELEASES_PAGE_URL = "https://github.com/SEKY443/Android-LibreThing/releases"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ReleaseDto(
        val tag_name: String = "",
        val html_url: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    /** Null on any failure (offline, GitHub down, malformed response) or when [currentVersionName]
     * is already current -- callers treat both the same way (nothing to show), so there's no
     * separate error channel to plumb through just for a background nicety like this. */
    suspend fun checkForNewerRelease(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val dto = runCatching {
            val request = Request.Builder().url(RELEASES_API_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { json.decodeFromString<ReleaseDto>(it) }
            }
        }.getOrNull() ?: return@withContext null

        if (dto.draft || dto.prerelease || dto.tag_name.isBlank()) return@withContext null
        val remoteVersion = dto.tag_name.removePrefix("v").removePrefix("V")
        if (!isNewer(remoteVersion, currentVersionName)) return@withContext null

        UpdateInfo(
            version = remoteVersion,
            releaseUrl = dto.html_url.ifBlank { RELEASES_PAGE_URL },
            changelog = dto.body.trim(),
        )
    }

    /** Numeric per-segment comparison ("1.10" beats "1.9", unlike a plain string compare) --
     * missing trailing segments count as 0, so "1.3" and "1.3.0" compare equal rather than one
     * looking newer just for having an extra segment. Non-numeric segments (a stray "-beta"
     * suffix, say) fall out of the split silently; this is a best-effort compare for this
     * project's own plain "major.minor.patch" tags, not a general semver parser. */
    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
