/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class HistoryItem;

namespace Main {
class Session;
} // namespace Main

namespace Lumen {

[[nodiscard]] bool LocalTranscribeEnabled();
void SetLocalTranscribeEnabled(bool enabled);

// Free speech to text on this computer, for accounts without Premium.
[[nodiscard]] bool UseLocalTranscribe(not_null<Main::Session*> session);

// Calls done on the main thread with the text, empty when nothing was
// recognized or the speech engine could not run.
void TranscribeLocally(
	not_null<HistoryItem*> item,
	Fn<void(QString)> done);

} // namespace Lumen
