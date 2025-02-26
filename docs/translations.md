# Managing strings

We use Android's [resource system](https://developer.android.com/guide/topics/resources/localization) to localize
user-visible strings in our apps.

Our source language is English (American English to be more precise, but simply "English" (en) on Weblate).

Translations of source strings happen exclusively in our
[Weblate project](https://hosted.weblate.org/projects/tb-android/). This means the source language is only modified by
changes to this repository, i.e. via pull requests. Translations are only updated on Weblate and then merged into this
repository by the Thunderbird team. This is to avoid overlapping changes in both repositories that will lead to merge
conflicts.

## Adding a string

Add a new string to the appropriate `res/values/strings.xml` file.

Please don't add any translations for this new string to this repository. If you can also provide a translation for the
new string, wait until the change is merged into this repository and propagated to Weblate. Then translate the new
string on Weblate.

## Changing a string

Changing a string should be avoided. Weblate doesn't automatically invalidate translations when a source string is
changed. This can be worked around by removing the old string and adding a new one. Make sure to only modify the source
language. It's fine for the translations to then contain unused strings. The next merge with Weblate will remove those.

## Removing a string

Remove the source string from `res/values/strings.xml`. Don't modify translations under `res/values-<lang>/strings.xml`.
The next merge from Weblate will automatically get rid of the translated strings.

## Changing translations in this repository

This should be avoided whenever possible, as it can create merge conflicts between Weblate and this repository. If you
need to change individual strings, please translate them on Weblate instead. If a mechanical change is necessary across
all languages, this should be discussed with the core team who will use this procedure:

1. Lock all components on Weblate by clicking the "Lock" button in the
   [repository maintenance](https://hosted.weblate.org/projects/tb-android/#repository) screen.
2. Commit all outstanding changes by clicking the "Commit" button in the same screen.
3. Trigger creating a pull request containing translation updates from Weblate by clicking the "Push" button in the
   repository maintenance screen.
4. Merge that pull request containing updates from Weblate into this repository.
5. Create a pull request to change the translated files, following the established procedures to get it merged. Make
   sure you've rebased against the latest changes.
6. Wait for the changes in this repository to be automatically propagated to and processed by Weblate.
7. Unlock components on Weblate by clicking the "Unlock" button in the
   [repository maintenance](https://hosted.weblate.org/projects/tb-android/#repository) screen.

# Managing translations

Right now we're using the `androidResources.localeFilters` mechanism provided by the Android Gradle Plugin to limit
which languages are included in builds of the app,
See [localFilters](<https://developer.android.com/reference/tools/gradle-api/8.8/com/android/build/api/dsl/ApplicationAndroidResources#localeFilters()>).

This list needs to be kept in sync with the string array `supported_languages`, so the in-app language picker offers
exactly the languages that are included in the app.

## Removing a language

1. Remove the language code from the `androidResources.localeFilters` list in `app-thunderbird/build.gradle.kts` and
   `app-k9mail/build.gradle.kts`.
2. Remove the entry from `supported_languages` in `app/core/src/main/res/values/arrays_general_settings_values.xml`.

## Adding a language

1. Add the language code to the `androidResources.localeFilters` list in `app-thunderbird/build.gradle.kts` and
   `app-k9mail/build.gradle.kts`.
2. Add an entry to `supported_languages` in `app/core/src/main/res/values/arrays_general_settings_values.xml`.
3. Make sure that `language_values` in `app/core/src/main/res/values/arrays_general_settings_values.xml` contains an
   entry for the language code you just added. If not:
   1. Add the language name (in its native script) to `language_entries` in
      `app/ui/legacy/src/main/res/values/arrays_general_settings_strings.xml`. Please note that this list should be
      ordered using the Unicode default collation order.
   2. Add the language code to `language_values` in `app/core/src/main/res/values/arrays_general_settings_values.xml`
      so that the index in the list matches that of the newly added entry in `language_entries`.

## Adding a component on Weblate

When adding a new code module that is including translatable strings, a new components needs to be added to Weblate.

1. Go the the Weblate page to [add a component](https://hosted.weblate.org/create/component/?project=3696).
2. Switch to the "From existing component" tab.
3. Enter a name for the component.
4. For "Component", select "K-9 Mail/Thunderbird/ui-legacy".
5. Press the "Continue" button.
6. Under "Choose translation files to import", select "Specify configuration manually".
7. Press the "Continue" button.
8. For "File format", select "Android String Resource".
9. Under "File mask", enter the path to the string resource files with a wildcard,
   e.g. `feature/account/common/src/main/res/values-*/strings.xml`.
10. Under "Monolingual base language file", enter the path to the string source file,
    e.g. `feature/account/common/src/main/res/values/strings.xml`.
11. Uncheck "Edit base file".
12. For "Translation license", select "Apache License 2.0".
13. Press the "Save" button.

## Things to note

For some languages Android uses different language codes than typical translation tools, e.g. Hebrew's code is _he_ on
Weblate, but _iw_ on Android. When writing automation tools, there needs to be a mapping step involved.

See [translation-cli](https://github.com/thunderbird/thunderbird-android/blob/ed07da8be5513ac74aabb1c934a4545aaae4f5a3/cli/translation-cli/src/main/kotlin/net/thunderbird/cli/translation/LanguageCodeLoader.kt#L12-L13)
for an example.
