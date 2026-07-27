import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Firebase is applied ONLY when `app/google-services.json` is present.
 *
 * The google-services plugin hard-fails the build when that file is missing, so
 * applying it unconditionally would mean nobody can compile Ultron Wear until a
 * Firebase project exists. The messaging SDK stays on the classpath either way —
 * it is the plugin, not the library, that needs the config — so the code
 * compiles identically and simply finds no FirebaseApp at runtime. Drop the JSON
 * in and FCM lights up with no source change.
 */
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

/** Igor credentials come from local.properties, never from source control. */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key).orEmpty()

android {
    namespace = "com.spedatox.ultroncore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spedatox.ultroncore"
        // Wear OS 4 (API 33) is the floor for the Galaxy Watch 6 line; going
        // lower would drag in compat paths this app never runs.
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "2.0-ultron-wear"

        buildConfigField("String", "IGOR_BASE_URL", "\"${localProp("IGOR_BASE_URL")}\"")
        buildConfigField("String", "IGOR_API_KEY", "\"${localProp("IGOR_API_KEY")}\"")
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebaseConfig")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Keep the debug build honest about performance. Compose in a
            // debuggable build is 2–5× slower than release because composition
            // tracing and bounds checks are live; never judge watch smoothness
            // from a debug APK.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            // Wear Compose Material 3 went stable in 1.5.0, so its old
            // ExperimentalWearMaterial3Api marker no longer exists in 1.6.2 —
            // opting into it produced an "unresolved marker" warning. The
            // foundation marker is still real and still needed.
            "-opt-in=androidx.wear.compose.foundation.ExperimentalWearFoundationApi",
        )
    }
}

composeCompiler {
    // Strong skipping is on by default from Kotlin 2.0.20; naming it here is
    // documentation. It is what lets composables with unstable lambda params
    // still skip, which is most of the win in a list of 13 cards.
    includeSourceInformation.set(false)
}

dependencies {
    constraints {
        // This app contains no Fragments. But play-services-base and
        // play-services-basement still declare androidx.fragment:1.1.0 (2019),
        // and `registerForActivityResult` in MainActivity trips lint's
        // InvalidFragmentVersionForActivityResult against anything below 1.3.0
        // — which fails lintVitalRelease and so blocks the release build.
        //
        // A constraint rather than an `implementation` dependency: it raises the
        // transitive version if Play Services drags it in, without adding
        // Fragment to the graph on its own. Suppressing the lint check instead
        // would leave a genuinely ancient transitive in the APK.
        implementation(libs.fragment)
    }

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)

    // ── Wear Compose (Material 3) ────────────────────────────────────────────
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // ── Wear platform surfaces ───────────────────────────────────────────────
    // NOTE: `androidx.wear:wear` is deliberately NOT here. It exists for the
    // View-system widgets (WearableRecyclerView, AmbientModeSupport, curved
    // text) that a Compose app never touches, and it transitively pins
    // androidx.fragment to 1.2.4 — old enough that lintVital fails the release
    // build with InvalidFragmentVersionForActivityResult. Dropping an unused
    // dependency is the honest fix; forcing a Fragment upgrade would just be
    // paying to silence a warning about a library we do not use.
    implementation(libs.tiles)
    implementation(libs.tiles.material)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.play.services.wearable)
    // TileService's request callbacks return Guava ListenableFutures.
    implementation(libs.guava)

    // ── AndroidX ─────────────────────────────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime.ktx)

    // Installs the baseline profile in src/main/baseline-prof.txt on first run,
    // so Compose's hot paths are AOT-compiled instead of being interpreted
    // through the first few frames. This is the single largest cold-start win
    // available to a Compose app and costs nothing at runtime.
    implementation(libs.profileinstaller)

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.coroutines.play.services)

    // ── Kotlin ───────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ── Tooling (debug only) ─────────────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
