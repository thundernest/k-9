package app.k9mail.widget

import app.k9mail.feature.widget.unread.UnreadWidgetConfig
import org.koin.dsl.module

val appWidgetModule = module {
    single<UnreadWidgetConfig> { K9UnreadWidgetConfig() }
}
