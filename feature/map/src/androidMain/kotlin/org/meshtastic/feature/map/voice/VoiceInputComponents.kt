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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Компонент кнопки голосового ввода для карты.
 * Отображает состояние записи и позволяет управлять голосовым вводом.
 *
 * @param isRecording Текущее состояние записи
 * @param onToggleVoiceInput Callback для переключения состояния записи
 * @param modifier Modifier для компонента
 */
@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    onToggleVoiceInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = modifier
            .size(56.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = {
                scope.launch {
                    onToggleVoiceInput()
                }
            },
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isRecording) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isRecording) {
                    Icons.Default.MicOff
                } else {
                    Icons.Default.Mic
                },
                contentDescription = if (isRecording) {
                    "Остановить голосовой ввод"
                } else {
                    "Начать голосовой ввод"
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
 * Компонент отображения статуса голосового ввода.
 * Показывает текущее состояние и последнюю распознанную команду.
 *
 * @param isRecording Состояние записи
 * @param lastCommand Последняя распознанная команда
 * @param modifier Modifier для компонента
 */
@Composable
fun VoiceInputStatus(
    isRecording: Boolean,
    lastCommand: VoiceCommand?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Голосовой ввод",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                AnimatedVisibility(visible = isRecording) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color.Red,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "Запись...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (lastCommand != null) {
                Text(
                    text = "Последняя команда: ${getCommandDisplayName(lastCommand)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isRecording) {
                        "Произнесите команду..."
                    } else {
                        "Нажмите на микрофон для активации голосового ввода"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Список доступных команд
            if (!isRecording) {
                Text(
                    text = "Доступные команды:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CommandHint("Создать точку", "Добавить waypoint на карту")
                    CommandHint("Начать трек", "Записать GPS трек")
                    CommandHint("Передать точку", "Отправить точку через Meshtastic")
                    CommandHint("Масштаб +/-", "Приблизить/отдалить карту")
                    CommandHint("Север/Юг/Запад/Восток", "Перемещение по карте")
                }
            }
        }
    }
}

/**
 * Компонент подсказки для команды.
 */
@Composable
private fun CommandHint(command: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$command - ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Получение отображаемого имени для команды.
 */
private fun getCommandDisplayName(command: VoiceCommand): String {
    return when (command) {
        VoiceCommand.CREATE_WAYPOINT -> "Создать точку"
        VoiceCommand.DELETE_WAYPOINT -> "Удалить точку"
        VoiceCommand.START_TRACK -> "Начать трек"
        VoiceCommand.STOP_TRACK -> "Остановить трек"
        VoiceCommand.SEND_WAYPOINT -> "Передать точку"
        VoiceCommand.ZOOM_IN -> "Приблизить"
        VoiceCommand.ZOOM_OUT -> "Отдалить"
        VoiceCommand.MOVE_NORTH -> "Север"
        VoiceCommand.MOVE_SOUTH -> "Юг"
        VoiceCommand.MOVE_WEST -> "Запад"
        VoiceCommand.MOVE_EAST -> "Восток"
        VoiceCommand.SHOW_HELP -> "Помощь"
    }
}
