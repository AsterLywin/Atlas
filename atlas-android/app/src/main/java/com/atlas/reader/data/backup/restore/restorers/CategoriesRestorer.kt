package com.atlas.reader.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import com.atlas.reader.data.backup.models.BackupCategory
import atlas.domain.category.interactor.GetCategories
import atlas.domain.category.model.NewCategory
import atlas.domain.category.repository.CategoryRepository
import atlas.domain.library.service.LibraryPreferences

@Inject
class CategoriesRestorer(
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getCategories.await()
        val dbCategoryNames = dbCategories.mapTo(HashSet()) { it.name }

        val newCategories = backupCategories
            .filter { it.name !in dbCategoryNames }
            .sortedBy { it.order }
        categoryRepository.insertAll(newCategories.map { NewCategory(name = it.name, flags = it.flags) })

        val flags = buildSet {
            dbCategories.mapTo(this) { it.flags }
            newCategories.mapTo(this) { it.flags }
        }
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)
    }
}
