package dev.isearch.tracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log

/** Loud looping alarm on the alarm stream at max volume. The original volume is put back on stop. */
class PhoneAlarm(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var savedVolume = -1

    val isPlaying: Boolean get() = player != null

    fun start(): Boolean {
        if (player != null) {
            return true
        }
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            p.setDataSource(context, uri)
            p.isLooping = true
            p.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "player error $what/$extra")
                stop()
                true
            }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            Log.e(TAG, "alarm start failed", e)
            p.release()
            putVolumeBack()
            false
        }
    }

    fun stop() {
        val p = player ?: return
        player = null
        try {
            p.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop failed", e)
        }
        p.release()
        putVolumeBack()
    }

    private fun putVolumeBack() {
        if (savedVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            savedVolume = -1
        }
    }

    private companion object {
        const val TAG = "TrackerAlarm"
    }
}
