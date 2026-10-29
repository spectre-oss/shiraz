// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.*;

public final class LocaleList implements Parcelable {
    private final Locale[] locales;
    private static final LocaleList EMPTY = new LocaleList();
    public LocaleList(Locale... values) {
        if (values == null) throw new NullPointerException("locales");
        if (values.length > 64) throw new IllegalArgumentException("Too many locales");
        LinkedHashSet<Locale> unique = new LinkedHashSet<Locale>();
        for (Locale value : values) unique.add(Objects.requireNonNull(value, "locale"));
        locales = unique.toArray(new Locale[unique.size()]);
    }
    public Locale get(int index) { return index < 0 || index >= locales.length ? null : locales[index]; }
    public int size() { return locales.length; }
    public boolean isEmpty() { return locales.length == 0; }
    public int indexOf(Locale locale) { for (int i = 0; i < locales.length; ++i) if (locales[i].equals(locale)) return i; return -1; }
    public String toLanguageTags() {
        StringBuilder out = new StringBuilder();
        for (Locale value : locales) { if (out.length() != 0) out.append(','); out.append(value.toLanguageTag()); }
        return out.toString();
    }
    public static LocaleList getEmptyLocaleList() { return EMPTY; }
    public static LocaleList forLanguageTags(String tags) {
        if (tags == null || tags.isEmpty()) return EMPTY;
        String[] parts = tags.split(",", 65);
        if (parts.length > 64) throw new IllegalArgumentException("Too many locales");
        Locale[] values = new Locale[parts.length];
        for (int i = 0; i < parts.length; ++i) values[i] = Locale.forLanguageTag(parts[i]);
        return new LocaleList(values);
    }
    private static Locale lastDefault;
    private static LocaleList explicitDefault, defaultList, adjustedDefault;
    private static LocaleList withFirst(Locale first, LocaleList rest) {
        LinkedHashSet<Locale> ordered = new LinkedHashSet<Locale>();
        ordered.add(first);
        if (rest != null) Collections.addAll(ordered, rest.locales);
        // Leave room for an externally selected Java default at the existing bound.
        Locale[] values = ordered.toArray(new Locale[ordered.size()]);
        return new LocaleList(values.length > 64 ? Arrays.copyOf(values, 64) : values);
    }
    public static synchronized LocaleList getDefault() {
        Locale current = Locale.getDefault();
        if (!current.equals(lastDefault)) {
            lastDefault = current;
            if (defaultList == null || !current.equals(defaultList.get(0)))
                defaultList = withFirst(current, explicitDefault);
            adjustedDefault = withFirst(current, defaultList);
        }
        return defaultList;
    }
    public static synchronized LocaleList getAdjustedDefault() {
        getDefault();
        return adjustedDefault;
    }
    public static void setDefault(LocaleList values) { setDefault(values, 0); }
    public static synchronized void setDefault(LocaleList values, int index) {
        Objects.requireNonNull(values, "locales");
        if (values.isEmpty()) throw new IllegalArgumentException("Empty default locales");
        Locale selected = Objects.requireNonNull(values.get(index), "default locale index");
        Locale.setDefault(selected);
        lastDefault = selected;
        explicitDefault = defaultList = values;
        adjustedDefault = index == 0 ? values : withFirst(selected, values);
    }
    @Override public boolean equals(Object other) { return other instanceof LocaleList && Arrays.equals(locales, ((LocaleList)other).locales); }
    @Override public int hashCode() { return Arrays.hashCode(locales); }
    @Override public String toString() { return Arrays.toString(locales); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("LocaleList parcel transport is not implemented"); }
}
