pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // FrostWire 官方 Maven repo,jlibtorrent 引擎發布在這裡(持續維護,對應真實 API)
        maven {
            url = uri("https://dl.frostwire.com/maven")
            content { includeGroup("com.frostwire") }
        }
    }
}
rootProject.name = "SimpleTorrent"
include(":app")
