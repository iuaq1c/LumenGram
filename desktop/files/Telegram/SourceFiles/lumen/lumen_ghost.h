/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class History;

namespace Main {
class Session;
} // namespace Main

namespace Ui {
class SettingsButton;
class Show;
} // namespace Ui

namespace Lumen {

void SetupGhostToggle(
	not_null<Ui::SettingsButton*> button,
	std::shared_ptr<Ui::Show> show);

void MessageSent(not_null<History*> history);
void SelfMarkedOnline(not_null<Main::Session*> session);

} // namespace Lumen
