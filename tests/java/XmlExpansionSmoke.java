// SPDX-License-Identifier: GPL-3.0-only
import android.util.Xml;
import java.io.*;
import org.xmlpull.v1.*;
public final class XmlExpansionSmoke {
    static void exhaust(XmlPullParser parser) throws Exception { while (parser.next() != XmlPullParser.END_DOCUMENT) { } }
    static void limited(XmlPullParser parser, String input) throws Exception {
        parser.setInput(new StringReader(input));
        try { exhaust(parser); throw new AssertionError("Unbounded XML entity expansion accepted"); }
        catch (XmlPullParserException expected) {
            if (!expected.getMessage().contains("Entity expansion exceeds host XML limits")) throw expected;
        }
    }
    static void reuse(XmlPullParser parser, boolean bytes) throws Exception {
        String xml = "<?xml version='1.0' encoding='UTF-8'?><r>clean</r>";
        if (bytes) parser.setInput(new ByteArrayInputStream(xml.getBytes("UTF-8")), null);
        else parser.setInput(new StringReader(xml));
        parser.nextTag(); if (!"clean".equals(parser.nextText())) throw new AssertionError("XML parser recovery lost primary input buffer");
        exhaust(parser);
    }
    public static void main(String[] args) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        String recursive = "<!DOCTYPE r [<!ENTITY loop '&loop;'>]><r>&loop;</r>";
        limited(parser, recursive); reuse(parser, false);
        limited(parser, recursive); reuse(parser, true);
        StringBuilder exponential = new StringBuilder("<!DOCTYPE r [<!ENTITY e0 '0123456789'>");
        for (int i = 1; i <= 20; ++i) exponential.append("<!ENTITY e").append(i).append(" '&e").append(i - 1).append(";&e").append(i - 1).append(";'>");
        exponential.append("]><r>&e20;</r>");
        limited(parser, exponential.toString()); reuse(parser, true);
        // Each document receives its own budget; a prior failure does not poison future valid input.
        parser.setInput(new StringReader("<!DOCTYPE r [<!ENTITY word 'one &amp; two'>]><r>&word;</r>"));
        parser.nextTag(); if (!"one & two".equals(parser.nextText())) throw new AssertionError("Valid entity after budget failure");
        System.out.println("XML_ENTITY_LIMITS_AND_RECOVERY_OK");
    }
}
