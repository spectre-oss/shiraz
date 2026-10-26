// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.ArrayList;

/** Rounds line-to-line corners with quadratic joins; existing cubic curves are preserved. */
public class CornerPathEffect extends PathEffect {
    private final float radius;
    public CornerPathEffect(float radius){if(!Float.isFinite(radius)||radius<0||radius>10000000)throw new IllegalArgumentException("Corner radius");this.radius=radius;}
    private static final class Segment {
        final float x,y,ex,ey;final float[] curve;
        Segment(float x,float y,float ex,float ey,float[] curve){this.x=x;this.y=y;this.ex=ex;this.ey=ey;this.curve=curve;}
        double length(){return Math.hypot((double)ex-x,(double)ey-y);}
    }
    Path apply(Path source){
        if(radius==0)return source;
        Path out=new Path();out.setFillType(source.getFillType());ArrayList<Segment> segments=new ArrayList<Segment>();
        float[] commands=source.commands();float x=0,y=0,startX=0,startY=0;boolean active=false;
        for(int i=0;i<commands.length;){int command=(int)commands[i++];
            if(command==1){if(active)contour(out,segments,startX,startY,false);segments.clear();x=startX=commands[i++];y=startY=commands[i++];active=true;}
            else if(command==2){float ex=commands[i++],ey=commands[i++];if(!active){startX=x;startY=y;active=true;}if(ex!=x||ey!=y)segments.add(new Segment(x,y,ex,ey,null));x=ex;y=ey;}
            else if(command==3){float[] curve={commands[i++],commands[i++],commands[i++],commands[i++],commands[i++],commands[i++]};if(!active){startX=x;startY=y;active=true;}segments.add(new Segment(x,y,curve[4],curve[5],curve));x=curve[4];y=curve[5];}
            else if(command==4){if(active){if(x!=startX||y!=startY)segments.add(new Segment(x,y,startX,startY,null));contour(out,segments,startX,startY,true);segments.clear();x=startX;y=startY;active=false;}}
            else throw new IllegalArgumentException("Unknown path command");
        }
        if(active)contour(out,segments,startX,startY,false);return out;
    }
    private boolean corner(Segment incoming,Segment outgoing){return incoming!=null&&outgoing!=null&&incoming.curve==null&&outgoing.curve==null;}
    private float step(Segment segment,boolean x){double length=segment.length(),scale=length==0?0:Math.min(.5,radius/length);return (float)(((double)(x?segment.ex:segment.ey)-(x?segment.x:segment.y))*scale);}
    private void contour(Path out,ArrayList<Segment> segments,float startX,float startY,boolean closed){
        if(segments.isEmpty()){out.moveTo(startX,startY);if(closed)out.close();return;}
        Segment first=segments.get(0),last=segments.get(segments.size()-1);
        boolean firstCorner=closed&&corner(last,first);
        out.moveTo(first.x+(firstCorner?step(first,true):0),first.y+(firstCorner?step(first,false):0));
        for(int i=0;i<segments.size();i++){
            Segment segment=segments.get(i),next=i+1<segments.size()?segments.get(i+1):closed?first:null;
            boolean round=corner(segment,next);
            if(segment.curve!=null){float[] c=segment.curve;out.cubicTo(c[0],c[1],c[2],c[3],c[4],c[5]);}
            else out.lineTo(segment.ex-(round?step(segment,true):0),segment.ey-(round?step(segment,false):0));
            if(round)out.quadTo(segment.ex,segment.ey,segment.ex+step(next,true),segment.ey+step(next,false));
        }
        if(closed)out.close();
    }
}
