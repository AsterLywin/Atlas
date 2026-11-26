package atlas.domain.category.interactor

import atlas.domain.library.model.LibraryDisplayMode
import atlas.domain.library.service.LibraryPreferences
import dev.zacsweers.metro.Inject

@Inject
class SetDisplayMode(
    private val preferences: LibraryPreferences,
) {

    fun await(display: LibraryDisplayMode) {
        preferences.displayMode.set(display)
    }
}
