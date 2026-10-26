// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;
import java.io.UnsupportedEncodingException;
/** Android text XML utilities backed by the runtime's existing AOSP KXML implementation. */
public class Xml {
    public static final String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";
    private Xml() { }
    public static XmlPullParser newPullParser() {
        KXmlParser parser = new KXmlParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException error) { throw new AssertionError(error); }
        return parser;
    }
    public static AttributeSet asAttributeSet(XmlPullParser parser){return parser instanceof AttributeSet?(AttributeSet)parser:new XmlPullAttributes(parser);}
    public static XmlSerializer newSerializer() { return new KXmlSerializer(); }
    public enum Encoding { US_ASCII("US-ASCII"), UTF_8("UTF-8"), UTF_16("UTF-16"), ISO_8859_1("ISO-8859-1");
        final String name;
        Encoding(String name) { this.name = name; }
    }
    public static Encoding findEncodingByName(String name) throws UnsupportedEncodingException {
        if (name == null) return Encoding.UTF_8;
        for (Encoding encoding : Encoding.values()) if (encoding.name.equalsIgnoreCase(name)) return encoding;
        throw new UnsupportedEncodingException(name);
    }
}
