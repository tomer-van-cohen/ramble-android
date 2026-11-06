# Recording sources settings

The settings page has two matching switches (Voice memos and Phone calls) and one folder list. Each persisted tree grant is shown once, including the default Recordings parent and any additional folders. Every row has the same Change folder / Remove menu. Folder access errors appear on the affected folder row.

Switching a category off preserves its folder grants, cutoffs and baseline observations. Switching it back on reuses the existing permission. Ordinary custom audio folders belong to Voice memos; native Call/call_rec folder paths belong to Phone calls. A known recorder parent scans voice and call branches separately, so disabling Phone calls cannot be bypassed by its generic parent source.

Removing a folder disables all source members under that grant, records its removal durably, and releases the app's persisted read grant. It never deletes the folder or audio. Removal prevents background recovery from reconnecting the folder. Explicitly selecting the folder again reconnects its known source members. Changing a grouped parent updates its members together and retires the previous grant.

A Samsung Recordings or Xiaomi MIUI/sound_recorder parent covers a call folder that has not been created yet. A known call source scans the Call/call_rec branch when it appears; parent voice files are not treated as calls. Existing disabled call sources remain disabled.

Validation covers independent switches, preservation of off-state folders and baselines, removal of the default parent alongside an additional folder, app recreation after removal, explicit reconnection, replacement of a grouped parent, partial grants, late child creation and custom call folders. Tests run against disposable emulator document fixtures. This does not establish phone-call recording availability on every model or region.

Onboarding stays on Find your recordings after any successful grant, including when both sources are connected. Users can review, change or add folders and must tap Continue to advance. The previous delayed automatic transition has been removed.
