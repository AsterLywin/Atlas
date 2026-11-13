package atlas.presentation.widget.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import atlas.presentation.widget.BaseUpdatesGridGlanceWidget

@ContributesTo(AppScope::class)
interface PresentationWidgetGraph {
    fun inject(widget: BaseUpdatesGridGlanceWidget)
}
