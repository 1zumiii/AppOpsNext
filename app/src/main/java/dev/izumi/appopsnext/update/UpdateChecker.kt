package dev.izumi.appopsnext.update

import dev.izumi.appopsnext.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asks GitHub for the latest release and reports whether it is newer.
 *
 * This is the only network request the app makes. It sends no identifying
 * information beyond what any HTTPS request carries, reads a single public
 * endpoint, and never downloads or installs anything: an available update is
 * reported so the user can open the release page themselves.
 */
class UpdateChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val endpoint: String = LATEST_RELEASE_ENDPOINT,
) {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetch() ?: return@runCatching UpdateState.Failed
            val json = JSONObject(body)
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                return@runCatching UpdateState.UpToDate
            }
            val tag = json.optString("tag_name").takeIf(String::isNotBlank)
                ?: return@runCatching UpdateState.Failed
            if (AppVersion.isNewer(tag, currentVersion)) {
                UpdateState.Available(
                    versionName = tag.removePrefix("v"),
                    releaseUrl = json
                        .optString("html_url")
                        .takeIf(String::isNotBlank)
                        ?: RELEASES_PAGE,
                )
            } else {
                UpdateState.UpToDate
            }
        }.getOrDefault(UpdateState.Failed)
    }

    private fun fetch(): String? {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream
                .bufferedReader()
                .use { reader -> reader.readText().take(MAX_BODY_CHARS) }
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
        private const val MAX_BODY_CHARS = 512 * 1024
    }
}
