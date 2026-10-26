// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
public final class CornerSmoke {
 public static void main(String[] args){
  org.shiraz.fixture.CornerFixture.check();
  View view=new View(new android.content.ContextWrapper(null));view.setScrollContainer(true);
  if(view.isScrollContainer())throw new AssertionError("Detached scroll container registered");
  ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();view.shirazAttachTree(observer);
  if(!view.isScrollContainer())throw new AssertionError("Attached scroll container absent");
  view.setScrollContainer(false);if(view.isScrollContainer())throw new AssertionError("Scroll container removal");
  view.setScrollContainer(true);view.shirazDetachTree();if(view.isScrollContainer())throw new AssertionError("Detached registration leaked");
  view.shirazAttachTree(observer);if(!view.isScrollContainer())throw new AssertionError("Scroll intent lost across attachment");
  view.shirazDetachTree();observer.shirazKill();System.out.println("SCROLL_CONTAINER_LIFECYCLE_OK");
 }
}
