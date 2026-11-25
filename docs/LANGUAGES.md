# Languages

Cercis has four app languages: **English** (the base), **Nederlands**, **Deutsch** and **Français**. The
choice is in *Settings → Language* and is separate from the language of AI reports and the coach:

- **App language**: `Phone language`, English, Nederlands, Deutsch or Français. It changes the screens, the widget,
  notifications and the language Android reports to the system (Android 13 and newer).
- **Report and coach language**: English or Dutch only for now. A German or French phone still gets English reports
  unless a report language is chosen; the app says so on the Settings card. This is deliberate: the offline report
  writer only knows English and Dutch, and silently falling back would look like a broken translation.

The first start follows the phone's language. A phone set to German or French therefore opens in that language after
this update; a phone set to a language we do not have opens in English.

## Translation status

The German and French texts are a **machine/LLM draft**. They were written against the English source and a shared
glossary, then checked mechanically for keys, placeholders, plural shapes and obvious omissions. They have **not been
reviewed by a native speaker**, so they are useful but not a claim of native quality. Corrections are welcome: open a
GitHub issue with the screen, the language and the key if you can find it, or a screenshot with the text. Do not include
personal health data in an issue.

What is covered:

- Every resource string, plural and string array: 1,439 entries in each language, the same keys and placeholders as
  English. `LanguageParityTest` fails when a key, placeholder, plural shape or array length differs.
- Dates and numbers through the app's own formatter: German `24.11.2026` and a decimal comma, French `24/11/2026` and
  French grouping.
- The Settings language card, the four day wallpapers, the widget, the notification channels and the
  live/forwarding notifications use the chosen language. A channel that already exists on the phone keeps the name it
  was created with; Android does not rename channels on every language change.

What intentionally stays English:

- **Technical details** from the watch, BLE, USB and sync log. A localized problem line introduces the issue; where the
  app shows a raw diagnostic line, that line can remain English so a bug report stays searchable. The same applies to
  FIT workout names (`Easy`, `Long`, `Tempo`, ...): they are data from the watch, not app copy.
- **AI reports and the coach** stay in English or Dutch, as described above.
- Product names and formats: Cercis, Garmin, Oura, WHOOP, FIT, GPX, TCX, BLE, USB, Body Battery, HRV, SpO2.

## How it is tested

- `LanguageParityTest` discovers every `values-<language>/strings.xml`, requires `en`, `nl`, `de` and `fr`, and checks
  keys, non-empty values, format placeholders (skipping `formatted="false"`), plural quantities and array lengths.
  French is allowed its extra `many` plural; English, Dutch and German are not.
- `FmtTest` pins German and French date and number output.
- `LocaleSwitchTest` launches the app with `app_language=de` and `app_language=fr` and checks the German/French
  Settings screen, then picks German in the UI. `MainActivity` recreates itself when the language flow changes, so the
  test verifies that the stored choice reaches a fresh Activity instead of only changing a label.
- `:app:lintDebug` catches missing translations and invalid format strings. The full app suite runs on CI.

No test can prove that a translation reads naturally. A native review is still an open item, not a hidden checkbox.

## Adding or correcting a language

1. Copy `values/strings.xml` to `values-<language>/strings.xml` and translate every entry; keep keys, placeholders,
   `formatted` and `translatable` attributes, and plural/array shapes. French-style languages may need an extra
   plural quantity; follow the language's CLDR rules rather than copying English.
2. Add the language to `AppLocale.SUPPORTED`/`CHOICES`, to `res/xml/locales_config.xml`, and to the Settings card if it
   should be offered. Keep English as the base and the fallback.
3. Add date/number cases to `FmtTest` and a launch case to `LocaleSwitchTest`.
4. Run `:app:testGithubDebugUnitTest :app:testStoreDebugUnitTest`, `:app:lintGithubDebug :app:lintStoreDebug` and
   `:core:test`. Do not send personal data, screenshots or health values to a translation service; only public UI
   strings belong in a translation pass.

The app language is stored as `app_language` (`system`, `en`, `nl`, `de`, `fr`) and is independent of the older
`report_language` setting. Existing installs keep their report choice; an install without `app_language` follows the
phone.
