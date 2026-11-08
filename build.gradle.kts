import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.shenszq.restful.toolkit"
version = "0.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 编译/沙箱目标：用 Community 版 2024.3（编译下限 = 兼容下限），避免 IU 沙箱每次启动弹未激活提示。
        // 本插件仅依赖 Java/Properties/YAML/Kotlin bundled 插件，IC 全部具备；253+ 兼容性由下方 verifier 矩阵兼顾。
        intellijIdeaCommunity("2024.3")
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

    // Plugin Verifier 版本矩阵：编译下限 2024.3，验证 2024.3 → 2026.3 二进制兼容性（verifyPlugin 任务）
    pluginVerification {
        ides {
            // IC 仅存在于 252 及更早；253+ 使用 IntellijIdea（统一发行版）
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdea, "2025.3")
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }
    }
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/java")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Sandbox IDE exits with code 7 when it requests a restart (e.g. after changing i18n.locale).
// Gradle's default JavaExec treats any non-zero exit as failure; ignore it so `runIde` doesn't FAIL.
tasks.named<JavaExec>("runIde") {
    isIgnoreExitValue = true
}
