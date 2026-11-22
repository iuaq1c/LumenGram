/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Lumen {

struct LangString {
	const char *key = nullptr;
	const char *value = nullptr;
};

// Russian for the LumenGram strings: Telegram's cloud language packs don't have them.
[[nodiscard]] const std::vector<LangString> &RussianStrings();

// Whether a language id (like "ru" or "ru-raw") should use RussianStrings().
[[nodiscard]] bool UsesRussianStrings(const QString &languageId);

} // namespace Lumen
