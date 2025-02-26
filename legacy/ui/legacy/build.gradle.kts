plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
    alias(libs.plugins.kotlin.parcelize)
}

dependencies {
    api(projects.legacy.ui.base)
    api(projects.legacy.ui.account)
    api(projects.legacy.ui.folder)
    api(projects.core.ui.legacy.designsystem)

    implementation(projects.legacy.core)
    implementation(projects.mail.common)
    implementation(projects.uiUtils.toolbarBottomSheet)

    implementation(projects.core.featureflags)
    implementation(projects.core.ui.theme.api)
    implementation(projects.feature.launcher)
    implementation(projects.feature.navigation.drawer)
    // TODO: Remove AccountOauth dependency
    implementation(projects.feature.account.oauth)
    implementation(projects.feature.funding.api)
    implementation(projects.feature.settings.import)
    implementation(projects.feature.telemetry.api)

    compileOnly(projects.mail.protocols.imap)

    implementation(projects.plugins.openpgpApiLib.openpgpApi)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.preferencex)
    implementation(libs.preferencex.datetimepicker)
    implementation(libs.preferencex.colorpicker)
    implementation(libs.androidx.recyclerview)
    implementation(projects.uiUtils.linearLayoutManager)
    implementation(projects.uiUtils.itemTouchHelper)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.ckchangelog.core)
    implementation(projects.library.tokenAutoComplete)
    implementation(libs.safeContentResolver)
    implementation(libs.searchPreference)
    implementation(libs.fastadapter)
    implementation(libs.fastadapter.extensions.drag)
    implementation(libs.fastadapter.extensions.utils)
    implementation(libs.circleImageView)
    implementation(libs.androidx.work.runtime)

    implementation(libs.commons.io)
    implementation(libs.androidx.core.ktx)
    implementation(libs.jcip.annotations)
    implementation(libs.timber)
    implementation(libs.mime4j.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // This is necessary as RecipientPresenterTest fails to inject
    testImplementation(projects.legacy.common)
    testImplementation(projects.core.testing)
    testImplementation(projects.mail.testing)
    testImplementation(projects.legacy.storage)
    testImplementation(projects.legacy.testing)
    testImplementation(projects.feature.telemetry.noop)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

android {
    namespace = "com.fsck.k9.ui"

    buildFeatures {
        buildConfig = true
    }
}
