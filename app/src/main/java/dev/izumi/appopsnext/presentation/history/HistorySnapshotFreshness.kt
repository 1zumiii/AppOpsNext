package dev.izumi.appopsnext.presentation.history

internal object HistorySnapshotFreshness {
    fun isFresh(fetchedAtMillis: Long?, nowMillis: Long, intervalMillis: Long): Boolean =
        fetchedAtMillis != null && fetchedAtMillis <= nowMillis &&
            nowMillis - fetchedAtMillis < intervalMillis
}
