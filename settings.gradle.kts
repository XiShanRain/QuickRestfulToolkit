pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public/") }
        maven { url = uri("https://maven.aliyun.com/nexus/content/repositories/google/") }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "QuickRestfulToolkit"
