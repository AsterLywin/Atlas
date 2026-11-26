package atlas.presentation.widget.di

import atlas.presentation.widget.BaseUpdatesGridGlanceWidget
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface PresentationWidgetGraph {
    fun inject(widget: BaseUpdatesGridGlanceWidget)
}
