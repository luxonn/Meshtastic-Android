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

package org.meshtastic.feature.map

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.model.DataPacket
import org.meshtastic.feature.map.voice.AudioRecordingService
import org.meshtastic.feature.map.voice.VoiceCommand
import org.meshtastic.feature.map.voice.VoiceCommandManager
import org.meshtastic.proto.Waypoint

/**
 * Расширение BaseMapViewModel для поддержки голосового ввода.
 * Может быть добавлено в любой ViewModel наследующий BaseMapViewModel.
 */
interface VoiceInputSupport {
    val isVoiceRecording: StateFlow<Boolean>
    val lastVoiceCommand: StateFlow<VoiceCommand?>
    
    fun initializeVoiceCommands(context: Context)
    fun toggleVoiceRecording()
    fun releaseVoiceResources()
}

/**
 * Миксин для добавления функциональности голосового ввода в MapViewModel.
 * Использование: внедрить в ViewModel через делегирование или композицию.
 */
class VoiceInputMixin(
    private val viewModel: BaseMapViewModel,
    private val context: Context
) : VoiceInputSupport {
    
    private var voiceCommandManager: VoiceCommandManager? = null
    private var audioRecordingService: AudioRecordingService? = null
    
    private val _isVoiceRecording = MutableStateFlow(false)
    override val isVoiceRecording: StateFlow<Boolean> = _isVoiceRecording.asStateFlow()
    
    private val _lastVoiceCommand = MutableStateFlow<VoiceCommand?>(null)
    override val lastVoiceCommand: StateFlow<VoiceCommand?> = _lastVoiceCommand.asStateFlow()
    
    /**
     * Инициализация компонентов голосового ввода.
     */
    override fun initializeVoiceCommands(context: Context) {
        voiceCommandManager = VoiceCommandManager(context) { command ->
            handleVoiceCommand(command)
            _lastVoiceCommand.value = command
        }
        
        audioRecordingService = AudioRecordingService(context) { audioData ->
            voiceCommandManager?.processAudioData(audioData)
        }
        
        voiceCommandManager?.initialize()
    }
    
    /**
     * Переключение состояния записи голоса.
     */
    override fun toggleVoiceRecording() {
        if (_isVoiceRecording.value) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }
    
    /**
     * Обработка распознанной голосовой команды.
     */
    private fun handleVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.CREATE_WAYPOINT -> createWaypointAtCenter()
            VoiceCommand.DELETE_WAYPOINT -> deleteSelectedWaypoint()
            VoiceCommand.START_TRACK -> startTrackRecording()
            VoiceCommand.STOP_TRACK -> stopTrackRecording()
            VoiceCommand.SEND_WAYPOINT -> sendSelectedWaypointViaMesh()
            VoiceCommand.ZOOM_IN -> zoomIn()
            VoiceCommand.ZOOM_OUT -> zoomOut()
            VoiceCommand.MOVE_NORTH -> moveMap(0.01, 0.0)
            VoiceCommand.MOVE_SOUTH -> moveMap(-0.01, 0.0)
            VoiceCommand.MOVE_WEST -> moveMap(0.0, -0.01)
            VoiceCommand.MOVE_EAST -> moveMap(0.0, 0.01)
            VoiceCommand.SHOW_HELP -> showHelpDialog()
        }
    }
    
    /**
     * Создание точки в центре карты.
     */
    private fun createWaypointAtCenter() {
        // Получаем текущий центр карты из предпочтений
        val centerLat = viewModel.mapPrefs.mapCenterLatitude.value
        val centerLon = viewModel.mapPrefs.mapCenterLongitude.value
        
        val waypoint = Waypoint.newBuilder().apply {
            latitudeI = (centerLat * 1e7).toInt()
            longitudeI = (centerLon * 1e7).toInt()
            id = System.currentTimeMillis().toInt()
            name = "Голосовая точка"
        }.build()
        
        viewModel.sendWaypoint(waypoint)
    }
    
    /**
     * Удаление выбранной точки.
     */
    private fun deleteSelectedWaypoint() {
        // Предполагается, что есть выбранный waypoint
        // Реализация зависит от конкретного UI
        viewModel.waypoints.value.values.firstOrNull()?.let { packet ->
            packet.waypoint?.id?.let { id ->
                viewModel.deleteWaypoint(id)
            }
        }
    }
    
    /**
     * Начало записи трека.
     */
    private fun startTrackRecording() {
        // Интеграция с существующей системой треков
        // Реализация зависит от конкретной архитектуры
    }
    
    /**
     * Остановка записи трека.
     */
    private fun stopTrackRecording() {
        // Интеграция с существующей системой треков
    }
    
    /**
     * Отправка выбранной точки через Meshtastic.
     */
    private fun sendSelectedWaypointViaMesh() {
        viewModel.waypoints.value.values.firstOrNull()?.let { packet ->
            packet.waypoint?.let { wpt ->
                viewModel.sendWaypoint(wpt)
            }
        }
    }
    
    /**
     * Приблизить карту.
     */
    private fun zoomIn() {
        // Обновление масштаба карты
        val currentZoom = viewModel.mapPrefs.mapZoom.value
        viewModel.mapPrefs.setMapZoom(currentZoom * 1.2f)
    }
    
    /**
     * Отдалить карту.
     */
    private fun zoomOut() {
        // Обновление масштаба карты
        val currentZoom = viewModel.mapPrefs.mapZoom.value
        viewModel.mapPrefs.setMapZoom(currentZoom / 1.2f)
    }
    
    /**
     * Перемещение карты.
     */
    private fun moveMap(deltaLat: Double, deltaLon: Double) {
        val currentLat = viewModel.mapPrefs.mapCenterLatitude.value
        val currentLon = viewModel.mapPrefs.mapCenterLongitude.value
        
        viewModel.mapPrefs.setMapCenterLatitude(currentLat + deltaLat)
        viewModel.mapPrefs.setMapCenterLongitude(currentLon + deltaLon)
    }
    
    /**
     * Показать диалог помощи.
     */
    private fun showHelpDialog() {
        // Показываем список доступных команд
        // Реализация через UI event
    }
    
    /**
     * Начало записи аудио.
     */
    private fun startVoiceRecording() {
        if (audioRecordingService?.hasRecordAudioPermission() == true) {
            audioRecordingService?.startRecording()
            _isVoiceRecording.value = true
        } else {
            // Запрос разрешения - должен быть обработан в Activity/Fragment
            // requestAudioPermissionLauncher.launch()
        }
    }
    
    /**
     * Остановка записи аудио.
     */
    private fun stopVoiceRecording() {
        audioRecordingService?.stopRecording()
        voiceCommandManager?.finishRecognition()
        _isVoiceRecording.value = false
    }
    
    /**
     * Освобождение ресурсов.
     */
    override fun releaseVoiceResources() {
        audioRecordingService?.release()
        voiceCommandManager?.release()
        audioRecordingService = null
        voiceCommandManager = null
    }
}
