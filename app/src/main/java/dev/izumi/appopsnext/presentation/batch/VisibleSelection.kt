package dev.izumi.appopsnext.presentation.batch

/** Search and mode filters change the action's range, not the existing selection. */
fun <T> toggleVisibleSelection(selected: Set<T>, visible: Set<T>): Set<T> =
    if (selected.containsAll(visible)) selected - visible else selected + visible
