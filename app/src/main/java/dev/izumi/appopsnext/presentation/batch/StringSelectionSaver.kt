package dev.izumi.appopsnext.presentation.batch

import androidx.compose.runtime.saveable.listSaver

internal val StringSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)
