/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class HistoryItem;
struct HistoryMessageEdition;

namespace Main {
class SessionShow;
} // namespace Main

namespace Lumen {

[[nodiscard]] bool SaveDeletedMessages();
void SetSaveDeletedMessages(bool enabled);

[[nodiscard]] bool SaveEditHistory();
void SetSaveEditHistory(bool enabled);

// Returns true when the item is kept in the chat marked as deleted
// instead of being destroyed by a delete update from the server.
[[nodiscard]] bool KeepDeleted(not_null<HistoryItem*> item);
[[nodiscard]] bool IsDeleted(not_null<const HistoryItem*> item);

// Remembers the current text of the item before an edition replaces it.
void RememberEdit(
	not_null<HistoryItem*> item,
	const HistoryMessageEdition &edition);
[[nodiscard]] bool HasEditHistory(not_null<const HistoryItem*> item);

void ShowEditHistory(
	std::shared_ptr<Main::SessionShow> show,
	not_null<HistoryItem*> item);

} // namespace Lumen
