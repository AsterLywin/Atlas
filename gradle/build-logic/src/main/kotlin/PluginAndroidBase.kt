import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DefaultConfig
import atlas.gradle.configurations.configureKotlin
import atlas.gradle.extensions.android
import atlas.gradle.extensions.configureTest
import atlas.gradle.extensions.coreLibraryDesugaring
import atlas.gradle.extensions.libs
import atlas.gradle.extensions.atlasx
import atlas.gradle.extensions.release
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class PluginAndroidBase : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        configureKotlin()
        configureTest()

        android {
            defaultConfig {
                minSdk = atlasx.versions.android.sdk.min.get().toInt()
                if (this is ApplicationDefaultConfig) {
                    targetSdk = atlasx.versions.android.sdk.target.get().toInt()
                }

                ndkVersion = atlasx.versions.android.ndk.get()
            }

            compileSdk {
                version = release(atlasx.versions.android.sdk.compile)
            }

            compileOptions {
                isCoreLibraryDesugaringEnabled = true
            }
        }

        dependencies {
            coreLibraryDesugaring(libs.android.desugar)
        }
    }
}

private fun CommonExtension.defaultConfig(block: DefaultConfig.() -> Unit) {
    defaultConfig.apply(block)
}

private fun CommonExtension.compileOptions(block: CompileOptions.() -> Unit) {
    compileOptions.apply(block)
}
