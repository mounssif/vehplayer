// Root build file. Per-module config lives in app/build.gradle.kts.
// Gate-1 scope only: this project currently exists to host the S1 reachability
// probe and later the real capture/encode/serve pipeline (ARCHITECTURE.md).
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
