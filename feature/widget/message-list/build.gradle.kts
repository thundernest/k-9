plugins {
    id(ThunderbirdPlugins.Library.android)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(projects.legacy.ui.legacy)
    implementation(projects.legacy.core)

    implementation(libs.timber)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}

android {
    buildFeatures {
        compose = true
    }
    namespace = "app.k9mail.feature.widget.message.list"
}
