// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.Parcel;
/** Bounded pointer snapshots; wheel axes supported; history, other extended axes and parcel transport remain unsupported. */
public final class MotionEvent extends InputEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3, ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5, ACTION_POINTER_UP = 6, ACTION_HOVER_MOVE = 7, ACTION_SCROLL = 8, ACTION_HOVER_ENTER = 9, ACTION_HOVER_EXIT = 10, ACTION_BUTTON_PRESS = 11, ACTION_BUTTON_RELEASE = 12;
    public static final int ACTION_MASK = 255, ACTION_POINTER_INDEX_MASK = 0xff00, ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int FLAG_WINDOW_IS_OBSCURED = 0x1, FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2, FLAG_IS_GENERATED_GESTURE = 0x8, FLAG_NO_FOCUS_CHANGE = 0x1000;
    public static final int BUTTON_PRIMARY = 1, TOOL_TYPE_UNKNOWN = 0, TOOL_TYPE_FINGER = 1, TOOL_TYPE_MOUSE = 3;
    public static final int AXIS_VSCROLL=9,AXIS_HSCROLL=10;
    public float getAxisValue(int axis){return getAxisValue(axis,0);}
    public float getAxisValue(int axis,int pointer){index(pointer);return axis==0?coords[pointer].x:axis==1?coords[pointer].y:axis==9?coords[pointer].vscroll:axis==10?coords[pointer].hscroll:0;}
    public static MotionEvent shirazScroll(long time,float x,float y,float horizontal,float vertical){MotionEvent e=shirazObtain(time,time,ACTION_SCROLL,x,y,0x2002);e.buttons=0;e.coords[0].pressure=0;e.coords[0].hscroll=horizontal;e.coords[0].vscroll=vertical;return e;}
    private long down, time;
    private int action, meta, device, edge, source, buttons, flags;
    private float precisionX, precisionY;
    private PointerProperties[] properties;
    private PointerCoords[] coords;
    private float[] rawX,rawY;
    public static final class PointerProperties {
        public int id=-1,toolType;
        public PointerProperties(){} public PointerProperties(PointerProperties other){copyFrom(other);}
        public void clear(){id=-1;toolType=0;} public void copyFrom(PointerProperties other){id=other.id;toolType=other.toolType;}
    }
    public static final class PointerCoords {
        public float hscroll,vscroll;
        public float x,y,pressure,size,touchMajor,touchMinor,toolMajor,toolMinor,orientation;
        public PointerCoords(){} public PointerCoords(PointerCoords other){copyFrom(other);}
        public void clear(){hscroll=vscroll=x=y=pressure=size=touchMajor=touchMinor=toolMajor=toolMinor=orientation=0;}
        public void copyFrom(PointerCoords other){hscroll=other.hscroll;vscroll=other.vscroll;x=other.x;y=other.y;pressure=other.pressure;size=other.size;touchMajor=other.touchMajor;touchMinor=other.touchMinor;toolMajor=other.toolMajor;toolMinor=other.toolMinor;orientation=other.orientation;}
    }
    private boolean recycled;
    private MotionEvent() { }
    private void live() { if (recycled) throw new IllegalStateException("MotionEvent is recycled"); }
    private void index(int index) { live(); if(index<0||index>=coords.length)throw new IllegalArgumentException("Pointer index"); }
    private static void action(int value,int count){int masked=value&ACTION_MASK,at=(value&ACTION_POINTER_INDEX_MASK)>>ACTION_POINTER_INDEX_SHIFT;if(masked<0||masked>ACTION_BUTTON_RELEASE||(value&~0xffff)!=0||at<0||at>=count)throw new IllegalArgumentException("Pointer action/index");}
    public static MotionEvent obtain(long down,long time,int action,float x,float y,int meta){return obtain(down,time,action,x,y,1,1,meta,1,1,0,0);}
    public static MotionEvent obtain(long down,long time,int action,float x,float y,float pressure,float size,int meta,float px,float py,int device,int edge){
        PointerProperties prop=new PointerProperties();prop.id=0;PointerCoords coord=new PointerCoords();coord.x=x;coord.y=y;coord.pressure=pressure;coord.size=size;
        return obtain(down,time,action,1,new PointerProperties[]{prop},new PointerCoords[]{coord},meta,0,px,py,device,edge,0,0);
    }
    public static MotionEvent obtain(long down,long time,int action,int count,PointerProperties[] properties,PointerCoords[] coords,int meta,int buttons,float px,float py,int device,int edge,int source,int flags){
        if(count<1||count>32||properties==null||coords==null||properties.length<count||coords.length<count)throw new IllegalArgumentException("Pointer array/count");
        action(action,count);if((flags&~(FLAG_WINDOW_IS_OBSCURED|FLAG_WINDOW_IS_PARTIALLY_OBSCURED|FLAG_IS_GENERATED_GESTURE|FLAG_NO_FOCUS_CHANGE))!=0)throw new UnsupportedOperationException("Motion event flags");
        MotionEvent event=new MotionEvent();event.down=down;event.time=time;event.action=action;event.meta=meta;event.buttons=buttons;event.precisionX=px;event.precisionY=py;event.device=device;event.edge=edge;event.source=source;event.flags=flags;
        event.properties=new PointerProperties[count];event.coords=new PointerCoords[count];event.rawX=new float[count];event.rawY=new float[count];int ids=0;
        for(int i=0;i<count;i++){PointerProperties prop=properties[i];PointerCoords coord=coords[i];if(prop==null||coord==null||prop.id<0||prop.id>31||(ids&(1<<prop.id))!=0||!Float.isFinite(coord.x)||!Float.isFinite(coord.y))throw new IllegalArgumentException("Invalid pointer identity/coordinate");ids|=1<<prop.id;event.properties[i]=new PointerProperties(prop);event.coords[i]=new PointerCoords(coord);event.rawX[i]=coord.x;event.rawY[i]=coord.y;}
        return event;
    }
    public static MotionEvent obtain(MotionEvent source){source.live();MotionEvent event=obtain(source.down,source.time,source.action,source.coords.length,source.properties,source.coords,source.meta,source.buttons,source.precisionX,source.precisionY,source.device,source.edge,source.source,source.flags);event.rawX=source.rawX.clone();event.rawY=source.rawY.clone();return event;}
    public int getPointerIdBits(){live();int bits=0;for(PointerProperties prop:properties)bits|=1<<prop.id;return bits;}
    public MotionEvent split(int bits){
        live();bits&=getPointerIdBits();int count=Integer.bitCount(bits);if(count==0)throw new IllegalArgumentException("Empty pointer subset");
        PointerProperties[] props=new PointerProperties[count];PointerCoords[] points=new PointerCoords[count];int at=0,changed=-1;float[] rx=new float[count],ry=new float[count];
        for(int i=0;i<coords.length;i++)if((bits&(1<<properties[i].id))!=0){if(i==getActionIndex())changed=at;props[at]=properties[i];points[at]=coords[i];rx[at]=rawX[i];ry[at]=rawY[i];at++;}
        int next=action,masked=getActionMasked();if(masked==ACTION_POINTER_DOWN||masked==ACTION_POINTER_UP)next=changed<0?ACTION_MOVE:count==1?(masked==ACTION_POINTER_DOWN?ACTION_DOWN:ACTION_UP):masked|(changed<<8);
        MotionEvent result=obtain(down,time,next,count,props,points,meta,buttons,precisionX,precisionY,device,edge,source,0);result.rawX=rx;result.rawY=ry;return result;
    }
    public void getPointerProperties(int index,PointerProperties out){index(index);out.copyFrom(properties[index]);}
    public void getPointerCoords(int index,PointerCoords out){index(index);out.copyFrom(coords[index]);}
    public static MotionEvent obtainNoHistory(MotionEvent source) { return obtain(source); }
    public static MotionEvent shirazObtain(long down, long time, int action, float x, float y, int source) {
        MotionEvent event = obtain(down, time, action, x, y, 0);
        event.source = source;
        event.coords[0].pressure = action == ACTION_UP || action == ACTION_CANCEL || action == ACTION_HOVER_MOVE ? 0 : 1;
        event.properties[0].toolType=source==0x2002?TOOL_TYPE_MOUSE:source==0x1002?TOOL_TYPE_FINGER:TOOL_TYPE_UNKNOWN;
        event.buttons = source == 0x2002 && event.coords[0].pressure != 0 ? BUTTON_PRIMARY : 0;
        return event;
    }
    public static MotionEvent shirazTouch(long down,long time,int action,int[] ids,float[] xs,float[] ys,float[] pressures) {
        if(ids==null||xs==null||ys==null||pressures==null||ids.length<1||ids.length>32||ids.length!=xs.length||ids.length!=ys.length||ids.length!=pressures.length)throw new IllegalArgumentException("Touch arrays");
        PointerProperties[] props=new PointerProperties[ids.length];PointerCoords[] points=new PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){props[i]=new PointerProperties();props[i].id=ids[i];props[i].toolType=TOOL_TYPE_FINGER;points[i]=new PointerCoords();points[i].x=xs[i];points[i].y=ys[i];points[i].pressure=pressures[i];}
        return obtain(down,time,action,ids.length,props,points,0,0,1,1,0,0,0x1002,0);
    }
    public void recycle() {}
    public int getAction() { live(); return action; }
    public int getActionMasked() { return getAction() & ACTION_MASK; }
    public int getFlags(){live();return flags;}
    public int getActionIndex() { live(); return (action&ACTION_POINTER_INDEX_MASK)>>ACTION_POINTER_INDEX_SHIFT; }
    public void setAction(int value) { live(); action(value,coords.length); action = value; }
    public long getDownTime() { live(); return down; }
    public long getEventTime() { live(); return time; }
    public int getPointerCount() { live(); return coords.length; }
    public int getPointerId(int index) { index(index); return properties[index].id; }
    public int findPointerIndex(int id) { live();for(int i=0;i<properties.length;i++)if(properties[i].id==id)return i;return -1; }
    public float getX() { live(); return coords[0].x; }
    public float getY() { live(); return coords[0].y; }
    public float getX(int index) { index(index); return coords[index].x; }
    public float getY(int index) { index(index); return coords[index].y; }
    public float getRawX() { live(); return rawX[0]; }
    public float getRawY() { live(); return rawY[0]; }
    public float getPressure() { live(); return coords[0].pressure; }
    public float getPressure(int index) { index(index); return coords[index].pressure; }
    public float getSize() { live(); return coords[0].size; }
    public float getSize(int index) { index(index); return coords[index].size; }
    public float getTouchMajor(int index) { index(index); return coords[index].touchMajor; }
    public float getTouchMinor(int index) { index(index); return coords[index].touchMinor; }
    public float getToolMajor(int index) { index(index); return coords[index].toolMajor; }
    public float getToolMinor(int index) { index(index); return coords[index].toolMinor; }
    public float getOrientation(int index) { index(index); return coords[index].orientation; }
    public int getMetaState() { live(); return meta; }
    public int getButtonState() { live(); return buttons; }
    public int getDeviceId() { live(); return device; }
    public int getSource() { live(); return source; }
    public void setSource(int value) { live(); source = value; }
    public int getToolType(int index) { index(index); return properties[index].toolType; }
    public float getXPrecision() { live(); return precisionX; }
    public float getYPrecision() { live(); return precisionY; }
    public int getEdgeFlags() { live(); return edge; }
    public void setEdgeFlags(int value) { live(); edge = value; }
    public int getHistorySize() { live(); return 0; }
    public void offsetLocation(float dx,float dy){live();if(!Float.isFinite(dx)||!Float.isFinite(dy))throw new IllegalArgumentException("Pointer offset");for(PointerCoords point:coords)if(!Float.isFinite(point.x+dx)||!Float.isFinite(point.y+dy))throw new IllegalArgumentException("Pointer overflow");for(PointerCoords point:coords){point.x+=dx;point.y+=dy;}}
    /** Host window-animation mapping; raw screen coordinates remain unchanged. */
    public void shirazScaleLocation(float scale,float pivotX,float pivotY){live();if(!Float.isFinite(scale)||scale<=0||!Float.isFinite(pivotX)||!Float.isFinite(pivotY))throw new IllegalArgumentException("Pointer scale");for(PointerCoords point:coords)if(!Float.isFinite(pivotX+(point.x-pivotX)*scale)||!Float.isFinite(pivotY+(point.y-pivotY)*scale))throw new IllegalArgumentException("Pointer overflow");for(PointerCoords point:coords){point.x=pivotX+(point.x-pivotX)*scale;point.y=pivotY+(point.y-pivotY)*scale;}}
    final boolean shirazInverseViewScale(View view){live();
        for(PointerCoords point:coords)if(!Float.isFinite(view.shirazLocalX(point.x))||!Float.isFinite(view.shirazLocalY(point.y)))return false;
        for(PointerCoords point:coords){point.x=view.shirazLocalX(point.x);point.y=view.shirazLocalY(point.y);if(view.shirazMirroredY()^(view.getScaleX()<0)^(view.getScaleY()<0))point.orientation=-point.orientation;}return true;}
    final void shirazMirrorX(float width){live();for(PointerCoords point:coords){point.x=width-point.x;point.orientation=-point.orientation;}}
    public void setLocation(float x,float y){live();double dx=(double)x-coords[0].x,dy=(double)y-coords[0].y;if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Pointer location");for(PointerCoords point:coords)if(!Float.isFinite((float)(point.x+dx))||!Float.isFinite((float)(point.y+dy)))throw new IllegalArgumentException("Pointer location overflow");for(PointerCoords point:coords){point.x=(float)(point.x+dx);point.y=(float)(point.y+dy);}coords[0].x=x;coords[0].y=y;}
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("MotionEvent parcel transport is not implemented"); }
}
