# Голосовой ввод на основе Vosk

## Обзор

Добавлена поддержка голосового управления для приложения Meshtastic на базе движка распознавания речи **Vosk**. Это позволяет управлять картой, создавать точки и треки, а также передавать данные через Meshtastic с помощью голосовых команд.

## Компоненты

### 1. VoiceCommandManager
Основной класс для распознавания и обработки голосовых команд.

**Расположение:** `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/voice/VoiceCommandManager.kt`

**Основные функции:**
- Инициализация модели распознавания
- Обработка аудиопотока в реальном времени
- Распознавание команд на русском языке
- Преобразование речи в команды приложения

### 2. AudioRecordingService
Сервис для записи аудио с микрофона устройства.

**Расположение:** `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/voice/AudioRecordingService.kt`

**Основные функции:**
- Запись аудио с частотой 16kHz
- Проверка разрешений на запись
- Потоковая передача аудиоданных
- Управление ресурсами

### 3. UI Компоненты
Готовые Compose компоненты для интеграции в интерфейс.

**Расположение:** `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/voice/VoiceInputComponents.kt`

**Компоненты:**
- `VoiceInputButton` - кнопка включения/выключения голосового ввода
- `VoiceInputStatus` - панель статуса с подсказками
- `CommandHint` - отображение доступных команд

## Поддерживаемые команды

| Команда | Действие |
|---------|----------|
| "Создать точку" / "Новая точка" | Создание waypoint на карте |
| "Удалить точку" | Удаление выбранной точки |
| "Начать трек" / "Записать трек" | Начало записи GPS трека |
| "Остановить трек" / "Закончить трек" | Остановка записи трека |
| "Передать точку" / "Отправить точку" | Передача точки через Meshtastic |
| "Масштаб +" / "Приблизить" | Zoom in |
| "Масштаб -" / "Отдалить" | Zoom out |
| "Север" / "Вверх" | Перемещение карты на север |
| "Юг" / "Вниз" | Перемещение карты на юг |
| "Запад" / "Влево" | Перемещение карты на запад |
| "Восток" / "Вправо" | Перемещение карты на восток |
| "Помощь" / "Команды" | Показать список команд |

## Установка

### 1. Добавление зависимости

Зависимость Vosk уже добавлена в `gradle/libs.versions.toml`:

```toml
vosk = "0.3.45"
# ...
vosk = { module = "com.alphacephei:vosk-android", version.ref = "vosk" }
```

И в `feature/map/build.gradle.kts`:

```kotlin
androidMain.dependencies {
    implementation(libs.vosk)
}
```

### 2. Загрузка модели распознавания

Необходимо загрузить русскую языковую модель Vosk:

1. Скачайте модель с https://alphacephei.com/vosk/models
   - Рекомендуется: **vosk-model-small-ru-0.22** (компактная, ~40MB)
   - Или полная: **vosk-model-ru-0.22** (~1.5GB, выше точность)

2. Распакуйте модель в директорию `app/src/main/assets/vosk-model-ru/`

Структура должна выглядеть так:
```
app/src/main/assets/
└── vosk-model-ru/
    ├── conf/
    │   └── model.conf
    ├── graph/
    │   ├── HCLG.fst
    │   └── words.txt
    ├── ivector/
    │   └── final.ie
    ├── rescore/
    │   └── G.carpa
    └── ...
```

### 3. Разрешения

