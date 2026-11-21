// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.zone.ZoneRulesProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TimeZone;

/** Time-zone lookup, tzdb version query, and {@link LocalTime} interval helpers. */
public class TimeUtils {
    private static final Map<String, List<String>> COUNTRY_ZONES = new HashMap<>();

    static {
        putZones("US", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Phoenix", "America/Anchorage", "Pacific/Honolulu");
        putZones("CA", "America/Toronto", "America/Vancouver", "America/Edmonton", "America/Winnipeg", "America/Halifax", "America/St_Johns");
        putZones("GB", "Europe/London");
        putZones("IE", "Europe/Dublin");
        putZones("FR", "Europe/Paris");
        putZones("DE", "Europe/Berlin");
        putZones("ES", "Europe/Madrid", "Atlantic/Canary");
        putZones("IT", "Europe/Rome");
        putZones("NL", "Europe/Amsterdam");
        putZones("CH", "Europe/Zurich");
        putZones("SE", "Europe/Stockholm");
        putZones("PL", "Europe/Warsaw");
        putZones("UA", "Europe/Kyiv", "Europe/Kiev");
        putZones("TR", "Europe/Istanbul");
        putZones("RU", "Europe/Moscow", "Asia/Yekaterinburg", "Asia/Novosibirsk", "Asia/Vladivostok");
        putZones("IR", "Asia/Tehran");
        putZones("AE", "Asia/Dubai");
        putZones("IL", "Asia/Jerusalem");
        putZones("IN", "Asia/Kolkata", "Asia/Calcutta");
        putZones("CN", "Asia/Shanghai", "Asia/Urumqi");
        putZones("HK", "Asia/Hong_Kong");
        putZones("TW", "Asia/Taipei");
        putZones("SG", "Asia/Singapore");
        putZones("KR", "Asia/Seoul");
        putZones("JP", "Asia/Tokyo");
        putZones("AU", "Australia/Sydney", "Australia/Melbourne", "Australia/Brisbane", "Australia/Adelaide", "Australia/Perth", "Australia/Darwin");
        putZones("NZ", "Pacific/Auckland", "Pacific/Chatham");
        putZones("BR", "America/Sao_Paulo", "America/Manaus", "America/Fortaleza");
        putZones("MX", "America/Mexico_City", "America/Cancun", "America/Tijuana");
        putZones("AR", "America/Argentina/Buenos_Aires");
        putZones("ZA", "Africa/Johannesburg");
        putZones("EG", "Africa/Cairo");
    }

    private static void putZones(String country, String... ids) {
        COUNTRY_ZONES.put(country, Collections.unmodifiableList(Arrays.asList(ids.clone())));
    }

    public TimeUtils() {}

    public static TimeZone getTimeZone(int offset, boolean dst, long when, String country) {
        if (country == null) {
            return null;
        }
        List<String> candidates = getTimeZoneIdsForCountryCode(country);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Date current = new Date(when);
        TimeZone currentDefault = TimeZone.getDefault();
        if (currentDefault != null
                && candidates.contains(currentDefault.getID())
                && currentDefault.getOffset(when) == offset
                && currentDefault.inDaylightTime(current) == dst) {
            return currentDefault;
        }
        for (String id : candidates) {
            TimeZone tz = TimeZone.getTimeZone(id);
            if (id.equals(tz.getID()) || "GMT".equals(id) || "UTC".equals(id)) {
                if (tz.getOffset(when) == offset && tz.inDaylightTime(current) == dst) {
                    return tz;
                }
                continue;
            }
            TimeZone icuBacked = matchIcuTimeZone(id, offset, dst, when);
            if (icuBacked != null) {
                return icuBacked;
            }
        }
        return null;
    }

    private static TimeZone matchIcuTimeZone(String id, int offset, boolean dst, long when) {
        try {
            Class<?> icuClass = Class.forName("android.icu.util.TimeZone");
            Method getTz = icuClass.getMethod("getTimeZone", String.class);
            Method getId = icuClass.getMethod("getID");
            Method getOffset = icuClass.getMethod("getOffset", long.class, boolean.class, int[].class);
            Object icuTz = getTz.invoke(null, id);
            if (icuTz == null || !id.equals(getId.invoke(icuTz))) {
                return null;
            }
            int[] parts = new int[2];
            getOffset.invoke(icuTz, Long.valueOf(when), Boolean.FALSE, parts);
            int totalOffset = parts[0] + parts[1];
            boolean inDst = parts[1] != 0;
            if (totalOffset == offset && inDst == dst) {
                final int rawOffset = parts[0];
                final int dstSavings = parts[1];
                return new TimeZone() {
                    private static final long serialVersionUID = 1L;
                    {
                        setID(id);
                    }

                    @Override
                    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) {
                        return rawOffset + dstSavings;
                    }

                    @Override
                    public int getOffset(long date) {
                        try {
                            int[] dynamicParts = new int[2];
                            getOffset.invoke(icuTz, Long.valueOf(date), Boolean.FALSE, dynamicParts);
                            return dynamicParts[0] + dynamicParts[1];
                        } catch (Throwable ignored) {
                            return rawOffset + dstSavings;
                        }
                    }

                    @Override
                    public void setRawOffset(int offsetMillis) {}

                    @Override
                    public int getRawOffset() {
                        return rawOffset;
                    }

                    @Override
                    public boolean useDaylightTime() {
                        return dstSavings != 0;
                    }

                    @Override
                    public boolean inDaylightTime(Date date) {
                        try {
                            int[] dynamicParts = new int[2];
                            getOffset.invoke(icuTz, Long.valueOf(date.getTime()), Boolean.FALSE, dynamicParts);
                            return dynamicParts[1] != 0;
                        } catch (Throwable ignored) {
                            return dstSavings != 0;
                        }
                    }
                };
            }
        } catch (Throwable ignored) {
            // ICU time-zone lookup unavailable.
        }
        return null;
    }

