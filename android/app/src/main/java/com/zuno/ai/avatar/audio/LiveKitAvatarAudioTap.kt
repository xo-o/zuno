package com.zuno.ai.avatar.audio

import io.livekit.android.room.track.RemoteAudioTrack
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer
import java.util.IdentityHashMap

/**
 * Attaches an [AudioTrackSink] to LiveKit [RemoteAudioTrack] instances so decoded tutor PCM
 * streams into [AvatarSpeechSink] without altering WebRTC audio routing, AEC, or mute state.
 */
class LiveKitAvatarAudioTap(
    private val speechSink: AvatarSpeechSink,
) {
    private val lock = Any()
    private val tracked = IdentityHashMap<RemoteAudioTrack, AudioTrackSink>()

    fun syncRemoteTracks(tracks: Collection<RemoteAudioTrack>) {
        synchronized(lock) {
            // Detach tracks no longer present
            val iterator = tracked.entries.iterator()
            while (iterator.hasNext()) {
                val (track, sink) = iterator.next()
                if (tracks.none { it === track }) {
                    runCatching { track.removeSink(sink) }
                    iterator.remove()
                }
            }

            // Attach newly subscribed tracks
            for (track in tracks) {
                if (!tracked.containsKey(track)) {
                    val sink = AudioTrackSink { audioData, bitsPerSample, sampleRate, channels, frameCount, _ ->
                        speechSink.pushPcm16(
                            audioData = audioData,
                            bitsPerSample = bitsPerSample,
                            sampleRate = sampleRate,
                            channels = channels,
                            frameCount = frameCount,
                        )
                    }
                    runCatching {
                        track.addSink(sink)
                        tracked[track] = sink
                    }
                }
            }
        }
    }

    fun detachAll() {
        synchronized(lock) {
            for ((track, sink) in tracked) {
                runCatching { track.removeSink(sink) }
            }
            tracked.clear()
        }
        speechSink.reset()
    }
}
