# NotiVault: research & roadmap

_Researched 21 Oct 2026. Sources are listed at the end. Store listings and blog posts are marketing material, so
treat them as signals, not facts._

## What the market looks like
| App | Approach | What users get | Weak spots |
|---|---|---|---|
| **WAMR** (market leader, 10M+ users per 2020 coverage) | Notification log **plus** saving WhatsApp's media files | Deleted text **and** deleted photos, videos and voice notes | Ads/paywall (≈$9.99/mo, $33.99 lifetime on iOS), WhatsApp-centric, unofficial |
| **Notisave** | General notification log | Grouping, hiding notifications | Old UI; battery listed as a downside |
| **Notification History Log** | Notification log + search | Search, ad-supported | Ads |
| **Notification History (svprdga)** | Local log | Per-app exclusion, auto-delete, local-only storage | Basic |
| **NotificationLog** (F-Droid) | Open source, local log | Privacy, search, export | Minimal features |
| Android 11+ built-in "Notification history" | System | Free, no install | Only 24 h, no search, no deletion marking |

**Common user complaints:** messages missing because chats were muted, the chat was open on screen,
notifications were off or previews hidden. Nothing from before install. Some apps want accessibility
permission, which is a privacy red flag. Many are ad-heavy.

**Platform/policy facts that shape the design:**
- Google Play treats the installed-app list as sensitive. `QUERY_ALL_PACKAGES` is limited to approved use
  cases and needs a declaration form. This app now uses a narrow `<queries>` launcher filter instead.
- Sensitive data access must be core to the app and come with a **prominent in-app disclosure** before the
  permission prompt, plus a privacy policy and Data Safety form. The disclosure dialog and `PRIVACY.md` are
  now in place.
- Android 11+ scoped storage: no broad file access. A user-granted Storage Access Framework folder is the
  sanctioned route to chat media folders. Granting `Android/media/com.whatsapp/...` was verified on an
  API 36 emulator.

## Where NotiVault stands out
1. **Privacy you can verify**: no INTERNET permission, SQLCipher plus Keystore, open source. No competitor
   offers all three.
2. **Deletion intelligence**: placeholder detection, history-removal diffing and a "possibly unsent"
   heuristic. Most loggers show raw history and leave users to spot deletions themselves.
3. **Receipts**: hash-stamped PNG/PDF exports and transcripts, useful for disputes.
4. **No ads, no accessibility permission.**

## Status
### ✅ Built (this release)
- Notification capture, 27 app profiles, dedup, placeholder and history-removal deletion detection
- **Media vault**: encrypted copies of chat media (via SAF folder grant). Files the sender deletes are flagged.
  This closes the main gap with WAMR without broad storage access.
- "Possibly unsent" heuristic for apps that cancel the notification instead of editing it
- Receipts (PNG/PDF), chat transcript export, encrypted `.nvault` backups
- Pause tile in Quick Settings, messaging-only mode, per-app exclusion, retention
- "Why are some messages missing?" help for the top user complaint
- Prominent disclosure, privacy policy, no `QUERY_ALL_PACKAGES`
- App lock (PIN/biometric), `FLAG_SECURE` in release, accessibility semantics (TalkBack summaries, headings)
- CI: unit, screenshot (Paparazzi) and instrumented tests; signed tag releases

### 🔜 Next (high value, feasible)
1. **Link media to messages.** Match vault files to "📷 Photo" messages by app and time, then show the real
   photo inside the chat bubble and on the receipt.
2. **Keyword and contact alerts.** Notify on deleted messages from specific people only, or on keywords.
3. **Home-screen widget.** Shows the latest recovered messages.
4. **Statistics.** Who deletes most, deletions per app, timeline chart.
5. **Encrypted auto-backup to a user-chosen folder** (SAF, scheduled). This stays offline and gives
   device-loss protection.
6. **Per-chat retention and pinning.** Keep important chats forever and expire the rest.
7. **Localization** of the UI (en, fr, es, pt, yo, ha, ig, sw…), with native-speaker-verified deletion
   strings.

### 🧪 Needs real-device validation before claiming
- Parsing on real WhatsApp, Telegram, Instagram and Signal builds, including their localized placeholders
- Media folder layouts on Samsung, Xiaomi, Oppo and Pixel, plus WhatsApp's per-version folder changes
- Background survival on aggressive OEMs, and battery cost of 15-minute media scans

### ⛔ Deliberately not building
- **Accessibility-service scraping** of chat screens. It is invasive, a Play policy risk, and fragile.
- **Cloud sync or accounts.** They break the core privacy promise.
- **Reading other people's devices / "monitoring".** This is a spyware use case.
- **Status/story saver.** It is outside the "receipts of what was sent to me" scope and a WhatsApp ToS risk.

## Sources
- WAMR store listings: https://apps.apple.com/vc/app/wamr-recover-deleted-messages/id6736562521 ·
  https://mwm.ai/apps/wamr-recover-deleted-messages/6736562521
- WAMR coverage: https://yourstory.com/2020/04/app-recovering-deleted-messages-whatsapp-wamr ·
  https://en.androidayuda.com/applications/recommended/wamr-recovers-deleted-whatsapp-messages/
- Roundups: https://techzox.in/best-apps-to-read-deleted-whatsapp-messages-2026/ ·
  https://steptechglobal.com/what-apps-recover-deleted-messages-best-tools-for-android-iphone-in-2026/
- Notification History (svprdga): https://play.google.com/store/apps/details?id=com.svprdga.notificationhistory
- Notification History Log: https://play.google.com/store/apps/details?id=com.ikvaesolutions.notificationhistorylog
- Alternatives: https://alternativeto.net/software/notification-history-log
- Notisave review: https://mobilesyrup.com/2019/04/21/drowning-in-notifications-notisave-app-android-review/
- Play QUERY_ALL_PACKAGES policy: https://support.google.com/googleplay/android-developer/answer/10158779
- Play user data / prominent disclosure: https://support.google.com/googleplay/android-developer/answer/10144311 ·
  https://support.google.com/googleplay/android-developer/answer/11150561
- Android 11 storage: https://developer.android.com/about/versions/11/privacy/storage
