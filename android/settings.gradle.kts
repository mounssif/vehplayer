import org.gradle.authentication.http.BasicAuthentication
import java.util.Properties

// Mapbox's Maven repo needs a secret DOWNLOADS:READ token as the basic-auth
// password (build-time artifact fetch only, unrelated to the public runtime
// token used in app code). Read from local.properties, same gitignored file
// sdk.dir already lives in, never committed.
val localProperties = Properties().apply {
    val f = File(rootDir, "local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapboxDownloadsToken: String = localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN") ?: ""

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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = mapboxDownloadsToken
            }
        }
    }
}

// Working codename "vehplayer" (session delta: an unrelated Swedish B2B app
// already ships under the name "VEPLA" on both stores, different Nice class
// but close enough to be worth dodging until trademark clearance is done,
// Foundation §1 TODO). Product/brand name in all user-facing docs is still
// under research; this is a code-level identifier only.
rootProject.name = "vehplayer"
include(":app")
