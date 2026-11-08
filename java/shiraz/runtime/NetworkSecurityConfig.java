// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.*;

/** Bounded cleartext domain policy. Unsupported trust directives fail before installation. */
public final class NetworkSecurityConfig {
    public interface Booleans { boolean resolve(BinaryXml.Value value); }
    private static final class Rule {
        final Rule parent; final Boolean cleartext;
        Rule(Rule parent,Boolean cleartext){this.parent=parent;this.cleartext=cleartext;}
        boolean permitted(boolean base){return cleartext!=null?cleartext:parent==null?base:parent.permitted(base);}
    }
    private static final class Domain {
        final String name;final boolean subdomains;final Rule rule;
        Domain(String name,boolean subdomains,Rule rule){this.name=name;this.subdomains=subdomains;this.rule=rule;}
    }
    private boolean base;
    private final ArrayList<Domain> domains=new ArrayList<Domain>();
    private final HashSet<String> names=new HashSet<String>();
    private final Booleans booleans;
    private NetworkSecurityConfig(boolean fallback,Booleans booleans){base=fallback;this.booleans=booleans;}
    public static NetworkSecurityConfig parse(byte[] xml,boolean fallback,Booleans booleans){
        NetworkSecurityConfig result=new NetworkSecurityConfig(fallback,booleans);
        BinaryXml.Node root=BinaryXml.parse(xml);
        result.tag(root,"network-security-config");result.attributes(root);
        boolean baseSeen=false;
        for(BinaryXml.Node node:result.children(root)){
            if(node.name.equals("base-config")){
                if(baseSeen)throw bad("Duplicate base-config");baseSeen=true;
                result.tag(node,"base-config");result.attributes(node,"cleartextTrafficPermitted");
                Boolean value=result.bool(node,"cleartextTrafficPermitted");if(value!=null)result.base=value;
                boolean trustSeen=false;
                for(BinaryXml.Node child:result.children(node)){if(trustSeen)throw bad("Duplicate trust-anchors");result.trust(child);trustSeen=true;}
            }else if(node.name.equals("domain-config"))result.domainConfig(node,null,0);
            else throw unsupported(node.name);
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
        String host=hostname.toLowerCase(Locale.US);if(host.endsWith("."))host=host.substring(0,host.length()-1);
        Domain best=null;
        for(Domain domain:domains)if(host.equals(domain.name)||domain.subdomains&&host.endsWith("."+domain.name))
            if(best==null||domain.name.length()>best.name.length())best=domain;
        return best==null?base:best.rule.permitted(base);
    }
    private void domainConfig(BinaryXml.Node node,Rule parent,int depth){
        if(depth>=32)throw bad("Domain configuration nesting limit");
        tag(node,"domain-config");attributes(node,"cleartextTrafficPermitted");
        Rule rule=new Rule(parent,bool(node,"cleartextTrafficPermitted"));int count=0;boolean trustSeen=false;
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
            else {if(trustSeen)throw bad("Duplicate trust-anchors");trust(child);trustSeen=true;}
        }
        if(count==0)throw bad("domain-config requires a domain");
    }
    private void trust(BinaryXml.Node node){
        // System-only anchors are exactly the existing host default trust path.
        // Do not accept custom/user anchors, pinning, debug overrides or newer
        // security directives until their enforcement exists in the TLS bridge.
        if(!node.name.equals("trust-anchors"))throw unsupported(node.name);
        tag(node,"trust-anchors");attributes(node);int count=0;
        for(BinaryXml.Node child:children(node)){
            tag(child,"certificates");attributes(child,"src","overridePins");
            BinaryXml.Value src=value(child,"src");
            if(src==null||src.type!=3||!"system".equals(src.text))throw unsupported("custom certificate anchors");
            if(Boolean.TRUE.equals(bool(child,"overridePins")))throw unsupported("overridePins");
            if(!children(child).isEmpty())throw bad("certificates contains elements");count++;
        }
        if(count==0)throw bad("Empty trust-anchors");
    }
    private List<BinaryXml.Node> children(BinaryXml.Node node){
        for(Object item:node.content)if(item instanceof String&&!((String)item).trim().isEmpty())throw bad("Unexpected configuration text");
        return node.children;
    }
    private Boolean bool(BinaryXml.Node node,String name){BinaryXml.Value value=value(node,name);return value==null?null:booleans.resolve(value);}
    private static BinaryXml.Value value(BinaryXml.Node node,String name){for(BinaryXml.Attribute attr:node.attributes)if(attr.name.equals(name)&&attr.namespace.isEmpty())return attr.value;return null;}
    private void attributes(BinaryXml.Node node,String... allowed){
        for(BinaryXml.Attribute attr:node.attributes){boolean found=false;for(String name:allowed)if(attr.namespace.isEmpty()&&attr.name.equals(name))found=true;if(!found)throw unsupported("attribute "+attr.name);}
    }
    private void tag(BinaryXml.Node node,String name){if(!node.namespace.isEmpty()||!node.name.equals(name))throw bad("Expected "+name);}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException("Network security config: "+message);}
    private static UnsupportedOperationException unsupported(String feature){return new UnsupportedOperationException("Network security config does not yet support "+feature);}
}
