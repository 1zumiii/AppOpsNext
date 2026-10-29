package dev.izumi.appopsnext.update

import dev.izumi.appopsnext.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asks GitHub for the latest release and reports whether it is newer.
 *
 * This is the only network request the app makes. It sends no identifying
 * information beyond what any HTTPS request carries, reads public GitHub pages
 * only, and never downloads or installs anything: an available update is
 * reported so the user can open the release page themselves.
 *
 * The API allows 60 requests an hour for each address, which a carrier or VPN
 * address shared by many people uses up quickly. When it is used up, the
 * redirect of the public "latest release" page names the same release.
 *
 * A beta build is offered the next beta as well as the next release, so it
 * reads the recent releases instead of the latest one, which skips prereleases.
 * The redirect fallback names releases only.
 */
class UpdateChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val includePrereleases: Boolean = AppVersion.isPrerelease(currentVersion),
) {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = fetch(if (includePrereleases) RECENT_RELEASES_ENDPOINT else LATEST_RELEASE_ENDPOINT)
            when {
                body != null && includePrereleases -> fromRecent(JSONArray(body))
                body != null -> fromApi(JSONObject(body))
                code == HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS -> fromLatestPage()
                else -> UpdateState.Failed
            }
        }.getOrDefault(UpdateState.Failed)
    }

    private fun fromApi(json: JSONObject): UpdateState {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return UpdateState.UpToDate
        }
        val tag = json.optString("tag_name").takeIf(String::isNotBlank)
            ?: return UpdateState.Failed
        return fromTag(tag, json.optString("html_url").takeIf(String::isNotBlank))
    }

    private fun fromRecent(json: JSONArray): UpdateState {
        val releases = (0 until json.length()).mapNotNull { index ->
            val release = json.optJSONObject(index) ?: return@mapNotNull null
            val tag = release.optString("tag_name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            PublishedRelease(
                tag = tag,
                url = release.optString("html_url").takeIf(String::isNotBlank),
                draft = release.optBoolean("draft"),
            )
        }
        val newest = newestRelease(releases) ?: return UpdateState.UpToDate
        return fromTag(newest.tag, newest.url)
    }

    /** The page skips drafts and prereleases itself, as the API endpoint does. */
    private fun fromLatestPage(): UpdateState {
        val location = latestPageRedirect() ?: return UpdateState.Failed
        val tag = tagOfReleaseUrl(location) ?: return UpdateState.Failed
        return fromTag(tag, location)
    }

    private fun fromTag(tag: String, releaseUrl: String?): UpdateState =
        if (AppVersion.isNewer(tag, currentVersion)) {
            UpdateState.Available(
                versionName = tag.removePrefix("v"),
                releaseUrl = releaseUrl ?: RELEASES_PAGE,
            )
        } else {
            UpdateState.UpToDate
        }

    /** The status code, and the body when the request succeeded. */
    private fun fetch(endpoint: String): Pair<Int?, String?> {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return code to null
            if (connection.contentLengthLong > MAX_BODY_BYTES) return code to null
            code to connection.inputStream.use { it.readBoundedResponse(MAX_BODY_BYTES) }
        } catch (_: Exception) {
            null to null
        } finally {
            connection.disconnect()
        }
    }

    /** Where the latest release page redirects, read without following it or loading the page. */
    private fun latestPageRedirect(): String? {
        val connection = URL(RELEASES_PAGE).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 300..399) return null
            connection.getHeaderField("Location")
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASES_PAGE =
            "https://github.com/1zumiii/AppOpsNext/releases/latest"
        private const val LATEST_RELEASE_ENDPOINT =
            "https://api.github.com/repos/1zumiii/AppOpsNext/releases/latest"
        /** Newest first. Ten reaches past any run of betas this project has published. */
        private const val RECENT_RELEASES_ENDPOINT =
            "https://api.github.com/repos/1zumiii/AppOpsNext/releases?per_page=10"
        private const val USER_AGENT = "AppOpsNext"
        private const val TIMEOUT_MILLIS = 10_000
        private const val MAX_BODY_BYTES = 512 * 1024
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val RELEASE_TAG_PATH = "/1zumiii/AppOpsNext/releases/tag/"

        /** The highest published version, beta or not. Drafts are skipped. */
        internal fun newestRelease(releases: List<PublishedRelease>): PublishedRelease? =
            releases
                .filterNot(PublishedRelease::draft)
                .maxWithOrNull { left, right -> AppVersion.compare(left.tag, right.tag) }

        /** The tag a release page of this repository names, or null for any other address. */
        internal fun tagOfReleaseUrl(url: String): String? {
            val path = runCatching { URL(url) }.getOrNull()
                ?.takeIf { it.protocol == "https" && it.host == "github.com" }
                ?.path
                ?: return null
            return path.takeIf { it.startsWith(RELEASE_TAG_PATH) }
                ?.removePrefix(RELEASE_TAG_PATH)
                ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
                ?.takeIf { it.isNotBlank() && '/' !in it }
        }
    }
}

internal data class PublishedRelease(
    val tag: String,
    val url: String?,
    val draft: Boolean,
)
