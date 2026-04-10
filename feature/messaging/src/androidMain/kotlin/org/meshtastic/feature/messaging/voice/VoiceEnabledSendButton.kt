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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream

/**
 * Компонент кнопки отправки сообщения с поддержкой голосового ввода.
 * 
 * Логика работы:
 * - Если текст в поле ввода есть - кнопка отправляет сообщение
 * - Если текст пустой - кнопка активирует голосовой ввод для диктовки текста
 * 
 * @param hasText Есть ли текст в поле ввода
 * @param isRecording Состояние записи голоса
 * @param onSendMessage Callback для отправки текстового сообщения
 * @param onStartVoiceInput Callback для запуска голосового ввода
 * @param modifier Modifier для компонента
 */
@Composable
fun VoiceEnabledSendButton(
    hasText: Boolean,
    isRecording: Boolean,
    onSendMessage: () -> Unit,
    onStartVoiceInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = {
                scope.launch {
                    if (hasText) {
                        // Если есть текст - отправляем сообщение
                        onSendMessage()
                    } else {
                        // Если текста нет - запускаем голосовой ввод
                        onStartVoiceInput()
                    }
                }
            },
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else if (hasText) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isRecording) {
                MaterialTheme.colorScheme.onError
            } else if (hasText) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            shape = CircleShape
        ) {
            Icon(
                imageVector = when {
                    isRecording -> Icons.Default.MicOff
                    hasText -> Icons.Default.Send
                    else -> Icons.Default.Mic
                },
                contentDescription = when {
                    isRecording -> "Остановить голосовой ввод"
                    hasText -> "Отправить сообщение"
                    else -> "Голосовой ввод текста"
                },
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Индикатор активной записи (пульсирующий круг)
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Менеджер голосового ввода для диктовки текста сообщений.
 * Использует Vosk для распознавания речи.
 */
class VoiceInputManager(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit
) {
    private var recognizer: Recognizer? = null
    private var isInitialized = false
    private var isRecording = false
    
    /**
     * Инициализация Vosk и загрузка модели распознавания.
     */
    fun initialize(modelPath: String = "vosk-model-ru") {
        if (isInitialized) return
        
        try {
            LibVosk.setLogLevel(LogLevel.INFO)
            
            val modelDir = File(context.filesDir, "vosk-model")
            if (!modelDir.exists()) {
                extractModelFromAssets(modelPath, modelDir)
            }
            
            // Для диктовки текста используем более широкую грамматику
            recognizer = Recognizer(modelDir.absolutePath, 16000f)
            isInitialized = true
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Извлечение модели из assets.
     */
    private fun extractModelFromAssets(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return
        
        for (file in files) {
            val assetFile = "$assetPath/$file"
            val targetFile = File(targetDir, file)
            
            if (assetManager.list(assetFile)?.isNotEmpty() == true) {
                extractModelFromAssets(assetFile, targetFile)
            } else {
                assetManager.open(assetFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    
    /**
     * Обработка аудио данных.
     */
    fun processAudioData(audioData: ByteArray): Boolean {
        if (!isInitialized || recognizer == null) return false
        
        return try {
            if (recognizer!!.acceptWaveForm(audioData, audioData.size)) {
                val result = recognizer!!.result
                val text = extractTextFromResult(result)
                if (text.isNotEmpty()) {
                    onTextRecognized(text)
                }
                true
            } else {
                recognizer!!.partialResult
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Завершение распознавания.
     */
    fun finishRecognition() {
        if (!isInitialized || recognizer == null) return
        
        try {
            val finalResult = recognizer!!.finalResult
            val text = extractTextFromResult(finalResult)
            if (text.isNotEmpty()) {
                onTextRecognized(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Извлечение текста из JSON результата Vosk.
     */
    private fun extractTextFromResult(jsonResult: String): String {
        return try {
            val startIndex = jsonResult.indexOf("\"text\"")
            if (startIndex == -1) return ""
            
            val colonIndex = jsonResult.indexOf(':', startIndex)
            if (colonIndex == -1) return ""
            
            val quoteStart = jsonResult.indexOf('"', colonIndex + 1)
            if (quoteStart == -1) return ""
            
            val quoteEnd = jsonResult.indexOf('"', quoteStart + 1)
            if (quoteEnd == -1) return ""
            
            jsonResult.substring(quoteStart + 1, quoteEnd).trim()
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Начать запись.
     */
    fun startRecording() {
        if (!isInitialized) initialize()
        isRecording = true
        recognizer?.reset()
    }
    
    /**
     * Остановить запись.
     */
    fun stopRecording() {
        isRecording = false
        finishRecognition()
    }
    
    /**
     * Проверка состояния записи.
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Освобождение ресурсов.
     */
    fun release() {
        recognizer?.close()
        recognizer = null
        isInitialized = false
        isRecording = false
    }
}