    public static List<String> getTimeZoneIdsForCountryCode(String countryCode) {
        if (countryCode == null) {
            throw new NullPointerException("countryCode == null");
        }
        String upper = countryCode.toUpperCase(Locale.ROOT);
        if (upper.length() != 2) {
            return null;
        }
        // Prefer ART's ICU country-to-timezone database when available at runtime.
        try {
            Class<?> icuTimeZone = Class.forName("android.icu.util.TimeZone");
            Method method = icuTimeZone.getMethod("getAvailableIDs", String.class);
            String[] ids = (String[]) method.invoke(null, upper);
            if (ids != null && ids.length > 0) {
                return Collections.unmodifiableList(Arrays.asList(ids.clone()));
            }
        } catch (Throwable ignored) {
            // Fall back to curated ISO-3166 table on OpenJDK hosts without android.icu.
        }
        return COUNTRY_ZONES.get(upper);
    }

    public static String getTimeZoneDatabaseVersion() {
        try {
            NavigableMap<String, ?> versions = ZoneRulesProvider.getVersions("UTC");
            if (versions != null && !versions.isEmpty()) {
                return versions.lastKey();
            }
        } catch (Throwable ignored) {
            // Fall through to ICU / fallback check.
        }
        try {
            Class<?> icuTimeZone = Class.forName("android.icu.util.TimeZone");
            Method method = icuTimeZone.getMethod("getTZDataVersion");
            Object version = method.invoke(null);
            if (version instanceof String && !((String) version).isEmpty()) {
                return (String) version;
            }
        } catch (Throwable ignored) {
            // Ignore.
        }
        return "unknown";
    }

    public static boolean isTimeBetween(LocalTime reference, LocalTime start, LocalTime end) {
        Objects.requireNonNull(reference, "reference == null");
        Objects.requireNonNull(start, "start == null");
        Objects.requireNonNull(end, "end == null");
        if ((reference.isBefore(start) && reference.isAfter(end))
                || (reference.isBefore(end) && reference.isBefore(start) && start.isBefore(end))
                || (reference.isAfter(end) && reference.isAfter(start) && start.isBefore(end))) {
            return false;
        }
        return true;
    }
}
