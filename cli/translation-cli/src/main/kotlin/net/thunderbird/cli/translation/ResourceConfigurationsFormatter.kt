package net.thunderbird.cli.translation

class ResourceConfigurationsFormatter {
    fun format(languageCodes: List<String>) = buildString {
        appendLine("android {")
        appendLine("    androidResources {")
        appendLine("        // Keep in sync with the resource string array \"supported_languages\"")
        appendLine("        localeFilters += listOf(")
        languageCodes.forEach { code ->
            appendLine("            \"$code\",")
        }
        appendLine("        )")
        appendLine("    }")
        appendLine("}")
    }.trim()
}
