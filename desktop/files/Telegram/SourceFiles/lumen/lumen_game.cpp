/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_game.h"

#include "api/api_common.h"
#include "apiwrap.h"
#include "core/application.h"
#include "core/click_handler_types.h"
#include "data/data_session.h"
#include "data/data_user.h"
#include "history/history.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "ui/layers/generic_box.h"
#include "ui/painter.h"
#include "ui/rp_widget.h"
#include "window/window_controller.h"
#include "window/window_session_controller.h"
#include "styles/style_layers.h"

#include <QtCore/QUrlQuery>

namespace Lumen {
namespace {

constexpr auto kSize = 3;
constexpr auto kCell = 72;
constexpr auto kGap = 8;

struct Game {
	QString board = u"---------"_q;
	uint64 xId = 0;
	uint64 oId = 0;
};

[[nodiscard]] QChar Turn(const Game &game) {
	return (game.board.count('x') == game.board.count('o')) ? 'x' : 'o';
}

[[nodiscard]] QChar Winner(const Game &game) {
	static const int lines[8][3] = {
		{ 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 },
		{ 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 },
		{ 0, 4, 8 }, { 2, 4, 6 },
	};
	for (const auto &line : lines) {
		const auto first = game.board[line[0]];
		if (first != '-'
			&& first == game.board[line[1]]
			&& first == game.board[line[2]]) {
			return first;
		}
	}
	return '-';
}

[[nodiscard]] QString Mark(QChar cell) {
	return (cell == 'x')
		? QString::fromUtf8("\xE2\x9C\x96\xEF\xB8\x8F")
		: (cell == 'o')
		? QString::fromUtf8("\xE2\xAD\x95")
		: QString();
}

[[nodiscard]] QString Url(const Game &game) {
	return u"https://tunexa.ru/lumengram/game?g=ttt&b=%1&x=%2&o=%3"_q
		.arg(game.board)
		.arg(game.xId)
		.arg(game.oId);
}

void Send(
		not_null<Main::Session*> session,
		const Game &game,
		QChar me) {
	const auto mark = Mark(me);
	const auto filled = 9 - game.board.count('-');
	const auto status = (Winner(game) == me)
		? tr::lng_lumen_game_msg_won(tr::now, lt_mark, mark)
		: !game.board.contains('-')
		? tr::lng_lumen_game_msg_draw(tr::now)
		: (filled == 1)
		? tr::lng_lumen_game_msg_invite(tr::now)
		: tr::lng_lumen_game_msg_move(tr::now, lt_mark, mark);
	const auto opponent = (session->userId().bare == game.xId)
		? game.oId
		: game.xId;
	const auto history = session->data().history(
		peerFromUser(UserId(opponent)));
	auto message = Api::MessageToSend(Api::SendAction(history));
	message.textWithTags = { status + '\n' + Url(game) };
	message.webPage.removed = true;
	session->api().sendMessage(std::move(message));
}

void ShowGame(
		not_null<Window::SessionController*> controller,
		Game game) {
	const auto session = &controller->session();
	const auto self = session->userId().bare;
	const auto me = (self == game.xId)
		? QChar('x')
		: (self == game.oId)
		? QChar('o')
		: QChar('-');
	const auto winner = Winner(game);
	const auto over = (winner != '-') || !game.board.contains('-');
	const auto myTurn = !over && me != '-' && Turn(game) == me;
	const auto status = over
		? ((winner == '-')
			? tr::lng_lumen_game_draw(tr::now)
			: (winner == me)
			? tr::lng_lumen_game_you_won(tr::now)
			: (me == '-')
			? tr::lng_lumen_game_won(tr::now, lt_mark, Mark(winner))
			: tr::lng_lumen_game_you_lost(tr::now))
		: (me == '-')
		? tr::lng_lumen_game_watching(tr::now)
		: myTurn
		? tr::lng_lumen_game_your_turn(tr::now, lt_mark, Mark(me))
		: tr::lng_lumen_game_wait(tr::now);

	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_game_title());
		box->addRow(object_ptr<Ui::FlatLabel>(
			box,
			status,
			st::boxLabel));
		const auto side = kSize * kCell + (kSize - 1) * kGap;
		const auto board = box->addRow(
			object_ptr<Ui::RpWidget>(box),
			style::al_top);
		board->resize(side, side);
		board->setCursor(myTurn ? style::cur_pointer : style::cur_default);
		board->paintRequest() | rpl::on_next([=] {
			auto p = QPainter(board);
			auto hq = PainterHighQualityEnabler(p);
			auto font = p.font();
			font.setPixelSize(36);
			p.setFont(font);
			for (auto i = 0; i != kSize * kSize; ++i) {
				const auto rect = QRect(
					(i % kSize) * (kCell + kGap),
					(i / kSize) * (kCell + kGap),
					kCell,
					kCell);
				p.setPen(Qt::NoPen);
				p.setBrush(st::windowBgOver);
				p.drawRoundedRect(rect, 12, 12);
				p.setPen(st::windowFg);
				p.drawText(rect, Qt::AlignCenter, Mark(game.board[i]));
			}
		}, board->lifetime());
		board->events() | rpl::filter([](not_null<QEvent*> e) {
			return e->type() == QEvent::MouseButtonRelease;
		}) | rpl::on_next([=](not_null<QEvent*> e) {
			if (!myTurn) {
				return;
			}
			const auto pos = static_cast<QMouseEvent*>(e.get())->pos();
			const auto column = pos.x() / (kCell + kGap);
			const auto row = pos.y() / (kCell + kGap);
			const auto index = row * kSize + column;
			if (column >= kSize
				|| row >= kSize
				|| game.board[index] != '-') {
				return;
			}
			auto next = game;
			next.board[index] = me;
			Send(session, next, me);
			box->closeBox();
		}, board->lifetime());
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
	}));
}

} // namespace

bool CanStartGame(not_null<PeerData*> peer) {
	const auto user = peer->asUser();
	return user && !user->isBot() && !user->isSelf();
}

void StartGame(
		not_null<Window::SessionController*> controller,
		not_null<PeerData*> peer) {
	auto game = Game();
	game.xId = controller->session().userId().bare;
	game.oId = peerToUser(peer->id).bare;
	ShowGame(controller, game);
}

bool HandleGameUrl(const QString &url, const QVariant &context) {
	const auto parsed = QUrl(url);
	if (parsed.host() != u"tunexa.ru"_q
		|| parsed.path() != u"/lumengram/game"_q) {
		return false;
	}
	const auto query = QUrlQuery(parsed);
	auto game = Game();
	game.board = query.queryItemValue(u"b"_q);
	game.xId = query.queryItemValue(u"x"_q).toULongLong();
	game.oId = query.queryItemValue(u"o"_q).toULongLong();
	static const auto kBoard = QRegularExpression(u"^[xo-]{9}$"_q);
	if (query.queryItemValue(u"g"_q) != u"ttt"_q
		|| !kBoard.match(game.board).hasMatch()
		|| !game.xId
		|| !game.oId) {
		return false;
	}
	auto controller = context.value<ClickHandlerContext>().sessionWindow.get();
	if (!controller) {
		if (const auto window = Core::App().activeWindow()) {
			controller = window->sessionController();
		}
	}
	if (!controller) {
		return false;
	}
	ShowGame(controller, game);
	return true;
}

} // namespace Lumen
