package app.k9mail.legacy.ui.folder

import org.koin.dsl.module

val uiFolderModule = module {
    single<DisplayFolderRepository> {
        DefaultDisplayFolderRepository(
            accountManager = get(),
            messagingController = get(),
            messageStoreManager = get(),
        )
    }
}
