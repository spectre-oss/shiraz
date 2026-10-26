// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.net.Uri;
import android.os.*;
import java.util.*;

/** Action, category, MIME and scheme matching; authority/path patterns remain explicit gaps. */
public class IntentFilter implements Parcelable {
    public static final int MATCH_ADJUSTMENT_MASK = 65535, MATCH_ADJUSTMENT_NORMAL = 32768,
        MATCH_CATEGORY_EMPTY = 0x100000, MATCH_CATEGORY_SCHEME = 0x200000, MATCH_CATEGORY_HOST = 0x300000,
        MATCH_CATEGORY_PORT = 0x400000, MATCH_CATEGORY_PATH = 0x500000, MATCH_CATEGORY_SCHEME_SPECIFIC_PART = 0x580000,
        MATCH_CATEGORY_TYPE = 0x600000, MATCH_CATEGORY_MASK = 0xfff0000,
        NO_MATCH_TYPE = -1, NO_MATCH_DATA = -2, NO_MATCH_ACTION = -3, NO_MATCH_CATEGORY = -4,
        SYSTEM_HIGH_PRIORITY = 1000, SYSTEM_LOW_PRIORITY = -1000;
    private final ArrayList<String> actions = new ArrayList<String>(), categories = new ArrayList<String>(), types = new ArrayList<String>(), schemes = new ArrayList<String>();
    private int priority, textBytes;
    public IntentFilter() { }
    public IntentFilter(String action) { addAction(action); }
    public IntentFilter(String action, String type) throws MalformedMimeTypeException { addAction(action); addDataType(type); }
    public IntentFilter(IntentFilter source) { actions.addAll(source.actions); categories.addAll(source.categories); types.addAll(source.types); schemes.addAll(source.schemes); priority = source.priority; textBytes = source.textBytes; }
    public static IntentFilter create(String action, String type) { try { return new IntentFilter(action, type); } catch (MalformedMimeTypeException error) { throw new IllegalArgumentException(error); } }
    public final int shirazByteSize() { return textBytes + 256; }
    public final void setPriority(int value) { priority = value; }
    public final int getPriority() { return priority; }
    private void add(ArrayList<String> values, String value) { Objects.requireNonNull(value); if (values.contains(value)) return; if (value.length() > 65536 || values.size() >= 1024 || value.length() * 2L + 32 > 131072 - textBytes) throw new IllegalArgumentException("Intent filter exceeds host limit"); values.add(value); textBytes += value.length() * 2 + 32; }
    public final void addAction(String value) { add(actions, value); }
    public final int countActions() { return actions.size(); }
    public final String getAction(int index) { return actions.get(index); }
    public final boolean hasAction(String action) { return actions.contains(action); }
    public final boolean matchAction(String action) { return hasAction(action); }
    private static Iterator<String> iterator(ArrayList<String> values) { return values.isEmpty() ? null : Collections.unmodifiableList(new ArrayList<String>(values)).iterator(); }
    public final Iterator<String> actionsIterator() { return iterator(actions); }
    public final void addCategory(String value) { add(categories, value); }
    public final int countCategories() { return categories.size(); }
    public final String getCategory(int index) { return categories.get(index); }
    public final boolean hasCategory(String category) { return categories.contains(category); }
    public final Iterator<String> categoriesIterator() { return iterator(categories); }
    public final String matchCategories(Set<String> intentCategories) { if (intentCategories != null) for (String category : intentCategories) if (!categories.contains(category)) return category; return null; }
    public static class MalformedMimeTypeException extends Exception { public MalformedMimeTypeException(String type) { super(type); } }
    public final void addDataType(String type) throws MalformedMimeTypeException {
        int slash = type == null ? -1 : type.indexOf('/');
        if (slash <= 0 || slash == type.length() - 1 || type.indexOf('/', slash + 1) >= 0) throw new MalformedMimeTypeException(type);
        add(types, type);
    }
    private boolean mime(String type) {
        if (type == null) return false;
        for (String candidate : types) {
            if (candidate.equals(type) || candidate.equals("*/*") || type.equals("*/*")) return true;
            int slash = type.indexOf('/'), other = candidate.indexOf('/');
            if (slash > 0 && other == slash && type.regionMatches(0, candidate, 0, slash) && (type.substring(slash + 1).equals("*") || candidate.substring(other + 1).equals("*"))) return true;
        } return false;
    }
    public final boolean hasDataType(String type) { return mime(type); }
    public final int countDataTypes() { return types.size(); }
    public final String getDataType(int index) { String type = types.get(index); return type.endsWith("/*") ? type.substring(0, type.length() - 2) : type; }
    public final Iterator<String> typesIterator() { if (types.isEmpty()) return null; ArrayList<String> result = new ArrayList<String>(); for (int i = 0; i < types.size(); ++i) result.add(getDataType(i)); return Collections.unmodifiableList(result).iterator(); }
    public final void addDataScheme(String value) { add(schemes, value); }
    public final int countDataSchemes() { return schemes.size(); }
    public final String getDataScheme(int index) { return schemes.get(index); }
    public final boolean hasDataScheme(String value) { return schemes.contains(value); }
    public final Iterator<String> schemesIterator() { return iterator(schemes); }
    public final void addDataAuthority(String host, String port) { throw new UnsupportedOperationException("Intent authority matching is not implemented"); }
    public final void addDataPath(String path, int pattern) { throw new UnsupportedOperationException("Intent path pattern matching is not implemented"); }
    public final void addDataSchemeSpecificPart(String part, int pattern) { throw new UnsupportedOperationException("Intent scheme-specific matching is not implemented"); }
    public final int matchData(String type, String scheme, Uri data) {
        int result = MATCH_CATEGORY_EMPTY;
        if (types.isEmpty() && schemes.isEmpty()) return type == null && data == null ? result + MATCH_ADJUSTMENT_NORMAL : NO_MATCH_DATA;
        if (!schemes.isEmpty()) { if (!schemes.contains(scheme == null ? "" : scheme)) return NO_MATCH_DATA; result = MATCH_CATEGORY_SCHEME; }
        else if (scheme != null && !scheme.isEmpty() && !scheme.equals("content") && !scheme.equals("file")) return NO_MATCH_DATA;
        if (!types.isEmpty()) { if (!mime(type)) return NO_MATCH_TYPE; result = MATCH_CATEGORY_TYPE; }
        else if (type != null) return NO_MATCH_TYPE;
        return result + MATCH_ADJUSTMENT_NORMAL;
    }
    public final int match(String action, String type, String scheme, Uri data, Set<String> intentCategories, String logTag) {
        if (action != null && !hasAction(action)) return NO_MATCH_ACTION;
        int result = matchData(type, scheme, data); if (result < 0) return result;
        return matchCategories(intentCategories) == null ? result : NO_MATCH_CATEGORY;
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("IntentFilter IPC is not implemented"); }
    public static final Parcelable.Creator<IntentFilter> CREATOR = new Parcelable.Creator<IntentFilter>() {
        public IntentFilter createFromParcel(Parcel in) { throw new UnsupportedOperationException("IntentFilter parcel decoding is not implemented"); }
        public IntentFilter[] newArray(int size) { return new IntentFilter[size]; }
    };
}
