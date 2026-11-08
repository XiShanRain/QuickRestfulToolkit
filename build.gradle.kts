import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.xishanrain.restful.toolkit"
version = "0.0.9"

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
            // 上限锁到 2026.3：不设置时 Marketplace 会声明“支持所有未来版本”，新大版本 API 变更可能导致运行期崩溃
            untilBuild = "263.*"
        }

        // 面向用户的更新日志：Marketplace 详情页 “What's new” 直接展示，支持 HTML 子集
        changeNotes = """
            <h3>0.0.9</h3>
            <ul>
                <li>新增独立入口动作 “Go to REST Service (Dialog)” 与 “Go to REST Service (Popup)”，可分别在 Settings → Keymap 中绑定快捷键，不受首选窗口形态影响。</li>
                <li>修复 Ctrl+Alt+/ 在部分场景下热键静默失效的问题，主入口动作改为独立实现，不再受 IDE 跳转动作共享状态干扰。</li>
                <li>搜索对话框支持直接用上下方向键在结果列表间移动。</li>
                <li>所有动作与配置项 ID 增加 RestfulRadar 前缀，避免与其它 RestfulToolkit 系插件冲突导致设置失效。</li>
                <li>修复输入按后缀拆分匹配造成的误命中，以及项目级扫描时模块信息缺失导致的分组错误。</li>
                <li>启动阶段的 REST 接口扫描改为等待索引就绪后执行，避免 IDE 启动时抛出 IndexNotReadyException。</li>
                <li>插件名称统一为 RestfulRadar。</li>
            </ul>
        """.trimIndent()
    }

    // 发布到 JetBrains Marketplace（publishPlugin 任务）
    // token 从环境变量 PUBLISH_TOKEN 读取（形如 perm:xxxx），切勿写入 gradle.properties 或提交到版本库
    // PowerShell 用法：$env:PUBLISH_TOKEN = "perm:xxxx"; gradlew.bat publishPlugin
    // 默认发布到 stable 渠道；如需灰度可改为 listOf("beta")
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
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
