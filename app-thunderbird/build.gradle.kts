import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

plugins {
    id(ThunderbirdPlugins.App.androidCompose)
    alias(libs.plugins.dependency.guard)
    id("thunderbird.quality.badging")
}

val testCoverageEnabled: Boolean by extra
if (testCoverageEnabled) {
    apply(plugin = "jacoco")
}

android {
    namespace = "net.thunderbird.android"

    defaultConfig {
        applicationId = "net.thunderbird.android"
        testApplicationId = "net.thunderbird.android.tests"

        versionCode = 4
        versionName = "10.0"

        buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"Thunderbird for Android\"")
    }

    androidResources {
        // Keep in sync with the resource string array "supported_languages"
        localeFilters += listOf(
            "ar",
            "be",
            "bg",
            "ca",
            "co",
            "cs",
            "cy",
            "da",
            "de",
            "el",
            "en",
            "en-rGB",
            "eo",
            "es",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "fy",
            "ga",
            "gl",
            "hr",
            "hu",
            "in",
            "is",
            "it",
            "iw",
            "ja",
            "ko",
            "lt",
            "lv",
            "nb",
            "nl",
            "nn",
            "pl",
            "pt-rBR",
            "pt-rPT",
            "ro",
            "ru",
            "sl",
            "sk",
            "sq",
            "sr",
            "sv",
            "tr",
            "uk",
            "vi",
            "zh-rCN",
            "zh-rTW",
        )
    }

    signingConfigs {
        val useUploadKey = properties.getOrDefault("tb.useUploadKey", "true") == "true"

        createSigningConfig(project, SigningType.TB_RELEASE, isUpload = useUploadKey)
        createSigningConfig(project, SigningType.TB_BETA, isUpload = useUploadKey)
        createSigningConfig(project, SigningType.TB_DAILY, isUpload = useUploadKey)
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-SNAPSHOT"

            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "null")
        }

        release {
            signingConfig = signingConfigs.getByType(SigningType.TB_RELEASE)

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"release\"")
        }

        create("beta") {
            signingConfig = signingConfigs.getByType(SigningType.TB_BETA)

            applicationIdSuffix = ".beta"
            versionNameSuffix = "b1"

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            matchingFallbacks += listOf("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"beta\"")
        }

        create("daily") {
            signingConfig = signingConfigs.getByType(SigningType.TB_DAILY)

            applicationIdSuffix = ".daily"
            versionNameSuffix = "a1"

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            matchingFallbacks += listOf("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )

            // See https://bugzilla.mozilla.org/show_bug.cgi?id=1918151
            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"nightly\"")
        }
    }

    flavorDimensions += listOf("app")
    productFlavors {
        create("foss") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"foss\"")
        }

        create("full") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"full\"")
        }
    }

    @Suppress("UnstableApiUsage")
    bundle {
        language {
            // Don't split by language. Otherwise our in-app language switcher won't work.
            enableSplit = false
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("kotlin/**")
        }

        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

// Initialize placeholders for the product flavor and build type combinations needed for dependency declarations.
// They are required to avoid "Unresolved configuration" errors.
val fullDebugImplementation by configurations.creating
val fullDailyImplementation by configurations.creating
val fullBetaImplementation by configurations.creating
val fullReleaseImplementation by configurations.creating

dependencies {
    implementation(projects.appCommon)
    implementation(projects.core.ui.compose.theme2.thunderbird)
    implementation(projects.core.ui.legacy.theme2.thunderbird)
    implementation(projects.feature.launcher)

    implementation(projects.legacy.core)
    implementation(projects.legacy.ui.legacy)

    implementation(projects.core.featureflags)

    implementation(projects.feature.widget.messageList)
    implementation(projects.feature.widget.shortcut)
    implementation(projects.feature.widget.unread)

    debugImplementation(projects.feature.telemetry.noop)
    "dailyImplementation"(projects.feature.telemetry.noop)
    "betaImplementation"(projects.feature.telemetry.noop)
    releaseImplementation(projects.feature.telemetry.noop)

    implementation(libs.androidx.work.runtime)

    implementation(projects.feature.autodiscovery.api)
    debugImplementation(projects.backend.demo)
    debugImplementation(projects.feature.autodiscovery.demo)

    "fossImplementation"(projects.feature.funding.link)

    fullDebugImplementation(projects.feature.funding.googleplay)
    fullDailyImplementation(projects.feature.funding.googleplay)
    fullBetaImplementation(projects.feature.funding.googleplay)
    fullReleaseImplementation(projects.feature.funding.googleplay)

    implementation(projects.feature.onboarding.migration.thunderbird)
    implementation(projects.feature.migration.launcher.thunderbird)

    // TODO remove once OAuth ids have been moved from TBD to TBA
    "betaImplementation"(libs.appauth)
    releaseImplementation(libs.appauth)

    testImplementation(libs.robolectric)

    // Required for DependencyInjectionTest to be able to resolve OpenPgpApiManager
    testImplementation(projects.plugins.openpgpApiLib.openpgpApi)
    testImplementation(projects.feature.account.setup)
}

dependencyGuard {
    configuration("fossDailyRuntimeClasspath")
    configuration("fossBetaRuntimeClasspath")
    configuration("fossReleaseRuntimeClasspath")

    configuration("fullDailyRuntimeClasspath")
    configuration("fullBetaRuntimeClasspath")
    configuration("fullReleaseRuntimeClasspath")
}

tasks.register("printConfigurations") {
    doLast {
        configurations.forEach { configuration ->
            println("Configuration: ${configuration.name}")
            configuration.dependencies.forEach { dependency ->
                println("  - ${dependency.group}:${dependency.name}:${dependency.version}")
            }
        }
    }
}

tasks.register("printVersionInfo") {
    val targetBuildType = project.findProperty("buildType") ?: "debug"
    val targetFlavorName = project.findProperty("flavorName") ?: "full"

    doLast {
        android.applicationVariants.all { variant ->
            if (variant.buildType.name == targetBuildType && variant.flavorName == targetFlavorName) {
                val flavor = variant.mergedFlavor

                var buildTypeSource = android.sourceSets.getByName(targetBuildType).res.srcDirs.first()
                var stringsXmlFile = File(buildTypeSource, "values/strings.xml")
                if (!stringsXmlFile.exists()) {
                    buildTypeSource = android.sourceSets.getByName("main").res.srcDirs.first()
                    stringsXmlFile = File(buildTypeSource, "values/strings.xml")
                }

                val xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXmlFile)
                val xPath = XPathFactory.newInstance().newXPath()
                val expression = "/resources/string[@name='app_name']/text()"
                val appName = xPath.evaluate(expression, xmlDocument, XPathConstants.STRING) as String

                val output = """
                    APPLICATION_ID=${variant.applicationId}
                    APPLICATION_LABEL=$appName
                    VERSION_CODE=${flavor.versionCode}
                    VERSION_NAME=${flavor.versionName}
                    VERSION_NAME_SUFFIX=${variant.buildType.versionNameSuffix ?: ""}
                    FULL_VERSION_NAME=${flavor.versionName}${variant.buildType.versionNameSuffix ?: ""}
                """.trimIndent()

                println(output)
                val githubOutput = System.getenv("GITHUB_OUTPUT")
                if (githubOutput != null) {
                    val outputFile = File(githubOutput)
                    outputFile.writeText(output + "\n")
                }
            }
            true
        }
    }
}
