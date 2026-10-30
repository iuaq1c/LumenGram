<div align="center">

<img src="assets/lumengram-logo.png" width="120" alt="LumenGram">

# LumenGram

**Неофициальный клиент Telegram для Windows, Android и браузера — с режимом призрака, сохранением удалённых сообщений, историей правок и десятками удобных мелочей.**

[![Release](https://img.shields.io/github/v/release/iuaq1c/LumenGram?style=flat&logo=github&label=release&color=7c5cff)](https://github.com/iuaq1c/LumenGram/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/iuaq1c/LumenGram/total?style=flat&logo=github&color=2ea043)](https://github.com/iuaq1c/LumenGram/releases)
[![Web](https://img.shields.io/badge/Web-web.tunexa.ru-0088cc?style=flat&logo=telegram&logoColor=white)](https://web.tunexa.ru/)
[![Site](https://img.shields.io/badge/Сайт-tunexa.ru-111111?style=flat&logo=googlechrome&logoColor=white)](https://tunexa.ru/docs/lumengram)

[![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D6?style=flat&logo=windows&logoColor=white)](#-скачать)
[![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?style=flat&logo=android&logoColor=white)](#-скачать)
[![x86-64](https://img.shields.io/badge/PE-x86--64-555555?style=flat&logo=windowsterminal&logoColor=white)](#-скачать)
[![C++](https://img.shields.io/badge/C++-00599C?style=flat&logo=cplusplus&logoColor=white)](#-сборка-из-исходников)
[![Qt](https://img.shields.io/badge/Qt-41CD52?style=flat&logo=qt&logoColor=white)](#-сборка-из-исходников)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white)](#-сборка-из-исходников)
[![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat&logo=typescript&logoColor=white)](#-веб-версия)
[![Python](https://img.shields.io/badge/Python-3776AB?style=flat&logo=python&logoColor=white)](#-сборка-из-исходников)
[![PowerShell](https://img.shields.io/badge/PowerShell-5391FE?style=flat&logo=powershell&logoColor=white)](#-сборка-из-исходников)
[![License](https://img.shields.io/badge/license-GPL-blue?style=flat&logo=gnu&logoColor=white)](#-лицензия)
[![Telegram API](https://img.shields.io/badge/Telegram-API-26A5E4?style=flat&logo=telegram&logoColor=white)](https://core.telegram.org/api)

[**Скачать**](#-скачать) · [**Веб-версия**](https://web.tunexa.ru/) · [**Возможности**](#-возможности) · [**FAQ**](#-частые-вопросы) · [**Документация**](https://tunexa.ru/docs/lumengram)

</div>

---

## ✨ Что такое LumenGram

LumenGram — это Telegram, в который добавили то, чего давно не хватает в официальном клиенте. Вы входите в свой обычный аккаунт: все чаты, каналы, контакты и папки остаются на месте и синхронизируются через Telegram как обычно. Сверху появляются новые возможности — приватность, контроль над перепиской, инструменты для работы с сообщениями и гибкая настройка внешнего вида.

LumenGram построен на исходном коде официальных клиентов — **Telegram Desktop** для Windows, **Telegram for Android** и **Telegram Web** — и работает через **официальный API Telegram**. Сообщения и коды входа идут напрямую на серверы Telegram и не проходят через сторонние серверы.

Клиент **бесплатный**, без рекламы и подписок.

## 🎯 Для кого он создан

- **Для тех, кто ценит приватность.** Читать сообщения, не показывая, что вы в сети и что прочитали, смотреть истории незаметно и скрывать «печатает».
- **Для тех, кто не хочет терять переписку.** Если собеседник удалит или перепишет сообщение, у вас останется исходный текст.
- **Для активных пользователей и админов каналов.** Шаблоны ответов, закладки из всех чатов, статистика чата и лента постов из всех каналов в одном месте.
- **Для стримеров и тех, кто показывает экран.** Режим стримера скрывает номер телефона, превью сообщений и текст уведомлений.
- **Для тех, кто много отвлекается на Telegram.** Режим фокуса, тихие часы и экранное время с дневным лимитом.
- **Для тех, у кого Telegram работает нестабильно.** Встроенное подключение без VPN — в приложениях для Windows и Android и в веб-версии.
- **Для любителей настраивать интерфейс.** Темы Lumen, 12 иконок приложения, жидкое стекло, квадратные аватарки, скругление сообщений и многое другое.

## 🧩 Возможности

### 🕶 Приватность

| Функция | Что делает |
|---|---|
| **Режим призрака** | Без статуса «в сети», отметок о прочтении, «печатает» и просмотров историй. Каждый пункт включается отдельно, с предупреждением о риске. |
| **Прочитать при ответе** | В режиме призрака чат отмечается прочитанным только тогда, когда вы сами отвечаете. |
| **Режим стримера** | Скрывает номер телефона, размывает превью сообщений и текст уведомлений, прячет окно от записи экрана. |
| **Подключение без VPN** | Встроенный прокси LumenGram во всех версиях — Windows, Android и веб: включается автоматически, когда Telegram недоступен напрямую. |

### 💬 Сообщения

| Функция | Что делает |
|---|---|
| **Удалённые сообщения** 🆕 | Если собеседник удалит сообщение, у вас оно останется в чате с пометкой «удалено». |
| **История правок** 🆕 | У изменённых сообщений сохраняются все прошлые версии текста с датами — их видно в меню сообщения. |
| **Отмена отправки** | Сообщение уходит через 5 секунд — за это время его можно вернуть в поле ввода. |
| **Шаблоны ответов** | Пишете `;ключ` — отправляется готовый текст. Бесплатно, без Telegram Business. |
| **Закладки** | Любое сообщение в закладки, все закладки из всех чатов — в одном списке. |
| **Скриншот сообщений** | Выделенные сообщения превращаются в аккуратную картинку. |
| **Детали сообщения** | ID сообщения и чата, точные даты отправки и правки, данные кнопок ботов, набор стикеров. |
| **Прочитать вслух** | Озвучивает текст сообщения голосом системы (Android). |
| **Секунды во времени** | Время сообщений с точностью до секунды и время в служебных сообщениях. |

### 📊 Чаты и продуктивность

| Функция | Что делает |
|---|---|
| **Статистика чата** | Кто сколько пишет, активность по часам, частые слова и среднее время ответа. |
| **Лента** | Посты из всех ваших каналов за 30 дней в одной ленте (Android, Windows). |
| **Режим фокуса** | На 25 или 50 минут остаются только важные чаты, остальные не отвлекают. |
| **Тихие часы** | Уведомления выключаются по расписанию, важные чаты всё равно дозвонятся. |
| **Экранное время** | Сколько времени уходит на каждый чат за день, плюс дневной лимит с напоминанием. |
| **Метки и свои названия** | Цветные метки чатов, своё название для любого чата и личные заметки к нему. |
| **ID и дата регистрации** | В профиле видны ID, дата-центр, примерная дата регистрации и дата вступления в канал. |

### 🎨 Внешний вид

| Функция | Что делает |
|---|---|
| **Темы Lumen** | Фирменные тёмные и светлые темы. |
| **12 иконок приложения** | Иконка окна, панели задач, трея и рабочего стола на выбор. |
| **Жидкое стекло** | Размытый полупрозрачный фон верхней панели и поля ввода. |
| **Баннеры и «Сейчас слушает»** | Свой баннер в профиле и трек из Spotify или Last.fm. |
| **Тонкая настройка** | Квадратные аватарки, скругление сообщений, без хвостика у пузырей, скрытие историй, кнопки «Поделиться», похожих каналов и счётчиков у папок. |

## 📥 Скачать

| Платформа | Требования | Файл |
|---|---|---|
| 🪟 **Windows** | Windows 10 и 11, 64-бит | [`LumenGram-1.2.0-win64.zip`](https://github.com/iuaq1c/LumenGram/releases/latest) — портативная версия, установка не нужна |
| 🤖 **Android** | Android 6.0 и новее | [`LumenGram-1.2.0-android.apk`](https://github.com/iuaq1c/LumenGram/releases/latest) |
| 🌐 **Браузер** | Любой современный браузер | [**web.tunexa.ru**](https://web.tunexa.ru/) — ничего не нужно устанавливать, работает без VPN |

Все файлы также доступны на сайте: **[tunexa.ru/docs/lumengram](https://tunexa.ru/docs/lumengram)**. Контрольные суммы SHA-256 указаны в описании каждого релиза — сверьте их перед установкой.

**Windows:** распакуйте папку куда удобно и запустите `LumenGram.exe`. Данные хранятся в папке `tdata` рядом с программой и не пересекаются с официальным Telegram Desktop. Для обновления замените `LumenGram.exe` и папку `modules`, папку `tdata` не трогайте. Windows может предупредить, что издатель неизвестен: у программы пока нет платной цифровой подписи.

**Android:** откройте APK и разрешите установку из этого источника. LumenGram ставится отдельным приложением рядом с официальным Telegram.

## 🌐 Веб-версия

**[web.tunexa.ru](https://web.tunexa.ru/)** — LumenGram прямо в браузере на компьютере и телефоне. Основан на Telegram Web A, поддерживает режим призрака, закладки, шаблоны, метки и другие функции LumenGram. Настройки и закладки хранятся в вашем браузере.

> В веб-версию встроен прокси LumenGram, поэтому она работает **без VPN** — даже там, где Telegram ограничен. В приложениях для Windows и Android подключение без VPN тоже встроено.

## 🔒 Безопасность и приватность

- Клиент подключается **напрямую к серверам Telegram** — сообщения и коды входа не проходят через серверы LumenGram.
- Закладки, заметки, метки, шаблоны, сохранённые удалённые сообщения и история правок хранятся **только на вашем устройстве**.
- Исходный код открыт: архивы исходников приложены к каждому релизу.

## ❓ Частые вопросы

<details>
<summary><b>Это официальный Telegram?</b></summary>

Нет. LumenGram — неофициальный клиент, он не связан с Telegram. Работает через официальный API Telegram, как и другие сторонние клиенты, поэтому вы входите в свой обычный аккаунт.
</details>

<details>
<summary><b>Могут ли заблокировать аккаунт?</b></summary>

При обычном использовании риск такой же, как у любого стороннего клиента. Режим призрака противоречит правилам Telegram для сторонних приложений: он выключен по умолчанию, а перед включением LumenGram предупреждает о риске.
</details>

<details>
<summary><b>Сохраняются ли удалённые сообщения навсегда?</b></summary>

На Android удалённые сообщения и история правок хранятся в памяти устройства и сохраняются после перезапуска. На Windows — пока приложение открыто. Сохраняется только то, что успело загрузиться на устройство до удаления.
</details>

<details>
<summary><b>Синхронизируются ли закладки и заметки между устройствами?</b></summary>

Нет, они хранятся локально — в браузере или в приложении. Переписка, контакты и чаты синхронизируются как обычно, через Telegram.
</details>

<details>
<summary><b>Сколько это стоит?</b></summary>

LumenGram бесплатный. Платные возможности самого Telegram, например Premium, по-прежнему оформляются в Telegram. Поддержать проект можно на [странице пожертвований](https://tunexa.ru/lumengram/donate).
</details>

## 🛠 Сборка из исходников

К каждому релизу приложены архивы исходников.

**Windows** (`LumenGram-<версия>-source.zip`) — полное дерево на основе Telegram Desktop. Сборка — как у [Telegram Desktop](https://github.com/telegramdesktop/tdesktop/blob/dev/docs/building-win-x64.md): Visual Studio 2022/2026, Qt и зависимости из `Libraries`, затем

```bash
cmake --build out --config Release --target Telegram
```

Код LumenGram лежит в `Telegram/SourceFiles/lumen`.

**Android** (`LumenGram-<версия>-android-source.zip`) — изменения поверх Telegram for Android: папка `files` с изменёнными и новыми файлами, `lumengram.patch`, список удалённых файлов и коммит исходного Telegram (`UPSTREAM.txt`). Задайте свои `LUMEN_API_ID` и `LUMEN_API_HASH` с [my.telegram.org](https://my.telegram.org) и соберите:

```bash
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
```

Нужны JDK 17+, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1. Код LumenGram — `TMessagesProj/src/main/java/org/telegram/lumen`.

## 📜 Лицензия

LumenGram распространяется на тех же условиях, что и клиенты, на которых он основан:

- версия для Windows — **GNU GPL v3** с исключением для OpenSSL, как [Telegram Desktop](https://github.com/telegramdesktop/tdesktop);
- версия для Android — **GNU GPL v2 или новее**, как [Telegram for Android](https://github.com/DrKLO/Telegram);
- веб-версия — **GNU GPL v3 или новее**, как [Telegram Web A](https://github.com/Ajaxy/telegram-tt).

## 🙏 Благодарности

[Telegram Desktop](https://github.com/telegramdesktop/tdesktop), [Telegram for Android](https://github.com/DrKLO/Telegram) и [Telegram Web A](https://github.com/Ajaxy/telegram-tt) — за открытый код, на котором построен LumenGram. Идея сохранения удалённых сообщений и истории правок вдохновлена [AyuGram](https://github.com/AyuGram/AyuGramDesktop).

---

<div align="center">

LumenGram не связан с Telegram FZ-LLC. Telegram — товарный знак Telegram FZ-LLC.

**[tunexa.ru/docs/lumengram](https://tunexa.ru/docs/lumengram)** · **[web.tunexa.ru](https://web.tunexa.ru/)**

</div>
