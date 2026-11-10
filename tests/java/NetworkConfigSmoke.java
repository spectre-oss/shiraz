// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.security.NetworkSecurityPolicy;
import java.io.*;
import java.net.*;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;
import shiraz.runtime.HostNetworkPolicy;

public final class NetworkConfigSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Properties facts(String apk,String config){
        Properties facts=NetworkPolicySmoke.facts(35,"false");facts.setProperty("sourceDir",apk);facts.setProperty("package","org.shiraz.networkpolicy");
        if(config!=null)facts.setProperty("networkSecurityConfig",config);return facts;
    }
    static void host(String name,boolean expected){check(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(name)==expected,"domain policy: "+name);}
    public static void run(String apk,String url,String ipv6Url)throws Exception{
        Properties domains=facts(apk,"@xml/domains");HostNetworkPolicy.install(domains);
        check(!NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(),"global policy must account for domain denial");
        host("example.com",true);host("www.example.com",true);host("EXAMPLE.COM.",true);
        host("secure.example.com",false);host("sub.secure.example.com",false);host("inherited.example.com",false);
        host("badexample.com",false);host("example.com.evil",false);host("default.example.org",false);host("other.org",false);
        for(String config:new String[]{"pins","unsupported_source","duplicate","invalid_bool","duplicate_base","duplicate_trust","namespace","unknown_attribute","duplicate_transparency"}){
            try{HostNetworkPolicy.install(facts(apk,"@xml/"+config));throw new AssertionError("invalid/unsupported policy accepted: "+config);}
            catch(IllegalArgumentException|UnsupportedOperationException expected){}
            host("example.com",true);host("secure.example.com",false);
        }
        // Android user CA store: Shiraz has none, so it adds no roots but is accepted.
        HostNetworkPolicy.install(facts(apk,"@xml/custom_ca"));host("example.com",false);host("other.org",false);
        HostNetworkPolicy.install(facts(apk,"@xml/user_only"));host("other.org",false);
        // API 36 certificateTransparency is ignored by the API 27 profile, as AOSP skips it there.
        HostNetworkPolicy.install(facts(apk,"@xml/transparency"));host("other.org",true);host("www.example.com",false);
        Properties defaults=facts(apk,"@xml/defaults");defaults.setProperty("usesCleartextTraffic","true");HostNetworkPolicy.install(defaults);
        host("example.com",false);host("other.org",false); // XML ignores manifest explicit true.
        defaults.setProperty("targetSdk","27");defaults.setProperty("usesCleartextTraffic","false");HostNetworkPolicy.install(defaults);
        host("example.com",true);host("other.org",true); // XML ignores manifest explicit false.
        Properties resource=facts(apk,null);resource.setProperty("usesCleartextTraffic","@bool/cleartext");HostNetworkPolicy.install(resource);host("other.org",true);
        resource.setProperty("dataDir","/data");
        android.content.pm.ApplicationInfo info=new shiraz.runtime.HostPackageManager(resource,null).getApplicationInfo("org.shiraz.networkpolicy",0);
        check((info.flags&android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)!=0,"resource-valued manifest flag");
        Resources resources=Resources.shirazOpen(apk,360,640);
        try{
            int id=resources.getIdentifier("domains","xml","org.shiraz.networkpolicy");
            domains.setProperty("networkSecurityConfig","@0x"+Integer.toHexString(id));HostNetworkPolicy.install(domains);host("www.example.com",true);
            // Public pull-cursor text behavior, including ordered mixed content.
            XmlResourceParser parser=resources.getXml(resources.getIdentifier("mixed","xml","org.shiraz.networkpolicy"));
            try{
                while(parser.next()!=XmlPullParser.START_TAG){}
                check(parser.next()==XmlPullParser.TEXT&&"before".equals(parser.getText()),"leading text");check(parser.getName()==null&&parser.getNamespace()==null,"text has no element name");
                int[] bounds=new int[2];char[] text=parser.getTextCharacters(bounds);check(new String(text,bounds[0],bounds[1]).equals("before")&&!parser.isWhitespace(),"text characters");
                check(parser.nextTag()==XmlPullParser.START_TAG&&parser.nextText().isEmpty(),"empty child");
                check(parser.next()==XmlPullParser.TEXT&&"after".equals(parser.getText()),"trailing text");
                try{parser.nextTag();}catch(Exception error){throw new AssertionError("closing mixed tag",error);}
            }finally{parser.close();}
            XmlResourceParser cursor=resources.getXml(id);
            try{boolean found=false;while(cursor.next()!=XmlPullParser.END_DOCUMENT)if(cursor.getEventType()==XmlPullParser.START_TAG&&"domain".equals(cursor.getName())){check(!cursor.nextText().isEmpty(),"domain text lost");found=true;}check(found,"domain elements missing");}finally{cursor.close();}
            byte[] encoded=resources.shirazReadResourceFile(id,new android.util.TypedValue());
            java.nio.ByteBuffer words=java.nio.ByteBuffer.wrap(encoded).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for(int at=8;at<encoded.length;at+=words.getInt(at+4))if((words.getShort(at)&65535)==0x104){
                byte[] broken=encoded.clone();broken[at+20]=7;
                try{shiraz.runtime.BinaryXml.parse(broken);throw new AssertionError("malformed text value accepted");}catch(IllegalArgumentException expected){}break;
            }
        }finally{resources.shirazClose();}
        HostNetworkPolicy.install(domains);
        URL deniedUrl=new URL(url.replace("127.0.0.1","localhost")+"/xml-forbidden");
        HttpURLConnection denied=(HttpURLConnection)deniedUrl.openConnection();denied.setConnectTimeout(1000);denied.setReadTimeout(1000);
        try{try{denied.getResponseCode();throw new AssertionError("XML denied HTTP reached server");}catch(IOException expected){check(expected.getMessage().toLowerCase(Locale.US).contains("cleartext"),"XML denial reason");}}finally{denied.disconnect();}
        HttpURLConnection allowed=(HttpURLConnection)new URL(url+"/xml-body").openConnection();allowed.setConnectTimeout(1000);allowed.setReadTimeout(1000);
        try{check(allowed.getResponseCode()==200,"XML allowed HTTP status");try(InputStream in=allowed.getInputStream()){check(in.read()==42&&in.read()==-1,"XML allowed body");}}finally{allowed.disconnect();}
        HostNetworkPolicy.install(facts(apk,"@xml/ipv6"));host("::1",true);host("[::1]",true);host("fe80::1%shiraz-test",true);host("fe80::1%other",false);host("0:0:0:0:0:0:0:1",true);host("::2",false);host("example.com",false);
        try{HostNetworkPolicy.install(facts(apk,"@xml/ipv6_invalid"));throw new AssertionError("invalid numeric host accepted");}catch(IllegalArgumentException expected){}host("::1",true);host("::2",false);
        HttpURLConnection ipv6=(HttpURLConnection)new URL(ipv6Url+"/ipv6-body").openConnection(Proxy.NO_PROXY);ipv6.setConnectTimeout(1500);ipv6.setReadTimeout(1500);
        try{check(ipv6.getResponseCode()==200,"IPv6 policy HTTP status");try(InputStream in=ipv6.getInputStream()){check(in.read()==42&&in.read()==-1,"IPv6 policy body");}}finally{ipv6.disconnect();}
        HostNetworkPolicy.install(facts(apk,"@xml/ipv6_deny"));host("::1",false);host("[::1]",false);host("example.com",true);
        ipv6=(HttpURLConnection)new URL(ipv6Url+"/ipv6-forbidden").openConnection(Proxy.NO_PROXY);ipv6.setConnectTimeout(1500);ipv6.setReadTimeout(1500);
        try{try{ipv6.getResponseCode();throw new AssertionError("IPv6 denied request sent");}catch(IOException expected){check(expected.getMessage().toLowerCase(Locale.US).contains("cleartext"),"IPv6 policy denial reason");}}finally{ipv6.disconnect();}
        System.out.println("NETWORK_IPV6_POLICY_VERIFIED");
        System.out.println("NETWORK_XML_POLICY_VERIFIED");
    }
}
