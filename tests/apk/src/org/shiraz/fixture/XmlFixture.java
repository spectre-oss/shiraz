// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.util.Xml;
import java.io.*;
import org.xmlpull.v1.*;
final class XmlFixture {
    static void drain(XmlPullParser parser) throws Exception { while (parser.next() != XmlPullParser.END_DOCUMENT) { } }
    static void check() throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        if (!parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES) || !parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
            throw new AssertionError("XML default features");
        parser.setInput(new StringReader("<?xml version='1.0'?><r xmlns='urn:r' xmlns:p='urn:p' p:a='café &amp; x'><p:item><![CDATA[a<b]]>&#x1f680;</p:item><empty/></r>"));
        if (parser.nextTag() != XmlPullParser.START_TAG || !"urn:r".equals(parser.getNamespace()) || parser.getDepth() != 1
                || !"café & x".equals(parser.getAttributeValue("urn:p", "a"))) throw new AssertionError("XML namespaces/attributes");
        parser.nextTag(); if (!"a<b\ud83d\ude80".equals(parser.nextText()) || parser.getDepth() != 2) throw new AssertionError("XML CDATA/character references");
        parser.nextTag(); if (!parser.isEmptyElementTag() || !"empty".equals(parser.getName())) throw new AssertionError("XML empty element");
        drain(parser);
        parser.setInput(new StringReader("<!DOCTYPE r [<!ENTITY text 'café'>]><r>&text;</r>"));
        parser.nextTag(); if (!"café".equals(parser.nextText())) throw new AssertionError("Internal XML entity");
        StringWriter output = new StringWriter(); XmlSerializer serializer = Xml.newSerializer(); serializer.setOutput(output);
        serializer.startDocument("UTF-8", true); serializer.setPrefix("p", "urn:p"); serializer.startTag("urn:p", "root");
        serializer.attribute("", "escaped", "a<&\""); serializer.text("café & \ud83d\ude80"); serializer.endTag("urn:p", "root"); serializer.endDocument();
        parser.setInput(new StringReader(output.toString())); parser.nextTag();
        if (!"urn:p".equals(parser.getNamespace()) || !"a<&\"".equals(parser.getAttributeValue(null, "escaped"))
                || !"café & \ud83d\ude80".equals(parser.nextText())) throw new AssertionError("XML serializer round trip");
        parser.setInput(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-16'?><r>café</r>".getBytes("UTF-16")), null);
        parser.nextTag(); if (!"café".equals(parser.nextText())) throw new AssertionError("XML UTF-16 BOM detection");
        parser.setInput(new StringReader("<r><wrong></r>"));
        try { drain(parser); throw new AssertionError("Malformed XML accepted"); } catch (XmlPullParserException expected) { }
        parser.setInput(new StringReader("<!DOCTYPE r [<!ENTITY loop '&loop;'>]><r>&loop;</r>"));
        try { drain(parser); throw new AssertionError("Recursive XML entity accepted"); }
        catch (XmlPullParserException expected) { if (!expected.getMessage().contains("Entity expansion exceeds host XML limits")) throw expected; }
        // Recovery must restore the primary buffer, including before input encoding sniffing.
        parser.setInput(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?><r>reused</r>".getBytes("UTF-8")), null);
        parser.nextTag(); if (!"reused".equals(parser.nextText())) throw new AssertionError("Parser reset after entity failure");
        if (Xml.findEncodingByName(null) != Xml.Encoding.UTF_8 || Xml.findEncodingByName("utf-16") != Xml.Encoding.UTF_16)
            throw new AssertionError("XML encoding lookup");
        try { Xml.findEncodingByName("unsupported"); throw new AssertionError("Unsupported encoding accepted"); } catch (UnsupportedEncodingException expected) { }
        parser.setInput(new StringReader("<r xmlns:p='urn:p' p:n='0x20' flag='TRUE' octal='010' unsigned='0xffffffff' fraction='1.25' choice='two' id='123' class='Thing' style='0x40' bad='abc'><child n='7'/></r>"));
        android.util.AttributeSet attributes=Xml.asAttributeSet(parser);parser.nextTag();
        if(attributes.getAttributeIntValue("urn:p","n",0)!=32||attributes.getAttributeIntValue(null,"octal",0)!=8||attributes.getAttributeUnsignedIntValue(null,"unsigned",0)!=-1||!attributes.getAttributeBooleanValue(null,"flag",false)||attributes.getAttributeFloatValue(null,"fraction",0)!=1.25f)throw new AssertionError("Text attribute typed conversion");
        if(attributes.getAttributeListValue(null,"choice",new String[]{"one","two"},-1)!=1||attributes.getAttributeIntValue(null,"missing",9)!=9||attributes.getIdAttributeResourceValue(0)!=123||attributes.getStyleAttribute()!=64||!attributes.getClassAttribute().equals("Thing"))throw new AssertionError("Attribute special values/defaults");
        for(int i=0;i<attributes.getAttributeCount();i++){if(attributes.getAttributeNameResource(i)!=0)throw new AssertionError("Text parser fabricated resource IDs");if(attributes.getAttributeName(i).equals("octal")&&attributes.getAttributeIntValue(i,0)!=8)throw new AssertionError("Indexed conversion");}
        try{attributes.getAttributeIntValue(null,"bad",0);throw new AssertionError("Invalid numeric attribute accepted");}catch(NumberFormatException expected){}
        parser.nextTag();if(attributes.getAttributeIntValue(null,"n",0)!=7||attributes.getAttributeCount()!=1)throw new AssertionError("Attribute adapter follows parser cursor");
        System.out.println("SHIRAZ_FIXTURE_XML_OK");
    }
}
