/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.feature.map.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Сервис записи аудио с микрофона для голосового ввода.
 * Работает в фоновом режиме и передает аудиоданные в VoiceCommandManager.
 */
class AudioRecordingService(
    private val context: Context,
    private val onAudioDataReceived: (ByteArray) -> Unit
) {
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Проверка наличия разрешения на запись аудио.
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Начало записи аудио.
     */
    fun startRecording() {
        if (isRecording || !hasRecordAudioPermission()) {
            Timber.w("Recording already started or no permission")
            return
        }
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            // Запуск coroutine для чтения аудиоданных
            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize / 2) // 16-bit = 2 bytes per sample
                
                while (isActive && isRecording) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    
                    if (readSize > 0) {
                        // Отправка аудиоданных для обработки
                        onAudioDataReceived(buffer.copyOf(readSize))
                    } else if (readSize == AudioRecord.ERROR_INVALID_OPERATION ||
                               readSize == AudioRecord.ERROR_BAD_VALUE) {
                        Timber.e("Error reading audio data: $readSize")
                        break
                    }
                }
            }
            
            Timber.d("Audio recording started")
            
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception while starting recording")
        } catch (e: Exception) {
            Timber.e(e, "Error starting audio recording")
        }
    }
    
    /**
     * Остановка записи аудио.
     */
    fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio recording")
        } finally {
            audioRecord = null
        }
        
        Timber.d("Audio recording stopped")
    }
    
    /**
     * Проверка состояния записи.
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Освобождение ресурсов.
     */
    fun release() {
        stopRecording()
        scope.cancel()
    }
}
