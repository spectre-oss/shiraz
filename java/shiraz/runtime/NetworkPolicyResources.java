// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.res.Resources;
import android.util.TypedValue;
import java.util.Properties;

/** Resource-valued manifest flags, without depending on ART/libcore policy classes. */
public final class NetworkPolicyResources {
    private NetworkPolicyResources() { }
    public static boolean cleartextPermitted(Properties metadata){
        String value=metadata.getProperty("usesCleartextTraffic");
        if(value==null||value.equals("true")||value.equals("false"))return ManifestNetworkPolicy.cleartextPermitted(metadata);
        String apk=metadata.getProperty("sourceDir");if(apk==null)throw new IllegalArgumentException("Network policy requires APK resources");
        Resources resources=Resources.shirazOpen(apk,360,640);
        try{TypedValue resolved=new TypedValue();resources.getValue(resource(resources,metadata,value,"bool"),resolved,true);return booleanValue(resources,new BinaryXml.Value(resolved.type,resolved.data,null));}
        finally{resources.shirazClose();}
    }
    public static boolean booleanValue(Resources resources,BinaryXml.Value value){
        TypedValue resolved=new TypedValue();
        if(value.type==1)resources.getValue(value.data,resolved,true);else{resolved.type=value.type;resolved.data=value.data;}
        if(resolved.type!=TypedValue.TYPE_INT_BOOLEAN)throw new IllegalArgumentException("Network policy requires a boolean resource");
        return resolved.data!=0;
    }
    public static int resource(Resources resources,Properties metadata,String value,String type){
        if(value==null||!value.startsWith("@"))throw new IllegalArgumentException("Expected network policy resource");
        String reference=value.substring(1);int id;
        if(reference.startsWith("0x"))try{id=(int)Long.parseLong(reference.substring(2),16);}catch(NumberFormatException error){throw new IllegalArgumentException("Invalid policy resource",error);}
        else id=resources.getIdentifier(reference,type,metadata.getProperty("package"));
        if(id==0||!type.equals(resources.getResourceTypeName(id)))throw new IllegalArgumentException("Invalid network policy "+type+" resource");
        return id;
    }
}
