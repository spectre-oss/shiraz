// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.os.Parcel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class ServiceInfo {
    private final Map<Locale, String> names;
    private final String className;
    private final List<Locale> locales;
    private final String serviceId;
    private final Date sessionStartTime;
    private final Date sessionEndTime;

    ServiceInfo(
            Map<Locale, String> newNames,
            String newClassName,
            List<Locale> newLocales,
            String newServiceId,
            Date start,
            Date end) {
        if (newNames == null || newClassName == null || newLocales == null
                || newServiceId == null || start == null || end == null) {
            throw new IllegalArgumentException("Bad ServiceInfo construction");
        }
        names = Collections.unmodifiableMap(new HashMap<Locale, String>(newNames));
        className = newClassName;
        locales = Collections.unmodifiableList(new ArrayList<Locale>(newLocales));
        serviceId = newServiceId;
        sessionStartTime = (Date) start.clone();
        sessionEndTime = (Date) end.clone();
    }

    ServiceInfo(Parcel in) {
        int mapCount = in.readInt();
        Map<Locale, String> parsedNames = new HashMap<Locale, String>(Math.max(0, mapCount));
        for (int i = 0; i < mapCount; i++) {
            Locale loc = Locale.forLanguageTag(in.readString());
            String name = in.readString();
            parsedNames.put(loc, name);
        }
        names = Collections.unmodifiableMap(parsedNames);
        className = in.readString();
        int locCount = in.readInt();
        List<Locale> parsedLocales = new ArrayList<Locale>(Math.max(0, locCount));
        for (int i = 0; i < locCount; i++) {
            parsedLocales.add(Locale.forLanguageTag(in.readString()));
        }
        locales = Collections.unmodifiableList(parsedLocales);
        serviceId = in.readString();
        long startMs = in.readLong();
        sessionStartTime = startMs >= 0 ? new Date(startMs) : null;
        long endMs = in.readLong();
        sessionEndTime = endMs >= 0 ? new Date(endMs) : null;
    }

    void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(names.size());
        for (Map.Entry<Locale, String> e : names.entrySet()) {
            dest.writeString(e.getKey().toLanguageTag());
            dest.writeString(e.getValue());
        }
        dest.writeString(className);
        dest.writeInt(locales.size());
        for (int i = 0; i < locales.size(); i++) {
            dest.writeString(locales.get(i).toLanguageTag());
        }
        dest.writeString(serviceId);
        dest.writeLong(sessionStartTime != null ? sessionStartTime.getTime() : -1L);
        dest.writeLong(sessionEndTime != null ? sessionEndTime.getTime() : -1L);
    }

    public CharSequence getNameForLocale(Locale locale) {
        if (!names.containsKey(locale)) {
            throw new NoSuchElementException("Locale not present");
        }
        return names.get(locale);
    }

    public Set<Locale> getNamedContentLocales() {
        return names.keySet();
    }

    public String getServiceClassName() {
        return className;
    }

    public List<Locale> getLocales() {
        return locales;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Date getSessionStartTime() {
        return sessionStartTime != null ? (Date) sessionStartTime.clone() : null;
    }

    public Date getSessionEndTime() {
        return sessionEndTime != null ? (Date) sessionEndTime.clone() : null;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceInfo)) return false;
        ServiceInfo that = (ServiceInfo) o;
        return Objects.equals(names, that.names)
                && Objects.equals(className, that.className)
                && Objects.equals(locales, that.locales)
                && Objects.equals(serviceId, that.serviceId)
                && Objects.equals(sessionStartTime, that.sessionStartTime)
                && Objects.equals(sessionEndTime, that.sessionEndTime);
    }

    public int hashCode() {
        return Objects.hash(names, className, locales, serviceId, sessionStartTime, sessionEndTime);
    }
}
