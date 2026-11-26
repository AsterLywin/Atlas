package com.atlas.reader.data.backup.create.creators

import atlas.domain.category.interactor.GetCategories
import atlas.domain.category.model.Category
import com.atlas.reader.data.backup.models.BackupCategory
import com.atlas.reader.data.backup.models.backupCategoryMapper
import dev.zacsweers.metro.Inject

@Inject
class CategoriesBackupCreator(
    private val getCategories: GetCategories,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getCategories.await()
            .filterNot(Category::isSystemCategory)
            .map(backupCategoryMapper)
    }
}
