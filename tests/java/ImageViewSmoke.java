// SPDX-License-Identifier: GPL-3.0-only
public final class ImageViewSmoke {
    public static void main(String[] args) {
        android.content.Context context=new android.content.ContextWrapper(null) {
            public android.content.res.Resources getResources() { return null; }
        };
        org.shiraz.fixture.ImageViewFixture.check(context);
        class TintOwner extends android.view.View {
            int calls, rectangles;
            final android.graphics.drawable.ColorDrawable icon=new android.graphics.drawable.ColorDrawable();
            TintOwner(){super(context);icon.setCallback(this);}
            protected boolean verifyDrawable(android.graphics.drawable.Drawable d){return d==icon||super.verifyDrawable(d);}
            public void invalidate(){if(++calls>1)throw new AssertionError("Drawable callback reentered tint update");icon.setTint(0xff123456);super.invalidate();}
            public void invalidate(int l,int t,int r,int b){rectangles++;super.invalidate(l,t,r,b);}
        }
        TintOwner owner=new TintOwner();owner.invalidate();if(owner.calls!=1||owner.rectangles!=1)throw new AssertionError("Drawable rectangle dispatch");
        class Sized extends android.graphics.drawable.ColorDrawable {int size=12;public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}}
        Sized drawable=new Sized();android.widget.ImageView image=new android.widget.ImageView(context);image.setImageDrawable(drawable);image.measure(1073741924,1073741924);image.layout(0,0,100,100);drawable.invalidateSelf();if(image.isLayoutRequested())throw new AssertionError("Pixel-only image change requested layout");Sized replacement=new Sized();image.setImageDrawable(replacement);if(image.isLayoutRequested())throw new AssertionError("Same-size image replacement requested layout");drawable=replacement;drawable.size=20;drawable.invalidateSelf();if(!image.isLayoutRequested())throw new AssertionError("Intrinsic size change did not request layout");
    }
}
