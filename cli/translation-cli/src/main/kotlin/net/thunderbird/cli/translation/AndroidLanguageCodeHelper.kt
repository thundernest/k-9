package net.thunderbird.cli.translation

object AndroidLanguageCodeHelper {

    /**
     * Fix the language code format to match the Android resource format.
     */
    fun fixLanguageCodeFormat(languageCode: String): String {
        return if (languageCode.contains("-r")) languageCode.replace("-r", "_") else languageCode
    }
}
