/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_templates.h"

#include "core/application.h"
#include "core/core_settings.h"
#include "lang/lang_keys.h"
#include "settings/settings_common.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/fields/input_field.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_settings.h"
#include "styles/style_widgets.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>

namespace Lumen {
namespace {

constexpr auto kTemplatesPref = "lumen/templates"_cs;
constexpr auto kPrefix = QChar(';');
constexpr auto kMaxKeyLength = 32;
constexpr auto kMaxTextLength = 4096;
constexpr auto kPreviewLength = 48;

struct Template {
	QString key;
	QString text;
};

[[nodiscard]] rpl::event_stream<> &Changes() {
	static auto result = rpl::event_stream<>();
	return result;
}

[[nodiscard]] std::vector<Template> Load() {
	auto result = std::vector<Template>();
	const auto bytes = Core::App().settings().readPref<QByteArray>(
		kTemplatesPref);
	for (const auto &value : QJsonDocument::fromJson(bytes).array()) {
		const auto entry = value.toObject();
		auto item = Template{
			.key = entry.value(u"k"_q).toString(),
			.text = entry.value(u"t"_q).toString(),
		};
		if (!item.key.isEmpty() && !item.text.isEmpty()) {
			result.push_back(std::move(item));
		}
	}
	return result;
}

void Save(const std::vector<Template> &list) {
	auto array = QJsonArray();
	for (const auto &item : list) {
		array.push_back(QJsonObject{
			{ u"k"_q, item.key },
			{ u"t"_q, item.text },
		});
	}
	Core::App().settings().writePref<QByteArray>(
		kTemplatesPref,
		QJsonDocument(array).toJson(QJsonDocument::Compact));
	Core::App().saveSettingsDelayed();
	Changes().fire({});
}

[[nodiscard]] QString NormalizeKey(QString key) {
	key = key.trimmed();
	if (key.startsWith(kPrefix)) {
		key = key.mid(1);
	}
	return key.toLower();
}

[[nodiscard]] bool ValidKey(const QString &key) {
	return !key.isEmpty()
		&& (key.size() <= kMaxKeyLength)
		&& ranges::none_of(key, [](QChar ch) { return ch.isSpace(); });
}

[[nodiscard]] QString Preview(const QString &text) {
	auto result = text.simplified();
	if (result.size() > kPreviewLength) {
		result = result.mid(0, kPreviewLength - 1) + QChar(0x2026);
	}
	return result;
}

void ShowEditBox(
		not_null<Window::SessionController*> controller,
		std::optional<Template> existing) {
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(existing
			? tr::lng_lumen_template_edit()
			: tr::lng_lumen_template_add());
		const auto key = box->addRow(object_ptr<Ui::InputField>(
			box,
			st::defaultInputField,
			tr::lng_lumen_template_key(),
			existing ? (kPrefix + existing->key) : QString(kPrefix)));
		key->setMaxLength(kMaxKeyLength + 1);
		const auto text = box->addRow(object_ptr<Ui::InputField>(
			box,
			st::newGroupDescription,
			Ui::InputField::Mode::MultiLine,
			tr::lng_lumen_template_text(),
			existing ? existing->text : QString()));
		text->setMaxLength(kMaxTextLength);
		box->setFocusCallback([=] {
			(existing ? text : key)->setFocusFast();
		});

		const auto save = [=] {
			const auto newKey = NormalizeKey(key->getLastText());
			const auto newText = text->getLastText().trimmed();
			if (!ValidKey(newKey)) {
				key->showError();
				return;
			}
			if (newText.isEmpty()) {
				text->showError();
				return;
			}
			auto list = Load();
			const auto taken = ranges::any_of(list, [&](const Template &t) {
				return (t.key == newKey)
					&& (!existing || existing->key != newKey);
			});
			if (taken) {
				key->showError();
				return;
			}
			if (existing) {
				const auto i = ranges::find(
					list,
					existing->key,
					&Template::key);
				if (i != end(list)) {
					*i = Template{ newKey, newText };
				} else {
					list.push_back({ newKey, newText });
				}
			} else {
				list.push_back({ newKey, newText });
			}
			Save(list);
			box->closeBox();
		};
		key->submits() | rpl::on_next([=] {
			text->setFocus();
		}, key->lifetime());

		box->addButton(tr::lng_settings_save(), save);
		box->addButton(tr::lng_cancel(), [=] { box->closeBox(); });
		if (existing) {
			const auto removeKey = existing->key;
			box->addLeftButton(tr::lng_lumen_template_delete(), [=] {
				auto list = Load();
				list.erase(
					ranges::remove(list, removeKey, &Template::key),
					end(list));
				Save(list);
				box->closeBox();
			});
		}
	}));
}

} // namespace

void ApplyTemplate(TextWithTags &text) {
	const auto trimmed = text.text.trimmed();
	if (!trimmed.startsWith(kPrefix) || trimmed.size() < 2) {
		return;
	}
	const auto key = NormalizeKey(trimmed);
	for (const auto &item : Load()) {
		if (item.key == key) {
			text = TextWithTags{ item.text };
			return;
		}
	}
}

rpl::producer<int> TemplatesCountValue() {
	return rpl::single(rpl::empty) | rpl::then(
		Changes().events()
	) | rpl::map([] {
		return int(Load().size());
	});
}

void ShowTemplates(not_null<Window::SessionController*> controller) {
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_templates());
		box->setWidth(st::boxWideWidth);
		const auto container = box->verticalLayout();
		const auto refill = [=] {
			container->clear();
			const auto list = Load();
			for (const auto &item : list) {
				Settings::AddButtonWithLabel(
					container,
					rpl::single(kPrefix + item.key),
					rpl::single(Preview(item.text)),
					st::settingsButtonNoIcon
				)->addClickHandler([=] {
					ShowEditBox(controller, item);
				});
			}
			container->add(
				object_ptr<Ui::FlatLabel>(
					container,
					list.empty()
						? tr::lng_lumen_templates_empty()
						: tr::lng_lumen_templates_about(),
					st::boxDividerLabel),
				st::boxPadding);
			container->resizeToWidth(st::boxWideWidth);
		};
		refill();
		Changes().events() | rpl::on_next(refill, box->lifetime());

		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
		box->addLeftButton(tr::lng_lumen_template_add(), [=] {
			ShowEditBox(controller, std::nullopt);
		});
	}));
}

} // namespace Lumen
