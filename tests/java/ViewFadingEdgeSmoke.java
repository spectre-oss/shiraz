// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContextWrapper;
import android.view.View;

public final class ViewFadingEdgeSmoke {
 private static final class ResizingView extends View {
  int vertical = -1;
  ResizingView() { super(new ContextWrapper(null)); }
  @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
   vertical=getVerticalFadingEdgeLength();
  }
 }
 public static void main(String[] args) {
  ResizingView view=new ResizingView();
  if(view.getVerticalFadingEdgeLength()!=0||view.getHorizontalFadingEdgeLength()!=0)
   throw new AssertionError("Disabled fading edges must have zero length");
  view.layout(0,0,480,800);
  view.layout(0,0,800,480);
  if(view.vertical!=0)throw new AssertionError("Fading-edge lookup during resize");
  System.out.println("VIEW_FADING_EDGE_OK");
 }
}
