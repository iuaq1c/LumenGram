/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_fullscreen_effect.h"

#include "base/call_delayed.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "main/main_session.h"
#include "mainwindow.h"
#include "ui/effects/animations.h"
#include "ui/painter.h"
#include "ui/rp_widget.h"
#include "window/window_controller.h"
#include "window/window_session_controller.h"

#include <random>

namespace Lumen {
namespace {

constexpr auto kEnabledPref = "lumen/fullscreen_effects"_cs;
constexpr auto kDuration = crl::time(2600);
constexpr auto kParticles = 34;

struct Particle {
	float64 x = 0.;
	float64 y = 0.;
	float64 speedX = 0.;
	float64 speedY = 0.;
	float64 size = 0.;
	float64 spin = 0.;
	crl::time delay = 0;
};

[[nodiscard]] const QStringList &Triggers() {
	static const auto result = QStringList{
		QString::fromUtf8("\xE2\x9D\xA4\xEF\xB8\x8F"), // ❤️
		QString::fromUtf8("\xE2\x9D\xA4"), // ❤
		QString::fromUtf8("\xF0\x9F\x94\xA5"), // 🔥
		QString::fromUtf8("\xF0\x9F\x8E\x89"), // 🎉
		QString::fromUtf8("\xF0\x9F\x8E\x86"), // 🎆
		QString::fromUtf8("\xF0\x9F\x8E\x8A"), // 🎊
		QString::fromUtf8("\xE2\x9C\xA8"), // ✨
		QString::fromUtf8("\xF0\x9F\x98\x82"), // 😂
		QString::fromUtf8("\xF0\x9F\xA5\xB3"), // 🥳
		QString::fromUtf8("\xF0\x9F\x92\x9C"), // 💜
		QString::fromUtf8("\xF0\x9F\x91\x8D"), // 👍
		QString::fromUtf8("\xF0\x9F\x98\x8D"), // 😍
		QString::fromUtf8("\xF0\x9F\x8E\x84"), // 🎄
	};
	return result;
}

// Fireworks and party emoji burst from the middle, the rest float up.
[[nodiscard]] bool IsBurst(const QString &emoji) {
	return Triggers().indexOf(emoji) >= 3 && Triggers().indexOf(emoji) <= 8;
}

void Play(not_null<QWidget*> parent, const QString &emoji) {
	const auto widget = Ui::CreateChild<Ui::RpWidget>(parent.get());
	widget->setAttribute(Qt::WA_TransparentForMouseEvents);
	widget->setGeometry(QRect(QPoint(), parent->size()));
	widget->show();
	widget->raise();

	const auto burst = IsBurst(emoji);
	const auto width = float64(widget->width());
	const auto height = float64(widget->height());
	auto generator = std::mt19937(std::random_device()());
	auto random = std::uniform_real_distribution<float64>(0., 1.);
	auto particles = std::make_shared<std::vector<Particle>>();
	for (auto i = 0; i != kParticles; ++i) {
		auto particle = Particle();
		particle.size = 22 + random(generator) * 30;
		particle.spin = random(generator) * 120 - 60;
		if (burst) {
			const auto angle = random(generator) * 2 * M_PI;
			const auto speed = 160 + random(generator) * 320;
			particle.x = width / 2;
			particle.y = height * 0.45;
			particle.speedX = std::cos(angle) * speed;
			particle.speedY = std::sin(angle) * speed - 200;
			particle.delay = crl::time(random(generator) * 250);
		} else {
			particle.x = random(generator) * width;
			particle.y = height + particle.size;
			particle.speedX = random(generator) * 60 - 30;
			particle.speedY = -(380 + random(generator) * 320);
			particle.delay = crl::time(random(generator) * 900);
		}
		particles->push_back(particle);
	}

	const auto started = crl::now();
	const auto animation = widget->lifetime().make_state<Ui::Animations::Basic>(
		[=] { widget->update(); });
	widget->paintRequest() | rpl::on_next([=] {
		auto p = QPainter(widget);
		const auto elapsed = crl::now() - started;
		const auto gravity = burst ? 520. : 0.;
		for (const auto &particle : *particles) {
			const auto t = (elapsed - particle.delay) / 1000.;
			if (t < 0) {
				continue;
			}
			const auto x = particle.x + particle.speedX * t;
			const auto y = particle.y + particle.speedY * t + gravity * t * t / 2;
			const auto life = (elapsed - particle.delay)
				/ float64(kDuration - particle.delay);
			p.setOpacity(std::clamp((1. - life) * 2.5, 0., 1.));
			auto font = p.font();
			font.setPixelSize(int(particle.size));
			p.setFont(font);
			p.save();
			p.translate(x, y);
			p.rotate(particle.spin * t);
			p.drawText(QPointF(-particle.size / 2, 0), emoji);
			p.restore();
		}
	}, widget->lifetime());
	animation->start();
	base::call_delayed(kDuration, widget, [=] { delete widget; });
}

} // namespace

bool FullscreenEffects() {
	return Core::App().settings().readPref<bool>(kEnabledPref, true);
}

void SetFullscreenEffects(bool enabled) {
	Core::App().settings().writePref<bool>(kEnabledPref, enabled);
	Core::App().saveSettingsDelayed();
}

void SetupFullscreenEffects(not_null<Main::Session*> session) {
	session->data().newItemAdded(
	) | rpl::on_next([=](not_null<HistoryItem*> item) {
		if (!FullscreenEffects()) {
			return;
		}
		const auto text = item->originalText().text.trimmed();
		if (!Triggers().contains(text)) {
			return;
		}
		const auto window = Core::App().activeWindow();
		if (!window || !window->widget()->isActiveWindow()) {
			return;
		}
		const auto controller = window->sessionController();
		if (!controller
			|| controller->activeChatCurrent().peer() != item->history()->peer) {
			return;
		}
		Play(window->widget(), text);
	}, session->lifetime());
}

} // namespace Lumen
