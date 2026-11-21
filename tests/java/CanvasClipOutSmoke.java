// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class CanvasClipOutSmoke {
 public static void main(String[] args){
  Bitmap bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
  if(!canvas.quickReject(40,40,45,45,Canvas.EdgeType.AA)||canvas.quickReject(new RectF(1,1,4,4),Canvas.EdgeType.BW))throw new AssertionError("quick reject bounds");
  int transform=canvas.save();canvas.translate(10,10);canvas.rotate(90);
  if(canvas.quickReject(0,0,4,4)||!canvas.quickReject(50,50,55,55))throw new AssertionError("transformed rejection");
  canvas.restoreToCount(transform);
  Path outside=new Path();outside.addRect(40,40,45,45,Path.Direction.CW);
  if(!canvas.quickReject(outside,Canvas.EdgeType.AA))throw new AssertionError("path rejection");
  outside.setFillType(Path.FillType.INVERSE_WINDING);
  if(canvas.quickReject(outside))throw new AssertionError("inverse path covers clip");
  Path hole=new Path();hole.addCircle(12,12,5,Path.Direction.CW);int saved=canvas.save();
  if(!canvas.clipOutPath(hole))throw new AssertionError("remaining clip empty");
  canvas.drawColor(Color.RED);
  if(bitmap.getPixel(12,12)!=0||bitmap.getPixel(1,1)!=Color.RED)throw new AssertionError("path subtraction pixels");
  if(canvas.clipRect(10,10,14,14))throw new AssertionError("excluded region is not empty");
  canvas.restoreToCount(saved);canvas.drawColor(Color.BLUE);
  if(bitmap.getPixel(12,12)!=Color.BLUE)throw new AssertionError("clip restore");
  bitmap.recycle();System.out.println("CANVAS_CLIP_OUT_OK");
 }
}
