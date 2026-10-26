// SPDX-License-Identifier: GPL-3.0-only
package android.net;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable URI text and component queries; no content-provider or IPC transport. */
public abstract class Uri implements Parcelable, Comparable<Uri> {
    private final String text;
    private Uri(String text) { this.text = Objects.requireNonNull(text); if (text.length() > 65536) throw new IllegalArgumentException("URI exceeds host limit"); }
    private static final class Value extends Uri { Value(String text) { super(text); } }
    public static final Uri EMPTY = new Value("");
    public static Uri parse(String text) { return new Value(text); }
    public static Uri fromFile(File file) { return parse("file://" + encode(Objects.requireNonNull(file).getAbsolutePath(), "/")); }
    public static Uri fromParts(String scheme, String ssp, String fragment) {
        if (scheme == null) throw new NullPointerException("scheme");
        if (ssp == null) throw new NullPointerException("ssp");
        return parse(scheme + ":" + encode(ssp, "/@:") + (fragment == null ? "" : "#" + encode(fragment, "")));
    }
    public static Uri withAppendedPath(Uri base, String pathSegment) {
        if (base == null) throw new NullPointerException("base");
        if (pathSegment == null) throw new NullPointerException("pathSegment");
        return base.buildUpon().appendEncodedPath(pathSegment).build();
    }
    private int schemeEnd() { int colon = text.indexOf(':'); if (colon < 0) return -1; for (int i = 0; i < colon; ++i) if ("/?#".indexOf(text.charAt(i)) >= 0) return -1; return colon; }
    public String getScheme() { int end = schemeEnd(); return end < 0 ? null : text.substring(0, end); }
    /** Normalize only the scheme; preserve escaped components and their case. */
    public Uri normalizeScheme() {
        int end=schemeEnd();if(end<0)return this;
        String scheme=text.substring(0,end),lower=scheme.toLowerCase(Locale.ROOT);
        return scheme.equals(lower)?this:parse(lower+text.substring(end));
    }
    public boolean isRelative() { return schemeEnd() < 0; }
    public boolean isAbsolute() { return !isRelative(); }
    public boolean isHierarchical() { int end = schemeEnd(); return end < 0 || (end + 1 < text.length() && text.charAt(end + 1) == '/'); }
    public boolean isOpaque() { return !isHierarchical(); }
    private int fragment() { int value = text.indexOf('#'); return value < 0 ? text.length() : value; }
    private int query() { int value = text.indexOf('?', schemeEnd() + 1); return value < 0 || value > fragment() ? fragment() : value; }
    public String getEncodedFragment() { int at = text.indexOf('#'); return at < 0 ? null : text.substring(at + 1); }
    public String getFragment() { return decode(getEncodedFragment()); }
    public String getEncodedSchemeSpecificPart() { return text.substring(schemeEnd() + 1, fragment()); }
    public String getSchemeSpecificPart() { return decode(getEncodedSchemeSpecificPart()); }
    public String getEncodedAuthority() {
        if (!isHierarchical()) return null;
        int start = schemeEnd() + 1; if (!text.startsWith("//", start)) return null;
        int end = start + 2; while (end < text.length() && "/?#".indexOf(text.charAt(end)) < 0) ++end;
        return text.substring(start + 2, end);
    }
    public String getAuthority() { return decode(getEncodedAuthority()); }
    public String getEncodedUserInfo() {
        String authority=getEncodedAuthority();
        int separator=authority==null?-1:authority.lastIndexOf('@');
        return separator<0?null:authority.substring(0,separator);
    }
    public String getUserInfo() { return decode(getEncodedUserInfo()); }
    public String getHost() {
        String value = getEncodedAuthority(); if (value == null) return null;
        value = value.substring(value.lastIndexOf('@') + 1);
        int end = value.startsWith("[") ? value.indexOf(']') + 1 : value.indexOf(':');
        return decode(end <= 0 ? value : value.substring(0, end));
    }
    public int getPort() {
        String value = getEncodedAuthority(); if (value == null) return -1;
        int colon = value.lastIndexOf(':'); if (colon < 0 || (value.startsWith("[") && colon < value.indexOf(']'))) return -1;
        try { return Integer.parseInt(decode(value.substring(colon + 1))); } catch (NumberFormatException error) { return -1; }
    }
    public String getEncodedPath() {
        if (isOpaque()) return null;
        int start = schemeEnd() + 1;
        String authority = getEncodedAuthority(); if (authority != null) start += authority.length() + 2;
        return text.substring(start, query());
    }
    public String getPath() { return decode(getEncodedPath()); }
    public List<String> getPathSegments() {
        String value = getEncodedPath(); List<String> result = new ArrayList<String>();
        if (value != null) for (String part : value.split("/")) if (!part.isEmpty()) result.add(decode(part));
        return Collections.unmodifiableList(result);
    }
    public String getLastPathSegment() { List<String> parts = getPathSegments(); return parts.isEmpty() ? null : parts.get(parts.size() - 1); }
    public String getEncodedQuery() { if (isOpaque()) return null; int at = query(); return at == fragment() ? null : text.substring(at + 1, fragment()); }
    public String getQuery() { return decode(getEncodedQuery()); }
    private String hierarchicalQuery(){
        if(isOpaque())throw new UnsupportedOperationException("Opaque URI has no query parameters");
        return getEncodedQuery();
    }
    public Set<String> getQueryParameterNames(){
        String query=hierarchicalQuery();if(query==null)return Collections.emptySet();
        Set<String> names=new LinkedHashSet<String>();int start=0;
        do{int end=query.indexOf('&',start);if(end<0)end=query.length();int equal=query.indexOf('=',start);if(equal<0||equal>end)equal=end;
            names.add(decode(query.substring(start,equal)));start=end+1;
        }while(start<query.length());return Collections.unmodifiableSet(names);
    }
    public List<String> getQueryParameters(String key){
        String query=hierarchicalQuery();Objects.requireNonNull(key);if(query==null)return Collections.emptyList();
        String encoded;try{encoded=java.net.URLEncoder.encode(key,"UTF-8");}catch(UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
        List<String> values=new ArrayList<String>();int start=0;
        while(true){int end=query.indexOf('&',start);if(end<0)end=query.length();int equal=query.indexOf('=',start);if(equal<0||equal>end)equal=end;
            if(equal-start==encoded.length()&&query.regionMatches(start,encoded,0,encoded.length()))values.add(equal==end?"":decode(query.substring(equal+1,end)));
            if(end==query.length())break;start=end+1;
        }return Collections.unmodifiableList(values);
    }
    public String getQueryParameter(String key){
        String query=hierarchicalQuery();Objects.requireNonNull(key);if(query==null)return null;String encoded=encode(key);int start=0;
        while(true){int end=query.indexOf('&',start);if(end<0)end=query.length();int equal=query.indexOf('=',start);if(equal<0||equal>end)equal=end;
            if(equal-start==encoded.length()&&query.regionMatches(start,encoded,0,encoded.length()))return equal==end?"":decode(query.substring(equal+1,end).replace("+"," "));
            if(end==query.length())break;start=end+1;
        }return null;
    }
    public boolean getBooleanQueryParameter(String key,boolean defaultValue){
        String value=getQueryParameter(key);if(value==null)return defaultValue;
        return !value.equals("0")&&!value.toLowerCase(Locale.ROOT).equals("false");
    }
    public static String decode(String value) {
        return value == null ? null : libcore.net.UriCodec.decode(value, false, StandardCharsets.UTF_8, false);
    }
    public static String encode(String value) { return encode(value, null); }
    public static String encode(String value, String allow) {
        if (value == null) return null; StringBuilder output = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) { int c = item & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "_-.!~'()*".indexOf(c) >= 0 || (c < 128 && allow != null && allow.indexOf(c) >= 0)) output.append((char)c);
            else output.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
        } return output.toString();
    }
    @Override public String toString() { return text; }
    @Override public boolean equals(Object value) { return value instanceof Uri && text.equals(value.toString()); }
    @Override public int hashCode() { return text.hashCode(); }
    public int compareTo(Uri value) { return text.compareTo(value.toString()); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("URI IPC is not implemented"); }
    public static final Parcelable.Creator<Uri> CREATOR = new Parcelable.Creator<Uri>() {
        public Uri createFromParcel(Parcel in) { throw new UnsupportedOperationException("URI parcel decoding is not implemented"); }
        public Uri[] newArray(int size) { return new Uri[size]; }
    };
    public Builder buildUpon() {
        Builder builder = new Builder();
        builder.scheme(getScheme());
        if (isOpaque()) builder.opaquePart(getEncodedSchemeSpecificPart());
        else {
            builder.encodedAuthority(getEncodedAuthority());
            builder.encodedPath(getEncodedPath());
            builder.encodedQuery(getEncodedQuery());
        }
        builder.encodedFragment(getEncodedFragment());
        return builder;
    }
    public static final class Builder {
        private String scheme, opaque, authority, path, query, fragment;
        private boolean hierarchical;
        public Builder scheme(String scheme) { this.scheme = scheme; return this; }
        public Builder opaquePart(String opaquePart) { opaque = opaquePart; hierarchical = false; return this; }
        public Builder encodedOpaquePart(String opaquePart) { return opaquePart(opaquePart); }
        public Builder authority(String authority) { this.authority = authority == null ? null : encode(authority, "@/[]"); hierarchical = true; opaque = null; return this; }
        public Builder encodedAuthority(String authority) { this.authority = authority; hierarchical = true; opaque = null; return this; }
        public Builder path(String path) { this.path = path == null ? null : encode(path, "/"); hierarchical = true; opaque = null; return this; }
        public Builder encodedPath(String path) { this.path = path; hierarchical = true; opaque = null; return this; }
        public Builder appendPath(String newSegment) { return appendEncodedPath(encode(newSegment)); }
        public Builder appendEncodedPath(String newSegment) {
            if (newSegment == null) throw new NullPointerException("newSegment");
            if (path == null || path.isEmpty() || path.equals("/")) path = "/" + newSegment;
            else if (path.endsWith("/")) path = path + newSegment;
            else path = path + "/" + newSegment;
            hierarchical = true; opaque = null; return this;
        }
        public Builder query(String query) { this.query = query == null ? null : encode(query, "@/?"); hierarchical = true; opaque = null; return this; }
        public Builder encodedQuery(String query) { this.query = query; hierarchical = true; opaque = null; return this; }
        public Builder appendQueryParameter(String key, String value) {
            if (key == null) throw new NullPointerException("key");
            String item = encode(key) + "=" + encode(value == null ? "" : value);
            query = query == null || query.isEmpty() ? item : query + "&" + item;
            hierarchical = true; opaque = null; return this;
        }
        public Builder clearQuery() { query = null; return this; }
        public Builder fragment(String fragment) { this.fragment = fragment == null ? null : encode(fragment, "@/?#"); return this; }
        public Builder encodedFragment(String fragment) { this.fragment = fragment; return this; }
        public Uri build() {
            if (scheme == null && hierarchical) return parse((authority != null ? "//" + authority : "") + (path == null ? "" : path) + (query == null ? "" : "?" + query) + (fragment == null ? "" : "#" + fragment));
            if (scheme == null) throw new UnsupportedOperationException("An opaque URI must have a scheme.");
            if (!hierarchical && opaque != null) return parse(scheme + ":" + opaque + (fragment == null ? "" : "#" + fragment));
            StringBuilder out = new StringBuilder();
            out.append(scheme).append(':');
            if (authority != null) out.append("//").append(authority);
            if (path != null) out.append(path);
            if (query != null) out.append('?').append(query);
            if (fragment != null) out.append('#').append(fragment);
            return parse(out.toString());
        }
        @Override public String toString() { return build().toString(); }
    }
}
