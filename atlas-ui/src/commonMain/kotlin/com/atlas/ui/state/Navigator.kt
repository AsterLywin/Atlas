package com.atlas.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One place in the app. Root destinations show the bottom bar;
 * detail/reader screens are pushed on top of them. */
sealed interface AtlasDestination {
    data object Library : AtlasDestination
    data object Browse : AtlasDestination
    data object Settings : AtlasDestination
    data class BookDetail(val bookId: String) : AtlasDestination
    data class Reader(val bookId: String, val chapterId: String) : AtlasDestination

    val isRoot: Boolean
        get() = this is Library || this is Browse || this is Settings
}

/**
 * The navigation back stack. Pure Kotlin, no Compose navigation
 * library: the same stack drives Android and desktop, and the JVM
 * tests push/pop it without a UI.
 */
class AtlasNavigator(initial: AtlasDestination = AtlasDestination.Library) {
    var stack by mutableStateOf(listOf(initial))
        private set

    val current: AtlasDestination get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun navigateTo(destination: AtlasDestination) {
        if (destination == current) return
        stack = stack + destination
    }

    /** Replace the top entry (chapter-to-chapter moves in the reader). */
    fun replaceTop(destination: AtlasDestination) {
        stack = stack.dropLast(1) + destination
    }

    /** Pop one entry. False when already at the root. */
    fun navigateBack(): Boolean {
        if (!canGoBack) return false
        stack = stack.dropLast(1)
        return true
    }

    /** Back to the root destination, dropping every pushed screen. */
    fun popToRoot() {
        stack = listOf(stack.first())
    }
}
