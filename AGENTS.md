# PlateCam — заметки для агентов

## Среда

- Termux на Android, aarch64, без Java/Gradle/Android SDK.
- Сборка и тесты выполняются только в GitHub Actions (JDK 17, Gradle 8.7, AGP 8.4.0).
- Пакет: `com.dimakut228.platecam`; minSdk 24, target/compileSdk 34.

## Команды

```bash
gradle :app:testDebugUnitTest :app:assembleRelease
```

Локально не запускать: нет JDK и Android SDK. Проверка — через workflow `.github/workflows/android.yml`.

## Модели

- `app/src/main/assets/plate_yolov8n.onnx` — YOLOv8n, вход `[1,3,448,448]`, выход `[1,5,4116]`, 1 класс.
- `app/src/main/assets/ocr_ppocrv5_mobile.onnx` — PP-OCRv5 rec, вход `[1,3,48,W]`, выход `[1,T,438]`.
- `app/src/main/assets/ppocr_keys.txt` — 436 символов из `ppocrv5_en_dict.txt`. Первые 36 классов совпадают с нужными цифрами/латиницей; остальные классы маскируются в `PlateOcr`.
- Препроцессинг OCR: RGB, resize 320×48, значения `0..1`, CHW, без mean/std. Проверено на синтетическом номере.

## Правила

- Не менять бинарные модели без проверки форм через `onnxruntime`.
- Не добавлять сетевые разрешения сверх `INTERNET`: кадры в сеть не уходят, `INTERNET` используется только для проверки и загрузки обновлений (`UpdateChecker`).
- Keystore и пароли подписи хранятся только в GitHub Secrets; `signing/*.jks` и пароли нельзя коммитить.
- Форматирование номеров и исправления — в `PlateParser`; покрывать изменения unit-тестами.
- UI только «рамка + крупный номер»; историю, CSV, сохранение фото и звук не добавлять без запроса.
