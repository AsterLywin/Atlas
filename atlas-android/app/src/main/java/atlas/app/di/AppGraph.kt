package atlas.app.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import com.atlas.domain.base.BasePreferences
import com.atlas.domain.extension.interactor.TrustExtension
import com.atlas.domain.source.service.SourcePreferences
import com.atlas.domain.track.interactor.AddTracks
import com.atlas.domain.track.service.DelayedTrackingUpdateWorker
import com.atlas.domain.track.service.TrackPreferences
import com.atlas.domain.ui.UiPreferences
import com.atlas.reader.App
import com.atlas.reader.core.security.PrivacyPreferences
import com.atlas.reader.core.security.SecurityPreferences
import com.atlas.reader.data.backup.create.BackupCreateWorker
import com.atlas.reader.data.backup.restore.BackupRestoreWorker
import com.atlas.reader.data.cache.ChapterCache
import com.atlas.reader.data.cache.CoverCache
import com.atlas.reader.data.download.DownloadCache
import com.atlas.reader.data.download.DownloadManager
import com.atlas.reader.data.download.DownloadWorker
import com.atlas.reader.data.library.LibraryUpdateWorker
import com.atlas.reader.data.library.MetadataUpdateWorker
import com.atlas.reader.data.notification.NotificationReceiver
import com.atlas.reader.data.track.TrackerManager
import com.atlas.reader.data.updater.AppUpdateChecker
import com.atlas.reader.extension.ExtensionManager
import com.atlas.reader.extension.util.ExtensionInstallActivity
import com.atlas.reader.network.JavaScriptEngine
import com.atlas.reader.network.NetworkHelper
import com.atlas.reader.network.NetworkPreferences
import com.atlas.reader.ui.base.delegate.SecureActivityDelegateImpl
import com.atlas.reader.ui.main.MainActivity
import com.atlas.reader.ui.reader.ReaderActivity
import com.atlas.reader.ui.reader.setting.ReaderPreferences
import com.atlas.reader.ui.setting.track.BaseOAuthLoginActivity
import com.atlas.reader.ui.webview.WebViewActivity
import com.atlas.reader.util.CrashLogUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import atlas.core.metro.IsDebugBuild
import atlas.domain.extension.interactor.GetExtensionStoreCountAsFlow
import atlas.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import atlas.domain.backup.service.BackupPreferences
import atlas.domain.category.interactor.GetCategories
import atlas.domain.category.interactor.ResetCategoryFlags
import atlas.domain.download.service.DownloadPreferences
import atlas.domain.library.service.LibraryPreferences
import atlas.domain.manga.interactor.GetFavorites
import atlas.domain.manga.interactor.ResetViewerFlags
import atlas.domain.source.service.SourceManager
import atlas.domain.storage.service.StoragePreferences
import atlas.domain.track.interactor.UpsertTrack

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(readerActivity: ReaderActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(baseOAuthLoginActivity: BaseOAuthLoginActivity)
    fun inject(libraryUpdateWorker: LibraryUpdateWorker)
    fun inject(metadataUpdateWorker: MetadataUpdateWorker)
    fun inject(backupRestoreWorker: BackupRestoreWorker)
    fun inject(backupCreateWorker: BackupCreateWorker)
    fun inject(delayedTrackingUpdateWorker: DelayedTrackingUpdateWorker)
    fun inject(downloadWorker: DownloadWorker)
    fun inject(notificationReceiver: NotificationReceiver)
    fun inject(notificationReceiver: SecureActivityDelegateImpl)
    fun inject(extensionInstallActivity: ExtensionInstallActivity)

    val context: Context

    val viewModelFactory: MetroViewModelFactory

    val basePreferences: BasePreferences
    val uiPreferences: UiPreferences
    val readerPreferences: ReaderPreferences
    val networkPreferences: NetworkPreferences
    val libraryPreferences: LibraryPreferences
    val sourcePreferences: SourcePreferences
    val trackPreferences: TrackPreferences
    val backupPreferences: BackupPreferences
    val storagePreferences: StoragePreferences
    val privacyPreferences: PrivacyPreferences
    val securityPreferences: SecurityPreferences
    val downloadPreferences: DownloadPreferences

    val crashLogUtil: CrashLogUtil

    val downloadManager: DownloadManager

    val updateChecker: AppUpdateChecker

    val trustExtension: TrustExtension
    val extensionStoreRepository: ExtensionStoreRepository

    val sourceManager: SourceManager
    val trackerManager: TrackerManager
    val extensionManager: ExtensionManager
    val chapterCache: ChapterCache
    val coverCache: CoverCache
    val downloadCache: DownloadCache

    val json: Json
    val protoBuf: ProtoBuf
    val xml: XML
    val networkHelper: NetworkHelper
    val javaScriptEngine: JavaScriptEngine

    val getFavorites: GetFavorites
    val getCategories: GetCategories
    val resetViewerFlags: ResetViewerFlags
    val resetCategoryFlags: ResetCategoryFlags
    val addTracks: AddTracks
    val upsertTrack: UpsertTrack

    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context, @Provides @IsDebugBuild isDebugBuild: Boolean): AppGraph
    }
}
