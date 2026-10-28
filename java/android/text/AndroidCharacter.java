// SPDX-License-Identifier: GPL-3.0-only
package android.text;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;

/** Unicode properties backed by the runtime's ICU data. */
public class AndroidCharacter {
    public static final int EAST_ASIAN_WIDTH_NEUTRAL=0,EAST_ASIAN_WIDTH_AMBIGUOUS=1;
    public static final int EAST_ASIAN_WIDTH_HALF_WIDTH=2,EAST_ASIAN_WIDTH_FULL_WIDTH=3;
    public static final int EAST_ASIAN_WIDTH_NARROW=4,EAST_ASIAN_WIDTH_WIDE=5;
    private static void range(int length,int start,int count){
        if(start<0||count<0||start>length-count)throw new ArrayIndexOutOfBoundsException();
    }
    public static char getMirror(char value){return (char)UCharacter.getMirror(value);}
    public static boolean mirror(char[] text,int start,int count){
        range(text.length,start,count);boolean changed=false;
        for(int i=start;i<start+count;i++){char next=getMirror(text[i]);changed|=next!=text[i];text[i]=next;}
        return changed;
    }
    public static int getEastAsianWidth(char value){return UCharacter.getIntPropertyValue(value,UProperty.EAST_ASIAN_WIDTH);}
    public static void getEastAsianWidths(char[] text,int start,int count,byte[] output){
        range(text.length,start,count);range(output.length,0,count);
        for(int i=0;i<count;){
            int point=Character.codePointAt(text,start+i,start+count);
            byte width=(byte)UCharacter.getIntPropertyValue(point,UProperty.EAST_ASIAN_WIDTH);
            int units=Character.charCount(point);
            for(int j=0;j<units;j++)output[i++]=width;
        }
    }
    public static void getDirectionalities(char[] text,byte[] output,int count){
        range(text.length,0,count);range(output.length,0,count);
        for(int i=0;i<count;){
            int point=Character.codePointAt(text,i,count);
            byte direction=Character.getDirectionality(point);
            int units=Character.charCount(point);
            for(int j=0;j<units;j++)output[i++]=direction;
        }
    }
}
