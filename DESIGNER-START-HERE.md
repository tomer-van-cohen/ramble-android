# Ramble — designer assignment

Design a delightful, calm, minimal native Android experience using the existing Ramble design
system and logo. This is a design assignment, not authorization to implement an Android app,
create a hosted service, deploy, publish a repository or change an existing product.

## Read in this order

1. `docs/PRODUCT.md`: agreed scope, constraints and exact approved Hebrew/English agent prompt.
2. `docs/UX-HANDOFF.md`: screen-by-screen behavior, recovery states, accessibility and validation gates.
3. `docs/design/ramble-reference-tokens.json` and `docs/design/assets/`: authoritative brand references.
4. `design/user-flow.html` and `design/first-screen.png`, if present: earlier exploratory references,
   not the final specification. The written requirements take precedence.

## Product in one paragraph

People keep recording with Samsung's built-in recorder and, on supported devices, the built-in
phone-call recorder. Ramble processes only new completed recording files with the user's
Groq or OpenAI key, saves a local text file and optionally exports the text to their Google Drive.
There is no Ramble login/backend, desktop dependency or in-app AI chat. Their existing assistants
read the exported transcripts through their own authorized Drive connections.

## Required flow

1. Logo and short introduction integrated with provider/key entry; no unnecessary welcome screen.
2. Detected recording source with contextual access approval; optional independent call-recording source.
3. Automatic local destination; connect Google Drive or continue locally. Automatically create/reuse
   the Ramble Drive folder after consent. No output-folder configuration in normal onboarding.
4. Ready screen with a concise summary, bilingual agent-instruction copy component when Drive is
   connected, and a compact optional invitation to configure quick recording using Samsung's Side
   button. Include a short illustrated guide and an honest "Open recorder" action; opening the native
   recorder is not a promise to start recording. Finish is always available without this optional step.
5. Home: processing status, empty state and chronological transcript/job list.
6. Transcript: readable text, copy, share and verified Drive link. No chat or generated summary.
7. Settings and focused repair flows, preserving queued work and completed local transcripts.

## Design deliverables

- A navigable mobile prototype covering the full flow and local-only/Drive branches.
- Light and dark variants, Hebrew RTL, English prompt tab, readable large-font states and Android
  touch targets of at least 48dp. Do not design an iPhone shell or redraw the Ramble logo.
- Reusable components and token mapping, plus concise copy and interaction annotations.
- Missing/invalid API key, offline, quota, denied/revoked source access, storage full, cancelled/revoked
  Drive authorization, pending upload and paused states. Clearly distinguish Android/Google system
  surfaces from screens we own.
- First-recording guidance, including unavailable recorder/shortcut fallback. Persist activation
  before opening the recorder so that this first recording is not excluded as historical.
- A list of assumptions requiring engineering/device validation. Never represent mocked permission,
  authentication or shortcut configuration as a proven integration.

Background processing is scheduled approximately every 15 minutes, best effort; Android can delay
it further. No permanent foreground watcher or mandatory persistent notification is planned.
The UI must not promise immediate transcription or invisible unlimited background execution.

Keep onboarding short, with defaults instead of configuration. The user should understand when
setup is complete, where files go and how to use them with their existing assistant.

## Competitive context

Recly is a close existing open-source product with personal API keys and local/Drive storage:
https://recly.dev/ and https://github.com/rokrokss/recly . Its documented recording flow uses its
own app/watch. Automatic ingestion of native Samsung memo/call recordings and Hebrew quality have
not been validated in our research. Do not claim exclusivity or copy its identity. The assignment
remains the agreed Ramble design; a decision to reuse another project's code is outside its scope.
