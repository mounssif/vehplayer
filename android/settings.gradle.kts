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
    }
}

// Working codename "vehplayer" (session delta: an unrelated Swedish B2B app
// already ships under the name "VEPLA" on both stores, different Nice class
// but close enough to be worth dodging until trademark clearance is done,
// Foundation §1 TODO). Product/brand name in all user-facing docs is still
// under research; this is a code-level identifier only.
rootProject.name = "vehplayer"
include(":app")
