// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.view.*;
public final class DragSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  String[] types={"text/plain","image/png"};ClipDescription description=new ClipDescription("payload",types);types[0]="bad/type";
  check(description.hasMimeType("text/*")&&description.hasMimeType("*/*")&&!description.hasMimeType("text/html"),"MIME matching and input copy");
  String[] filtered=description.filterMimeTypes("image/*");check(filtered.length==1&&filtered[0].equals("image/png")&&description.filterMimeTypes("audio/*")==null,"MIME filter");
  ClipData clip=ClipData.newHtmlText("label","Map <route>","Map &lt;route&gt;");clip.addItem(new ClipData.Item("second"));ClipData copy=new ClipData(clip);clip.addItem(new ClipData.Item("third"));
  check(copy.getItemCount()==2&&copy.getDescription()!=clip.getDescription()&&copy.getDescription().hasMimeType("text/plain")&&copy.getItemAt(0).coerceToText(null).equals("Map <route>"),"Clip copy and text payload");
  try{new ClipData.Item(null,"html");throw new AssertionError("HTML without text");}catch(IllegalArgumentException expected){}
  Object local=new Object();DragEvent event=DragEvent.shirazObtain(DragEvent.ACTION_DROP,12,34,local,clip.getDescription(),clip,false);DragEvent cloned=DragEvent.obtain(event);event.recycle();
  check(cloned.getX()==12&&cloned.getY()==34&&cloned.getLocalState()==local&&cloned.getClipData()==clip&&!cloned.getResult(),"Drag copy preserves payload identity and fields");
  try{event.getX();throw new AssertionError("Recycled drag readable");}catch(IllegalStateException expected){}
  final int[] calls={0,0};View target=new View(new ContextWrapper(null)){public boolean onDragEvent(DragEvent value){calls[1]++;return value.getAction()==DragEvent.ACTION_DROP;}};
  target.setOnDragListener(new View.OnDragListener(){public boolean onDrag(View view,DragEvent value){calls[0]++;return true;}});
  check(target.dispatchDragEvent(cloned)&&calls[0]==1&&calls[1]==0,"Listener precedes view handling");target.setEnabled(false);check(target.dispatchDragEvent(cloned)&&calls[0]==1&&calls[1]==1,"Disabled view skips listener but retains override dispatch");target.setEnabled(true);target.setOnDragListener(null);check(target.dispatchDragEvent(cloned)&&calls[1]==2,"Listener removal and fallback");cloned.recycle();
  try{clip.writeToParcel(null,0);throw new AssertionError("Unsupported parcel accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("DRAG_PAYLOAD_DISPATCH_OK");
 }
}
