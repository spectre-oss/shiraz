// SPDX-License-Identifier: GPL-3.0-only
package android.util;
public class Base64 {
 public static final int DEFAULT=0,NO_PADDING=1,NO_WRAP=2,CRLF=4,URL_SAFE=8,NO_CLOSE=16;
 private Base64(){}
 public static byte[] decode(String text,int flags){return decode(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),flags);}
 public static byte[] decode(byte[] input,int flags){return decode(input,0,input.length,flags);}
 public static byte[] decode(byte[] input,int offset,int length,int flags){
  StringBuilder clean=new StringBuilder(length);
  for(int i=offset;i<offset+length;i++){char c=(char)(input[i]&0xff);if(c==' '||c=='\n'||c=='\r'||c=='\t')continue;clean.append(c);}
  try{return ((flags&URL_SAFE)!=0?java.util.Base64.getUrlDecoder():java.util.Base64.getDecoder()).decode(clean.toString());}
  catch(IllegalArgumentException error){throw new IllegalArgumentException("bad base-64",error);}
 }
 public static byte[] encode(byte[] input,int flags){return encode(input,0,input.length,flags);}
 public static byte[] encode(byte[] input,int offset,int length,int flags){
  byte[] slice=java.util.Arrays.copyOfRange(input,offset,offset+length);
  java.util.Base64.Encoder encoder=(flags&URL_SAFE)!=0?java.util.Base64.getUrlEncoder():java.util.Base64.getEncoder();
  if((flags&NO_PADDING)!=0)encoder=encoder.withoutPadding();
  byte[] raw=encoder.encode(slice);
  if((flags&NO_WRAP)!=0||raw.length<=76)return (flags&NO_WRAP)!=0||(flags&CRLF)==0&&(flags&NO_WRAP)==0&&raw.length==0?raw:withEnding(raw,flags);
  return withEnding(raw,flags);
 }
 private static byte[] withEnding(byte[] raw,int flags){
  if((flags&NO_WRAP)!=0)return raw;
  String eol=(flags&CRLF)!=0?"\r\n":"\n";
  StringBuilder out=new StringBuilder();
  for(int i=0;i<raw.length;i+=76){out.append(new String(raw,i,Math.min(76,raw.length-i),java.nio.charset.StandardCharsets.ISO_8859_1)).append(eol);}
  return out.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
 }
 public static String encodeToString(byte[] input,int flags){return new String(encode(input,flags),java.nio.charset.StandardCharsets.ISO_8859_1);}
 public static String encodeToString(byte[] input,int offset,int length,int flags){return new String(encode(input,offset,length,flags),java.nio.charset.StandardCharsets.ISO_8859_1);}
}
