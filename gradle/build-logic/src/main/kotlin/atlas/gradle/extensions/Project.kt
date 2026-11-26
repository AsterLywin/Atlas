package atlas.gradle.extensions

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForAtlasx
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

internal val Project.libs get() = the<LibrariesForLibs>()
internal val Project.atlasx get() = the<LibrariesForAtlasx>()

internal fun Project.plugins(block: PluginManager.() -> Unit) {
    pluginManager.apply(block)
}

internal fun Project.android(block: CommonExtension.() -> Unit) {
    extensions.configure(block)
}

fun Project.configureTest() {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
        // Local VM only: Robolectric downloads android-all through the
        // Maven mirror instead of Maven Central directly (the sandbox
        // egress breaks its HTTPS fetch). Pass
        // -ProbolectricRepoUrl=http://127.0.0.1:18080/central on the
        // Gradle command line; CI omits it and uses Maven Central.
        findProperty("robolectricRepoUrl")?.let {
            systemProperty("robolectric.dependency.repo.url", it.toString())
        }
    }
}