Добавьте разрешение на запись аудио в `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Для Android 13+ также может потребоваться запрос разрешения во время выполнения.

## Интеграция в MapScreen

### Пример использования в ViewModel

```kotlin
@HiltViewModel
class MapViewModel @Inject constructor(
    // ... другие зависимости
) : BaseMapViewModel() {
    
    private lateinit var voiceCommandManager: VoiceCommandManager
    private lateinit var audioRecordingService: AudioRecordingService
    
    private val _isVoiceRecording = MutableStateFlow(false)
    val isVoiceRecording: StateFlow<Boolean> = _isVoiceRecording.asStateFlow()
    
    private val _lastVoiceCommand = MutableStateFlow<VoiceCommand?>(null)
    val lastVoiceCommand: StateFlow<VoiceCommand?> = _lastVoiceCommand.asStateFlow()
    
    fun initializeVoiceCommands(context: Context) {
        voiceCommandManager = VoiceCommandManager(context) { command ->
            handleVoiceCommand(command)
            _lastVoiceCommand.value = command
        }
        
        audioRecordingService = AudioRecordingService(context) { audioData ->
            voiceCommandManager.processAudioData(audioData)
        }
        
        voiceCommandManager.initialize()
    }
    
    fun toggleVoiceRecording() {
        if (_isVoiceRecording.value) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }
    
    private fun startVoiceRecording() {
        if (audioRecordingService.hasRecordAudioPermission()) {
            audioRecordingService.startRecording()
            _isVoiceRecording.value = true
        } else {
            // Запрос разрешения
            requestAudioPermissionLauncher.launch()
        }
    }
    
    private fun stopVoiceRecording() {
        audioRecordingService.stopRecording()
        voiceCommandManager.finishRecognition()
        _isVoiceRecording.value = false
    }
    
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
    
    override fun onCleared() {
        super.onCleared()
        audioRecordingService.release()
        voiceCommandManager.release()
    }
}
```

### Пример использования в UI

```kotlin
@Composable
fun MapScreenWithVoice(
    viewModel: MapViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isVoiceRecording by viewModel.isVoiceRecording.collectAsState()
    val lastCommand by viewModel.lastVoiceCommand.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        // Основной контент карты
        MapContent(/* ... */)
        
        // Кнопка голосового ввода
        VoiceInputButton(
            isRecording = isVoiceRecording,
            onToggleVoiceInput = { viewModel.toggleVoiceRecording() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
        )
        
        // Панель статуса (опционально)
        VoiceInputStatus(
            isRecording = isVoiceRecording,
            lastCommand = lastCommand,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }
}
```

## Настройка и оптимизация

### Выбор модели

**Малая модель (recommended для мобильных):**
- Размер: ~40MB
- Точность: хорошая для основных команд
- Скорость: быстрая
- Модель: `vosk-model-small-ru-0.22`

**Полная модель:**
- Размер: ~1.5GB
- Точность: отличная
- Скорость: медленнее на слабых устройствах
- Модель: `vosk-model-ru-0.22`

### Оптимизация распознавания

1. **Настройка грамматики**: Можно ограничить словарь только командами приложения
2. **Порог уверенности**: Фильтрация низкокачественных результатов
3. **Адаптация**: Обучение на часто используемых командах пользователя

### Энергопотребление

Голосовой ввод потребляет дополнительные ресурсы:
- Микрофон активен постоянно
- Обработка аудио в реальном времени
- Рекомендуется отключать когда не используется

## Отладка

### Логирование

Vosk поддерживает различные уровни логирования:

```kotlin
LibVosk.setLogLevel(LogLevel.DEBUG)  // Подробные логи
LibVosk.setLogLevel(LogLevel.INFO)   // Основная информация
LibVosk.setLogLevel(LogLevel.WARNING) // Только предупреждения
LibVosk.setLogLevel(LogLevel.ERROR)  // Только ошибки
```

### Распространенные проблемы

1. **Модель не загружается**
   - Проверьте путь к модели в assets
   - Убедитесь, что все файлы модели присутствуют
   - Проверьте права доступа к файлам

2. **Нет разрешения на микрофон**
   - Запросите разрешение во время выполнения
   - Проверьте настройки приложения в системе

3. **Низкая точность распознавания**
   - Используйте полную модель вместо маленькой
   - Говорите четче и ближе к микрофону
   - Избегайте фонового шума

4. **Высокое потребление батареи**
   - Отключайте голосовой ввод когда не используется
   - Используйте малую модель
   - Оптимизируйте частоту дискретизации

## Расширение функционала

### Добавление новых команд

1. Добавьте новую команду в enum `VoiceCommand`
2. Обновите метод `parseCommand()` в `VoiceCommandManager`
3. Реализуйте обработку в `handleVoiceCommand()` ViewModel
4. Добавьте отображаемое имя в `getCommandDisplayName()`

### Поддержка других языков

1. Загрузите модель для нужного языка
2. Обновите список распознаваемых фраз
3. Локализуйте UI компоненты

### Интеграция с другими функциями

Голосовые команды можно расширить для:
- Управления узлами Meshtastic
- Отправки текстовых сообщений
- Переключения каналов
- Настройки параметров устройства

## Ресурсы

- [Официальная документация Vosk](https://alphacephei.com/vosk/)
- [Модели языков](https://alphacephei.com/vosk/models)
- [GitHub репозиторий Vosk](https://github.com/alphacep/vosk-api)
- [Примеры использования](https://github.com/alphacep/vosk-api/tree/master/java/demo)

## Лицензия

Интеграция Vosk распространяется под лицензией Apache 2.0. Убедитесь, что использование соответствует лицензионным требованиям вашего проекта.
