package com.casualchess.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class SoundCue { MOVE, CAPTURE, GAME_END }

/** Plays the three short bundled effects (see tools/gen_sounds.py). Silent if a sound has not finished loading. */
class SoundPlayer(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<SoundCue, Int> = mapOf(
        SoundCue.MOVE to pool.load(context, R.raw.move, 1),
        SoundCue.CAPTURE to pool.load(context, R.raw.capture, 1),
        SoundCue.GAME_END to pool.load(context, R.raw.game_end, 1),
    )

    fun play(cue: SoundCue) {
        ids[cue]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
