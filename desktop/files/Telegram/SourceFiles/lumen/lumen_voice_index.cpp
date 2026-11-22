/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_voice_index.h"

#include "data/data_peer.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "settings.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/fields/input_field.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_settings.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>

namespace Lumen {
namespace {

constexpr auto kMaxEntries = 3000;
constexpr auto kMaxResults = 100;

[[nodiscard]] QString IndexPath() {
	return cWorkingDir() + u"tdata/lumen-voice.json"_q;
}

[[nodiscard]] QJsonArray Load() {
	auto file = QFile(IndexPath());
	if (!file.open(QIODevice::ReadOnly)) {
		return {};
	}
	return QJsonDocument::fromJson(file.readAll()).array();
}

void Store(const QJsonArray &entries) {
	auto file = QFile(IndexPath());
	if (file.open(QIODevice::WriteOnly)) {
		file.write(QJsonDocument(entries).toJson(QJsonDocument::Compact));
	}
}

} // namespace

void SaveVoiceText(not_null<HistoryItem*> item, const QString &text) {
	if (text.trimmed().isEmpty()) {
		return;
	}
	const auto peer = QString::number(item->history()->peer->id.value);
	const auto id = qint64(item->id.bare);
	auto entries = QJsonArray();
	entries.append(QJsonObject{
		{ u"peer"_q, peer },
		{ u"id"_q, id },
		{ u"date"_q, qint64(item->date()) },
		{ u"text"_q, text },
	});
	for (const auto &value : Load()) {
		const auto entry = value.toObject();
		if (entry.value(u"peer"_q).toString() == peer
			&& entry.value(u"id"_q).toInteger() == id) {
			continue;
		}
		if (entries.size() >= kMaxEntries) {
			break;
		}
		entries.append(entry);
	}
	Store(entries);
}

void ShowVoiceSearch(not_null<Window::SessionController*> controller) {
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_voice_search_title());
		box->setWidth(st::boxWideWidth);
		const auto field = box->addRow(object_ptr<Ui::InputField>(
			box,
			st::defaultInputField,
			tr::lng_lumen_voice_search_hint()));
		const auto results = box->addRow(
			object_ptr<Ui::VerticalLayout>(box),
			style::margins());
		const auto refresh = [=] {
			while (results->count()) {
				delete results->widgetAt(0);
			}
			const auto needle = field->getLastText().trimmed().toLower();
			auto shown = 0;
			for (const auto &value : Load()) {
				const auto entry = value.toObject();
				const auto text = entry.value(u"text"_q).toString();
				if (!needle.isEmpty() && !text.toLower().contains(needle)) {
					continue;
				}
				const auto peerId = PeerId(
					entry.value(u"peer"_q).toString().toULongLong());
				const auto msgId = MsgId(entry.value(u"id"_q).toInteger());
				const auto peer = controller->session().data().peerLoaded(
					peerId);
				const auto button = results->add(object_ptr<Ui::SettingsButton>(
					results,
					rpl::single((peer ? peer->name() + u": "_q : QString())
						+ text.left(120)),
					st::settingsButtonNoIcon));
				button->setClickedCallback([=] {
					box->closeBox();
					controller->showPeerHistory(
						peerId,
						Window::SectionShow::Way::Forward,
						msgId);
				});
				if (++shown >= kMaxResults) {
					break;
				}
			}
			if (!shown) {
				results->add(object_ptr<Ui::FlatLabel>(
					results,
					(needle.isEmpty()
						? tr::lng_lumen_voice_search_empty(tr::now)
						: tr::lng_lumen_voice_search_nothing(tr::now)),
					st::boxDividerLabel));
			}
			results->resizeToWidth(box->width());
		};
		field->changes() | rpl::on_next(refresh, field->lifetime());
		box->setFocusCallback([=] { field->setFocusFast(); });
		refresh();
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
	}));
}

} // namespace Lumen
