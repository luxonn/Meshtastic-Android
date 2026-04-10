# Голосовой ввод текста в сообщениях

## Обзор

Реализована функция голосового ввода текста для поля отправки сообщений. Если поле ввода пустое, при нажатии на кнопку отправки активируется голосовой ввод вместо отправки сообщения.

## Логика работы

```
┌─────────────────────────────────────┐
│  Кнопка отправки (FAB)              │
├─────────────────────────────────────┤
│  Поле ввода пустое?                 │
│  ├─ ДА → Запуск голосового ввода    │
│  └─ НЕТ → Отправка сообщения        │
└─────────────────────────────────────┘
```

**Состояния кнопки:**
- 🎤 **Микрофон** (серый) - поле пустое, готов к голосовому вводу
- ✉️ **Отправить** (синий) - текст введен, готов к отправке
- 🔴 **Стоп** (красный) - идет запись голоса

## Созданные файлы

### 1. VoiceEnabledSendButton.kt
**Путь:** `/workspace/feature/messaging/src/androidMain/kotlin/org/meshtastic/feature/messaging/voice/VoiceEnabledSendButton.kt`

**Компоненты:**
- `VoiceEnabledSendButton` - UI компонент кнопки с двойной функцией
- `VoiceInputManager` - менеджер распознавания речи на базе Vosk

**Использование:**
```kotlin
@Composable
fun MessageInputWithVoice(
    textFieldState: TextFieldState,
    viewModel: MessageViewModel
) {
    var isRecording by remember { mutableStateOf(false) }
    val voiceInputManager = remember { 
        VoiceInputManager(context) { text ->
            // Добавляем распознанный текст в поле ввода
            textFieldState.edit {
                append(text)
            }
        }
    }
    
    val hasText = textFieldState.text.isNotEmpty()
    
    VoiceEnabledSendButton(
        hasText = hasText,
        isRecording = isRecording,
        onSendMessage = {
            viewModel.sendMessage(textFieldState.text.toString())
            textFieldState.clearText()
        },
        onStartVoiceInput = {
            if (isRecording) {
                voiceInputManager.stopRecording()
                isRecording = false
            } else {
                voiceInputManager.startRecording()
                isRecording = true
            }
        }
    )
}
```

## Интеграция в существующий код

### Шаг 1: Обновление Message.kt

Замените стандартную кнопку отправки в функции `MessageInput`:

```kotlin
// Было:
trailingIcon = {
    IconButton(onClick = { if (canSend) onSendMessage() }, enabled = canSend) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.Send,
            contentDescription = stringResource(Res.string.send),
        )
    }
}

// Стало:
trailingIcon = {
    VoiceEnabledSendButton(
        hasText = currentText.isNotEmpty(),
        isRecording = isRecording,
        onSendMessage = onSendMessage,
        onStartVoiceInput = {
            isRecording = !isRecording
            if (isRecording) {
                voiceInputManager.startRecording()
            } else {
                voiceInputManager.stopRecording()
            }
        },
        modifier = Modifier.size(40.dp)
    )
}
```

### Шаг 2: Добавление состояния в ViewModel

Добавьте в `MessageViewModel`:

```kotlin
private val _isRecording = MutableStateFlow(false)
val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

fun toggleVoiceRecording() {
    _isRecording.value = !_isRecording.value
}

fun appendRecognizedText(text: String) {
    // Метод для добавления распознанного текста
    // Реализация зависит от вашей архитектуры
}
```

### Шаг 3: Инициализация VoiceInputManager

В `MessageScreen`:

```kotlin
val context = LocalContext.current
val voiceInputManager = remember {
    VoiceInputManager(context.applicationContext) { recognizedText ->
        // Добавляем текст в поле ввода
        messageInputState.edit {
            if (text.isNotEmpty()) append(" ")
            append(recognizedText)
        }
        // Останавливаем запись после распознавания
        voiceInputManager.stopRecording()
        isRecording = false
    }
}

// Инициализация при старте
LaunchedEffect(Unit) {
    voiceInputManager.initialize()
}

// Очистка при уничтожении
DisposableEffect(Unit) {
    onDispose {
        voiceInputManager.release()
    }
}
```

## Необходимые разрешения

Добавьте в `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Модель распознавания

1. Скачайте русскую модель Vosk:
   - URL: https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
   - Размер: ~40 MB (облегченная версия)

2. Разместите в assets:
   ```
   app/src/main/assets/vosk-model-ru/
   ├── am/
   ├── conf/
   ├── graph/
   ├── ivector/
   ├── tdnn/
   └── ...
   ```

3. Альтернативно - модель загрузится автоматически при первом запуске

## Зависимости

Зависимость уже добавлена в `gradle/libs.versions.toml`:
```toml
vosk = "0.3.45"
```

И в `feature/messaging/build.gradle.kts`:
```kotlin
androidMain.dependencies { 
    implementation(libs.vosk)
}
```

## Обработка аудио

Для записи аудио используйте `AudioRecord`:

```kotlin
class AudioRecordingService(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    fun startRecording(sampleRate: Int = 16000) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        audioRecord?.startRecording()
        isRecording = true
        
        // Чтение данных в фоне
        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    // Передаем данные в VoiceInputManager
                    voiceInputManager.processAudioData(buffer.copyOf(read))
                }
            }
        }.start()
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

## Тестирование

1. Запустите приложение на устройстве с микрофоном
2. Откройте экран сообщений
3. Убедитесь, что поле ввода пустое
4. Нажмите на кнопку микрофона
5. Произнесите текст
6. Проверьте, что текст появился в поле ввода
7. Нажмите еще раз для остановки записи
8. Отредактируйте текст при необходимости
9. Нажмите кнопку отправки

## Известные ограничения

- Требуется разрешение на запись аудио
- Первая инициализация может занять несколько секунд
- Для лучшей точности используйте качественную модель
- Распознавание работает офлайн после загрузки модели
- Поддерживается только русский язык (можно добавить другие модели)

## Расширение функционала

### Мультиязычность
```kotlin
// Загрузка английской модели
voiceInputManager.initialize("vosk-model-en")

// Переключение между языками
fun switchLanguage(language: String) {
    voiceInputManager.release()
    voiceInputManager.initialize("vosk-model-$language")
}
```

### Команды управления
```kotlin
// Специальные команды в тексте
when {
    text.contains("отправить") -> sendMessage()
    text.contains("очистить") -> clearText()
    text.contains("запятая") -> appendText(",")
    text.contains("точка") -> appendText(".")
    text.contains("новая строка") -> appendText("\n")
}
```

## Ссылки

- [Vosk Android](https://github.com/alphacep/vosk-android)
- [Модели Vosk](https://alphacephei.com/vosk/models)
- [Документация по распознаванию речи](https://alphacephei.com/vosk/doc/)
