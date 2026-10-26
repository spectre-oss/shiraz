// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.*;
import org.xmlpull.v1.XmlPullParser;
public final class CompiledXmlFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int id(Resources r,String type,String name){return r.getIdentifier(name,type,"org.shiraz.fixture");}
    public static void check(Resources r)throws Exception{
        try(XmlResourceParser p=r.getLayout(id(r,"layout","xml_attributes"))){check(p.getEventType()==XmlPullParser.START_DOCUMENT,"initial document event");while(p.next()!=XmlPullParser.START_TAG){}String ns="http://schemas.android.com/apk/res/android";
            check(android.util.Xml.asAttributeSet(p)==p,"Compiled parser retains typed AttributeSet identity");int rootLine=p.getLineNumber();check(p.getName().equals("FrameLayout")&&p.getDepth()==1&&p.getLineNumber()>0,"root position/depth");check(p.getNamespaceCount(1)==2&&p.getNamespace("android").equals(ns),"namespace scope");
            check(p.getIdAttributeResourceValue(0)==id(r,"id","xml_root")&&p.getStyleAttribute()==id(r,"style","FixtureBase"),"compiled special attribute indices");check(p.getAttributeBooleanValue(ns,"enabled",false)&&p.getAttributeIntValue(ns,"layout_width",0)==-1&&p.getAttributeFloatValue(ns,"alpha",0)==.5f,"typed attribute getters");
            boolean mapped=false;for(int i=0;i<p.getAttributeCount();i++)if(p.getAttributeName(i).equals("alpha")){check(p.getAttributeNameResource(i)==android.R.attr.alpha&&"android".equals(p.getAttributePrefix(i)),"resource-map attribute ID");mapped=true;}check(mapped,"alpha attribute present");
            check(p.nextTag()==XmlPullParser.START_TAG&&p.getName().equals("View")&&p.getDepth()==2,"nested start");check(p.getAttributeValue(ns,"tag").equals("literal")&&p.getAttributeValue(ns,"layout_width").equals("12.0dip"),"coerced attribute values");check(p.getNamespaceCount(1)==2&&p.getNamespaceCount(2)==2,"inherited namespace count");
            check(p.nextText().isEmpty()&&p.getDepth()==2&&p.getAttributeCount()==-1,"empty child end");check(p.nextTag()==XmlPullParser.END_TAG&&p.getDepth()==1&&p.getLineNumber()==rootLine,"root end depth and compiled source line");check(p.next()==XmlPullParser.END_DOCUMENT&&p.getDepth()==0,"document end");
        }
        System.out.println("SHIRAZ_FIXTURE_COMPILED_XML_OK");
    }
}
