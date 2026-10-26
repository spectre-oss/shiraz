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
    public static LocaleList getDefault() { return new LocaleList(Locale.getDefault()); }
    public static LocaleList getAdjustedDefault() { return getDefault(); }
    @Override public boolean equals(Object other) { return other instanceof LocaleList && Arrays.equals(locales, ((LocaleList)other).locales); }
    @Override public int hashCode() { return Arrays.hashCode(locales); }
    @Override public String toString() { return Arrays.toString(locales); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("LocaleList parcel transport is not implemented"); }
}
