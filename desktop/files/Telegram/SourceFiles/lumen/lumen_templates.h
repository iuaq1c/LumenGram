/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

struct TextWithTags;

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// A message that is exactly `;key` is sent as the text of that template.
void ApplyTemplate(TextWithTags &text);

[[nodiscard]] rpl::producer<int> TemplatesCountValue();
void ShowTemplates(not_null<Window::SessionController*> controller);

} // namespace Lumen
