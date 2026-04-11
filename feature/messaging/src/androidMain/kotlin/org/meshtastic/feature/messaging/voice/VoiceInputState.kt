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

package org.meshtastic.feature.messaging.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние голосового ввода для экрана сообщений.
 * Управляет жизненным циклом VoiceInputManager и AudioRecordingService.
 */
class VoiceInputState(
    private val voiceInputManager: VoiceInputManager,
    private val audioRecordingService: AudioRecordingService,
    private val onTextRecognized: (String) -> Unit
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    /**
     * Переключение состояния записи.
     */
    fun toggleRecording(): Boolean {
        return if (_isRecording.value) {
            stopRecording()
            false
        } else {
            startRecording()
            true
        }
    }
    
    /**
     * Начало записи.
     */
    private fun startRecording(): Boolean {
        if (!audioRecordingService.hasPermission()) {
            return false
        }
        
        voiceInputManager.startRecording()
        val started = audioRecordingService.startRecording()
        
        if (started) {
            _isRecording.value = true
        } else {
            voiceInputManager.stopRecording()
        }
        
        return started
    }
    
    /**
     * Остановка записи.
     */
    private fun stopRecording() {
        _isRecording.value = false
        audioRecordingService.stopRecording()
        voiceInputManager.stopRecording()
    }
    
    /**
     * Инициализация.
     */
    fun initialize() {
        voiceInputManager.initialize()
    }
    
    /**
     * Освобождение ресурсов.
     */
    fun release() {
        stopRecording()
        voiceInputManager.release()
        audioRecordingService.release()
    }
}

/**
 * Composable функция для создания и управления VoiceInputState.
 * 
 * @param context Android Context
 * @param onTextRecognized Callback с распознанным текстом
 * @return VoiceInputState для управления голосовым вводом
 */
@Composable
fun rememberVoiceInputState(
    context: android.content.Context,
    onTextRecognized: (String) -> Unit
): VoiceInputState {
    val state = remember {
        val voiceInputManager = VoiceInputManager(context) { text ->
            onTextRecognized(text)
        }
        
        val audioRecordingService = AudioRecordingService(context) { audioData ->
            voiceInputManager.processAudioData(audioData)
        }
        
        VoiceInputState(voiceInputManager, audioRecordingService, onTextRecognized)
    }
    
    // Инициализация при первом запуске
    LaunchedEffect(Unit) {
        state.initialize()
    }
    
    // Очистка при уничтожении
    DisposableEffect(Unit) {
        onDispose {
            state.release()
        }
    }
    
    return state
}
