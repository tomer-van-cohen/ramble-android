pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google { content { excludeGroup("org.json") } }; mavenCentral() } }
rootProject.name = "RambleFlow"
include(":app")
