import com.xpdustry.ksr.kotlinRelocate
import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.extension.configureDesktop
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	`java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.common)
    alias(libs.plugins.indra.git)
    alias(libs.plugins.indra.publishing)
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
    alias(libs.plugins.ksr)
    alias(libs.plugins.dokka)
}

val metadata = ModMetadata.fromJson(rootProject.file("plugin.json"))
if (indraGit.headTag() == null) metadata.version += "-SNAPSHOT"
group = "com.xpdustry"
val rootPackage = "com.xpdustry.flex"
version = metadata.version
description = metadata.description

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenCentral()
    anukeXpdustry()
    maven("https://maven.xpdustry.com/releases") {
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
    maven("https://maven.xpdustry.com/snapshots") {
        name = "xpdustry-snapshots"
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.coroutines.jdk8)
    testImplementation(libs.kotlinx.coroutines.jdk8)
    compileOnly(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.serialization.json)

    compileOnly(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.arcCore)
    compileOnly(libs.distributor.api)
    testImplementation(libs.distributor.api)

    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.deepl) {
        exclude("org.jetbrains", "annotations")
    }
    implementation(libs.caffeine) {
        exclude("org.checkerframework", "checker-qual")
        exclude("com.google.errorprone", "error_prone_annotations")
    }

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.slf4j.simple)
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    if (metadata.repository.isNotBlank()) {
        val repo = metadata.repository.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            organization {
                name = "xpdustry"
                url = "https://www.xpdustry.com"
            }
        }
    }
}

spotless {
    kotlin {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        licenseHeaderFile(rootProject.file("HEADER.txt"))
    }
    kotlinGradle {
        ktlint().editorConfigOverride(mapOf("max_line_length" to "120"))
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        apiVersion = KotlinVersion.KOTLIN_2_1
    }
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
}

val generateMetadataFile by tasks.registering {
    // inputs.property("metadata", metadata)
    // val output = temporaryDir.resolve("plugin.json")
    // outputs.file(output)
    // doLast { output.writeText(ModMetadata.toJson(metadata)) }
}

tasks.shadowJar {
    archiveFileName = "${metadata.name}.jar"
    archiveClassifier = "plugin"
    from(generateMetadataFile)
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    val shadowPackage = "com.xpdustry.flex.shadow"
    kotlinRelocate("com.sksamuel.hoplite", "$shadowPackage.hoplite")
    relocate("org.yaml.snakeyaml", "$shadowPackage.snakeyaml")
    relocate("com.deepl", "$shadowPackage.deepl")
    relocate("com.github.benmanes.caffeine", "$shadowPackage.caffeine")
    relocate("com.google.gson", "$shadowPackage.gson")
    mergeServiceFiles()
    minimize {
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency(libs.caffeine.get()))
    }
}

tasks.javadocJar {
    from(tasks.dokkaHtml)
}

tasks.register<Copy>("release") {
    dependsOn(tasks.build)
    from(tasks.shadowJar)
    destinationDir = temporaryDir
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md-simple.jar"
    version = "v${libs.versions.slf4md.get()}"
}

val downloadDistributorCommon by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "distributor"
    asset = "distributor-common.jar"
    version = "v${libs.versions.distributor.get()}"
}

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "kotlin-runtime"
    asset = "kotlin-runtime.jar"
    version = "v${libs.versions.kotlin.runtime.get()}-k.${libs.versions.kotlin.core.get()}"
}

tasks.runMindustryServer {
    mods.from(downloadSlf4md, downloadDistributorCommon, downloadKotlinRuntime)
}

val downloadFooClient by tasks.registering(GithubAssetDownload::class) {
    owner = "mindustry-antigrief"
    repo = "mindustry-client-v7-builds"
    asset = "desktop.jar"
    version = libs.versions.mindustry.foo.get()
}

tasks.register<MindustryExec>("runMindustryDesktopFoo") {
    group = Toxopid.TASK_GROUP_NAME
    configureDesktop()
    setClasspath(files(downloadFooClient))
}
