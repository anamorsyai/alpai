# AlpAI — smooth Android AI agent client

AlpAI is a smooth, Muse-style Android AI agent client: chat with bubbles
(user right / AI left), streaming replies, providers & models, and a light
local terminal. Built with Kotlin + Jetpack Compose (Material 3).

> v0.1 scaffold — the UI and the data layer are real; the terminal shell
> and a few extras are clearly-marked stubs. See "What's real / what's a
> placeholder" below.

## Screens

- **Chats** — chat list with search, pin-free for now, delete, and a `+`
  button for a new chat.
- **Chat** — smooth bubbles, live streaming, stop / regenerate, a model
  picker in the top bar, and a `+` sheet: Files, Photo from gallery,
  Camera, Voice (coming soon). Attachments show as chips; sending file
  *contents* to the model is on the roadmap.
- **Providers & Models** — add / edit / delete any OpenAI-compatible
  provider (name + base URL + API key), enable / disable it, and add
  custom model IDs. Presets ship for OpenAI, OpenRouter and a no-key
  Demo provider.
- **Terminal** — light local terminal UI (stub interpreter: `help`,
  `echo`, `date`, `clear`). The real shell is the next step — see below.
- **Settings** — theme (system / light / dark), font size, streaming
  toggle, default model, clear all chats, export (coming soon).

## How to add a provider

1. Open **Providers** → `+`.
2. Name it, paste the base URL (e.g. `https://api.openai.com/v1` or
   `https://openrouter.ai/api/v1`), paste your API key.
3. Pick it in the chat top bar, then pick a model — or add a custom
   model ID from the provider card (`+ Add custom model`).

No key? The **Demo (no key needed)** provider answers out of the box
so you can try the whole UI first.

## What's real / what's a placeholder

| Area | Status |
|---|---|
| Chat UI, bubbles, streaming, stop/regenerate | Real |
| Room storage for chats & messages | Real |
| Settings in DataStore (theme, font, streaming, default model) | Real |
| OpenAI-compatible client (OkHttp SSE, `/chat/completions`) | Real |
| Demo provider (no key, no network) | Real |
| Providers add/edit/delete, custom models | Real UI; storage is in-memory in v0.1 (keys are NOT persisted yet) |
| File/photo/camera pickers | Real pickers; file *content* is not sent to the model yet |
| Voice input, chat export | Placeholder (toast + TODO) |
| Terminal shell / PTY | Stub UI + local echo commands only |

## The terminal plan (next step)

Port AlpDroid's PTY session + Alpine rootfs approach into a
`:core-terminal` module here — see the long comment at the top of
`ui/terminal/TerminalScreen.kt` for the exact 4 steps. The UI already
speaks in lines + input, so only the backend changes.

## Build

```sh
gradle assembleDebug testDebugUnitTest
# or, once the real wrapper is generated locally:
./gradlew assembleDebug
```

CI (`.github/workflows/android.yml`) builds the debug APK and runs the
unit tests on every push / PR to `main`, and uploads the APK as an
artifact.

- `compileSdk` / `targetSdk` 34, `minSdk` 26
- Package: `com.anamorsyai.alpai`
- Kotlin 2.0.21, Compose BOM 2024.09.00, Room 2.6.1, DataStore 1.1.1

## Roadmap

- Real terminal: AlpDroid PTY + Alpine, tabs, extra-keys row
- Encrypted storage for API keys (Encrypted DataStore) + Room persistence for providers
- File attachment parsing (text / PDF / images) sent with the message
- Voice input & TTS, chat export (Markdown/JSON), agent presets in the UI
