// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.StrictMode;
import java.util.*;

/** Compiled against the unmodified Android SDK. No Shiraz entry points. */
final class NotificationFixture {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void check(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        check(manager != null && manager == context.getSystemService(Context.NOTIFICATION_SERVICE), "notification service identity");
        check(Context.NOTIFICATION_SERVICE.equals(context.getSystemServiceName(NotificationManager.class)), "typed notification service name");
        String id = "fixture-channel";
        NotificationChannel channel = new NotificationChannel(id, "fixture", NotificationManager.IMPORTANCE_MIN);
        channel.setBypassDnd(true); channel.setAllowBubbles(true);
        channel.setDescription("description 😀"); channel.setShowBadge(false); channel.enableLights(true); channel.setLightColor(0xff123456);
        channel.setLockscreenVisibility(0); long[] pattern = {0, 5, 10}; channel.setVibrationPattern(pattern); pattern[1] = 100;
        check(channel.getVibrationPattern()[1] == 5, "channel owns pattern");
        StrictMode.ThreadPolicy old = StrictMode.getThreadPolicy();
        StrictMode.ThreadPolicy strict = new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyDeath().build();
        StrictMode.setThreadPolicy(strict);
        try {
            manager.createNotificationChannel(channel);
            check(manager.getNotificationChannel(id) != null, "strict caller query");
        } finally { StrictMode.setThreadPolicy(old); }
        channel.setName("changed outside"); NotificationChannel stored = manager.getNotificationChannel(id);
        check("fixture".contentEquals(stored.getName()) && "description 😀".equals(stored.getDescription()), "channel input snapshot");
        check(!stored.canBypassDnd() && !stored.canBubble() && stored.getLockscreenVisibility() == -1000, "app cannot grant privileged presentation policy");
        check(stored.getImportance() == NotificationManager.IMPORTANCE_MIN && !stored.canShowBadge() && stored.shouldShowLights() && stored.getLightColor() == 0xff123456, "channel values");
        long[] copied = stored.getVibrationPattern(); copied[1] = 200;
        stored.setName("changed query"); check("fixture".contentEquals(manager.getNotificationChannel(id).getName()), "channel query snapshot");
        check(manager.getNotificationChannel(id).getVibrationPattern()[1] == 5, "pattern query snapshot");
        NotificationChannel update = new NotificationChannel(id, "renamed", NotificationManager.IMPORTANCE_HIGH);
        update.setDescription("updated"); manager.createNotificationChannel(update);
        stored = manager.getNotificationChannel(id);
        check("renamed".contentEquals(stored.getName()) && "updated".equals(stored.getDescription()) && stored.getImportance() == NotificationManager.IMPORTANCE_MIN, "existing channel name/description and importance rules");
        check(stored.shouldVibrate() && !stored.canShowBadge(), "existing settings retained");
        manager.deleteNotificationChannel(id); check(manager.getNotificationChannel(id) == null, "deleted channel hidden");
        manager.createNotificationChannel(update); check(manager.getNotificationChannel(id).shouldVibrate(), "restored channel retains settings");
        List<NotificationChannel> all = manager.getNotificationChannels(); all.clear(); check(manager.getNotificationChannel(id) != null, "list snapshot");
        check(manager.getNotificationChannel("fixture-missing") == null, "missing channel query");
        for (NotificationChannel invalid : Arrays.asList(new NotificationChannel("", "x", 1), new NotificationChannel("bad", "", 1), new NotificationChannel("bad", "x", 6), new NotificationChannel("miscellaneous", "x", 1))) {
            try { manager.createNotificationChannel(invalid); throw new AssertionError("accepted invalid channel"); } catch (IllegalArgumentException expected) { }
        }
        NotificationChannel grouped = new NotificationChannel("grouped", "x", 1); grouped.setGroup("missing");
        try { manager.createNotificationChannel(grouped); throw new AssertionError("accepted nonexistent group"); } catch (IllegalArgumentException expected) { }
        String mode = System.getenv("SHIRAZ_FIXTURE_PREFERENCES");
        if ("write".equals(mode)) manager.createNotificationChannel(new NotificationChannel("fixture-persist", "saved", 2));
        if ("read".equals(mode)) {
            check("saved".contentEquals(manager.getNotificationChannel("fixture-persist").getName()), "channel persisted across processes");
            System.out.println("SHIRAZ_FIXTURE_NOTIFICATION_PERSISTED");
        }
        check(!manager.isNotificationPolicyAccessGranted(), "no fabricated DND policy authority");
        System.out.println("SHIRAZ_FIXTURE_NOTIFICATION_CHANNELS_OK");
    }
}
