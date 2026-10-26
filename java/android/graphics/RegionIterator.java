// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
public class RegionIterator {
 private final java.util.ArrayList<Rect> rectangles;private int index;
 public RegionIterator(Region region){rectangles=region.snapshot();}
 public boolean next(Rect out){if(index==rectangles.size())return false;out.set(rectangles.get(index++));return true;}
}
