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
package org.meshtastic.app.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.layers
import org.meshtastic.core.resources.visible

/**
 * Модель слоя карты для управления видимостью и порядком отображения
 */
data class MapLayerItem(
    val id: String,
    val name: String,
    val isVisible: Boolean,
    val layerType: LayerType,
    val itemCount: Int = 0,
    val color: Color = Color.Unspecified,
)

enum class LayerType {
    WAYPOINTS,
    TRACKS,
    NODES,
    CUSTOM,
    TILE_SOURCE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerManagerSheet(
    layers: List<MapLayerItem>,
    onLayerVisibilityChanged: (String, Boolean) -> Unit,
    onLayerReordered: (Int, Int) -> Unit,
    onLayerDeleted: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var showDeleteDialog by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.layers),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(layers, key = { it.id }) { layer ->
                    LayerListItem(
                        layer = layer,
                        onVisibilityChanged = { onLayerVisibilityChanged(layer.id, it) },
                        onEditClick = { /* TODO: Open layer settings */ },
                        onDeleteClick = { showDeleteDialog = layer.id },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { layerId ->
        AlertDialog(
            title = "Удалить слой?",
            message = "Вы уверены, что хотите удалить этот слой?",
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onLayerDeleted(layerId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteDialog = null }
                ) {
                    Text("Отмена")
                }
            },
            onDismissRequest = { showDeleteDialog = null }
        )
    }
}

@Composable
private fun LayerListItem(
    layer: MapLayerItem,
    onVisibilityChanged: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Переместить",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Layer color indicator
                if (layer.color != Color.Unspecified) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(layer.color, RoundedCornerShape(4.dp))
                            .padding(end = 8.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Layer info
                Column {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "${layer.layerType} • ${layer.itemCount} объектов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = layer.isVisible,
                    onCheckedChange = onVisibilityChanged,
                )

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLayerToggle(
    layers: List<MapLayerItem>,
    onLayerToggled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        layers.forEach { layer ->
            LayerChip(
                layer = layer,
                onToggle = { onLayerToggled(layer.id, !layer.isVisible) },
            )
        }
    }
}

@Composable
private fun LayerChip(
    layer: MapLayerItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (layer.isVisible) {
        layer.color.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (layer.color != Color.Unspecified) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(layer.color, RoundedCornerShape(4.dp)),
                )
            }

            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (layer.isVisible) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (layer.isVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
