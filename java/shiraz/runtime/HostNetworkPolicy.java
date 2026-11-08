// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.res.Resources;
import android.util.TypedValue;
import java.util.Properties;

/** Install application policy atomically before loading any application classes. */
public final class HostNetworkPolicy {
    private HostNetworkPolicy() { }
    public static void install(Properties metadata) {
        String config=metadata.getProperty("networkSecurityConfig");
        boolean custom=config!=null&&!config.equals("@null")&&!config.equals("@0x00000000")&&!config.equals("0");
        String explicit=metadata.getProperty("usesCleartextTraffic");
        if(!custom&&(explicit==null||explicit.equals("true")||explicit.equals("false"))){
            libcore.net.NetworkSecurityPolicy.setInstance(new Applied(ManifestNetworkPolicy.cleartextPermitted(metadata),null));return;
        }
        String apk=metadata.getProperty("sourceDir");
        if(apk==null)throw new IllegalArgumentException("Network policy requires APK resources");
        final Resources resources=Resources.shirazOpen(apk,360,640);
        try{
            NetworkSecurityConfig.Booleans resolver=new NetworkSecurityConfig.Booleans(){public boolean resolve(BinaryXml.Value value){
                return NetworkPolicyResources.booleanValue(resources,value);
            }};
            if(custom){
                // With XML present Android ignores usesCleartextTraffic and uses
                // the target-SDK default for values not inherited from XML.
                Properties defaults=new Properties();defaults.putAll(metadata);defaults.remove("usesCleartextTraffic");
                byte[] xml=resources.shirazReadResourceFile(NetworkPolicyResources.resource(resources,metadata,config,"xml"),new TypedValue());
                NetworkSecurityConfig parsed=NetworkSecurityConfig.parse(xml,ManifestNetworkPolicy.cleartextPermitted(defaults),resolver);
                libcore.net.NetworkSecurityPolicy.setInstance(new Applied(false,parsed));
            }else{
                TypedValue value=new TypedValue();resources.getValue(NetworkPolicyResources.resource(resources,metadata,explicit,"bool"),value,true);
                boolean permitted=resolver.resolve(new BinaryXml.Value(value.type,value.data,null));
                libcore.net.NetworkSecurityPolicy.setInstance(new Applied(permitted,null));
            }
        }finally{resources.shirazClose();}
    }
    private static final class Applied extends libcore.net.NetworkSecurityPolicy {
        private final boolean cleartext;private final NetworkSecurityConfig config;
        Applied(boolean cleartext,NetworkSecurityConfig config){this.cleartext=cleartext;this.config=config;}
        public boolean isCleartextTrafficPermitted(){return config==null?cleartext:config.isCleartextTrafficPermitted();}
        public boolean isCleartextTrafficPermitted(String hostname){if(hostname==null)throw new NullPointerException("hostname");return config==null?cleartext:config.isCleartextTrafficPermitted(hostname);}
        public boolean isCertificateTransparencyVerificationRequired(String hostname){return false;}
    }
}
