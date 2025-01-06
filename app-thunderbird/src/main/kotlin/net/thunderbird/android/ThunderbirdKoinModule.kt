package net.thunderbird.android

import app.k9mail.core.common.oauth.OAuthConfigurationFactory
import app.k9mail.core.common.provider.AppNameProvider
import app.k9mail.core.common.provider.BrandNameProvider
import app.k9mail.core.featureflag.FeatureFlagFactory
import app.k9mail.core.ui.theme.api.FeatureThemeProvider
import app.k9mail.core.ui.theme.api.ThemeProvider
import app.k9mail.feature.widget.shortcut.LauncherShortcutActivity
import com.fsck.k9.AppConfig
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.preferences.FilePrefixProvider
import net.thunderbird.android.auth.TbOAuthConfigurationFactory
import net.thunderbird.android.dev.developmentModuleAdditions
import net.thunderbird.android.feature.featureModule
import net.thunderbird.android.featureflag.TbFeatureFlagFactory
import net.thunderbird.android.provider.TbAppNameProvider
import net.thunderbird.android.provider.TbFeatureThemeProvider
import net.thunderbird.android.provider.TbThemeProvider
import net.thunderbird.android.widget.appWidgetModule
import net.thunderbird.android.widget.provider.UnreadWidgetProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {
    includes(appWidgetModule)
    includes(featureModule)

    single(named("ClientInfoAppName")) { BuildConfig.CLIENT_INFO_APP_NAME }
    single(named("ClientInfoAppVersion")) { BuildConfig.VERSION_NAME }
    single<AppConfig> { appConfig }
    single<OAuthConfigurationFactory> { TbOAuthConfigurationFactory() }
    single {
        TbAppNameProvider(androidContext())
    } binds arrayOf(AppNameProvider::class, BrandNameProvider::class, FilePrefixProvider::class)
    single<ThemeProvider> { TbThemeProvider() }
    single<FeatureThemeProvider> { TbFeatureThemeProvider() }
    single<FeatureFlagFactory> { TbFeatureFlagFactory() }

    developmentModuleAdditions()
}

val appConfig = AppConfig(
    componentsToDisable = listOf(
        MessageCompose::class.java,
        LauncherShortcutActivity::class.java,
        UnreadWidgetProvider::class.java,
    ),
)
