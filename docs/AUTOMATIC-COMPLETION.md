# Automatic recording completion — v0.2

Normal supported recordings do not require a completion button. WorkManager discovers finalized AAC/M4A and sends it automatically with the selected user key. Its periodic trigger is best effort (15-minute minimum, possibly delayed by Android); two observations at least 60 seconds apart are required. No permanent foreground notification is added.

## Evidence and bounds

The bounded pure `FinalizedMp4` parser rejects open-ended/truncated boxes and fragmented containers. It requires finite ftyp/mdat/moov structure, positive mvhd/mdhd durations, exactly one mp4a audio track, consistent sample counts/time/chunk mapping, and every sample offset and size within mdat. It hashes the final index with actual descriptor length. Two matching observations plus stable metadata and a valid post-activation recording date automatically queue the job. A private snapshot must match that proof and the source is checked again before processing. Unsupported, incomplete or undated files remain waiting without upload. An exceptional uncertain provider outcome still requires explicit retry approval to avoid duplicate billing.

Samsung's document metadata may report fewer bytes than the actual stream. Snapshotting uses the descriptor's actual size and EOF, with budget limits, so a trailing final index is not silently omitted. We never edit the original.

Android's writer fixes mdat length and emits its final moov index during stop. A real Android MediaCodec AAC/MediaMuxer test probes the still-open file repeatedly (rejected), stops the muxer (accepted), performs two scans and executes the real worker with a test-only fake provider. It publishes one real Downloads transcript and a repeated worker does not resend. The test also advertises a deliberately stale shorter SAF size.

This demonstrates the Android AAC/MP4 path. It is not physical Samsung certification: OEM modes may differ or publish a finalized segment during pause. Test long pause/resume, locked screen, calls/hold/mute and battery behavior on a real Samsung before broad stable claims. Formats other than supported AAC/M4A wait rather than infer completion from size stability. MP4 timestamp fallback is labeled estimated and conservatively subtracts duration; recognized filename timestamps are preferred. There is no general guarantee that a copied file with rewritten metadata retains its original recording date.

## Source references

- [AOSP MPEG4Writer](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/libstagefright/MPEG4Writer.cpp): reset/stop writes final index and fixes media size; structural checks derive from this implementation, not a universal SAF completion event.
- [Samsung SAF size report](https://github.com/tailscale/tailscale/issues/20585): motivates testing actual descriptor length rather than trusting reported size. This is an external device report, not our own Samsung measurement.

## Compatibility

VersionCode 2, Room schema 2. Migration adds completion evidence fields and returns old unbilled confirmation candidates to automatic waiting. It preserves source cutoff, baseline, completed jobs and provider attempt history. Unknown-outcome retries remain protected.
