# AI in OpenConnect: Claude, Gemini and the coach

OpenConnect can use an AI in two places: the **reports** (one per activity, one per week) and, since v0.5.0, the
**Coach** (a chat about your own numbers). Both work offline without any key, with plain rules, and both can use
**Claude** (Anthropic) or **Gemini** (Google) when you add your own key.

## Setting it up

1. Open **Settings**. Under *AI: reports and coach* choose who writes:
   - **Automatic**: Claude if a Claude key is saved, otherwise Gemini if a Gemini key is saved, otherwise offline.
   - **Offline only**: nothing ever leaves the phone.
   - **Claude** or **Gemini**: that one, and offline again if it has no key.
2. In the card of the provider you want, tap *Open Claude key page* or *Open Gemini key page*, make **your own key**
   there, copy it and paste it into the field, then *Save*. The app asks the provider whether the key is accepted
   and shows the answer in words (accepted, refused, or the provider could not be reached). *Check key* asks again.
3. Optionally pick a model (suggestions are listed) and the language of reports and coach answers (English or Dutch).

The key is **only ever typed into the app**. It is stored in Android Keystore-backed encrypted preferences. If a phone
cannot do that, the key is not stored at all, never in plain text. It is sent only to its own provider, in a header
(`x-api-key` for Claude, `x-goog-api-key` for Gemini), never in a web address, and error messages shown in the app
have the key replaced by `***`. Nothing about keys is written to logs, the repository or the APK. If a key was ever
pasted somewhere it should not be, delete it at the provider and make a new one.

## What is sent

Reports and the Coach send **numbers, not tracks**: a JSON digest of the activity or of the last four weeks (daily
steps, sleep, resting heart rate, Body Battery, stress, HRV, the readiness score, the week totals, and a list of at most
30 recent activities with name, sport, distance, duration and heart rate). There is **no GPS** and no coordinates.
The digest is put in a block marked as data, and the instructions tell the model to treat anything in it (such as an
activity name from an imported file) as data and never as instructions. The coach is told to give general guidance, to
say when the data is too thin to answer, and not to give medical advice or diagnoses.

## Models

| Provider | Default | Others offered | Notes |
|----------|---------|----------------|-------|
| Claude | `claude-opus-5-5` | `claude-sonnet-5-5`, `claude-haiku-5-5` | Messages API. Extended thinking is not asked for; reports are short. |
| Gemini | `gemini-3.6-flash` | `gemini-3.8-flash`, `gemini-3.5-flash-lite` | `generateContent` on `generativelanguage.googleapis.com`. Thought parts are skipped, only the answer is shown. A generous output limit leaves room for thinking models. |

Model names change at the providers. The model field is free text: type any name your key can use. Reports are cached
per input, provider and model, so unchanged data is not written (and billed) twice.

## The Coach

*Insights, then Coach.* Ask anything about sleep, recovery and training; the first questions are offered as buttons.
The conversation stays in memory only: closing the app or tapping *New chat* ends it, and an answer that arrives
after *New chat* is dropped. One question at a time. Without a key the screen explains how to add one.

## How it is built

| Where | What |
|-------|------|
| `core/.../reports/Providers.kt` | `ReportProvider` (`generate`, `answer`, `checkKey`), `AnthropicReportProvider`, `GeminiReportProvider`, `AiVendor` (names, default model, key page) |
| `core/.../reports/Prompts.kt` | the report and coach instructions, turn normalisation (a chat must alternate user and model turns) |
| `core/.../reports/Facts.kt`, `Coach.kt` | `coachFacts` (the digest) and `CoachService` |
| `app/.../ai/AiController.kt` | keys, checks, choice, language, model, and which provider is active (no Android types) |
| `app/.../ai/CoachController.kt` | the conversation, one question at a time |
| `app/.../data/SettingsStore.kt` | encrypted key storage; the v0.1 to v0.4 Claude key keeps its name, so an earlier key is still found |

## What has and has not been verified

- Tested with fakes: both request shapes (including that the key is in a header and not in the address), the answers,
  refused keys (Claude 401, Gemini 400 `API_KEY_INVALID`), rate limits, blocked or empty answers, the coach's turn
  handling, the digest size and its contents (no GPS), and the screens.
- **Not tried live:** no real Claude or Gemini key was used in this release, so the real services have not answered the
  app yet. The Claude request is the one used since v0.1.0; the Gemini request follows Google's documented
  `generateContent` shape. If a real answer ever looks wrong, the *Check key* message and the model name are the first
  things to look at.
