// SPDX-License-Identifier: GPL-3.0-only
import java.lang.reflect.*;
import java.util.Properties;
public final class ServiceDeclarationSmoke {
    private static Method method;
    private static void validate(Properties metadata,boolean receiver)throws Exception{
        try{method.invoke(null,metadata,"fixture","fixture.Worker","fixture:worker",receiver);}
        catch(InvocationTargetException error){throw (Exception)error.getCause();}
    }
    private static Properties metadata(String kind,int count){Properties p=new Properties();p.setProperty(kind+".count",Integer.toString(count));p.setProperty(kind+"."+(count-1)+".name","fixture.Worker");p.setProperty(kind+"."+(count-1)+".process",":worker");return p;}
    public static void main(String[] args)throws Exception{
        method=Class.forName("shiraz.runtime.ServiceRunner").getDeclaredMethod("validateDeclaration",Properties.class,String.class,String.class,String.class,boolean.class);method.setAccessible(true);
        validate(metadata("service",406),false);validate(metadata("service",512),false);validate(metadata("receiver",256),true);
        try{validate(metadata("service",513),false);throw new AssertionError("unbounded services");}catch(IllegalArgumentException expected){}
        try{validate(metadata("receiver",257),true);throw new AssertionError("unbounded receivers");}catch(IllegalArgumentException expected){}
        Properties disabled=metadata("service",406);disabled.setProperty("service.405.enabled","false");
        try{validate(disabled,false);throw new AssertionError("disabled service");}catch(SecurityException expected){}
        Properties wrong=metadata("service",406);wrong.setProperty("service.405.process",":other");
        try{validate(wrong,false);throw new AssertionError("wrong process");}catch(SecurityException expected){}
        System.out.println("SERVICE_DECLARATION_OK");
    }
}
