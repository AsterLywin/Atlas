package com.atlas.ui

import com.atlas.ui.state.AtlasDestination
import com.atlas.ui.state.AtlasNavigator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The navigation back stack: push, replace, back, root. */
class NavigatorTest {

    @Test
    fun `starts at the library with no back`() {
        val navigator = AtlasNavigator()
        assertEquals(AtlasDestination.Library, navigator.current)
        assertFalse(navigator.canGoBack)
        assertFalse(navigator.navigateBack())
    }

    @Test
    fun `pushing detail and reader then going back unwinds in order`() {
        val navigator = AtlasNavigator()
        navigator.navigateTo(AtlasDestination.Browse)
        navigator.navigateTo(AtlasDestination.BookDetail("b1"))
        navigator.navigateTo(AtlasDestination.Reader("b1", "c1"))
        assertEquals(AtlasDestination.Reader("b1", "c1"), navigator.current)
        assertTrue(navigator.canGoBack)

        assertTrue(navigator.navigateBack())
        assertEquals(AtlasDestination.BookDetail("b1"), navigator.current)
        assertTrue(navigator.navigateBack())
        assertEquals(AtlasDestination.Browse, navigator.current)
        assertTrue(navigator.navigateBack())
        assertEquals(AtlasDestination.Library, navigator.current)
        assertFalse(navigator.navigateBack())
    }

    @Test
    fun `navigating to the current destination does not duplicate it`() {
        val navigator = AtlasNavigator()
        navigator.navigateTo(AtlasDestination.Library)
        navigator.navigateTo(AtlasDestination.Browse)
        navigator.navigateTo(AtlasDestination.Browse)
        assertEquals(
            listOf(AtlasDestination.Library, AtlasDestination.Browse),
            navigator.stack,
        )
    }

    @Test
    fun `replaceTop swaps the reader chapter without growing the stack`() {
        val navigator = AtlasNavigator()
        navigator.navigateTo(AtlasDestination.BookDetail("b1"))
        navigator.navigateTo(AtlasDestination.Reader("b1", "c1"))
        navigator.replaceTop(AtlasDestination.Reader("b1", "c2"))
        assertEquals(
            listOf(
                AtlasDestination.Library,
                AtlasDestination.BookDetail("b1"),
                AtlasDestination.Reader("b1", "c2"),
            ),
            navigator.stack,
        )
    }

    @Test
    fun `popToRoot drops every pushed screen`() {
        val navigator = AtlasNavigator()
        navigator.navigateTo(AtlasDestination.Browse)
        navigator.navigateTo(AtlasDestination.BookDetail("b1"))
        navigator.popToRoot()
        assertEquals(listOf(AtlasDestination.Library), navigator.stack)
    }

    @Test
    fun `only library browse and settings are root destinations`() {
        assertTrue(AtlasDestination.Library.isRoot)
        assertTrue(AtlasDestination.Browse.isRoot)
        assertTrue(AtlasDestination.Settings.isRoot)
        assertFalse(AtlasDestination.BookDetail("b").isRoot)
        assertFalse(AtlasDestination.Reader("b", "c").isRoot)
    }
}
