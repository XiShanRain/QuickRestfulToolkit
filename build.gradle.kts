plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.shenszq.restful.toolkit"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2024.3.6")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }

        changeNotes = """
            Migrated from Java to Kotlin.
        """.trimIndent()
    }
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/java")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

// Sandbox IDE exits with code 7 when it requests a restart (e.g. after changing i18n.locale).
// Gradle's default JavaExec treats any non-zero exit as failure; ignore it so `runIde` doesn't FAIL.
tasks.named<JavaExec>("runIde") {
    isIgnoreExitValue = true
}
