package net.thunderbird.cli.translation

import net.thunderbird.cli.translation.net.Language
import net.thunderbird.cli.translation.net.Translation
import net.thunderbird.cli.translation.net.WeblateClient

class LanguageCodeLoader(
    private val client: WeblateClient = WeblateClient(),
) {
    fun loadCurrentAndroidLanguageCodes(token: String, threshold: Double): List<String> {
        val languages = client.loadLanguages(token)
        val translations = client.loadTranslations(token)
        val languageCodeLookup = createLanguageCodeLookup(translations)

        return filterAndMapLanguages(languages, threshold, languageCodeLookup)
    }

    private fun createLanguageCodeLookup(translations: List<Translation>): Map<String, String> {
        return translations.associate { it.language.code to it.languageCode }
    }

    private fun filterAndMapLanguages(
        languages: List<Language>,
        threshold: Double,
        languageCodeLookup: Map<String, String>,
    ): List<String> {
        return languages.filter { it.translatedPercent >= threshold }
            .map {
                languageCodeLookup[it.code] ?: throw IllegalArgumentException("Language code ${it.code} is not mapped")
            }.sorted()
    }
}
