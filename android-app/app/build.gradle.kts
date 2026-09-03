plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The Phase 2 archive pipeline is the single source of truth for recipe data.
// This task copies its generated import bundle into a generated (gitignored)
// assets directory at build time, so the checked-in source tree never holds a
// second, potentially-stale copy of recipe-app-import.json.
val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/importBundle")

val copyImportBundle by tasks.registering(Copy::class) {
    description = "Copies the Phase 2 recipe-app-import.json bundle into generated Android assets."
    val sourcePath = providers.gradleProperty("recipeBundleSource")
        .orElse("/media/nas/RecipeScans/.processed/archive/recipe-app-import.json")
    val sourceFile = file(sourcePath.get())
    from(sourceFile)
    into(generatedAssetsDir)
    onlyIf {
        val exists = sourceFile.exists()
        if (!exists) {
            logger.warn(
                "Recipe bundle source not found at $sourceFile; skipping copy. " +
                    "Pass -PrecipeBundleSource=<path> to override, or ensure a previously " +
                    "generated copy exists at $generatedAssetsDir.",
            )
        }
        exists
    }
}

android {
    namespace = "com.recipearchive.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.recipearchive.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Adaptive icons require the mipmap-anydpi-v26 qualifier by convention; lint's generic
        // "obsolete version qualifier" check doesn't know that and suggests an invalid merge.
        // A monochrome launcher icon is a cosmetic nicety, not something Phase 3 needs.
        disable += setOf("ObsoleteSdkInt", "MonochromeLauncherIcon")
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
            assets.srcDir(generatedAssetsDir)
        }
        getByName("debug") {
            // Robolectric (used by JVM unit tests) and instrumented androidTest both read the
            // *debug* variant's merged assets, not a separate test-only merge. Fixture bundles
            // and Room's exported schema JSON (for MigrationTestHelper) live here so both kinds
            // of test can see them without shipping into a release build.
            assets.srcDirs("src/debug/assets", "$projectDir/schemas")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.named("preBuild") {
    dependsOn(copyImportBundle)
}

tasks.matching { it.name.contains("MergeAssets", ignoreCase = false) }.configureEach {
    dependsOn(copyImportBundle)
}

tasks.matching { it.name.startsWith("test") && it.name.endsWith("UnitTest") }.configureEach {
    dependsOn(copyImportBundle)
}
