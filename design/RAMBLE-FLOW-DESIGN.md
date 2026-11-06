# Ramble — design spec for the interactive prototype

Companion to [`prototype.html`](prototype.html). Open it in a browser: the left/right panels jump to
every screen and state, switch light/dark and Android font scale (100–200%), and set the simulated
outcome of each external step (key check, folder detection, call recordings, Drive folder creation,
Side-button support). Behavior notes and a "senior user" check appear next to each screen.

The prototype makes no network calls, stores no secrets and uses synthetic content. The key field is
a simulated paste; the Drive folder URL is an example ID. Android, Google and Samsung screens are
approximations, labeled blue in the harness, and are not designed by us.

`design/user-flow.html` remains an earlier sketch; this prototype supersedes its screen split.

## Design principles applied

- One goal and one primary action per screen; at most two action buttons. When a step needed more,
  it was split (provider → key → key help; sources → calls; save → agent instructions; quick record
  → guide → self-check).
- Buttons stack full-width at the bottom: primary (brand green fill, ink text) above an outlined
  secondary. Minimum height 56dp / 52dp, scales with font size.
- Body text 18sp, titles 28sp, supporting text 15–16sp: one step larger than the handoff's 16sp body,
  for older readers. Layout verified at 200% font scale without horizontal overflow.
- Every result is stated in words next to the control that caused it, and scrolled into view.
  Success uses a large check plus "המשך"; errors use a short reason plus one fix, keeping progress.
- Address form: Hebrew plural ("לחצו", "שלכם"), gender-neutral. The handoff headline
  "הקלט כרגיל…" was adapted to "הקליטו כרגיל. התמלול יגיע לתיקייה שלכם." for consistency.
- "עוזר ה-AI" is used in UI copy instead of "אייג׳נט" (plainer for non-technical users). The
  approved prompt text itself is unchanged.

## Flow

```text
Setup (4 steps, progress bar, back on every step, resumable)
 1 Provider (Groq preselected) → API key ─(help)→ Key help → provider site (browser)
 2 Recorder access → [Android folder picker + allow] → Granted
   → Phone calls? (optional; yes → picker; no / absent → continue)
 3 Saved on phone (automatic) + Connect Drive? ─ yes → [Google account + consent] → Connecting → Connected
                                              └ no  → local only (skips agent step)
   → Agent instructions (Drive only): עברית / English tabs, Copy
 4 Quick record invitation → How to set up (model-specific) → [Samsung Settings] → Self-check
   (unsupported device → short fallback)
 Done: "הכול מוכן" summary → Finish → Home   |   Open recorder → [Samsung Voice Recorder]

Home: status card (one action max) + transcript list
  → Transcript (copy / share; "open in Drive" link only after verified upload)
  → Waiting item → explanation sheet (no partial text)
  → Settings: provider, sources, local folder, Drive, agent instructions, background switch
  → Status action → focused repair → back to Home with queue intact
```

## Screen inventory

| Screen | Primary | Secondary | Notes |
| --- | --- | --- | --- |
| Provider | המשך | — | Logo + one-line intro; no splash. Groq tagged "מומלץ". |
| API key | בדיקת המפתח → המשך | link: איך משיגים מפתח? | In-field "הדבקה". States: missing, checking, ok, invalid, offline. Privacy line always visible. |
| Key help | פתיחת האתר של … | חזרה | 3 numbered steps; free-tier note without promises. |
| Recorder access | מתן גישה / בחירת התיקייה / לנסות שוב | — | Detected path shown; numbered "in the next Android window" steps. |
| Phone calls | כן, להוסיף שיחות | לא, תודה | Absent → explanation + המשך. Denied → continue without calls. |
| Save | חיבור Google Drive | לשמור רק בטלפון | Local save shown as already done. Cancel / error keep both choices. |
| Connecting Drive | — | — | Our progress screen after Google consent; auto-advances. |
| Agent instructions | העתקת ההנחיות → המשך | דילוג → להעתיק שוב | Approved copy, verbatim; URL isolated LTR; remembers language. |
| Quick record | כך מגדירים | דילוג | Own illustration; side key on physical right edge (not mirrored). |
| How to set up | פתיחת ההגדרות | דילוג | Device chip (model · One UI). Never claims the mapping was changed. |
| Self-check | המשך | פתיחת ההגדרות שוב | User verifies by double-pressing. |
| Done | סיום | פתיחת המקליט | Original wave, once. "Approximately every 15 minutes, sometimes longer." |
| Home | (status action, if any) | — | Empty state: פתיחת המקליט (opens Samsung app; not a record button). |
| Transcript | העתקת הטקסט | שיתוף | Saved-location strip; Drive link only when uploaded. |
| Settings | — | — | 5 navigation rows + background switch. No account row. |
| Repair: quota | הבנתי | החלפת ספק | Nothing to do; auto-retry. |
| Repair: storage | פתיחת ניהול האחסון | — | Re-check on return. |
| Repair: key / access / Drive | reuse the setup screen in "repair" mode with a banner | | Return to Home with a snackbar; queue retained. |

