# InstagramWebView

Нативная Android-оболочка на Kotlin для локального HTML/CSS/JS интерфейса (`app/src/main/assets/Instagram.html`).

## Что реализовано

- Kotlin + Gradle Kotlin DSL.
- `MainActivity` с полноэкранным `WebView`.
- Включены `JavaScript`, `DOM Storage`, `Database`.
- Отключены вертикальные и горизонтальные scrollbars.
- Все обычные переходы остаются внутри `WebView`.
- Edge-to-edge: WebView рисуется под status bar, status icons тёмные.
- Splash Screen через `androidx.core:core-splashscreen`.
- Системная кнопка Back: сначала история WebView, затем закрытие приложения.
- File chooser для `<input type="file">`: галерея + камера через `FileProvider`.
- Runtime permissions для доступа к фото/камере.

## Сборка

Откройте папку `instagram-webview` в Android Studio и запустите сборку `app`.

Если используете консольный Gradle wrapper, можно выполнить:

```bash
./gradlew assembleDebug
```

> В этой папке wrapper не сгенерирован, потому что среда не содержит установленный Gradle/Android SDK. Android Studio предложит использовать встроенный Gradle или сгенерировать wrapper.

## Где лежит HTML

Файл, который был приложен пользователем, скопирован сюда:

```text
app/src/main/assets/Instagram.html
```

В коде он загружается так:

```kotlin
webView.loadUrl("file:///android_asset/Instagram.html")
```
