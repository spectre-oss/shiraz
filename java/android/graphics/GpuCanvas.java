// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import shiraz.runtime.NativeGpu;
/**
 * Screen Canvas backed by Skia Ganesh. Ordinary bitmap Canvases remain CPU
 * addressable.
 */
final class GpuCanvas extends Canvas {
  private final long handle;
  private final float[] matrix = new float[9];
  // Canvas is thread-confined. Reuse JNI argument storage instead of allocating
  // several Java objects for every shape in a scrolling list.
  private final int[] paintValues = new int[12];
  private final float[] paintScalars = new float[6], shaderMatrix =
                                                         new float[9];
  private final Matrix localMatrix = new Matrix();
  private final Paint defaultPaint = new Paint();
  private final float[] sourceRect = new float[4], destinationRect =
                                                       new float[4];
  GpuCanvas(Bitmap bitmap) {
    super();
    shirazHardwareBitmap(bitmap);
    handle = bitmap.shirazGpuTarget();
    NativeGpu.begin(handle);
  }
  private void transform() {
    shirazMatrixValues(matrix);
    NativeGpu.matrix(handle, matrix);
  }
  private void prepare(Paint value) {
    shirazMatrixValues(matrix);
    Paint p = value == null ? defaultPaint : value;
    Xfermode transfer = p.getXfermode();
    if (transfer != null && !(transfer instanceof PorterDuffXfermode))
      throw new UnsupportedOperationException("GPU transfer type");
    PathEffect effect = p.getPathEffect();
    PathEffect.shirazValidate(effect);
    DashPathEffect dash =
        effect instanceof DashPathEffect ? (DashPathEffect)effect : null;
    Shader shader = p.getShader();
    long image = 0;
    int tx = 0, ty = 0;
    int[] colors = null;
    float[] positions = null, points = null;
    if (shader != null) {
      localMatrix.reset();
      shader.getLocalMatrix(localMatrix);
      localMatrix.getValues(shaderMatrix);
    }
    if (shader instanceof BitmapShader) {
      BitmapShader b = (BitmapShader)shader;
      image = b.bitmap.shirazGpuImage();
      tx = b.tileX.ordinal();
      ty = b.tileY.ordinal();
    } else if (shader instanceof LinearGradient) {
      LinearGradient g = (LinearGradient)shader;
      colors = g.colors;
      positions = g.positions;
      points = g.points;
      tx = ty = g.tile.ordinal();
    } else if (shader instanceof RadialGradient) {
      RadialGradient g = (RadialGradient)shader;
      colors = g.colors; positions = g.positions; points = g.points;
      tx = ty = g.tile.ordinal();
    } else if (shader != null)
      throw new UnsupportedOperationException("GPU shader type");
    ColorFilter filter = p.getColorFilter();
    paintValues[0] = p.getColor();
    paintValues[1] = p.getStyle().ordinal();
    paintValues[2] = p.getStrokeCap().ordinal();
    paintValues[3] = p.getStrokeJoin().ordinal();
    paintValues[4] = p.isAntiAlias() ? 1 : 0;
    paintValues[5] =
        transfer == null ? 3 : ((PorterDuffXfermode)transfer).mode.ordinal();
    paintValues[6] = ColorFilter.shirazColor(filter);
    paintValues[7] = ColorFilter.shirazMode(filter);
    paintValues[8] = tx;
    paintValues[9] = ty;
    paintValues[10] = p.isFilterBitmap() ? 1 : 0;
    paintValues[11] = p.getShadowLayerColor();
    paintScalars[0] = p.getStrokeWidth();
    paintScalars[1] = p.getStrokeMiter();
    paintScalars[2] = dash == null ? 0 : dash.shirazPhase();
    paintScalars[3] = p.getShadowLayerRadius();
    paintScalars[4] = p.getShadowLayerDx();
    paintScalars[5] = p.getShadowLayerDy();
    NativeGpu.paint(handle, matrix, paintValues, paintScalars,
                    ColorFilter.shirazMatrix(filter), image,
                    shader == null ? null : shaderMatrix, colors, positions,
                    points, dash == null ? null : dash.shirazIntervals());
  }
  @Override
  public boolean shirazPatternRipple(float[] geometry, int color, int sparkle,
                                     Paint paint) {
    prepare(paint);
    NativeGpu.ripple(handle, geometry, color, sparkle);
    return true;
  }
  @Override
  public boolean isHardwareAccelerated() {
    return true;
  }
  @Override
  public int save() {
    int count = super.save();
    NativeGpu.save(handle);
    return count;
  }
  @Override
  public void restore() {
    super.restore();
    NativeGpu.restore(handle);
  }
  @Override
  public int saveLayer(RectF bounds, Paint paint) {
    int count = super.save();
    prepare(paint);
    NativeGpu.layer(handle, bounds == null
                                ? null
                                : new float[] {bounds.left, bounds.top,
                                               bounds.right, bounds.bottom});
    return count;
  }
  @Override
  public int saveLayerAlpha(RectF bounds, int alpha) {
    Paint p = new Paint();
    p.setAlpha(alpha);
    return saveLayer(bounds, p);
  }
  @Override
  public boolean getClipBounds(Rect out) {
    transform();
    int[] b = NativeGpu.clipBounds(handle);
    out.set(b[0], b[1], b[2], b[3]);
    return !out.isEmpty();
  }
  @Override
  public boolean clipRect(float l, float t, float r, float b) {
    return clipRect(l, t, r, b, Region.Op.INTERSECT);
  }
  @Override
  public boolean clipRect(float l, float t, float r, float b, Region.Op op) {
    if (op != Region.Op.INTERSECT && op != Region.Op.DIFFERENCE)
      throw new UnsupportedOperationException(
          "Hardware Canvas supports intersect/difference clips");
    transform();
    return NativeGpu.clipRect(handle, l, t, r, b, op == Region.Op.DIFFERENCE);
  }
  @Override
  public boolean clipPath(Path path, Region.Op op) {
    if (op != Region.Op.INTERSECT && op != Region.Op.DIFFERENCE)
      throw new UnsupportedOperationException(
          "Hardware Canvas supports intersect/difference clips");
    transform();
    NativeGpu.clip(handle, path.commands(), path.getFillType().ordinal(),
                   op == Region.Op.DIFFERENCE);
    return !getClipBounds().isEmpty();
  }
  @Override
  public void drawColor(int color) {
    transform();
    NativeGpu.color(handle, color);
  }
  @Override
  public void drawRect(float l, float t, float r, float b, Paint paint) {
    if (paint == null)
      throw new NullPointerException("paint");
    if (paint.getPathEffect() instanceof CornerPathEffect) {
      Path path = new Path();
      if (l < r && t < b)
        path.addRect(l, t, r, b, Path.Direction.CW);
      drawPath(path, paint);
      return;
    }
    prepare(paint);
    NativeGpu.shape(handle, 0, l, t, r, b, 0, 0);
  }
  @Override
  public void drawRoundRect(float l, float t, float r, float b, float rx,
                            float ry, Paint paint) {
    if (paint == null)
      throw new NullPointerException("paint");
    if (paint.getPathEffect() != null) {
      super.drawRoundRect(l, t, r, b, rx, ry, paint);
      return;
    }
    prepare(paint);
    NativeGpu.shape(handle, 1, l, t, r, b, rx, ry);
  }
  @Override
  public void drawOval(float l, float t, float r, float b, Paint paint) {
    if (paint == null)
      throw new NullPointerException("paint");
    if (paint.getPathEffect() != null) {
      super.drawOval(l, t, r, b, paint);
      return;
    }
    prepare(paint);
    NativeGpu.shape(handle, 2, l, t, r, b, 0, 0);
  }
  @Override
  public void drawCircle(float x, float y, float radius, Paint paint) {
    if (paint == null)
      throw new NullPointerException("paint");
    if (radius > 0)
      drawOval(x - radius, y - radius, x + radius, y + radius, paint);
  }

