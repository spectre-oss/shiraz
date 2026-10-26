// SPDX-License-Identifier: GPL-3.0-only
package android.text.util;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Linkify {
 public static final int WEB_URLS=1;
 public static final int EMAIL_ADDRESSES=2;
 public static final int PHONE_NUMBERS=4;
 public static final int MAP_ADDRESSES=8;
 public static final int ALL=WEB_URLS|EMAIL_ADDRESSES|PHONE_NUMBERS|MAP_ADDRESSES;
 private static final Pattern WEB=Pattern.compile("(?:(?:https?|ftp)://|www\\.)[^\\s<>\"']+[^\\s<>\"'.,;:!?)\\]]");
 private static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
 private static final Pattern PHONE=Pattern.compile("(?<![\\w])\\+?[0-9][0-9 ()\\-]{5,}[0-9]");
 public static final boolean addLinks(Spannable text,int mask){
  if(mask==0)return false;
  for(URLSpan old:text.getSpans(0,text.length(),URLSpan.class))text.removeSpan(old);
  boolean found=false;
  if((mask&WEB_URLS)!=0)found|=addLinks(text,WEB,null);
  if((mask&EMAIL_ADDRESSES)!=0)found|=addLinks(text,EMAIL,"mailto:");
  if((mask&PHONE_NUMBERS)!=0)found|=addLinks(text,PHONE,"tel:");
  return found;
 }
 public static final boolean addLinks(TextView view,int mask){
  if(mask==0)return false;
  CharSequence value=view.getText();
  if(value instanceof Spannable){
   if(!addLinks((Spannable)value,mask))return false;
   attach(view);
   return true;
  }
  SpannableString copy=SpannableString.valueOf(value);
  if(!addLinks(copy,mask))return false;
  view.setText(copy);
  attach(view);
  return true;
 }
 public static final void addLinks(TextView view,Pattern pattern,String scheme){
  CharSequence value=view.getText();
  if(value instanceof Spannable){
   if(addLinks((Spannable)value,pattern,scheme))attach(view);
   return;
  }
  SpannableString copy=SpannableString.valueOf(value);
  if(addLinks(copy,pattern,scheme)){view.setText(copy);attach(view);}
 }
 public interface MatchFilter{boolean acceptMatch(CharSequence s,int start,int end);}
 public interface TransformFilter{String transformUrl(Matcher match,String url);}
 public static final boolean addLinks(Spannable text,Pattern pattern,String scheme){
  return core(text,pattern,scheme==null?new String[0]:new String[]{scheme},null,null,scheme==null);
 }
 public static final boolean addLinks(Spannable text,Pattern pattern,String scheme,MatchFilter matchFilter,TransformFilter transformFilter){
  return core(text,pattern,scheme==null?new String[0]:new String[]{scheme},matchFilter,transformFilter,false);
 }
 public static final boolean addLinks(Spannable text,Pattern pattern,String defaultScheme,String[] schemes,MatchFilter matchFilter,TransformFilter transformFilter){
  String fallback=defaultScheme==null?"":defaultScheme;
  String[] known=schemes==null||schemes.length==0?new String[]{fallback}:schemes;
  String[] ordered=new String[known.length+1];ordered[0]=fallback;System.arraycopy(known,0,ordered,1,known.length);
  return core(text,pattern,ordered,matchFilter,transformFilter,false);
 }
 public static final void addLinks(TextView view,Pattern pattern,String scheme,MatchFilter matchFilter,TransformFilter transformFilter){
  CharSequence value=view.getText();
  if(value instanceof Spannable){
   if(addLinks((Spannable)value,pattern,scheme,matchFilter,transformFilter))attach(view);
   return;
  }
  SpannableString copy=SpannableString.valueOf(value);
  if(addLinks(copy,pattern,scheme,matchFilter,transformFilter)){view.setText(copy);attach(view);}
 }
 private static boolean core(Spannable text,Pattern pattern,String[] schemes,MatchFilter matchFilter,TransformFilter transformFilter,boolean web){
  boolean found=false;
  Matcher matcher=pattern.matcher(text);
  while(matcher.find()){
   int start=matcher.start(),end=matcher.end();
   if(matchFilter!=null&&!matchFilter.acceptMatch(text,start,end))continue;
   String match=matcher.group();
   String url=transformFilter==null?match:transformFilter.transformUrl(matcher,match);
   if(web){if(!url.regionMatches(true,0,"http://",0,7)&&!url.regionMatches(true,0,"https://",0,8)&&!url.regionMatches(true,0,"ftp://",0,6))url=(url.regionMatches(true,0,"www.",0,4)?"http://":"")+url;}
   else if(schemes.length>0){
    boolean prefixed=false;
    for(int i=schemes.length>1?1:0;i<schemes.length;i++)if(url.regionMatches(true,0,schemes[i],0,schemes[i].length())){prefixed=true;break;}
    if(!prefixed)url=schemes[0]+url;
   }
   text.setSpan(new URLSpan(url),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   found=true;
  }
  return found;
 }
 private static void attach(TextView view){
  if(view.getLinksClickable()&&view.getMovementMethod()==null)view.setMovementMethod(LinkMovementMethod.getInstance());
 }
}
