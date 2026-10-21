# NotiVault

Android app that keeps a private, encrypted record of every notification you receive, so messages the
sender later deletes ("delete for everyone") stay on your phone, marked **Recovered**.

## How it works
- `NotificationListenerService` (`capture/NotiListenerService.kt`) receives each posted notification.
- `NotificationExtractor` turns it into a framework-free `RawNotification`; `NotificationParser` splits
  `MessagingStyle` histories into individual messages and drops noise ("5 new messages", calls, progress).
- Dedup: exact SHA-256 content hash, plus count-based history dedup for apps that re-stamp their history
  (`DedupEngine.unseenFromHistory`).
- Per-app profiles (`capture/apps/AppProfiles.kt`) for 27 messaging and social apps: WhatsApp (+Business), Telegram
  (+forks), Signal/Molly, Messenger, Instagram, Threads, Facebook, X, Snapchat, Discord, Slack, Teams, Google
  Messages, Samsung Messages, Viber, LINE, WeChat, Skype, imo, KakaoTalk, LinkedIn, TikTok, Reddit, Google Chat,
  Element, Threema and AOSP SMS. Each profile describes the app's title format ("Ada @ Squad", "Squad: Ada"),
  whether senders are prefixed in the body ("Ada: hi"), its noise (typing indicators, sync banners) and how it
  signals deletions.
- Two deletion signals:
  - **Placeholder:** `DeletionDetector` recognises "This message was deleted", "unsent a message",
    "撤回了一条消息" and similar placeholders in several languages, then flags the original. The placeholder
    itself is never stored.
  - **History removal:** Telegram, Signal, Discord and similar apps silently drop a deleted message from the
    re-posted history. `HistoryDiff` compares consecutive payloads and ignores normal front-trimming and edits.
- `MessageKind` classifies photos, voice notes, videos, stickers, GIFs, documents, locations, contacts, polls,
  missed calls and reactions.
- Storage: Room + SQLCipher with a random 256-bit raw key (no per-connection KDF), wrapped by an Android
  Keystore AES-GCM key and opened lazily off the main thread. Photo previews are encrypted with the same
  Keystore key. **No INTERNET permission.**

## Features
**New:** media vault (keeps chat photos, videos and voice notes after "delete for everyone"), "possibly
unsent" detection, chat transcript export, Quick Settings pause tile, capture help. See
[docs/ROADMAP.md](docs/ROADMAP.md) for the research behind them and what's next.

Timeline with app/recovered/date filters · per-chat bubble view · full-text search (FTS4) with highlights ·
paper-style receipts exportable as PNG/PDF with SHA-256 · per-app capture toggles · PIN + biometric lock with
auto-lock · retention policy (daily WorkManager job) · password-encrypted `.nvault` backup/restore ·
recovery alerts · listener health banner + auto-rebind on boot · `FLAG_SECURE` in release builds.

## Design system ("Vault")
`ui/theme/` holds tokens (Palette, spacing, radii, motion, type) and `ui/components/` the component library.
Amber is reserved for "recovered". `PaletteContrastTest` enforces WCAG AA on every text pairing.

- **Type:** Inter for UI and JetBrains Mono for receipts, timestamps and hashes. Both are bundled as variable
  fonts (`res/font`, SIL OFL, see `licenses/`) and also used in exported PNG/PDF receipts.
- **Morphs** (`ui/motion/`):
  - container transforms: a message card or chat bubble morphs into its receipt (Compose shared bounds)
  - the avatar and title fly from the chat list into the thread header
  - `graphics-shapes` polygon morphs: PIN keys spring from a circle into a scalloped cookie when pressed,
    onboarding hero tiles breathe between shapes, and the PIN-setup badge morphs on confirm
  - the padlock shackle springs open on unlock
  - stat counters roll

## Limits (Android platform)
Only what the notification shows is captured: muted chats and anything received before install are missed,
and media is a preview only. Android only; iOS does not allow reading other apps' notifications.

## Settings worth knowing
- *Messaging & social apps only*: records only apps that have a profile.
- Apps screen: per-app switches, grouped into Messaging & social and Other, with each app's deletion-detection
  method shown.

## Media vault
In the Media tab, pick WhatsApp's (or Telegram's) media folder. Android opens it directly; tap **Use this
folder**. NotiVault gets read-only access to that folder only. New files are copied into the vault,
encrypted with the Keystore key, every 15 minutes and shortly after a media message arrives. When the chat
app removes a file (delete for everyone), the vault keeps its copy and marks it **Deleted by sender**.
Files over 40 MB are recorded as metadata only.

## Privacy & Play
See [PRIVACY.md](PRIVACY.md). The app shows a prominent disclosure before notification access, uses
narrow package visibility (no `QUERY_ALL_PACKAGES`), and has no INTERNET permission.

## Releases & signing
Copy `keystore.properties.example` to `keystore.properties` (git-ignored) to sign locally. CI signs
`v*` tags with repository secrets `NOTIVAULT_KEYSTORE_BASE64`, `NOTIVAULT_KEYSTORE_PASSWORD`,
`NOTIVAULT_KEY_ALIAS` and `NOTIVAULT_KEY_PASSWORD`, then publishes the APK and AAB to GitHub Releases.

## Build & test
```
./gradlew testDebugUnitTest      # parser, dedup, deletion, removal heuristic, media rules, crypto, contrast
./gradlew verifyPaparazziDebug   # screenshot tests (light, dark, 1.6× font)
./gradlew connectedDebugAndroidTest  # migrations v1→v3, encryption at rest, Keystore (needs a device)
./gradlew installDebug
adb shell cmd notification allow_listener com.notivault/com.notivault.capture.NotiListenerService
adb shell "cmd notification post -S messaging --conversation 'Squad' --message 'Ada:hi' sq x"
```

## License
Code is MIT-licensed (see `LICENSE`). Bundled Inter and JetBrains Mono fonts are under the SIL Open Font License (see `licenses/`).
