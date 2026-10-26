// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.graphics.Path;

/** SVG/VectorDrawable endpoint path grammar, normalized to lines and cubic curves. */
public final class PathData {
    private PathData() { }
    public static Path parse(String text) {
        if (text == null) throw new NullPointerException("path data");
        if (text.length() > 1024*1024) throw new IllegalArgumentException("Path text exceeds 1Mi characters");
        return new Reader(text).parse();
    }
    /** Compatible SVG command streams, validated once before interpolation. */
    public static final class Morph {
        private final char[] kinds;
        private final float[] from,to;
        private static final class Tokens {
            final java.util.ArrayList<Character> kinds=new java.util.ArrayList<Character>();
            final java.util.ArrayList<Float> values=new java.util.ArrayList<Float>();
            Tokens(String text){
                parse(text);Reader r=new Reader(text);char command=0;int parameter=0;
                while(true){r.spaces();if(r.at==text.length())break;
                    if(kinds.size()>=8192)throw new IllegalArgumentException("Path morph limit");
                    char c=text.charAt(r.at);
                    if(Character.isLetter(c)){r.at++;r.afterNumber=false;kinds.add(c);values.add(0f);command=c;parameter=0;}
                    else{kinds.add((char)0);values.add(Character.toUpperCase(command)=='A'&&(parameter%7==3||parameter%7==4)?(r.flag()?1f:0f):r.number());parameter++;}
                }
            }
        }
        public Morph(String start,String end){
            Tokens a=new Tokens(start),b=new Tokens(end);
            if(!a.kinds.equals(b.kinds))throw new IllegalArgumentException("Incompatible path morph commands");
            kinds=new char[a.kinds.size()];from=new float[kinds.length];to=new float[kinds.length];
            for(int i=0;i<kinds.length;i++){kinds[i]=a.kinds.get(i);from[i]=a.values.get(i);to[i]=b.values.get(i);}
        }
        public Evaluator newEvaluator(){return new Evaluator(this);}
        public Path interpolate(float fraction){Path path=new Path();newEvaluator().apply(path,fraction);return path;}
        /** Per-animator cursor; immutable command data can safely be shared by clones. */
        public static final class Evaluator {
            private final Reader reader;
            private Evaluator(Morph morph){reader=new Reader(morph);}
            public void apply(Path destination,float fraction){
                if(destination==null)throw new NullPointerException("destination");
                if(!Float.isFinite(fraction))throw new IllegalArgumentException("Path morph fraction");
                // Validate before changing the destination, including overshooting interpolators.
                for(int i=0;i<reader.morph.kinds.length;i++)if(reader.morph.kinds[i]==0){
                    float value=reader.value(i,fraction);
                    if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("Path morph coordinate range");
                }
                reader.at=0;reader.x=reader.y=reader.cx=reader.cy=reader.startX=reader.startY=0;
                reader.previous=0;reader.fraction=fraction;reader.path=destination;
                destination.rewind();
                try{reader.parse();}finally{reader.path=null;}
            }
        }
    }
    private static final class Reader {
        final String text; final Morph morph; float fraction; int at; boolean afterNumber;
        float x,y,cx,cy,startX,startY; char previous;
        Path path;
        Reader(String text) { this.text=text; morph=null; path=new Path(); }
        Reader(Morph morph) { text=null;this.morph=morph; }
        float value(int index,float fraction){return fraction==0?morph.from[index]:fraction==1?morph.to[index]:morph.from[index]+(morph.to[index]-morph.from[index])*fraction;}

