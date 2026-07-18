plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.vehplayer.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.vehplayer.android"
        // minSdk 29: AudioPlaybackCapture (Foundation §6) requires Android 10+.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-gate1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // revisit once app/ has real logic worth shrinking
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Copies the built webclient bundle (webclient/dist/, produced by `npm run
// build` in webclient/) into assets/webclient so HttpAssetServer.kt can serve
// it offline (ARCHITECTURE.md §6). Wires the open TODO from NEXT_SESSION.md.
val webclientDistDir = rootProject.projectDir.resolve("../webclient/dist")

tasks.register<Copy>("copyWebclientDist") {
    from(webclientDistDir)
    into("src/main/assets/webclient")
    onlyIf {
        val exists = webclientDistDir.exists()
        if (!exists) {
            logger.warn("webclient/dist not found at $webclientDistDir - run `npm run build` in webclient/ first. assets/webclient will be missing/stale.")
        }
        exists
    }
}

tasks.named("preBuild") {
    dependsOn("copyWebclientDist")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Local-only WS server (ARCHITECTURE.md §1/§4). Hand-rolling a compliant
    // RFC 6455 server is a bad use of a Gate-2 session; this is a small,
    // widely used, dependency-light server implementation. TODO(claude-code):
    // confirm this still resolves fine from mavenCentral() with your real
    // internet access, it's unverified in the chat sandbox (no maven access
    // there, see NEXT_SESSION.md).
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    // Serves the cached webclient bundle at the phone's local address
    // (ARCHITECTURE.md §6, resilience against the vepla.app domain being
    // unreachable). Small, dependency-light, the standard choice for
    // embedding an HTTP server in an Android app.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Coroutines: encoder/server loops are naturally async, avoids hand-rolled
    // thread management for what's fundamentally a small number of long-lived
    // background loops.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
