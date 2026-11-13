package com.atlas.reader.data.backup.create.creators

import dev.zacsweers.metro.Inject
import com.atlas.reader.data.backup.models.BackupCategory
import com.atlas.reader.data.backup.models.backupCategoryMapper
import atlas.domain.category.interactor.GetCategories
import atlas.domain.category.model.Category

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