        void spaces() { if(morph!=null)return; while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t' || text.charAt(at) == '\r' || text.charAt(at) == '\n')) at++; }
        void separator() { spaces(); if (at < text.length() && text.charAt(at) == ',') { if (!afterNumber) fail(); at++; spaces(); } }
        void fail() { throw new IllegalArgumentException("Malformed path data at "+at); }
        float number() {
            if(morph!=null)return value(at++,fraction);
            separator(); int begin=at,digits=0;
            if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
            while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') { at++; digits++; }
            if (at < text.length() && text.charAt(at) == '.') { at++; while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') { at++; digits++; } }
            if (digits == 0) fail();
            if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                at++; if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
                int exponent=at; while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
                if (at == exponent) fail();
            }
            float value=Float.parseFloat(text.substring(begin,at)); if (!Float.isFinite(value) || Math.abs(value) > 10000000) fail(); afterNumber=true; return value;
        }
        boolean flag() { if(morph!=null)return number()!=0; separator(); if (at >= text.length() || text.charAt(at) != '0' && text.charAt(at) != '1') { fail(); return false; } boolean result=text.charAt(at++) == '1'; afterNumber=true; return result; }
        Path parse() {
            char command=0;
            while (true) {
                spaces(); if (at == (morph==null?text.length():morph.kinds.length)) return path;
                char c=morph==null?text.charAt(at):morph.kinds[at];
                if (Character.isLetter(c)) { command=c; at++; afterNumber=false; }
                else if (command == 0) fail();
                char kind=Character.toUpperCase(command); boolean relative=Character.isLowerCase(command);
                // Android path data may begin with drawing commands at the implicit origin.
                float ox=relative ? x : 0,oy=relative ? y : 0;
                switch (kind) {
                    case 'M': x=number()+ox; y=number()+oy; path.moveTo(x,y); startX=x; startY=y; command=relative ? 'l' : 'L'; break;
                    case 'L': x=number()+ox; y=number()+oy; path.lineTo(x,y); break;
                    case 'H': x=number()+ox; path.lineTo(x,y); break;
                    case 'V': y=number()+oy; path.lineTo(x,y); break;
                    case 'C': { float a=number()+ox,b=number()+oy; cx=number()+ox; cy=number()+oy; x=number()+ox; y=number()+oy; path.cubicTo(a,b,cx,cy,x,y); break; }
                    case 'S': { float a=previous == 'C' || previous == 'S' ? 2*x-cx : x,b=previous == 'C' || previous == 'S' ? 2*y-cy : y; cx=number()+ox; cy=number()+oy; x=number()+ox; y=number()+oy; path.cubicTo(a,b,cx,cy,x,y); break; }
                    case 'Q': cx=number()+ox; cy=number()+oy; x=number()+ox; y=number()+oy; path.quadTo(cx,cy,x,y); break;
                    case 'T': { float a=previous == 'Q' || previous == 'T' ? 2*x-cx : x,b=previous == 'Q' || previous == 'T' ? 2*y-cy : y; x=number()+ox; y=number()+oy; path.quadTo(a,b,x,y); cx=a; cy=b; break; }
                    case 'A': { float rx=number(),ry=number(),rotation=number(); boolean large=flag(),sweep=flag(); float nx=number()+ox,ny=number()+oy; arc(path,x,y,rx,ry,rotation,large,sweep,nx,ny); x=nx; y=ny; break; }
                    case 'Z': path.close(); x=startX; y=startY; command=0; break;
                    default: fail();
                }
                previous=kind;
            }
        }
    }
    private static void arc(Path path,double x0,double y0,double rx,double ry,double rotation,boolean large,boolean sweep,double x1,double y1) {
        if (x0 == x1 && y0 == y1) return;
        rx=Math.abs(rx); ry=Math.abs(ry); if (rx == 0 || ry == 0) { path.lineTo((float)x1,(float)y1); return; }
        double angle=Math.toRadians(rotation%360),co=Math.cos(angle),si=Math.sin(angle);
        double xp=(co*(x0-x1)+si*(y0-y1))/2,yp=(-si*(x0-x1)+co*(y0-y1))/2;
        double scale=xp*xp/(rx*rx)+yp*yp/(ry*ry);
        if (scale > 1) { scale=Math.sqrt(scale); rx*=scale; ry*=scale; }
        double numerator=rx*rx*ry*ry-rx*rx*yp*yp-ry*ry*xp*xp,denominator=rx*rx*yp*yp+ry*ry*xp*xp;
        double factor=(large == sweep ? -1 : 1)*Math.sqrt(Math.max(0,numerator/denominator));
        double cxp=factor*rx*yp/ry,cyp=-factor*ry*xp/rx;
        double centerX=co*cxp-si*cyp+(x0+x1)/2,centerY=si*cxp+co*cyp+(y0+y1)/2;
        double ux=(xp-cxp)/rx,uy=(yp-cyp)/ry,vx=(-xp-cxp)/rx,vy=(-yp-cyp)/ry;
        double theta=Math.atan2(uy,ux),delta=Math.atan2(ux*vy-uy*vx,ux*vx+uy*vy);
        if (sweep && delta < 0) delta+=2*Math.PI; else if (!sweep && delta > 0) delta-=2*Math.PI;
        int pieces=Math.max(1,(int)Math.ceil(Math.abs(delta)/(Math.PI/2))); double step=delta/pieces;
        for (int i=0;i<pieces;i++) {
            double next=theta+step,k=4.0/3*Math.tan(step/4),cos0=Math.cos(theta),sin0=Math.sin(theta),cos1=Math.cos(next),sin1=Math.sin(next);
            double ax=centerX+rx*co*cos0-ry*si*sin0,ay=centerY+rx*si*cos0+ry*co*sin0;
            double bx=centerX+rx*co*cos1-ry*si*sin1,by=centerY+rx*si*cos1+ry*co*sin1;
            double dx0=-rx*co*sin0-ry*si*cos0,dy0=-rx*si*sin0+ry*co*cos0,dx1=-rx*co*sin1-ry*si*cos1,dy1=-rx*si*sin1+ry*co*cos1;
            path.cubicTo((float)(ax+k*dx0),(float)(ay+k*dy0),(float)(bx-k*dx1),(float)(by-k*dy1),(float)(i == pieces-1 ? x1 : bx),(float)(i == pieces-1 ? y1 : by));
            theta=next;
        }
    }
}
