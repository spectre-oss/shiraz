// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.ArrayList;

/** Snapshot contour measurement with bounded adaptive cubic arc-length integration. */
public class PathMeasure {
    public static final int POSITION_MATRIX_FLAG=1,TANGENT_MATRIX_FLAG=2;
    private ArrayList<Contour> contours=new ArrayList<Contour>();
    private int index;
    private static final class Leaf { double t0,t1,end; Leaf(double a,double b,double end){t0=a;t1=b;this.end=end;} }
    private static final class Segment {
        final double[] p; final boolean curve; final ArrayList<Leaf> leaves=new ArrayList<Leaf>(); double length,end;
        Segment(double[] points,int[] budget){p=points;curve=p.length==8;if(curve)subdivide(p,0,1,.001,0,budget);else length=Math.hypot(p[2]-p[0],p[3]-p[1]);}
        private static double[] mix(double[] a,double[] b,double t){return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t};}
        private static double[][] split(double[] p,double t){
            double[] a=mix(new double[]{p[0],p[1]},new double[]{p[2],p[3]},t),b=mix(new double[]{p[2],p[3]},new double[]{p[4],p[5]},t),c=mix(new double[]{p[4],p[5]},new double[]{p[6],p[7]},t);
            double[] d=mix(a,b,t),e=mix(b,c,t),f=mix(d,e,t);
            return new double[][]{{p[0],p[1],a[0],a[1],d[0],d[1],f[0],f[1]},{f[0],f[1],e[0],e[1],c[0],c[1],p[6],p[7]}};
        }
        double speed(double t){double u=1-t;return Math.hypot(3*(u*u*(p[2]-p[0])+2*u*t*(p[4]-p[2])+t*t*(p[6]-p[4])),3*(u*u*(p[3]-p[1])+2*u*t*(p[5]-p[3])+t*t*(p[7]-p[5])));}
        double integral(double a,double b){double center=(a+b)/2,half=(b-a)/2;return half*(.5688888888888889*speed(center)+.4786286704993665*(speed(center-half*.5384693101056831)+speed(center+half*.5384693101056831))+.2369268850561891*(speed(center-half*.9061798459386640)+speed(center+half*.9061798459386640)));}
        void subdivide(double[] q,double a,double b,double tolerance,int depth,int[] budget){
            double chord=Math.hypot(q[6]-q[0],q[7]-q[1]),polygon=0;for(int i=0;i<6;i+=2)polygon+=Math.hypot(q[i+2]-q[i],q[i+3]-q[i+1]);
            double whole=integral(a,b),middle=(a+b)/2,halves=integral(a,middle)+integral(middle,b);
            if(polygon-chord<=tolerance && Math.abs(whole-halves)<=tolerance){
                if(++budget[0]>262144)throw new IllegalArgumentException("PathMeasure integration budget");
                length+=Math.max(chord,Math.min(polygon,halves));leaves.add(new Leaf(a,b,length));return;
            }
            if(depth>=20)throw new IllegalArgumentException("PathMeasure curve precision/depth limit");
            double[][] halvesP=split(q,.5);subdivide(halvesP[0],a,middle,tolerance/2,depth+1,budget);subdivide(halvesP[1],middle,b,tolerance/2,depth+1,budget);
        }
        double parameter(double distance){
            if(distance<=0)return 0;if(distance>=length)return 1;if(!curve)return distance/length;
            int low=0,high=leaves.size()-1;while(low<high){int m=(low+high)>>>1;if(leaves.get(m).end<distance)low=m+1;else high=m;}
            Leaf leaf=leaves.get(low);double before=low==0?0:leaves.get(low-1).end,target=distance-before,left=leaf.t0,right=leaf.t1,t=left+(right-left)*target/(leaf.end-before);
            for(int n=0;n<32;n++){
                double error=integral(leaf.t0,t)-target;if(Math.abs(error)<Math.max(1e-8,length*1e-11))break;
                if(error>0)right=t;else left=t;double speed=speed(t),next=speed>0?t-error/speed:Double.NaN;
                t=Double.isFinite(next)&&next>left&&next<right?next:(left+right)/2;
            }return Math.max(leaf.t0,Math.min(leaf.t1,t));
        }
        double[] position(double t){if(!curve)return new double[]{p[0]+(p[2]-p[0])*t,p[1]+(p[3]-p[1])*t};double u=1-t;return new double[]{u*u*u*p[0]+3*u*u*t*p[2]+3*u*t*t*p[4]+t*t*t*p[6],u*u*u*p[1]+3*u*u*t*p[3]+3*u*t*t*p[5]+t*t*t*p[7]};}
        double[] tangent(double t){
            double dx,dy;if(!curve){dx=p[2]-p[0];dy=p[3]-p[1];}else{double u=1-t;dx=3*(u*u*(p[2]-p[0])+2*u*t*(p[4]-p[2])+t*t*(p[6]-p[4]));dy=3*(u*u*(p[3]-p[1])+2*u*t*(p[5]-p[3])+t*t*(p[7]-p[5]));}
            double norm=Math.hypot(dx,dy);if(norm==0){double[] a=position(Math.max(0,t-1e-5)),b=position(Math.min(1,t+1e-5));dx=b[0]-a[0];dy=b[1]-a[1];norm=Math.hypot(dx,dy);}return norm==0?new double[]{0,0}:new double[]{dx/norm,dy/norm};
        }
        void append(Path out,double first,double last){
            if(!curve){double[] end=position(last);out.lineTo((float)end[0],(float)end[1]);return;}
            double[] q=last<1?split(p,last)[0]:p;if(first>0)q=split(q,first/last)[1];out.cubicTo((float)q[2],(float)q[3],(float)q[4],(float)q[5],(float)q[6],(float)q[7]);
        }
    }
    private static final class Contour {
        final ArrayList<Segment> segments=new ArrayList<Segment>();double length;boolean closed;
        void add(Segment s){if(s.length>0){length+=s.length;s.end=length;segments.add(s);}}
        int atIndex(double distance){int low=0,high=segments.size()-1;while(low<high){int m=(low+high)>>>1;if(segments.get(m).end<distance)low=m+1;else high=m;}return low;}
        Segment at(double distance){return segments.get(atIndex(distance));}
    }
    public PathMeasure(){}
    public PathMeasure(Path path,boolean forceClosed){setPath(path,forceClosed);}
    private static void finish(ArrayList<Contour> out,Contour c,boolean closed,double x,double y,double sx,double sy,int[] budget){if(c==null)return;if(closed)c.add(new Segment(new double[]{x,y,sx,sy},budget));c.closed=closed;if(c.length>0)out.add(c);}
    public void setPath(Path path,boolean forceClosed){
        ArrayList<Contour> result=new ArrayList<Contour>();int[] budget={0};
        if(path!=null){float[] values=path.commands();Contour c=null;double x=0,y=0,sx=0,sy=0;
            for(int at=0;at<values.length;){int command=(int)values[at++];
                if(command==1){finish(result,c,forceClosed,x,y,sx,sy,budget);c=new Contour();x=sx=values[at++];y=sy=values[at++];}
                else if(command==4){finish(result,c,true,x,y,sx,sy,budget);c=null;x=sx;y=sy;}
                else{
                    if(c==null){c=new Contour();sx=x;sy=y;}
                    if(command==2){double nx=values[at++],ny=values[at++];c.add(new Segment(new double[]{x,y,nx,ny},budget));x=nx;y=ny;}
                    else if(command==3){double[] q={x,y,values[at],values[at+1],values[at+2],values[at+3],values[at+4],values[at+5]};at+=6;c.add(new Segment(q,budget));x=q[6];y=q[7];}
                    else throw new IllegalArgumentException("PathMeasure command");
                }
            }finish(result,c,forceClosed,x,y,sx,sy,budget);
        }contours=result;index=0;
    }
    private Contour current(){return index<contours.size()?contours.get(index):null;}
    public float getLength(){Contour c=current();return c==null?0:(float)c.length;}
    public boolean isClosed(){Contour c=current();return c!=null&&c.closed;}
    public boolean nextContour(){if(index<contours.size())index++;return current()!=null;}
    private static double pin(float distance,double length){return distance>=(float)length?length:Math.max(0,Math.min(length,distance));}
    public boolean getPosTan(float distance,float[] position,float[] tangent){
        if(position!=null&&position.length<2||tangent!=null&&tangent.length<2)throw new ArrayIndexOutOfBoundsException("position/tangent needs two elements");
        Contour c=current();if(c==null||Float.isNaN(distance))return false;double d=pin(distance,c.length);Segment s=c.at(d);double t=s.parameter(d-(s.end-s.length));double[] pos=s.position(t),tan=s.tangent(t);
        if(position!=null){position[0]=(float)pos[0];position[1]=(float)pos[1];}if(tangent!=null){tangent[0]=(float)tan[0];tangent[1]=(float)tan[1];}return true;
    }
    public boolean getSegment(float startD,float stopD,Path destination,boolean startWithMoveTo){
        if(destination==null)throw new NullPointerException("destination");Contour c=current();if(c==null||Float.isNaN(startD)||Float.isNaN(stopD))return false;
        double start=pin(startD,c.length),stop=pin(stopD,c.length);if(start>=stop)return false;Path result=new Path(destination);boolean first=true;
        for(int i=c.atIndex(start);i<c.segments.size();i++){Segment s=c.segments.get(i);double before=s.end-s.length;if(s.end<=start)continue;if(before>=stop)break;double a=s.parameter(Math.max(0,start-before)),b=s.parameter(Math.min(s.length,stop-before));
            if(first){double[] p=s.position(a);if(startWithMoveTo)result.moveTo((float)p[0],(float)p[1]);else result.lineTo((float)p[0],(float)p[1]);first=false;}s.append(result,a,b);
        }destination.set(result);return !first;
    }
}
