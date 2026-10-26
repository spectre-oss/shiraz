// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import java.util.Arrays;

public class Signature {
 private final byte[] data;
 public Signature(byte[] signature){if(signature==null)throw new NullPointerException("signature");data=signature.clone();}
 public Signature(String text){if(text==null)throw new NullPointerException("signature");data=parse(text);}
 public byte[] toByteArray(){return data.clone();}
 public char[] toChars(){char[] out=new char[data.length*2];for(int i=0;i<data.length;i++){int v=data[i]&255;out[i*2]=hex(v>>>4);out[i*2+1]=hex(v&15);}return out;}
 public String toCharsString(){return new String(toChars());}
 public boolean equals(Object other){return other instanceof Signature&&Arrays.equals(data,((Signature)other).data);}
 public int hashCode(){return Arrays.hashCode(data);}
 private static char hex(int v){return (char)(v<10?'0'+v:'a'+v-10);}
 private static byte[] parse(String text){
  String hex=text.length()%2==0?text:("0"+text);byte[] out=new byte[hex.length()/2];
  for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
  return out;
 }
}