## Status card mapping (Home)

| State | Tone · icon | Action |
| --- | --- | --- |
| Active / empty | ok · check | — |
| Waiting for processing | info · hourglass | — (explains next check) |
| Offline | warn · wifi-off | לבדוק שוב עכשיו (optional; auto-resumes) |
| Invalid key | rec · key | תיקון המפתח → key (repair) |
| Provider limit | warn · hourglass | מה אפשר לעשות? → explanation |
| Missing access | rec · folder | מתן גישה מחדש → picker; baseline preserved |
| Storage full | rec · storage | איך מפנים מקום → explanation → system storage |
| Upload pending | info · cloud-up | — (already saved locally; auto-retry) |
| Drive disconnected | warn · cloud-off | חיבור מחדש → Google consent |
| Paused | neutral · pause | המשך תמלול |

Colour is never the only signal: each state has an icon and a written title. Item chips: מחכה לתמלול,
מתמלל עכשיו, נשמר בטלפון, נשמר ב-Drive, נשמר בטלפון · מחכה להעלאה, מחכה · מושהה.

## Components

Top app bar (back at start edge / brand + settings) · Stepper ("שלב n מתוך 4 · name" + 4 bars) ·
Bottom action area (primary, secondary) · Link button · Choice card (radio) · Key field with paste ·
Notice (ok / info / warn / rec / neutral) · Info card with badge · Numbered instruction list ·
Success hero (ring + title) · Summary list · Prompt language tabs + prompt panel · Status card ·
List item with state chip · Settings row / switch row · Bottom sheet · Confirm dialog · Snackbar
(positioned above the action area) · System-surface frame.

## Token mapping (Android)

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| brand.green | #25D366 | #25D366 | Primary button fill, logo, wave. Never text. |
| brand.ink | #121212 | #121212 | Text on brand green. |
| bg / surface / surface2 | #F4F6F4 / #FFFFFF / #EDF0EE | #1C1E1D / #262928 / #303433 | Canvas, cards, insets & tabs track. |
| border | #D6DBD8 | #3C413F | Hairlines; strong border #AEB6B2 / #5A615E for secondary buttons and fields (added for ≥3:1 control contrast). |
| text / text2 / text3 | #151817 / #5B625F / #6F7774 | #F1F3F2 / #ABB2AF / #8E9693 | Body / supporting / placeholder. |
| accent | #0F7A3E | #3DDC84 | Links, selection, done icons, focus ring. |
| rec / warn / info | #C8231F / #8A5200 / #0B62C4 | #FF6B61 / #FFB340 / #6CB4FF | Error, attention, in-progress text and icons. |
| fill.* | ok/info/warn/rec/neutral | dark equivalents | Status cards, notices, chips. |
| Radius | buttons 16, cards 20, dialogs 28, sheets 28 top | | Android-adapted (desktop 6–12 was too tight for 56dp controls). |
| Spacing | 4dp grid, 20dp page inset, 12–16dp gaps | | |
| Type | Noto Sans Hebrew / Roboto (system on device) | | 28/18/15–16sp; respects font scale. |
| Motion | standard easing 200ms; wave draw 600ms once | | Reduced motion: no wave draw, no spinner rotation, no snackbar slide. |

## Assumptions requiring engineering / device validation

1. Samsung recorder paths (`Recordings/Voice Recorder`, `Recordings/Call`) and whether the SAF picker
   accepts them as an initial location on the target One UI versions.
2. Exact Hebrew labels in the Android folder-access window ("שימוש בתיקייה הזו", "לאפשר"); copy in our
   instructions must match the device.
3. Samsung Settings path and Hebrew labels for Side button → Double press → Voice Recorder → Start
   recording, per model / One UI / region; lock-screen behavior; whether a deep link exists.
4. Detecting device model and One UI version for the guide chip; fallback copy when unknown.
5. Local output at `Download/Ramble` via MediaStore.Downloads: MIME, relative path, visibility in
   "My Files", behavior after reinstall.
6. Google scope `drive.file` is enough to create/reuse the app folder and upload text; consent copy and
   app-verification requirements.
7. A low-cost, audio-free key check exists for both Groq and OpenAI.
8. Background cadence (~15 min, best effort) and the honesty of "last check" timestamps after Doze,
   force-stop and reboot.
9. Whether any long transcription needs a foreground notification; if so, design its copy truthfully.
10. Baseline persistence: activation timestamp is stored at grant time (before the recorder can be
    opened) and survives repair, so no historical recording is billed.
11. Call recording availability per region/model; empty-folder vs. unsupported cannot always be
    distinguished, hence the neutral "not found" copy.
12. Storage-full detection and recovery without losing completed text (private staging copy).
