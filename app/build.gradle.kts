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

/**
 * A build secret, resolved environment-first (CI) then local.properties (a
 * developer machine), and `null` when genuinely absent.
 *
 * Deliberately no default. A signing credential that silently falls back to
 * something is a credential you discover is wrong when the APK will not install
 * on the watch; `null` lets the build say so instead.
 */
fun secret(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }

/**
 * Release signing material. All four must be present or the release build is
 * assembled unsigned — see the `signingConfig` assignment in `buildTypes` for
 * why that is a warning rather than a hard failure.
 */
val releaseStoreFile = secret("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = secret("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

/**
 * Opt-in escape hatch for *local profiling only*: `-PdebugSignRelease=true`
 * signs the release build with the debug key so `installRelease` works on a
 * watch without the real keystore.
 *
 * This exists because release is the only build worth judging performance from
 * (see the note on the `debug` block below) and an unsigned APK cannot be
 * installed to measure. It is off by default and must never be set in CI — a
 * debug-signed release cannot be updated by a properly signed one later.
 */
val debugSignRelease = (findProperty("debugSignRelease") as String?).toBoolean()

// An unsigned APK builds cleanly and then refuses to install, which is a
// confusing way to find out a credential was missing. Say so up front — but
// only when a release task was actually asked for, so ordinary debug builds
// stay quiet.
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
    !hasReleaseSigning && !debugSignRelease
) {
    logger.warn(
        "\n⚠  No release signing credentials found (RELEASE_KEYSTORE_PATH, " +
            "RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD).\n" +
            "   The release build will be UNSIGNED and will not install on a watch.\n" +
            "   To profile locally: ./gradlew installRelease -PdebugSignRelease=true\n" +
            "   To cut a real release: see docs/RELEASING.md\n"
    )
}

android {
    namespace = "com.spedatox.ultroncore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spedatox.ultroncore"
        // Wear OS 4 (API 33) is the floor for the Galaxy Watch 6 line; going
        // lower would drag in compat paths this app never runs.
        minSdk = 33
        targetSdk = 36
        // Overridable so the release workflow can stamp the build from the git
        // tag (`-PultronVersionName=1.2.3 -PultronVersionCode=47`) without a
        // commit. The literals below stay the source of truth for every local
        // and CI build that is not cutting a release.
        versionCode = (findProperty("ultronVersionCode") as String?)?.toIntOrNull() ?: 2
        versionName = (findProperty("ultronVersionName") as String?) ?: "2.0-ultron-wear"

        buildConfigField("String", "IGOR_BASE_URL", "\"${localProp("IGOR_BASE_URL")}\"")
        buildConfigField("String", "IGOR_API_KEY", "\"${localProp("IGOR_API_KEY")}\"")
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebaseConfig")
    }

    signingConfigs {
        // Created only when every credential is present. Declaring it
        // unconditionally would make `file(null)` explode at configuration time,
        // which would break `./gradlew tasks` on a fresh clone.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Both schemes on: v1 is irrelevant at minSdk 33 but costs
                // nothing, v2/v3 are what Wear OS actually verifies.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Unsigned is a legitimate outcome, not a bug: it keeps
            // `assembleRelease` runnable on a fork or a PR checkout with no
            // access to the keystore, which is exactly how CI validates that R8
            // and lintVitalRelease still pass before a tag is ever cut. The
            // release *workflow* fails loudly if the keystore is missing, so an
            // unsigned artifact can never reach a GitHub Release.
            signingConfig = when {
                hasReleaseSigning -> signingConfigs.getByName("release")
                debugSignRelease -> signingConfigs.getByName("debug")
                else -> null
            }
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
