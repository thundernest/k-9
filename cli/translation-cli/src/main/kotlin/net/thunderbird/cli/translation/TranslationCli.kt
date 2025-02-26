package net.thunderbird.cli.translation

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double

const val TRANSLATED_THRESHOLD = 70.0

class TranslationCli(
    private val languageCodeLoader: LanguageCodeLoader = LanguageCodeLoader(),
    private val configurationsFormatter: ResourceConfigurationsFormatter = ResourceConfigurationsFormatter(),
    private val supportedLanguagesFormatter: SupportedLanguagesFormatter = SupportedLanguagesFormatter(),
) : CliktCommand(
    name = "translation",
) {
    private val token: String by option(
        help = "Weblate API token",
    ).required()

    private val threshold: Double by option(
        help = "Threshold for translation completion",
    ).double().default(TRANSLATED_THRESHOLD)

    private val printAll: Boolean by option(
        help = "Print code example",
    ).flag()

    override fun help(context: Context): String = "Translation CLI"

    override fun run() {
        val languageCodes = languageCodeLoader.loadCurrentAndroidLanguageCodes(token, threshold)
        val androidLanguageCodes = languageCodes.map { AndroidLanguageCodeHelper.fixLanguageCodeFormat(it) }
        val size = languageCodes.size

        echo("\nLanguages that are translated above the threshold of ($threshold%): $size")
        echo("--------------------------------------------------------------")
        echo("For androidResources.localeFilters:")
        echo(languageCodes.joinToString(", "))
        echo()
        echo("For array resource supported_languages:")
        echo(androidLanguageCodes.joinToString(", "))
        if (printAll) {
            echo()
            echo("--------------------------------------------------------------")
            echo(configurationsFormatter.format(languageCodes))
            echo("--------------------------------------------------------------")
            echo("--------------------------------------------------------------")
            echo(supportedLanguagesFormatter.format(androidLanguageCodes))
            echo("--------------------------------------------------------------")
            echo("Please read docs/translating.md for more information on how to update language values.")
            echo("--------------------------------------------------------------")
        }
        echo()
    }
}
