pluginManagement {
    val quarkusPluginVersion = providers.gradleProperty("quarkusPluginVersion").get()
    val quarkusPluginId = providers.gradleProperty("quarkusPluginId").get()
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
    }
}

rootProject.name = "backend-quarkus"
