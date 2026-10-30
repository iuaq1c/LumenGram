/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.telegram.messenger.LocaleController;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

// Telegram does not expose the registration date. User IDs are handed out roughly in order,
// so the date is interpolated between rough reference points (ID -> month the ID range was in use).
// The same table as the web version; expect an error of a few months.
public final class LumenRegistration {

    private static final long[] IDS = {
        0L, 100_000_000L, 200_000_000L, 400_000_000L, 700_000_000L,
        1_000_000_000L, 1_300_000_000L, 1_600_000_000L,
        1_638_932_794L, // verified by a real account
        1_900_000_000L, 2_147_483_647L, 5_000_000_000L, 5_500_000_000L,
        6_000_000_000L, 6_500_000_000L, 7_000_000_000L, 7_500_000_000L,
        8_000_000_000L, 8_500_000_000L, 9_000_000_000L,
    };
    private static final int[][] MONTHS = {
        {2013, 8}, {2015, 1}, {2016, 4}, {2017, 7}, {2018, 10},
        {2019, 12}, {2020, 7}, {2021, 1},
        {2021, 2},
        {2021, 7}, {2022, 2}, {2022, 3}, {2022, 10},
        {2023, 2}, {2023, 8}, {2024, 1}, {2024, 8},
        {2025, 1}, {2025, 7}, {2026, 1},
    };

    private LumenRegistration() {
    }

    private static long toTime(int[] month) {
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(month[0], month[1] - 1, 1);
        return calendar.getTimeInMillis();
    }

    // Milliseconds since epoch, or 0 for ids that are not users.
    public static long estimate(long userId) {
        if (userId <= 0) {
            return 0;
        }
        final long now = System.currentTimeMillis();
        for (int i = 1; i < IDS.length; i++) {
            if (userId > IDS[i]) {
                continue;
            }
            final long from = toTime(MONTHS[i - 1]);
            final long to = toTime(MONTHS[i]);
            final double part = (double) (userId - IDS[i - 1]) / (IDS[i] - IDS[i - 1]);
            return Math.min(from + (long) (part * (to - from)), now);
        }
        final int last = IDS.length - 1;
        final double perId = (double) (toTime(MONTHS[last]) - toTime(MONTHS[last - 1])) / (IDS[last] - IDS[last - 1]);
        return Math.min(toTime(MONTHS[last]) + (long) ((userId - IDS[last]) * perId), now);
    }

    // "≈ March 2021": month and year, the day would be false precision.
    public static String format(long time) {
        // LLLL is the standalone month name: "март", not "марта"
        final SimpleDateFormat format = new SimpleDateFormat("LLLL yyyy", LocaleController.getInstance().getCurrentLocale());
        return "≈ " + format.format(new Date(time));
    }
}
