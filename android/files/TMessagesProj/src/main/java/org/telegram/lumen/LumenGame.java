/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

// Tic-tac-toe in a private chat. Every move is a message with a tunexa.ru link that holds the whole
// board: LumenGram opens it as a game, other apps open a page that explains it.
public class LumenGame {

    private static final String HOST = "tunexa.ru";
    private static final String PATH = "/lumengram/game";
    private static final int SIZE = 3;
    private static final int[][] LINES = {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
        {0, 4, 8}, {2, 4, 6},
    };
    private static final char EMPTY = '-';
    private static final char X = 'x';
    private static final char O = 'o';

    private static class Game {
        char[] board = "---------".toCharArray();
        long xId;
        long oId;

        char turn() {
            int xs = 0;
            int os = 0;
            for (char cell : board) {
                if (cell == X) {
                    xs++;
                } else if (cell == O) {
                    os++;
                }
            }
            return xs == os ? X : O;
        }

        char winner() {
            for (int[] line : LINES) {
                final char first = board[line[0]];
                if (first != EMPTY && first == board[line[1]] && first == board[line[2]]) {
                    return first;
                }
            }
            return EMPTY;
        }

        boolean full() {
            for (char cell : board) {
                if (cell == EMPTY) {
                    return false;
                }
            }
            return true;
        }

        String url() {
            return "https://" + HOST + PATH + "?g=ttt&b=" + new String(board) + "&x=" + xId + "&o=" + oId;
        }
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    private static String mark(char cell) {
        return cell == X ? "✖️" : cell == O ? "⭕" : "";
    }

    public static boolean canStart(BaseFragment fragment, long dialogId) {
        if (!DialogObject.isUserDialog(dialogId)) {
            return false;
        }
        final TLRPC.User user = fragment.getMessagesController().getUser(dialogId);
        return user != null && !user.bot && !UserObject.isUserSelf(user);
    }

    // The one who starts plays ✖️ and makes the first move
    public static void start(BaseFragment fragment, long dialogId) {
        final Game game = new Game();
        game.xId = UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
        game.oId = dialogId;
        show(fragment, game);
    }

    // Opens game links instead of the browser; returns true when the link was a game
    public static boolean handle(Uri uri) {
        if (uri == null || !HOST.equals(uri.getHost()) || !PATH.equals(uri.getPath()) || !"ttt".equals(uri.getQueryParameter("g"))) {
            return false;
        }
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            return false;
        }
        final Game game = new Game();
        final String board = uri.getQueryParameter("b");
        if (board == null || !board.matches("[xo-]{9}")) {
            return false;
        }
        game.board = board.toCharArray();
        try {
            game.xId = Long.parseLong(uri.getQueryParameter("x"));
            game.oId = Long.parseLong(uri.getQueryParameter("o"));
        } catch (Exception e) {
            return false;
        }
        AndroidUtilities.runOnUIThread(() -> show(fragment, game));
        return true;
    }

    private static void show(BaseFragment fragment, Game game) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final long selfId = UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
        final char me = selfId == game.xId ? X : selfId == game.oId ? O : EMPTY;
        final char winner = game.winner();
        final boolean over = winner != EMPTY || game.full();
        final boolean myTurn = !over && me != EMPTY && game.turn() == me;

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(t("GameTitle"));
        if (over) {
            builder.setMessage(winner == EMPTY ? t("GameDraw") : winner == me ? t("GameYouWon") : me == EMPTY ? LumenLang.format("GameWon", "mark", mark(winner)) : t("GameYouLost"));
        } else {
            builder.setMessage(me == EMPTY ? t("GameWatching") : myTurn ? LumenLang.format("GameYourTurn", "mark", mark(me)) : t("GameWait"));
        }

        final GridLayout grid = new GridLayout(context);
        grid.setColumnCount(SIZE);
        grid.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        final AlertDialog[] dialog = new AlertDialog[1];
        final int cellSize = AndroidUtilities.dp(72);
        for (int i = 0; i < SIZE * SIZE; i++) {
            final int index = i;
            final TextView cell = new TextView(context);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 34);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setText(mark(game.board[i]));
            cell.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()), 0.08f)));
            final GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            grid.addView(cell, params);
            if (myTurn && game.board[i] == EMPTY) {
                cell.setOnClickListener(v -> {
                    game.board[index] = me;
                    send(fragment, game, me, selfId == game.xId ? game.oId : game.xId);
                    if (dialog[0] != null) {
                        dialog[0].dismiss();
                    }
                });
            }
        }
        final android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.addView(grid, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        builder.setView(container);
        builder.setPositiveButton(t("GameClose"), null);
        dialog[0] = builder.create();
        fragment.showDialog(dialog[0]);
    }

    private static void send(BaseFragment fragment, Game game, char me, long opponentId) {
        final char winner = game.winner();
        final String status;
        if (winner == me) {
            status = LumenLang.format("GameMsgWon", "mark", mark(me));
        } else if (game.full()) {
            status = t("GameMsgDraw");
        } else if (new String(game.board).replace("-", "").length() == 1) {
            status = t("GameMsgInvite");
        } else {
            status = LumenLang.format("GameMsgMove", "mark", mark(me));
        }
        final String text = status + "\n" + game.url();
        SendMessagesHelper.getInstance(fragment.getCurrentAccount()).sendMessage(SendMessagesHelper.SendMessageParams.of(
            text, opponentId, null, null, null, false, null, null, null, true, 0, 0, null, false));
    }
}
