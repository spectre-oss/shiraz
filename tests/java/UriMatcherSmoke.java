// SPDX-License-Identifier: GPL-3.0-only
import android.content.UriMatcher;
import android.net.Uri;
public final class UriMatcherSmoke {
 private static void match(UriMatcher matcher,String uri,int expected){int actual=matcher.match(Uri.parse(uri));if(actual!=expected)throw new AssertionError(uri+": "+actual+" != "+expected);}
 public static void main(String[] args){
  UriMatcher contacts=new UriMatcher(-1);String authority="com.android.contacts";
  contacts.addURI(authority,"contacts/lookup/*/#",1);contacts.addURI(authority,"contacts/lookup/*",1);contacts.addURI(authority,"contacts/#/photo",2);contacts.addURI(authority,"contacts/#",3);contacts.addURI(authority,"display_photo/#",4);
  match(contacts,"content://com.android.contacts/contacts/lookup/a%2Fb/123",1);match(contacts,"content://com.android.contacts/contacts/lookup/%D8%AD%D8%A7%D9%81%D8%B8",1);match(contacts,"content://com.android.contacts/contacts/42/photo?size=2#frag",2);match(contacts,"content://com.android.contacts/contacts/%34%32",3);match(contacts,"content://com.android.contacts/display_photo/0001",4);
  for(String path:new String[]{"contacts/-1","contacts/+2","contacts/۱۲","contacts/4x","contacts/42/photo/extra","contacts","display_photo/"})match(contacts,"content://com.android.contacts/"+path,-1);match(contacts,"content://other/contacts/42",-1);
  UriMatcher order=new UriMatcher(7);order.addURI("a","*/tail",1);order.addURI("a","exact/other",2);match(order,"content://a/exact/other",-1);match(order,"content://a/exact/tail",1);order.addURI("a","*/tail",9);match(order,"content://a/exact/tail",9);
  UriMatcher numeric=new UriMatcher(-1);numeric.addURI("a","#",1);numeric.addURI("a","42",2);numeric.addURI("a","*",3);match(numeric,"content://a/42",1);match(numeric,"content://a/word",3);match(numeric,"content://a/",-1);
  UriMatcher root=new UriMatcher(7);match(root,"",7);match(root,"mailto:person",7);root.addURI("a",null,0);match(root,"content://a",0);match(root,"http://a/?q=1",0);root.addURI("a","/x/y",8);match(root,"content://a/x/y/",8);match(root,"content://a/x//y",8);match(root,"content://a/x%2Fy",-1);match(root,"content://a/X/y",-1);
  try{root.addURI("a","bad",-1);throw new AssertionError("negative code");}catch(IllegalArgumentException expected){}
  System.out.println("URI_MATCHER_CONTACTS_ORDER_OK");
 }
}
