import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Export the resolved runtime artifacts, including transitive dependencies, for offline notices.
tasks.register("exportLicenseDependencies") {
    notCompatibleWithConfigurationCache("Resolves dependency metadata for the maintenance tool")
    doLast {
        val artifacts = listOf("releaseRuntimeClasspath", "debugRuntimeClasspath").flatMap { name ->
            configurations.getByName(name).incoming.artifacts.artifacts.mapNotNull { artifact ->
                val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
                Triple(id, name.removeSuffix("RuntimeClasspath"), artifact.file)
            }
        }
        val poms = dependencies.createArtifactResolutionQuery()
            .forComponents(artifacts.map { it.first }.distinct())
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java).execute()
            .resolvedComponents.associate { component ->
                component.id to component.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>().single().file.absolutePath
            }
        val entries = artifacts.groupBy { "${it.first.group}:${it.first.module}:${it.first.version}" }
            .toSortedMap().map { (coordinate, values) ->
                mapOf("coordinate" to coordinate, "scopes" to values.map { it.second }.distinct().sorted(),
                    "artifacts" to values.map { it.third.absolutePath }.distinct().sorted(), "pom" to poms.getValue(values.first().first))
            }
        val report = layout.buildDirectory.file("reports/licenses/dependencies.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(entries)))
        logger.lifecycle("Exported ${entries.size} runtime dependencies to $report")
    }
}

abstract class VerifyOpenSourceNotices : DefaultTask() {
    @get:Input abstract val coordinates: ListProperty<String>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val notices: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val projectLicense: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val projectNotice: RegularFileProperty

    @TaskAction fun verify() {
        val directory = notices.get().asFile
        val entries = JsonSlurper().parse(directory.resolve("catalog.json")) as List<*>
        val documented = entries.flatMap { (it as Map<*, *>)["coordinates"] as List<*> }.toSet()
        check(documented == coordinates.get().toSet()) { "Dependency notices are stale. Run python tools/update_licenses.py." }
        entries.forEach { entry ->
            val asset = (entry as Map<*, *>)["asset"] as String
            check(directory.parentFile.resolve(asset).isFile) { "Missing license asset: $asset" }
        }
        fun normalized(file: File) = file.readText().replace("\r\n", "\n")
        val expected = normalized(projectNotice.get().asFile).trimEnd() + "\n\n" + normalized(projectLicense.get().asFile).trim() + "\n"
        check(normalized(directory.resolve("project.txt")) == expected) { "Project license text is stale. Run python tools/update_licenses.py." }
    }
}

fun runtimeCoordinates(name: String) = configurations.named(name).flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
    artifacts.mapNotNull { artifact ->
        (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}:${it.version}" }
    }
}
val verifyOpenSourceNotices = tasks.register<VerifyOpenSourceNotices>("verifyOpenSourceNotices") {
    coordinates.set(runtimeCoordinates("releaseRuntimeClasspath").zip(runtimeCoordinates("debugRuntimeClasspath")) { release, debug ->
        (release + debug).distinct().sorted()
    })
    notices.set(layout.projectDirectory.dir("src/main/assets/licenses"))
    projectLicense.set(rootProject.layout.projectDirectory.file("LICENSE"))
    projectNotice.set(rootProject.layout.projectDirectory.file("NOTICE"))
}
tasks.named("preBuild").configure { dependsOn(verifyOpenSourceNotices) }

android {
    namespace = "com.example.skywallpaper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.skywallpaper"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigning = listOf("SKYWALLPAPER_KEYSTORE_FILE", "SKYWALLPAPER_STORE_PASSWORD", "SKYWALLPAPER_KEY_ALIAS", "SKYWALLPAPER_KEY_PASSWORD")
        .associateWith { providers.environmentVariable(it).orNull }
    require(releaseSigning.values.all { it == null } || releaseSigning.values.all { !it.isNullOrBlank() }) {
        "Provide all four SKYWALLPAPER signing environment variables, or leave all unset."
    }
    if (releaseSigning.values.all { it != null }) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigning.getValue("SKYWALLPAPER_KEYSTORE_FILE")!!)
            storePassword = releaseSigning.getValue("SKYWALLPAPER_STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("SKYWALLPAPER_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("SKYWALLPAPER_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose's transitive Espresso version uses input APIs removed in Android 16.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
