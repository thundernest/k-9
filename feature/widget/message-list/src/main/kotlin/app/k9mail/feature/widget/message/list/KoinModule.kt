package app.k9mail.feature.widget.message.list

import org.koin.dsl.module

val messageListWidgetModule = module {
    factory { MessageListLoader(preferences = get(), messageListRepository = get(), messageHelper = get()) }
}
