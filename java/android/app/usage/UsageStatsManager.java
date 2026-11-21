// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UsageStatsManager {
    public static final int INTERVAL_DAILY = 0;
    public static final int INTERVAL_WEEKLY = 1;
    public static final int INTERVAL_MONTHLY = 2;
    public static final int INTERVAL_YEARLY = 3;
    public static final int INTERVAL_BEST = 4;

    public static final int STANDBY_BUCKET_ACTIVE = 10;
    public static final int STANDBY_BUCKET_WORKING_SET = 20;
    public static final int STANDBY_BUCKET_FREQUENT = 30;
    public static final int STANDBY_BUCKET_RARE = 40;
    public static final int STANDBY_BUCKET_RESTRICTED = 45;

    public static final String EXTRA_EVENT_ACTION = "android.app.usage.extra.EVENT_ACTION";
    public static final String EXTRA_EVENT_CATEGORY = "android.app.usage.extra.EVENT_CATEGORY";

    private final List<UsageStats> mRecordedStats = new ArrayList<UsageStats>();
    private final List<UsageEvents.Event> mRecordedEvents = new ArrayList<UsageEvents.Event>();

    public UsageStatsManager() {
    }

    public synchronized List<UsageStats> queryUsageStats(int intervalType, long beginTime, long endTime) {
        List<UsageStats> out = new ArrayList<UsageStats>();
        for (UsageStats s : mRecordedStats) {
            if (s.getLastTimeStamp() >= beginTime && s.getFirstTimeStamp() <= endTime) {
                out.add(new UsageStats(s));
            }
        }
        return out;
    }

    public List<ConfigurationStats> queryConfigurations(int intervalType, long beginTime, long endTime) {
        return Collections.emptyList();
    }

    public List<EventStats> queryEventStats(int intervalType, long beginTime, long endTime) {
        return Collections.emptyList();
    }

    public synchronized UsageEvents queryEvents(long beginTime, long endTime) {
        List<UsageEvents.Event> matched = new ArrayList<UsageEvents.Event>();
        for (UsageEvents.Event ev : mRecordedEvents) {
            if (ev.getTimeStamp() >= beginTime && ev.getTimeStamp() <= endTime) {
                matched.add(ev);
            }
        }
        return new UsageEvents(matched);
    }

    public synchronized UsageEvents queryEvents(UsageEventsQuery query) {
        if (query == null) throw new NullPointerException("query");
        long begin = query.getBeginTimeMillis();
        long end = query.getEndTimeMillis();
        int[] types = query.getEventTypes();
        Set<String> pkgs = query.getPackageNames();
        List<UsageEvents.Event> matched = new ArrayList<UsageEvents.Event>();
        for (UsageEvents.Event ev : mRecordedEvents) {
            if (ev.getTimeStamp() < begin || ev.getTimeStamp() > end) {
                continue;
            }
            if (types.length > 0) {
                boolean typeOk = false;
                for (int t : types) {
                    if (t == ev.getEventType()) {
                        typeOk = true;
                        break;
                    }
                }
                if (!typeOk) continue;
            }
            if (!pkgs.isEmpty() && !pkgs.contains(ev.getPackageName())) {
                continue;
            }
            matched.add(ev);
        }
        return new UsageEvents(matched);
    }

    public UsageEvents queryEventsForSelf(long beginTime, long endTime) {
        return queryEvents(beginTime, endTime);
    }

    public synchronized Map<String, UsageStats> queryAndAggregateUsageStats(long beginTime, long endTime) {
        List<UsageStats> stats = queryUsageStats(INTERVAL_BEST, beginTime, endTime);
        if (stats.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, UsageStats> aggregated = new LinkedHashMap<String, UsageStats>();
        for (UsageStats stat : stats) {
            UsageStats existing = aggregated.get(stat.getPackageName());
            if (existing == null) {
                aggregated.put(stat.getPackageName(), stat);
            } else {
                existing.add(stat);
            }
        }
        return aggregated;
    }

    public boolean isAppInactive(String packageName) {
        return false;
    }

    public int getAppStandbyBucket() {
        return STANDBY_BUCKET_ACTIVE;
    }
}
