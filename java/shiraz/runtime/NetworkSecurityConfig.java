// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Bounded application cleartext, certificate-anchor and pin policy. */
public final class NetworkSecurityConfig {
    public interface ResourceValues {
        boolean booleanValue(BinaryXml.Value value);
        default byte[] certificateResource(BinaryXml.Value value){throw unsupported("certificate resource loader");}
    }
    private static final class Anchor {
        final X509Certificate[] certificates;final boolean overridePins;
        Anchor(X509Certificate[] certificates,boolean overridePins){this.certificates=certificates;this.overridePins=overridePins;}
    }
    private static final class Pin {final String algorithm;final byte[] digest;Pin(String algorithm,byte[] digest){this.algorithm=algorithm;this.digest=digest;}}
    private static final class Pins {final List<Pin> values=new ArrayList<Pin>();long expiration=Long.MAX_VALUE;}

    private static final class Rule {
        final Rule parent; Boolean cleartext;List<Anchor> anchors;Pins pins;
        Rule(Rule parent,Boolean cleartext){this.parent=parent;this.cleartext=cleartext;}
        boolean permitted(boolean base){return cleartext!=null?cleartext:parent==null?base:parent.permitted(base);}
        List<Anchor> anchors(){return anchors!=null?anchors:parent==null?null:parent.anchors();}
        Pins pins(){return pins!=null?pins:parent==null?null:parent.pins();}
    }
    private static final class Domain {
        final String name;final boolean subdomains;final Rule rule;
        Domain(String name,boolean subdomains,Rule rule){this.name=name;this.subdomains=subdomains;this.rule=rule;}
    }
    private boolean base;
    private final Rule baseRule=new Rule(null,null);
    private List<Anchor> debugAnchors=Collections.emptyList();
    private final ArrayList<Domain> domains=new ArrayList<Domain>();
    private final HashSet<String> names=new HashSet<String>();
    private final ResourceValues booleans;
    private int certificateBytes,certificateCount;
    private NetworkSecurityConfig(boolean fallback,ResourceValues booleans){base=fallback;this.booleans=booleans;}
    public static NetworkSecurityConfig parse(byte[] xml,boolean fallback,ResourceValues booleans){
        return parse(xml,fallback,booleans,false);
    }
    public static NetworkSecurityConfig parse(byte[] xml,boolean fallback,ResourceValues booleans,boolean debuggable){
        NetworkSecurityConfig result=new NetworkSecurityConfig(fallback,booleans);
        BinaryXml.Node root=BinaryXml.parse(xml);
        result.tag(root,"network-security-config");result.attributes(root);
        boolean baseSeen=false,debugSeen=false;
        for(BinaryXml.Node node:result.children(root)){
            if(node.name.equals("base-config")){
                if(baseSeen)throw bad("Duplicate base-config");baseSeen=true;
                result.tag(node,"base-config");result.attributes(node,"cleartextTrafficPermitted");
                Boolean value=result.bool(node,"cleartextTrafficPermitted");if(value!=null)result.base=value;
                result.directives(node,result.baseRule);
            }else if(node.name.equals("domain-config"))result.domainConfig(node,result.baseRule,0);
            else if(node.name.equals("debug-overrides")){
                result.tag(node,"debug-overrides");result.attributes(node);
                if(debugSeen)throw bad("Duplicate debug-overrides");debugSeen=true;
                if(debuggable){
                    List<BinaryXml.Node> children=result.children(node);
                    if(children.size()!=1)throw bad("debug-overrides requires trust-anchors");
                    result.debugAnchors=result.trust(children.get(0),true);
                }
            }else throw unsupported(node.name);
        }
        return result;
    }
    public boolean isCleartextTrafficPermitted(){
        if(!base)return false;
        for(Domain domain:domains)if(!domain.rule.permitted(base))return false;
        return true;
    }
    public boolean isCleartextTrafficPermitted(String hostname){
        if(hostname==null)throw new NullPointerException("hostname");
        return rule(hostname).permitted(base);
    }
    private Rule rule(String hostname){
        if(hostname==null)return baseRule;
        String host=hostname.toLowerCase(Locale.US);if(host.endsWith("."))host=host.substring(0,host.length()-1);
        Domain best=null;
        for(Domain domain:domains)if(host.equals(domain.name)||domain.subdomains&&host.endsWith("."+domain.name))
            if(best==null||domain.name.length()>best.name.length())best=domain;
        return best==null?baseRule:best.rule;
    }
    private void domainConfig(BinaryXml.Node node,Rule parent,int depth){
        if(depth>=32)throw bad("Domain configuration nesting limit");
        tag(node,"domain-config");attributes(node,"cleartextTrafficPermitted");
        Rule rule=new Rule(parent,bool(node,"cleartextTrafficPermitted"));int count=0;
        for(BinaryXml.Node child:children(node)){
            if(child.name.equals("domain")){
                tag(child,"domain");attributes(child,"includeSubdomains");
                StringBuilder text=new StringBuilder();
                for(Object content:child.content){if(!(content instanceof String))throw bad("Domain contains elements");if(text.length()+((String)content).length()>1024)throw bad("Domain text limit");text.append(content);}
                String name=text.toString().trim().toLowerCase(Locale.US);
                if(name.isEmpty()||name.length()>253||name.startsWith(".")||name.endsWith(".")||name.contains(".."))throw bad("Invalid domain");
                for(int i=0;i<name.length();i++){char c=name.charAt(i);if(!(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='.'||c=='-'))throw bad("Domain must be an ASCII hostname");}
                if(!names.add(name))throw bad("Duplicate domain");if(domains.size()>=1024)throw bad("Domain count limit");
                domains.add(new Domain(name,Boolean.TRUE.equals(bool(child,"includeSubdomains")),rule));count++;
            }else if(child.name.equals("domain-config"))domainConfig(child,rule,depth+1);
            else if(!child.name.equals("trust-anchors")&&!child.name.equals("pin-set"))throw unsupported(child.name);
        }
        if(count==0)throw bad("domain-config requires a domain");
        directives(node,rule);
    }
    private void directives(BinaryXml.Node node,Rule rule){
        for(BinaryXml.Node child:children(node)){
            if(child.name.equals("trust-anchors")){if(rule.anchors!=null)throw bad("Duplicate trust-anchors");rule.anchors=trust(child,false);}
            else if(child.name.equals("pin-set")){if(!node.name.equals("domain-config"))throw bad("pin-set requires domain-config");if(rule.pins!=null)throw bad("Duplicate pin-set");rule.pins=pins(child);}
            else if(!node.name.equals("domain-config")||!child.name.equals("domain")&&!child.name.equals("domain-config"))throw unsupported(child.name);
        }
    }
    private List<Anchor> trust(BinaryXml.Node node,boolean debug){
        tag(node,"trust-anchors");attributes(node);List<Anchor> anchors=new ArrayList<Anchor>();
        for(BinaryXml.Node child:children(node)){
            tag(child,"certificates");attributes(child,"src","overridePins");
            BinaryXml.Value src=value(child,"src");Boolean override=bool(child,"overridePins");
            boolean overridePins=override==null?debug:override;
            if(src==null)throw bad("Missing certificate source");
            X509Certificate[] certs=null;
            if(src.type==3&&"system".equals(src.text)){} // Default host public roots.
            else if(src.type==1){
                byte[] bytes=booleans.certificateResource(src);if(bytes==null||bytes.length==0||bytes.length>1024*1024||(certificateBytes+=bytes.length)>4*1024*1024)throw bad("Certificate resource bounds");
                try{
                    Collection<? extends java.security.cert.Certificate> parsed=CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));
                    if(parsed.isEmpty()||parsed.size()>256||(certificateCount+=parsed.size())>1024)throw bad("Certificate count bounds");certs=parsed.toArray(new X509Certificate[parsed.size()]);
                }catch(CertificateException error){throw new IllegalArgumentException("Invalid certificate resource",error);}
            }else throw unsupported("certificate source "+src.text);
            if(!children(child).isEmpty())throw bad("certificates contains elements");
            if(anchors.size()>=256)throw bad("Certificate source count limit");anchors.add(new Anchor(certs,overridePins));
        }
        if(anchors.isEmpty())throw bad("Empty trust-anchors");return Collections.unmodifiableList(anchors);
    }
    private Pins pins(BinaryXml.Node node){
        tag(node,"pin-set");attributes(node,"expiration");Pins pins=new Pins();BinaryXml.Value expiration=value(node,"expiration");
        if(expiration!=null){
            if(expiration.type!=3||expiration.text==null||!expiration.text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw bad("Pin expiration format");
            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("UTC"));
            try{pins.expiration=format.parse(expiration.text).getTime();}catch(java.text.ParseException error){throw bad("Invalid pin expiration");}
        }
        for(BinaryXml.Node child:children(node)){
            tag(child,"pin");attributes(child,"digest");BinaryXml.Value algorithm=value(child,"digest");
            if(algorithm==null||algorithm.type!=3||!("SHA-256".equals(algorithm.text)||"SHA-512".equals(algorithm.text)))throw bad("Unsupported pin digest");
            StringBuilder text=new StringBuilder();for(Object content:child.content){if(!(content instanceof String))throw bad("Pin contains elements");if(text.length()+((String)content).length()>1024)throw bad("Pin text limit");text.append(content);}
            byte[] digest;try{digest=Base64.getDecoder().decode(text.toString().trim());}catch(IllegalArgumentException error){throw bad("Invalid pin base64");}
            if(digest.length!=(algorithm.text.equals("SHA-256")?32:64))throw bad("Pin digest size");
            if(pins.values.size()>=256)throw bad("Pin count limit");pins.values.add(new Pin(algorithm.text,digest));
        }
        return pins;
    }
    public X509Certificate[] acceptedIssuers(X509TrustManager system){
        LinkedHashSet<X509Certificate> certs=new LinkedHashSet<X509Certificate>();
        for(Anchor anchor:allAnchors(baseRule.anchors()))certs.addAll(Arrays.asList(anchor.certificates==null?system.getAcceptedIssuers():anchor.certificates));
        return certs.toArray(new X509Certificate[certs.size()]);
    }
    public boolean sameTrustConfiguration(String first,String second){if(first==null||second==null)throw new NullPointerException("hostname");return rule(first)==rule(second);}
    public boolean needsHostname(){return !domains.isEmpty();}
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,String authType,String host,X509TrustManager system)throws CertificateException{
        if(host==null&&needsHostname())throw new CertificateException("A peer hostname is required for domain trust policy");
        Rule rule=rule(host);List<Anchor> anchors=rule.anchors();
        LinkedHashSet<X509Certificate> roots=new LinkedHashSet<X509Certificate>();
        for(Anchor anchor:allAnchors(anchors))roots.addAll(Arrays.asList(anchor.certificates==null?system.getAcceptedIssuers():anchor.certificates));
        List<X509Certificate> validated=ApplicationTrustManager.validatedChain(roots.toArray(new X509Certificate[roots.size()]),chain,authType);
        Pins pins=rule.pins();
        if(pins!=null&&!pins.values.isEmpty()&&System.currentTimeMillis()<pins.expiration&&!overridesPins(validated,anchors,system)){
            try{
                boolean matched=false;
                for(X509Certificate certificate:validated)for(Pin pin:pins.values)
                    matched|=MessageDigest.isEqual(pin.digest,MessageDigest.getInstance(pin.algorithm).digest(certificate.getPublicKey().getEncoded()));
                if(!matched)throw new CertificateException("Application certificate pin verification failed");
            }catch(NoSuchAlgorithmException error){throw new CertificateException("Pin digest unavailable",error);}
        }
        return validated;
    }
    private List<Anchor> allAnchors(List<Anchor> anchors){
        List<Anchor> all=new ArrayList<Anchor>();if(anchors==null)all.add(new Anchor(null,false));else all.addAll(anchors);all.addAll(debugAnchors);return all;
    }
    private boolean overridesPins(List<X509Certificate> chain,List<Anchor> anchors,X509TrustManager system){
        X509Certificate root=chain.get(chain.size()-1);
        for(Anchor anchor:allAnchors(anchors))if(anchor.overridePins)for(X509Certificate certificate:anchor.certificates==null?system.getAcceptedIssuers():anchor.certificates)if(root.equals(certificate))return true;
        return false;
    }
    private List<BinaryXml.Node> children(BinaryXml.Node node){
        for(Object item:node.content)if(item instanceof String&&!((String)item).trim().isEmpty())throw bad("Unexpected configuration text");
        return node.children;
    }
    private Boolean bool(BinaryXml.Node node,String name){BinaryXml.Value value=value(node,name);return value==null?null:booleans.booleanValue(value);}
    private static BinaryXml.Value value(BinaryXml.Node node,String name){for(BinaryXml.Attribute attr:node.attributes)if(attr.name.equals(name)&&attr.namespace.isEmpty())return attr.value;return null;}
    private void attributes(BinaryXml.Node node,String... allowed){
        for(BinaryXml.Attribute attr:node.attributes){boolean found=false;for(String name:allowed)if(attr.namespace.isEmpty()&&attr.name.equals(name))found=true;if(!found)throw unsupported("attribute "+attr.name);}
    }
    private void tag(BinaryXml.Node node,String name){if(!node.namespace.isEmpty()||!node.name.equals(name))throw bad("Expected "+name);}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException("Network security config: "+message);}
    private static UnsupportedOperationException unsupported(String feature){return new UnsupportedOperationException("Network security config does not yet support "+feature);}
}
