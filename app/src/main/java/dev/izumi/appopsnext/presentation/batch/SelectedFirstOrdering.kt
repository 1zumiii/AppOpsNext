package dev.izumi.appopsnext.presentation.batch

/** Use the selection captured when the list opens, rather than live checkbox state. */
internal fun <T> selectedFirstOrdering(
    items: List<T>,
    initialSelection: Set<String>,
    key: (T) -> String,
): List<T> {
    val byKey = items.associateBy(key)
    return initialSelection.mapNotNull(byKey::get) + items.filter { key(it) !in initialSelection }
}
