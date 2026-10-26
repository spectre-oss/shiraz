// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.graphics.Paint;
import java.util.ArrayList;
import java.util.TreeSet;

/** Visual geometry from the same glyph plan used by measurement and drawing. */
public final class TextGeometry {
    public final float[] clusters;
    public final int length;

    private final int baseLevel;
    public TextGeometry(String text,Paint paint){this(text,paint,new java.text.Bidi(text,java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).getBaseLevel());}
    public TextGeometry(String text,Paint paint,int baseLevel){length=text.length();clusters=paint.shirazTextClusters(text);this.baseLevel=baseLevel;}
    public static byte[] levels(java.text.Bidi paragraph,int start,int end){
        if(start==end)return new byte[0];java.text.Bidi line=paragraph.createLineBidi(start,end);byte[] result=new byte[end-start];for(int i=0;i<result.length;i++)result[i]=(byte)line.getLevelAt(i);return result;
    }
    public static int[] directions(byte[] levels){
        if(levels.length==0)return new int[]{0,0};ArrayList<Integer> starts=new ArrayList<Integer>();ArrayList<Integer> lengths=new ArrayList<Integer>();
        for(int at=0;at<levels.length;){int end=at+1;while(end<levels.length&&levels[end]==levels[at])end++;starts.add(at);lengths.add(end-at);at=end;}
        byte[] runLevels=new byte[starts.size()];Object[] order=new Object[starts.size()];for(int i=0;i<order.length;i++){runLevels[i]=levels[starts.get(i)];order[i]=i;}
        java.text.Bidi.reorderVisually(runLevels,0,order,0,order.length);int[] result=new int[order.length*2];
        for(int i=0;i<order.length;i++){int run=(Integer)order[i];result[i*2]=starts.get(run);result[i*2+1]=lengths.get(run)|((int)runLevels[run]<<26);}return result;
    }
    public int hit(float x){
        float distance=Float.POSITIVE_INFINITY;int offset=0;
        for(int i=0;i<clusters.length;i+=5){
            int start=(int)clusters[i],end=(int)clusters[i+1];boolean rtl=((int)clusters[i+4]&1)!=0;
            float a=rtl?clusters[i+3]:clusters[i+2],b=rtl?clusters[i+2]:clusters[i+3];
            if(Math.abs(x-a)<distance){distance=Math.abs(x-a);offset=start;}
            if(Math.abs(x-b)<distance){distance=Math.abs(x-b);offset=end;}
        }
        return offset;
    }
    public float caret(int offset){
        if(offset<0||offset>length)throw new IndexOutOfBoundsException();
        float width=clusters.length==0?0:clusters[clusters.length-2];
        float before=(baseLevel&1)==0?0:width,after=(baseLevel&1)==0?width:0;
        int beforeLevel=baseLevel,afterLevel=baseLevel;
        for(int i=0;i<clusters.length;i+=5){
            int start=(int)clusters[i],end=(int)clusters[i+1],level=(int)clusters[i+4];boolean rtl=(level&1)!=0;
            if(offset>start&&offset<end)return rtl?clusters[i+3]:clusters[i+2];
            if(end==offset){before=rtl?clusters[i+2]:clusters[i+3];beforeLevel=level;}
            if(start==offset){after=rtl?clusters[i+3]:clusters[i+2];afterLevel=level;}
        }
        return beforeLevel<afterLevel?before:after;
    }

    public float[] ranges(int start,int end){
        if(start<0||end<start||end>length)throw new IndexOutOfBoundsException();
        if(start==end)return new float[0];
        ArrayList<Float> result=new ArrayList<Float>();
        for(int i=0;i<clusters.length;i+=5)if(clusters[i]<end&&clusters[i+1]>start){
            float left=Math.min(clusters[i+2],clusters[i+3]),right=Math.max(clusters[i+2],clusters[i+3]);
            if(!result.isEmpty()&&Math.abs(result.get(result.size()-1)-left)<.001f)result.set(result.size()-1,right);
            else {result.add(left);result.add(right);}
        }
        float[] values=new float[result.size()];for(int i=0;i<values.length;i++)values[i]=result.get(i);return values;
    }
    public ArrayList<Integer> boundaries(){TreeSet<Integer> values=new TreeSet<Integer>();values.add(0);values.add(length);for(int i=0;i<clusters.length;i+=5){values.add((int)clusters[i]);values.add((int)clusters[i+1]);}return new ArrayList<Integer>(values);}
}
