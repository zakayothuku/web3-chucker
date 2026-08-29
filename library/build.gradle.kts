plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "io.github.web3chucker"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    // Production code uses org.json.JSONObject/JSONArray, which are stubbed (throw
    // RuntimeException("Stub!")) on the plain-JVM unit test classpath by default.
    // Pulling in a real implementation here lets unit tests actually exercise JSON parsing.
    testImplementation("org.json:json:20231013")
}


afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.github.zakayothuku"
                artifactId = "web3-chucker"
                version = "1.0.0"

                pom {
                    name.set("web3-chucker")
                    description.set("OkHttp JSON-RPC Interceptor & Compose Debug UI Overlay for Android Web3 Applications.")
                    url.set("https://github.com/zakayothuku/web3-chucker")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("zakayothuku")
                            name.set("Zakayo Thuku")
                            email.set("zakayothuku@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/zakayothuku/web3-chucker.git")
                        developerConnection.set("scm:git:ssh://github.com/zakayothuku/web3-chucker.git")
                        url.set("https://github.com/zakayothuku/web3-chucker")
                    }
                }
            }
        }
    }
}
