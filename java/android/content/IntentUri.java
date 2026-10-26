// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.net.Uri;
import java.net.URISyntaxException;

/** Bounded modern intent URI parser; never grants access by parsing a string. */
final class IntentUri {
 private static final int GRANTS=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
 static Intent parse(String text,int options)throws URISyntaxException{
  if(text==null)throw new NullPointerException("uri");
  if(text.length()>65536)throw new URISyntaxException(text,"Intent URI length limit");
  boolean app=text.startsWith("android-app:"),intentScheme=text.startsWith("intent:");
  if((options&(Intent.URI_INTENT_SCHEME|Intent.URI_ANDROID_APP_SCHEME))!=0&&!app&&!intentScheme)return new Intent(Intent.ACTION_VIEW,Uri.parse(text));
  int fragment=text.lastIndexOf('#');boolean encoded=fragment>=0&&text.startsWith("#Intent;",fragment);
  if(!encoded&&!app){
   if(fragment>=0&&(text.startsWith("#action(",fragment)||text.startsWith("#categories(",fragment)))throw new URISyntaxException(text,"Legacy Intent URI format is not supported");
   return new Intent(Intent.ACTION_VIEW,Uri.parse(text));
  }
  Intent base=new Intent(Intent.ACTION_VIEW),current=base;String scheme=null,data=encoded?text.substring(0,fragment):text;
  boolean explicitAction=false;int position=encoded?fragment+8:-1;
  try{
   if(encoded){int fields=0;
    while(!text.substring(position).equals("end")){
     if(++fields>1024)throw new IllegalArgumentException("Intent URI field limit");
     int end=text.indexOf(';',position);if(end<0)throw new IllegalArgumentException("Missing intent terminator");
     String field=text.substring(position,end);position=end+1;
     if(field.equals("SEL")){if(current!=base)throw new IllegalArgumentException("Duplicate selector");current=new Intent();continue;}
     int equal=field.indexOf('=');if(equal<0)throw new IllegalArgumentException("Missing intent value");
     String key=field.substring(0,equal),value=Uri.decode(field.substring(equal+1));
     if(key.equals("action")){current.setAction(value);if(current==base)explicitAction=true;}
     else if(key.equals("category"))current.addCategory(value);
     else if(key.equals("type"))current.setDataAndType(current.getData(),value);
     else if(key.equals("launchFlags")){int flags=Integer.decode(value);current.setFlags((options&Intent.URI_ALLOW_UNSAFE)!=0?flags:flags&~GRANTS);}
     else if(key.equals("package"))current.setPackage(value);
     else if(key.equals("component")){ComponentName component=ComponentName.unflattenFromString(value);if(component==null)throw new IllegalArgumentException("Invalid component");current.setComponent(component);}
     else if(key.equals("scheme")){if(current==base)scheme=value;else current.setDataAndType(Uri.parse(value+":"),current.getType());}
     else if(key.equals("sourceBounds")){android.graphics.Rect bounds=android.graphics.Rect.unflattenFromString(value);if(bounds==null)throw new IllegalArgumentException("Invalid source bounds");current.setSourceBounds(bounds);}
     else{
      if(key.length()<2||key.charAt(1)!='.')throw new IllegalArgumentException("Unknown intent field");String name=Uri.decode(key.substring(2));
      switch(key.charAt(0)){
       case 'S':current.putExtra(name,value);break;case 'B':current.putExtra(name,Boolean.parseBoolean(value));break;
       case 'b':current.putExtra(name,Byte.parseByte(value));break;case 'c':if(value.isEmpty())throw new IllegalArgumentException("Empty character");current.putExtra(name,value.charAt(0));break;
       case 'd':current.putExtra(name,Double.parseDouble(value));break;case 'f':current.putExtra(name,Float.parseFloat(value));break;
       case 'i':current.putExtra(name,Integer.parseInt(value));break;case 'l':current.putExtra(name,Long.parseLong(value));break;case 's':current.putExtra(name,Short.parseShort(value));break;
       default:throw new IllegalArgumentException("Unknown extra type");
      }
     }
    }
   }
   if(current!=base&&base.getPackage()==null)base.setSelector(current);
   if(intentScheme)data=(scheme==null?"":scheme+":")+data.substring(7);
   else if(app){
    if(!data.startsWith("android-app://"))throw new IllegalArgumentException("Invalid android-app URI");
    String rest=data.substring(14);int slash=rest.indexOf('/');
    base.setPackage(Uri.decode(slash<0?rest:rest.substring(0,slash)));
    if(base.getPackage().isEmpty())throw new IllegalArgumentException("Missing android-app package");
    if(slash<0||slash==rest.length()-1){data="";if(!explicitAction)base.setAction(Intent.ACTION_MAIN);}
    else{rest=rest.substring(slash+1);slash=rest.indexOf('/');data=slash<0?rest+":":rest.substring(0,slash)+"://"+rest.substring(slash+1);}
   }
   if(!data.isEmpty())base.setDataAndType(Uri.parse(data),base.getType());return base;
  }catch(IllegalArgumentException|IndexOutOfBoundsException failure){throw new URISyntaxException(text,"Invalid Intent URI: "+failure.getMessage(),Math.max(0,position));}
 }
}
