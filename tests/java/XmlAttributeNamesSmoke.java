// SPDX-License-Identifier: GPL-3.0-only
import java.util.Set;
import shiraz.runtime.XmlAttributeNames;

public final class XmlAttributeNamesSmoke {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Set<String> allowed = XmlAttributeNames.compile("name pathData fillColor");
        check(allowed.contains("pathData"), "exact name");
        for (String bad : new String[]{"", "path", "Data", "name pathData", "android:name", "Name"})
            check(!allowed.contains(bad), "reject nonmember: " + bad);
        check(XmlAttributeNames.compile(new String("name pathData fillColor")) == allowed, "reuse by content");
        try { allowed.add("unsupported"); throw new AssertionError("mutable allowlist"); }
        catch (UnsupportedOperationException expected) {}
        check(XmlAttributeNames.compile("").isEmpty(), "empty list rejects every attribute");
        for (int i=0;i<90;i++) XmlAttributeNames.compile("attribute"+i);
        java.lang.reflect.Field cache = XmlAttributeNames.class.getDeclaredField("CACHE");
        cache.setAccessible(true);
        check(((java.util.Map<?,?>)cache.get(null)).size()<=64, "bounded retained lists");
        check(XmlAttributeNames.compile("name pathData fillColor").equals(allowed), "eviction preserves membership");
        try { XmlAttributeNames.compile(new String(new char[2049])); throw new AssertionError("unbounded list"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("XML_ATTRIBUTE_NAMES_OK");
    }
}
