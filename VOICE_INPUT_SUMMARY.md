# Голосовой ввод для сообщений - Краткое руководство

## Что реализовано

### Созданные файлы:

1. **VoiceEnabledSendButton.kt** - UI компонент кнопки с двойной функцией
   - Путь: `feature/messaging/src/androidMain/kotlin/org/meshtastic/feature/messaging/voice/VoiceEnabledSendButton.kt`
   - Функции: `VoiceEnabledSendButton`, `VoiceInputManager`

2. **AudioRecordingService.kt** - Сервис записи аудио
   - Путь: `feature/messaging/src/androidMain/kotlin/org/meshtastic/feature/messaging/voice/AudioRecordingService.kt`
   - Класс: `AudioRecordingService`

3. **VoiceInputState.kt** - Управление состоянием голосового ввода
   - Путь: `feature/messaging/src/androidMain/kotlin/org/meshtastic/feature/messaging/voice/VoiceInputState.kt`
   - Функции: `VoiceInputState`, `rememberVoiceInputState`

4. **MESSAGING_VOICE_INPUT_GUIDE.md** - Полная документация

## Логика работы

```
Кнопка отправки → Поле пустое? 
  ├─ ДА → Запуск голосового ввода (Vosk)
  └─ НЕТ → Отправка сообщения
```

**Состояния кнопки:**
- 🎤 Микрофон (серый) - поле пустое, готов к голосовому вводу
- ✉️ Отправить (синий) - текст введен
- 🔴 Стоп (красный) - идет запись

## Быстрый старт

### 1. Добавьте зависимость (уже сделано)
```kotlin
// feature/messaging/build.gradle.kts
androidMain.dependencies { 
    implementation(libs.vosk)
}
```

### 2. Разрешение на микрофон
Добавьте в `app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 3. Модель Vosk
Скачайте и разместите в `app/src/main/assets/vosk-model-ru/`:
- https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip

### 4. Интеграция в MessageScreen

```kotlin
@Composable
fun MessageScreen(...) {
    val context = LocalContext.current
    
    // Создаем состояние голосового ввода
    val voiceInputState = rememberVoiceInputState(
        context = context
    ) { recognizedText ->
        // Добавляем распознанный текст в поле ввода
        messageInputState.edit {
            if (text.isNotEmpty()) append(" ")
            append(recognizedText)
        }
    }
    
    // В MessageInput передаем состояние
    MessageInput(
        isEnabled = connectionState.isConnected(),
        isHomoglyphEncodingEnabled = homoglyphEncodingEnabled,
        textFieldState = messageInputState,
        isRecording = voiceInputState.isRecording.collectAsStateWithLifecycle().value,
        onSendMessage = { ... },
        onStartVoiceInput = { voiceInputState.toggleRecording() }
    )
}
```

### 5. Обновление MessageInput

Функция `MessageInput` должна принимать новые параметры:

```kotlin
private fun MessageInput(
    isEnabled: Boolean,
    isHomoglyphEncodingEnabled: Boolean,
    textFieldState: TextFieldState,
    isRecording: Boolean = false,           // Новый параметр
    onStartVoiceInput: () -> Unit = {},     // Новый параметр
    modifier: Modifier = Modifier,
    maxByteSize: Int = MESSAGE_CHARACTER_LIMIT_BYTES,
    onSendMessage: () -> Unit,
) {
    // ... существующий код ...
    
    trailingIcon = {
        VoiceEnabledSendButton(
            hasText = currentText.isNotEmpty(),
            isRecording = isRecording,
            onSendMessage = onSendMessage,
            onStartVoiceInput = onStartVoiceInput,
            modifier = Modifier.size(40.dp)
        )
    }
}
```

## Тестирование

1. Установите приложение на устройство
2. Предоставьте разрешение на запись аудио
3. Откройте экран сообщений
4. Нажмите на микрофон (поле должно быть пустым)
5. Произнесите текст
6. Текст должен появиться в поле ввода
7. Нажмите отправить

## Примечания

- Распознавание работает офлайн после первой загрузки модели
- Поддерживается русский язык (можно добавить другие модели)
- Первая инициализация занимает ~2-3 секунды
- Для лучшей точности используйте полную модель (~400MB вместо 40MB)

## Следующие шаги

1. ✅ Создан UI компонент кнопки
2. ✅ Создан менеджер распознавания
3. ✅ Создан сервис записи аудио  
4. ✅ Добавлена зависимость vosk
5. ⏳ Интегрировать в MessageViewModel
6. ⏳ Добавить обработку разрешений
7. ⏳ Протестировать на устройстве

## Ссылки

- [Полная документация](MESSAGING_VOICE_INPUT_GUIDE.md)
- [Vosk Android](https://github.com/alphacep/vosk-android)
- [Модели](https://alphacephei.com/vosk/models)
