import atlas.gradle.extensions.alias
import atlas.gradle.extensions.atlasx
import atlas.gradle.extensions.libs
import atlas.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UNUSED")
class PluginAndroidApplication : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(atlasx.plugins.android.base)
        }
    }
}
