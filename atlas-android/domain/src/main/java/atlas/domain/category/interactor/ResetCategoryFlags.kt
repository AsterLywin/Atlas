package atlas.domain.category.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.category.repository.CategoryRepository
import atlas.domain.library.model.plus
import atlas.domain.library.service.LibraryPreferences

@Inject
class ResetCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.sortingMode.get()
        categoryRepository.updateAllFlags(sort.type + sort.direction)
    }
}
