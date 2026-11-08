// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;

/** Uses only public Android XML APIs to verify domain policy resources retain text. */
final class NetworkXmlChecks {
    static String run(Resources resources,String packageName)throws Exception{
        int id=resources.getIdentifier("network_domains","xml",packageName);
        NetworkChecks.require(id!=0,"network policy XML resource lookup");
        XmlResourceParser parser=resources.getXml(id);
        try{
            int count=0;
            while(parser.next()!=XmlPullParser.END_DOCUMENT)if(parser.getEventType()==XmlPullParser.START_TAG&&"domain".equals(parser.getName())){
                String expected=count++==0?"example.com":"localhost";
                NetworkChecks.require(expected.equals(parser.nextText()),"compiled domain text: "+expected);
                NetworkChecks.require(parser.getEventType()==XmlPullParser.END_TAG,"nextText cursor position");
            }
            NetworkChecks.require(count==2,"domain count");
        }finally{parser.close();}
        // Each cursor owns its position; a resource cache must not return EOF.
        XmlResourceParser reopened=resources.getXml(id);
        try{NetworkChecks.require(reopened.nextTag()==XmlPullParser.START_TAG,"first nextTag reaches root");NetworkChecks.require("network-security-config".equals(reopened.getName()),"reopened XML cursor");}
        finally{reopened.close();}
        return "Compiled network policy domains preserve text and independent XML cursors";
    }
}
