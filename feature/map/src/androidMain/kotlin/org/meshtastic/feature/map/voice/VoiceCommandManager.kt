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

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Менеджер голосового ввода на основе Vosk API.
 * Поддерживает распознавание команд для управления картой, точками и путями.
 *
 * Основные команды:
 * - "Создать точку" / "Новая точка" - создание waypoint
 * - "Удалить точку" - удаление выбранной точки
 * - "Начать трек" / "Записать трек" - начало записи трека
 * - "Остановить трек" / "Закончить трек" - остановка записи
 * - "Передать точку" / "Отправить точку" - передача через Meshtastic
 * - "Масштаб +" / "Приблизить" - zoom in
 * - "Масштаб -" / "Отдалить" - zoom out
 * - "Север" / "Юг" / "Запад" / "Восток" - навигация по карте
 */
class VoiceCommandManager(
    private val context: Context,
    private val onCommandRecognized: (VoiceCommand) -> Unit
) {
    
    private var recognizer: Recognizer? = null
    private var isInitialized = false
    
    /**
     * Инициализация Vosk и загрузка модели распознавания.
     * Модель должна быть размещена в assets/vosk-model/.
     */
    fun initialize(modelPath: String = "vosk-model-ru") {
        if (isInitialized) return
        
        try {
            // Инициализация библиотеки Vosk
            LibVosk.setLogLevel(LogLevel.INFO)
            
            // Копирование модели из assets во внутреннюю память
            val modelDir = File(context.filesDir, "vosk-model")
            if (!modelDir.exists()) {
                extractModelFromAssets(modelPath, modelDir)
            }
            
            // Создание распознавателя
            recognizer = Recognizer(modelDir.absolutePath, 16000f)
            isInitialized = true
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Извлечение модели из assets во внутреннюю память устройства.
     */
    private fun extractModelFromAssets(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return
        
        for (file in files) {
            val assetFile = "$assetPath/$file"
            val targetFile = File(targetDir, file)
            
            if (assetManager.list(assetFile)?.isNotEmpty() == true) {
                // Это директория - рекурсивное копирование
                extractModelFromAssets(assetFile, targetFile)
            } else {
                // Это файл - копирование
                assetManager.open(assetFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    
    /**
     * Обработка аудио данных для распознавания.
     * Вызывается при поступлении новых аудиоданных с микрофона.
     */
    fun processAudioData(audioData: ByteArray): Boolean {
        if (!isInitialized || recognizer == null) return false
        
        return try {
            if (recognizer!!.acceptWaveForm(audioData, audioData.size)) {
                val result = recognizer!!.result
                parseCommand(result)
                true
            } else {
                val partialResult = recognizer!!.partialResult
                // Можно обрабатывать частичные результаты для UI обратной связи
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Завершение распознавания и получение финального результата.
     */
    fun finishRecognition() {
        if (!isInitialized || recognizer == null) return
        
        try {
            val finalResult = recognizer!!.finalResult
            parseCommand(finalResult)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Парсинг распознанного текста и преобразование в команду.
     */
    private fun parseCommand(result: String) {
        val text = extractTextFromResult(result).lowercase().trim()
        
        val command = when {
            text.contains("создать точку") || text.contains("новая точка") || 
            text.contains("добавить точку") || text.contains("поставить точку") ->
                VoiceCommand.CREATE_WAYPOINT
            
            text.contains("удалить точку") || text.contains("убрать точку") ->
                VoiceCommand.DELETE_WAYPOINT
            
            text.contains("начать трек") || text.contains("записать трек") ||
            text.contains("старт трек") || text.contains("включить трек") ->
                VoiceCommand.START_TRACK
            
            text.contains("остановить трек") || text.contains("закончить трек") ||
            text.contains("стоп трек") || text.contains("выключить трек") ->
                VoiceCommand.STOP_TRACK
            
            text.contains("передать точку") || text.contains("отправить точку") ||
            text.contains("послать точку") ->
                VoiceCommand.SEND_WAYPOINT
            
            text.contains("масштаб плюс") || text.contains("масштаб +") ||
            text.contains("приблизить") || text.contains("увеличить") ->
                VoiceCommand.ZOOM_IN
            
            text.contains("масштаб минус") || text.contains("масштаб -") ||
            text.contains("отдалить") || text.contains("уменьшить") ->
                VoiceCommand.ZOOM_OUT
            
            text.contains("север") || text.contains("вверх") ->
                VoiceCommand.MOVE_NORTH
            
            text.contains("юг") || text.contains("вниз") ->
                VoiceCommand.MOVE_SOUTH
            
            text.contains("запад") || text.contains("влево") ->
                VoiceCommand.MOVE_WEST
            
            text.contains("восток") || text.contains("вправо") ->
                VoiceCommand.MOVE_EAST
            
            text.contains("помощь") || text.contains("команды") ->
                VoiceCommand.SHOW_HELP
            
            else -> null
        }
        
        command?.let { onCommandRecognized(it) }
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
            
            jsonResult.substring(quoteStart + 1, quoteEnd)
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Освобождение ресурсов.
     */
    fun release() {
        recognizer?.close()
        recognizer = null
        isInitialized = false
    }
}

/**
 * Перечисление поддерживаемых голосовых команд.
 */
enum class VoiceCommand {
    CREATE_WAYPOINT,    // Создать точку
    DELETE_WAYPOINT,    // Удалить точку
    START_TRACK,        // Начать запись трека
    STOP_TRACK,         // Остановить запись трека
    SEND_WAYPOINT,      // Передать точку через Meshtastic
    ZOOM_IN,            // Приблизить карту
    ZOOM_OUT,           // Отдалить карту
    MOVE_NORTH,         // Переместить карту на север
    MOVE_SOUTH,         // Переместить карту на юг
    MOVE_WEST,          // Переместить карту на запад
    MOVE_EAST,          // Переместить карту на восток
    SHOW_HELP           // Показать список команд
}
