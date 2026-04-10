# Компоненты управления картами и навигацией

## Обзор

Данный документ описывает новые UI-компоненты для управления офлайн-картами, точками (waypoints) и путями (tracks) в приложении Meshtastic, разработанные на основе анализа функциональности ZOV Карты.

## Новые компоненты

### 1. LayerManager.kt - Менеджер слоев карты

**Назначение:** Управление видимостью и порядком отображения слоев карты (точки, треки, узлы, пользовательские слои, источники тайлов).

#### Основные функции:
- `LayerManagerSheet` - модальное окно со списком всех слоев
- `QuickLayerToggle` - быстрые переключатели слоев в виде чипсов
- `LayerListItem` - элемент списка слоя с элементами управления

#### Возможности:
- ✅ Включение/выключение видимости слоев (Switch)
- ✅ Изменение порядка слоев (drag & handle)
- ✅ Удаление слоев с подтверждением
- ✅ Цветовая индикация слоев
- ✅ Отображение количества объектов в слое
- ✅ Быстрые переключатели для часто используемых слоев

#### Структура данных:
```kotlin
data class MapLayerItem(
    val id: String,
    val name: String,
    val isVisible: Boolean,
    val layerType: LayerType, // WAYPOINTS, TRACKS, NODES, CUSTOM, TILE_SOURCE
    val itemCount: Int = 0,
    val color: Color = Color.Unspecified,
)
```

---

### 2. WaypointManager.kt - Менеджер точек и треков

**Назначение:** Полный цикл управления точками (waypoints) и путями (tracks): создание, редактирование, импорт/экспорт, навигация, передача через Meshtastic.

#### Компоненты:

##### A. WaypointManagerSheet
Модальное окно для управления всеми точками.

**Функции:**
- Просмотр списка всех сохраненных точек
- Создание новой точки
- Редактирование существующих точек
- Удаление точек с подтверждением
- Импорт GPX файлов
- Экспорт GPX файлов
- Передача точек через Meshtastic
- Детальный просмотр информации о точке

**Элементы интерфейса:**
- Карточки точек с иконкой, названием, описанием, координатами
- Индикация расстояния и азимута от текущей позиции
- Кнопки действий: поделиться, удалить, редактировать
- Диалог детального просмотра с навигацией

##### B. WaypointCard
Карточка отдельной точки для отображения в списке.

**Отображаемая информация:**
- Иконка (emoji)
- Название и описание
- Координаты (широта, долгота)
- Расстояние от текущей позиции
- Кнопки быстрых действий

##### C. TrackControlPanel
Панель управления записью треков.

**Функции:**
- Старт/стоп записи трека
- Отображение статистики в реальном времени:
  - Количество точек
  - Пройденная дистанция
  - Название трека
- Сохранение записанного трека
- Отмена/удаление трека
- Загрузка существующих треков

**Режимы работы:**
- **Запись:** Красный индикатор, кнопки "Отмена" и "Сохранить"
- **Просмотр:** Кнопка "Загрузить" для импорта треков

#### Структура данных:
```kotlin
data class WaypointListItem(
    val id: Int,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val icon: Int,
    val expire: Int,
    val lockedTo: Int,
    val distance: Float? = null, // расстояние в метрах
    val bearing: Float? = null,   // азимут в градусах
)
```

---

## Интеграция с Meshtastic

### Передача точек через Meshtastic

Компоненты поддерживают передачу точек через протокол Meshtastic:

```kotlin
// Пример передачи точки
onWaypointShared = { waypoint ->
    meshService.sendWaypoint(waypoint)
}
```

**Формат Waypoint:**
```kotlin
data class Waypoint(
    val id: Int,
    val name: String,
    val description: String,
    val latitude_i: Int, // умноженные на 1e7
    val longitude_i: Int,
    val icon: Int,
    val expire: Int,
    val locked_to: Int,
)
```

---

## Работа с офлайн-картами

### Поддерживаемые форматы:
- **MBTiles** - основной формат для офлайн-карт
- **Кэш OSMdroid** - автоматическое кэширование просмотренных тайлов
- **GPX** - импорт/экспорт треков и точек
- **KML/KMZ** - планируется
- **GeoJSON** - планируется

### Функции кеширования:
- Загрузка регионов по выбору пользователя
- Управление размером кэша
- Очистка кэша
- Просмотр статистики кэша
- Создание MBTiles из кэша

---

## Использование в приложении

### Пример интеграции в MapView:

```kotlin
@Composable
fun MapScreen(viewModel: MapViewModel) {
    var showLayerManager by remember { mutableStateOf(false) }
    var showWaypointManager by remember { mutableStateOf(false) }
    
    // Слои карты
    val layers = listOf(
        MapLayerItem("waypoints", "Точки", true, LayerType.WAYPOINTS, 15, Color.Blue),
        MapLayerItem("tracks", "Треки", true, LayerType.TRACKS, 3, Color.Green),
        MapLayerItem("nodes", "Узлы", true, LayerType.NODES, 42, Color.Red),
    )
    
    // Точки
    val waypoints = viewModel.waypoints.collectAsState().value.map { it.toListItem() }
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showWaypointManager = true }) {
                Icon(Icons.Default.PinDrop, "Точки")
            }
        }
    ) { padding ->
        // Карта
        MapView(modifier = Modifier.padding(padding))
        
        // Менеджер слоев
        if (showLayerManager) {
            LayerManagerSheet(
                layers = layers,
                onLayerVisibilityChanged = { id, visible -> 
                    viewModel.toggleLayer(id, visible) 
                },
                onDismissRequest = { showLayerManager = false }
            )
        }
        
        // Менеджер точек
        if (showWaypointManager) {
            WaypointManagerSheet(
                waypoints = waypoints,
                onWaypointShared = { waypoint -> 
                    viewModel.sendWaypoint(waypoint) 
                },
                onCreateNewWaypoint = { 
                    viewModel.createWaypointAtCurrentLocation() 
                },
                onDismissRequest = { showWaypointManager = false }
            )
        }
    }
}
```

---

## Стилистика и UX

### Дизайн-система:
- Material Design 3
- Адаптивная темная/светлая тема
- Закругленные углы (12-16dp)
- Полупрозрачные фоны для карточек
- Цветовая кодировка типов объектов

### UX решения:
- Модальные окна снизу (bottom sheets) для удобства использования одной рукой
- Haptic feedback при важных действиях
- Подтверждение деструктивных операций
- Визуальная обратная связь состояний
- Минималистичные иконки и понятные лейблы

---

## План дальнейшей разработки

### Ближайшие улучшения:
1. [ ] Drag & drop для изменения порядка слоев
2. [ ] Массовое выделение и операции с точками
3. [ ] Поиск и фильтрация точек
4. [ ] Группировка точек по категориям
5. [ ] Рисование маршрутов на карте
6. [ ] Измерение расстояний и площадей
7. [ ] Экспорт в KML/KMZ
8. [ ] Интеграция с матрицами высот
9. [ ] Расчет зон видимости
10. [ ] Навигация по азимуту к точке

### Интеграция с Meshtastic:
- [ ] Синхронизация точек между устройствами
- [ ] Получение точек от других пользователей
- [ ] Отображение путей других участников сети
- [ ] Коллективное редактирование карт

---

## Совместимость

- **Минимальная версия Android:** 9.0 (API 28)
- **Библиотеки:**
  - OSMDroid 6.1.x+
  - OSMBonusPack 6.9.0+
  - Jetpack Compose
  - Material Components 1.9+

---

## Лицензия

GNU General Public License v3.0
