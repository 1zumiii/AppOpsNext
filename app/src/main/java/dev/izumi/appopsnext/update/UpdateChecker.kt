package dev.izumi.appopsnext.update

import dev.izumi.appopsnext.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 */
class UpdateChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val endpoint: String = LATEST_RELEASE_ENDPOINT,
) {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = fetch()
            when {
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
    private fun fetch(): Pair<Int?, String?> {
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
        private const val USER_AGENT = "AppOpsNext"
        private const val TIMEOUT_MILLIS = 10_000
        private const val MAX_BODY_BYTES = 512 * 1024
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val RELEASE_TAG_PATH = "/1zumiii/AppOpsNext/releases/tag/"

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
