package atlas.domain.category.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.library.model.LibraryDisplayMode
import atlas.domain.library.service.LibraryPreferences

@Inject
class SetDisplayMode(
    private val preferences: LibraryPreferences,
) {

    fun await(display: LibraryDisplayMode) {
        preferences.displayMode.set(display)
    }
}
