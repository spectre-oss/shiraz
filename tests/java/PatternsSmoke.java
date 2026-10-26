// SPDX-License-Identifier: GPL-3.0-only
import android.util.Patterns;
public final class PatternsSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static String repeat(char c,int count){char[] data=new char[count];java.util.Arrays.fill(data,c);return new String(data);}
 public static void main(String[] args){
  for(String value:new String[]{"info@example.com","first.last+tag_5%test@sub.example.travel","A@B.C","a@xn--example.test"})check(Patterns.EMAIL_ADDRESS.matcher(value).matches(),"valid Android email heuristic: "+value);
  for(String value:new String[]{"","Vienna","https://example.com","http://a@example.com","a@localhost","@example.com","a@@example.com","a@.example.com","a@-example.com","a@example..com","a@example.com/route"," a@example.com","a@example.com ","a@example.com\n","a\nb@example.com","a\u0000b@example.com","\"name\"@example.com","name!@example.com","نام@example.com"})check(!Patterns.EMAIL_ADDRESS.matcher(value).matches(),"malformed/nonemail text accepted");
  check(Patterns.EMAIL_ADDRESS.matcher(repeat('a',256)+"@example.com").matches(),"API local-part bound");
  check(!Patterns.EMAIL_ADDRESS.matcher(repeat('a',257)+"@example.com").matches(),"local-part bound enforced");
  check(Patterns.EMAIL_ADDRESS.matcher("a@"+repeat('b',65)+"."+repeat('c',26)).matches(),"API domain-label bounds");
  check(!Patterns.EMAIL_ADDRESS.matcher("a@"+repeat('b',66)+".com").matches(),"first label bound");
  check(!Patterns.EMAIL_ADDRESS.matcher("a@host."+repeat('c',27)).matches(),"later label bound");
  // Preserve Android's documented heuristic rather than silently promising RFC validation.
  check(Patterns.EMAIL_ADDRESS.matcher("a..b@example-.com").matches(),"Android permissive dot/hyphen behavior");
  java.util.regex.Matcher matcher=Patterns.EMAIL_ADDRESS.matcher("Email info@example.com today");check(matcher.find()&&matcher.group().equals("info@example.com"),"substring find contract");
  check(!Patterns.EMAIL_ADDRESS.matcher(repeat('x',10000)).matches(),"large nonemail input");
  try{Patterns.EMAIL_ADDRESS.matcher(null);throw new AssertionError("null accepted");}catch(NullPointerException expected){}
  System.out.println("PATTERNS_EMAIL_COMPATIBILITY_OK");
 }
}
