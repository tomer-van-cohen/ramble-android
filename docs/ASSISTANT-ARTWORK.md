# Assistant artwork

Product marks identify examples of assistants a user can give instructions to. They do not imply a dedicated integration or endorsement. Assets are bundled locally; loading this screen does not contact assistant vendors.

2026-11-05 source references:

- ChatGPT: exact blossom path from https://chatgpt.com/cdn/assets/favicon.6a33781f61.svg, bundled as a tight native vector with transparent background and light/dark monochrome variants.
- Claude: original path and color from https://claude.com/icon.svg, converted directly to Android VectorDrawable.
- Gemini: https://www.gstatic.com/lamda/images/gemini_sparkle_4g_512_lt_f94943af3be039176192d.png, referenced by https://gemini.google.com/.
- Grok Bot: product mark paths from the official https://x.ai/bot hero, retained as vector artwork with monochrome fill suitable for the light background. Original shape is preserved.
- Instinct: the user-selected serif I mark supplied in the 2026-11-05 screenshot. Reproduced as a native VectorDrawable, replacing the stick-figure website favicon. Background is transparent, with separate light/dark foreground variants.
- Muse: `app/src/main/res/drawable-nodpi/agent_muse.png`, transparent cutout derived from the existing supplied icon using the built-in image generation editor. Product reference https://ai.meta.com/muse/. The original tile and shadow are removed.

The visible prompt uses a short linked folder label. Copy always includes the complete Google Drive folder URL. Language tabs use the Material primary tab component, explicitly ordered English on the left and Hebrew on the right in either interface direction. The selected label uses the primary text color; the inactive label uses the muted text color. Next is independent of clipboard state and leads to the first-recording step.


## Muse image-edit prompt

Built-in image generation tool, background-extraction edit. Final prompt: "Change ONLY the alpha mask: remove every stray disconnected blue/cyan speck and all fuzzy halos around the main M silhouette. Preserve the exact main M shape and blue gradient. The boundary must be clean, smooth antialiased vector-like edges. Everything outside the single solid M silhouette must be fully transparent. No backgrounds, outlines, tiles, shadows, or shapes." The resulting alpha-bearing PNG is bundled in drawable-nodpi to prevent density expansion. No API-key CLI was used.
