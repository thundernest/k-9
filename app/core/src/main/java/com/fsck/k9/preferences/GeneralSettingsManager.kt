package com.fsck.k9.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Retrieve and modify general settings.
 *
 * TODO: Add more settings as needed.
 */
interface GeneralSettingsManager {
    fun getSettings(): GeneralSettings
    fun getSettingsFlow(): Flow<GeneralSettings>

    fun setShowAccountListOnStartup(showAccountListOnStartup: Boolean)
    fun setShowRecentChanges(showRecentChanges: Boolean)
}