  @Override
  public void shirazDrawPath(Path path, Paint paint, float[] extra) {
    if (path == null || paint == null)
      throw new NullPointerException();
    if (!path.shirazIsFinite())
      return;
    if (paint.getPathEffect() instanceof CornerPathEffect)
      path = ((CornerPathEffect)paint.getPathEffect()).apply(path);
    int saved = 0;
    if (extra != null) {
      if (extra.length != 6)
        throw new IllegalArgumentException("Path matrix");
      saved = save();
      Matrix m = new Matrix();
      m.setValues(new float[] {extra[0], extra[2], extra[4], extra[1], extra[3],
                               extra[5], 0, 0, 1});
      concat(m);
    }
    try {
      prepare(paint);
      NativeGpu.path(handle, path.commands(), path.getFillType().ordinal());
    } finally {
      if (saved != 0)
        restoreToCount(saved);
    }
  }
  @Override
  public void drawBitmap(Bitmap source, Rect from, RectF to, Paint paint) {
    if (source == null || to == null)
      throw new NullPointerException();
    source.requireLive();
    Rect r = from == null ? new Rect(0, 0, source.width, source.height) : from;
    if (r.left < 0 || r.top < 0 || r.right > source.width ||
        r.bottom > source.height)
      throw new IllegalArgumentException("Bitmap source bounds");
    if (r.isEmpty() || to.isEmpty())
      return;
    // ALPHA_8 is a coverage image tinted with the paint's color.
    Bitmap image = source;
    boolean temporary = source.getConfig() == Bitmap.Config.ALPHA_8;
    if (temporary) {
      image = Bitmap.fromPixels(
          source.width, source.height,
          source.colorPixels(paint == null ? Color.BLACK : paint.getColor()));
    }
    try {
      prepare(paint);
      sourceRect[0] = r.left;
      sourceRect[1] = r.top;
      sourceRect[2] = r.right;
      sourceRect[3] = r.bottom;
      destinationRect[0] = to.left;
      destinationRect[1] = to.top;
      destinationRect[2] = to.right;
      destinationRect[3] = to.bottom;
      NativeGpu.image(handle, image.shirazGpuImage(), sourceRect,
                      destinationRect, paint != null && paint.isFilterBitmap());
    } finally {
      if (temporary)
        image.recycle();
    }
  }
  private static Path outline(Outline o) {
    if (o.shirazPath() != null)
      return o.shirazPath();
    Path p = new Path();
    Rect r = o.shirazBounds();
    if (o.shirazIsOval())
      p.addOval(new RectF(r), Path.Direction.CW);
    else
      p.addRoundRect(new RectF(r), o.getRadius(), o.getRadius(),
                     Path.Direction.CW);
    return p;
  }
  @Override
  public int shirazSaveOutlineClip(Outline o) {
    int s = save();
    clipPath(outline(o));
    return s;
  }
  @Override
  public void shirazDrawOutlineShadow(Outline o, float z, float opacity) {
    if (z <= 0 || opacity <= 0 || o.isEmpty())
      return;
    prepare(null);
    NativeGpu.shadow(handle, outline(o).commands(), z, opacity * o.getAlpha());
  }
  // Android also shapes text and populates glyph caches on CPU. Preserve
  // Shiraz's HarfBuzz/font fallback behavior, upload a cached run once, then
  // transform and composite its texture on the GPU. Never read the destination
  // back for text.
  private static final java.util.LinkedHashMap<String, TextRun> textCache =
      new java.util.LinkedHashMap<String, TextRun>(32, .75f, true);
  private static long textBytes;
  private static final class TextRun {
    final Bitmap image;
    final int left, top;
    TextRun(Bitmap b, int l, int t) {
      image = b;
      left = l;
      top = t;
    }
  }
  @Override
  public void drawText(String text, float x, float y, Paint paint) {
    if (text == null || paint == null)
      throw new NullPointerException();
    if (!Float.isFinite(x) || !Float.isFinite(y) || text.isEmpty())
      return;
    if (paint.getShader() != null)
      throw new UnsupportedOperationException("Text shader rendering");
    String key = paint.shirazFilteredColor() + "|" + text + '\u0000' +
                 paint.shirazTypeface().shirazFamily() + '|' +
                 paint.shirazTypeface().getStyle() + '|' + paint.getTextSize() +
                 '|' + paint.getFlags() + '|' + paint.getTextSkewX() + '|' +
                 paint.getLetterSpacing() + '|' + paint.getTextScaleX() + '|' +
                 paint.shirazTextHinting() + '|' + paint.getStyle() + '|' +
                 paint.getStrokeWidth() + '|' + paint.getStrokeCap() + '|' +
                 paint.getStrokeJoin() + '|' + paint.getStrokeMiter() + '|' +
                 java.util.Arrays.toString(paint.shirazVariationTags()) + '|' +
                 java.util.Arrays.toString(paint.shirazVariationValues()) +
                 '|' + java.util.Arrays.toString(paint.shirazBidiLevels());
    TextRun run = textCache.get(key);
    if (run == null) {
      Paint raster = new Paint(paint);
      raster.setColor(paint.shirazFilteredColor());
      raster.setColorFilter(null);
      raster.setXfermode(null);
      raster.clearShadowLayer();
      raster.setTextAlign(Paint.Align.LEFT);
      Rect bounds = new Rect();
      raster.getTextBounds(text, 0, text.length(), bounds);
      double pad = Math.ceil(raster.getStrokeWidth() * (double)Math.max(1, raster.getStrokeMiter())) + 2;
      double left=bounds.left-pad,top=bounds.top-pad;
      double right=Math.max(bounds.right,Math.ceil(raster.measureText(text)))+pad;
      double bottom=Math.max(bounds.bottom,Math.ceil(raster.getTextSize()/6))+pad;
      if(right<=left||bottom<=top)return;
      if(right-left>2048||bottom-top>2048||(right-left)*(bottom-top)>1024*1024){
        drawLargeText(text,x,y,raster,paint,left,top,right,bottom);
        return;
      }
      int l=(int)left,t=(int)top,r=(int)right,b=(int)bottom;
      Bitmap bitmap =
          Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888);
      new Canvas(bitmap).drawText(text, -l, -t, raster);
      run = new TextRun(bitmap, l, t);
      textCache.put(key, run);
      textBytes += bitmap.getByteCount();
      while ((textBytes > 8 * 1024 * 1024 || textCache.size() > 256) &&
             textCache.size() > 1) {
        java.util.Iterator<TextRun> it = textCache.values().iterator();
        TextRun old = it.next();
        it.remove();
        textBytes -= old.image.getByteCount();
        old.image.recycle();
      }
    }
    if (paint.getTextAlign() != Paint.Align.LEFT)
      x -= paint.measureText(text) *
           (paint.getTextAlign() == Paint.Align.CENTER ? .5f : 1);
    Paint composite = new Paint(paint);
    composite.setStyle(Paint.Style.FILL);
    composite.setPathEffect(null);
    composite.setFilterBitmap(true);
    composite.setColorFilter(null);
    composite.setAlpha(255);
    drawBitmap(run.image, null,
               new RectF(x + run.left, y + run.top,
                         x + run.left + run.image.width,
                         y + run.top + run.image.height),
               composite);
  }
  // Rasterize only visible portions of oversized runs; keep shaping context
  // intact instead of splitting strings (which breaks ligatures and bidi).
  private void drawLargeText(String text,float x,float y,Paint raster,Paint paint,
                             double left,double top,double right,double bottom){
    if(paint.getTextAlign()!=Paint.Align.LEFT)x-=paint.measureText(text)*(paint.getTextAlign()==Paint.Align.CENTER?.5f:1);
    if(!Float.isFinite(x))return;
    Rect clip=new Rect();if(!getClipBounds(clip))return;
    int l=(int)Math.max(clip.left,Math.floor(x+left)),t=(int)Math.max(clip.top,Math.floor(y+top));
    int r=(int)Math.min(clip.right,Math.ceil(x+right)),b=(int)Math.min(clip.bottom,Math.ceil(y+bottom));
    Paint composite=new Paint(paint);composite.setStyle(Paint.Style.FILL);composite.setPathEffect(null);composite.setFilterBitmap(true);composite.setColorFilter(null);composite.setAlpha(255);
    for(long yy=t;yy<b;yy+=1024)for(long xx=l;xx<r;xx+=1024){
      int w=(int)Math.min(1024,(long)r-xx),h=(int)Math.min(1024,(long)b-yy);
      Bitmap tile=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
      try{new Canvas(tile).drawText(text,x-xx,y-yy,raster);drawBitmap(tile,null,new RectF(xx,yy,xx+w,yy+h),composite);}
      finally{tile.recycle();}
    }
  }
  @Override
  public void drawTextOnPath(String text, Path path, float h, float v,
                             Paint paint) {
    RectF bounds = new RectF();
    path.computeBounds(bounds, true);
    float pad = paint.getTextSize() * 2 + paint.getStrokeWidth() * 4;
    int l = (int)Math.floor(bounds.left - pad),
        t = (int)Math.floor(bounds.top - pad),
        w = (int)Math.ceil(bounds.right + pad) - l,
        height = (int)Math.ceil(bounds.bottom + pad) - t;
    if (w <= 0 || height <= 0)
      return;
    Bitmap tile = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
    try {
      Canvas cpu = new Canvas(tile);
      cpu.translate(-l, -t);
      cpu.drawTextOnPath(text, path, h, v, paint);
      drawBitmap(tile, null, new RectF(l, t, l + w, t + height), null);
    } finally {
      tile.recycle();
    }
  }
}
