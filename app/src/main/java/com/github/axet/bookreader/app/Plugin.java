package com.github.axet.bookreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;

import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.bookreader.widgets.ScrollWidget;
import com.github.axet.bookreader.widgets.SelectionView;

import org.geometerplus.android.fbreader.libraryService.BookCollectionShadow;
import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.fbreader.FBReaderApp;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.view.ZLView;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public interface Plugin {

    class Box {
        public int x; // lower left x
        public int y; // lower left y
        public int w; // x + w = upper right x
        public int h; // y + h = upper right y

        public Box() {
        }

        public Box(Box r) {
            this.x = r.x;
            this.y = r.y;
            this.w = r.w;
            this.h = r.h;
        }

        public Box(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public Rect toRect(int w, int h) {
            return new Rect(x, h - this.h - y, x + this.w, h - y);
        }
    }

    class RenderRect extends Box {
        public Rect src;
        public Rect dst;
    }

    abstract class Page {
        public int pageNumber;
        public int pageOffset; // pageBox sizes
        public Box pageBox; // pageBox sizes
        public int w; // display w
        public int h; // display h
        public double hh; // pageBox sizes, visible height
        public double ratio;
        public int pageStep; // pageBox sizes, page step size (fullscreen height == pageStep + pageOverlap)
        public int pageOverlap; // pageBox sizes, page overlap size (fullscreen height == pageStep + pageOverlap)
        public int dpi; // pageBox dpi, set manually

        public Page() {
        }

        public Page(Page r) {
            w = r.w;
            h = r.h;
            hh = r.hh;
            ratio = r.ratio;
            pageNumber = r.pageNumber;
            pageOffset = r.pageOffset;
            if (r.pageBox != null)
                pageBox = new Box(r.pageBox);
            pageStep = r.pageStep;
            pageOverlap = r.pageOverlap;
        }

        public Page(Page r, ZLViewEnums.PageIndex index) {
            this(r);
            load(index);
        }

        public void renderPage() {
            ratio = pageBox.w / (double) w;
            hh = h * ratio;

            pageOverlap = (int) (hh * FBReaderView.PAGE_OVERLAP_PERCENTS / 100);
            pageStep = (int) (hh - pageOverlap); // -5% or lowest base line
        }

        public void load(ZLViewEnums.PageIndex index) {
            switch (index) {
                case next:
                    next();
                    break;
                case previous:
                    prev();
                    break;
            }
        }

        public abstract void load();

        public abstract int getPagesCount();

        public boolean next() {
            int pageOffset = this.pageOffset + pageStep;
            int tail = pageBox.h - pageOffset;
            if (pageOffset >= pageBox.h || tail <= pageOverlap) {
                int pageNumber = this.pageNumber + 1;
                if (pageNumber >= getPagesCount())
                    return false;
                this.pageOffset = 0;
                this.pageNumber = pageNumber;
                load();
                renderPage();
                return true;
            }
            this.pageOffset = pageOffset;
            return true;
        }

        public boolean prev() {
            int pageOffset = this.pageOffset - pageStep;
            if (this.pageOffset > 0 && pageOffset < 0) { // happens only on screen rotate
                this.pageOffset = pageOffset; // sync to top = 0 or keep negative offset
                return true;
            } else if (pageOffset < 0) {
                int pageNumber = this.pageNumber - 1;
                if (pageNumber < 0)
                    return false;
                this.pageNumber = pageNumber;
                load(); // load pageBox
                renderPage(); // calculate pageStep
                int tail = pageBox.h % pageStep;
                pageOffset = pageBox.h - tail;
                if (tail <= pageOverlap)
                    pageOffset = pageOffset - pageStep; // skip tail
                this.pageOffset = pageOffset;
                return true;
            }
            this.pageOffset = pageOffset;
            return true;
        }

        public void scale(int w, int h) {
            double ratio = w / (double) pageBox.w;
            this.hh *= ratio;
            this.ratio *= ratio;
            pageBox.w = w;
            pageBox.h = (int) (pageBox.h * ratio);
            pageOffset = (int) (pageOffset * ratio);
            dpi = (int) (dpi * ratio);
        }

        public RenderRect renderRect() {
            RenderRect render = new RenderRect(); // render region

            render.x = 0;
            render.w = pageBox.w;

            if (pageOffset < 0) { // show empty space at beginig
                int tail = (int) (pageBox.h - pageOffset - hh); // tail to cut from the bottom
                if (tail < 0) {
                    render.h = pageBox.h;
                    render.y = 0;
                } else {
                    render.h = pageBox.h - tail;
                    render.y = tail;
                }
                render.dst = new Rect(0, (int) (-pageOffset / ratio), w, h);
            } else if (pageOffset == 0 && hh > pageBox.h) {  // show middle vertically
                int t = (int) ((hh - pageBox.h) / ratio / 2);
                render.h = pageBox.h;
                render.dst = new Rect(0, t, w, h - t);
            } else {
                render.h = (int) hh;
                render.y = pageBox.h - render.h - pageOffset - 1;
                if (render.y < 0) {
                    render.h += render.y;
                    h += render.y / ratio; // convert to display sizes
                    render.y = 0;
                }
                render.dst = new Rect(0, 0, w, h);
            }

            render.src = new Rect(0, 0, render.w, render.h);

            return render;
        }

        public boolean equals(int n, int o) {
            return pageNumber == n && pageOffset == o;
        }

        public void load(ZLTextPosition p) {
            if (p == null) {
                load(0, 0);
            } else {
                load(p.getParagraphIndex(), p.getElementIndex());
            }
        }

        public void load(int n, int o) {
            pageNumber = n;
            pageOffset = o;
            load();
        }

        public void updatePage(Page r) {
            w = r.w;
            h = r.h;
            ratio = r.ratio;
            hh = r.hh;
            pageStep = r.pageStep;
            pageOverlap = r.pageOverlap;
        }
    }

    class View {
        public static final String TAG = View.class.getSimpleName();

        public static final int RENDER_MIN = 512; // mininum screen width

        public static final float[] NEGATIVE = {
                -1.0f, 0, 0, 0, 255, // red
                0, -1.0f, 0, 0, 255, // green
                0, 0, -1.0f, 0, 255, // blue
                0, 0, 0, 1.0f, 0  // alpha
        };

        public Bitmap wallpaper;
        public int wallpaperColor;
        public Paint paint = new Paint(); // foreground / content color
        public Page current;
        public boolean reflow = false;
        public boolean reflowDebug;
        public Reflow reflower;

        public static class Selection { // plugin coords (render bm size's)

            public static int odd(int i) {
                return ((i + 1) / 2) * ((i + 1) % 2 - i % 2);
            }

            public static int odd(int page, int i, int max) {
                int p = page + odd(i);
                if (page <= i / 2)
                    p = i;
                if (page + i / 2 >= max)
                    p = max - i - 1;
                return p;
            }

            public interface Setter {
                void setStart(int x, int y);

                void setEnd(int x, int y);

                Bounds getBounds();
            }

            public static class Bounds {
                public Rect[] rr;
                public boolean reverse;
                public boolean start;
                public boolean end;
            }

            public static class Page {
                public int page;
                public int w;
                public int h;

                public Page(int p, int w, int h) {
                    this.page = p;
                    this.w = w;
                    this.h = h;
                }
            }

            public static class Point {
                public int x;
                public int y;

                public Point(int x, int y) {
                    this.x = x;
                    this.y = y;
                }

                public Point(android.graphics.Point p) {
                    this.x = p.x;
                    this.y = p.y;
                }
            }

            public boolean isWord(Character c) {
                if (Character.isSpaceChar(c))
                    return false;
                return Character.isDigit(c) || Character.isLetter(c) || Character.isLetterOrDigit(c) || c == '[' || c == ']' || c == '(' || c == ')';
            }

            public void setStart(Page page, Point point) {
            }

            public void setEnd(Page page, Point point) {
            }

            public String getText() {
                return null;
            }

            public ZLTextPosition getStart() {
                return null;
            }

            public ZLTextPosition getEnd() {
                return null;
            }

            public Rect[] getBoundsAll(Page page) {
                return null;
            }

            public Bounds getBounds(Page page) {
                return null;
            }

            public Boolean isAbove(Page page, Point point) {
                return null;
            }

            public Boolean isBelow(Page page, Point point) {
                return null;
            }

            public Boolean inBetween(Page page, Point start, Point end) {
                return null;
            }

            public boolean isValid(Page page, Point point) {
                return false;
            }

            public boolean isSelected(int page) {
                return false;
            }

            public void close() {
            }
        }

        public static class Link {
            public String url;
            public int index;
            public Rect rect;

            public Link() {
            }

            public Link(String url, int index, Rect rect) {
                this.url = url;
                this.index = index;
                this.rect = rect;
            }
        }

        public static class Search {
            public static class Bounds {
                public Rect[] rr;
                public Rect[] highlight;
            }

            public int getCount() {
                return 0;
            }

            public int next() {
                return -1;
            }

            public int prev() {
                return -1;
            }

            public void setPage(int page) {
            }

            public Bounds getBounds(Selection.Page page) {
                return null;
            }

            public void close() {
            }
        }

        public View() {
            updateTheme();
        }

        public void updateTheme() {
            try {
                FBReaderApp app = new FBReaderApp(null, new BookCollectionShadow());
                ZLFile wallpaper = app.BookTextView.getWallpaperFile();
                if (wallpaper != null)
                    this.wallpaper = BitmapFactory.decodeStream(wallpaper.getInputStream());
                else
                    this.wallpaper = null;
                wallpaperColor = (0xff << 24) | app.BookTextView.getBackgroundColor().intValue();
                if (ColorUtils.calculateLuminance(wallpaperColor) < 0.5f && ! (this instanceof ComicsPlugin.ComicsView))
                    paint.setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
                else
                    paint.setColorFilter(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void drawWallpaper(Canvas canvas) {
            if (wallpaper != null) {
                float dx = wallpaper.getWidth();
                float dy = wallpaper.getHeight();
                for (int cw = 0; cw < canvas.getWidth() + dx; cw += dx) {
                    for (int ch = 0; ch < canvas.getHeight() + dy; ch += dy)
                        canvas.drawBitmap(wallpaper, cw - dx, ch - dy, paint);
                }
            } else {
                canvas.drawColor(wallpaperColor);
            }
        }

        public void gotoPosition(ZLTextPosition p) {
            if (p == null)
                return;
            if (current.pageNumber != p.getParagraphIndex() || current.pageOffset != p.getElementIndex())
                current.load(p);
            if (reflower != null) {
                if (reflower.page != p.getParagraphIndex()) {
                    reflower.reset();
                    reflower.page = current.pageNumber;
                }
                reflower.index = p.getElementIndex();
            }
        }

        public boolean onScrollingFinished(ZLViewEnums.PageIndex index) {
            if (reflow && reflowDebug) {
                switch (index) {
                    case previous:
                        current.pageNumber--;
                        current.pageOffset = 0;
                        current.load();
                        break;
                    case next:
                        current.pageNumber++;
                        current.pageOffset = 0;
                        current.load();
                        break;
                }
                return false;
            }
            if (reflower != null) {
                reflower.onScrollingFinished(index);
                Log.d(TAG, "Reflow position: " + reflower.page + "." + reflower.index);
                if (index == ZLViewEnums.PageIndex.current)
                    return false;
                if (reflower.page != current.pageNumber) {
                    current.pageNumber = reflower.page;
                    current.pageOffset = 0;
                    current.load();
                    return false;
                }
                if (reflower.index == -1) {
                    current.pageNumber = reflower.page - 1;
                    current.pageOffset = 0;
                    current.load();
                    return false;
                }
                if (reflower.index >= reflower.emptyCount()) { // current points to next page +1
                    current.pageNumber = reflower.page + 1;
                    current.pageOffset = 0;
                    current.load();
                    return false;
                }
                return false;
            }
            Page old = new Page(current) {
                @Override
                public void load() {
                }

                @Override
                public int getPagesCount() {
                    return current.getPagesCount();
                }
            };
            current.load(index);
            Page r;
            switch (index) {
                case previous:
                    r = new Page(current, ZLViewEnums.PageIndex.next) {
                        @Override
                        public void load() {
                        }

                        @Override
                        public int getPagesCount() {
                            return current.getPagesCount();
                        }
                    };
                    break;
                case next:
                    r = new Page(current, ZLViewEnums.PageIndex.previous) {
                        @Override
                        public void load() {
                        }

                        @Override
                        public int getPagesCount() {
                            return current.getPagesCount();
                        }
                    };
                    break;
                default:
                    return false;
            }
            return !old.equals(r.pageNumber, r.pageOffset); // need reset cache true/false?
        }

        public ZLTextPosition getPosition() {
            return new ZLTextFixedPosition(current.pageNumber, current.pageOffset, 0);
        }

        public ZLTextPosition getNextPosition() {
            if (current.w == 0 || current.h == 0)
                return null; // after reset() we do not know display size
            Page next = new Page(current, ZLViewEnums.PageIndex.next) {
                @Override
                public void load() {
                }

                @Override
                public int getPagesCount() {
                    return current.getPagesCount();
                }
            };
            if (current.equals(next.pageNumber, next.pageOffset))
                return null; // !canScroll()
            ZLTextFixedPosition e = new ZLTextFixedPosition(next.pageNumber, next.pageOffset, 0);
            if (e.ParagraphIndex >= next.getPagesCount())
                return null;
            return e;
        }

        public boolean canScroll(ZLView.PageIndex index) {
            if (reflower != null) {
                if (reflower.canScroll(index))
                    return true;
                switch (index) {
                    case previous:
                        if (current.pageNumber > 0)
                            return true;
                        if (current.pageNumber != reflower.page) { // only happens to 0 page of document, we need to know it reflow count
                            int render = reflower.index;
                            Bitmap bm = render(reflower.rw, reflower.h, current.pageNumber); // 0 page
                            reflower.load(bm, current.pageNumber, 0);
                            bm.recycle();
                            int count = reflower.count();
                            count += render;
                            reflower.index = count;
                            return count > 0;
                        }
                        return false;
                    case next:
                        if (current.pageNumber + 1 < current.getPagesCount())
                            return true;
                        if (current.pageNumber != reflower.page) { // only happens to last page of document, we need to know it reflow count
                            int render = reflower.index - reflower.count();
                            Bitmap bm = render(reflower.rw, reflower.h, current.pageNumber); // last page
                            reflower.load(bm, current.pageNumber, 0);
                            bm.recycle();
                            reflower.index = render;
                            return render + 1 < reflower.count();
                        }
                        return false;
                    default:
                        return true; // current???
                }
            }
            Page r = new Page(current, index) {
                @Override
                public void load() {
                }

                @Override
                public int getPagesCount() {
                    return current.getPagesCount();
                }
            };
            return !r.equals(current.pageNumber, current.pageOffset);
        }

        public ZLTextView.PagePosition pagePosition() {
            return new ZLTextView.PagePosition(current.pageNumber + 1, current.getPagesCount());
        }

        public Bitmap render(int w, int h, int page, Bitmap.Config c) {
            return null;
        }

        public Bitmap render(int w, int h, int page) {
            if (w < RENDER_MIN) {
                float ratio = RENDER_MIN / (float) w;
                w *= ratio;
                h *= ratio;
            }
            return render(w, h, page, Bitmap.Config.RGB_565); // reflower active, always 565
        }

        public void drawOnBitmap(Context context, Bitmap bitmap, int w, int h, ZLView.PageIndex index, FBReaderView.CustomView custom, Storage.RecentInfo info) {
            Canvas canvas = new Canvas(bitmap);
            drawOnCanvas(context, canvas, w, h, index, custom, info);
        }

        public Page getPageInfo(int w, int h, ScrollWidget.ScrollAdapter.PageCursor c) {
            return null;
        }

        public double getPageHeight(int w, ScrollWidget.ScrollAdapter.PageCursor c) {
            Page r = getPageInfo(w, 0, c);
            return r.pageBox.h / r.ratio;
        }

        public void drawOnCanvas(Context context, Canvas canvas, int w, int h, ZLView.PageIndex index, FBReaderView.CustomView custom, Storage.RecentInfo info) {
            if (reflow) {
                if (reflower == null)
                    reflower = new Reflow(context, w, h, current.pageNumber, custom, info);
                Bitmap bm = null;
                reflower.reset(w, h);
                int render = reflower.index; // render reflow page index
                int page = reflower.page; // render pageNumber
                if (reflowDebug) {
                    switch (index) {
                        case previous:
                            page = current.pageNumber - 1;
                            break;
                        case next:
                            page = current.pageNumber + 1;
                            break;
                        case current:
                            break;
                    }
                    index = ZLViewEnums.PageIndex.current;
                    render = 0;
                }
                switch (index) {
                    case previous: // prev can point to many (no more then 2) pages behind, we need to walk every page manually
                        if (reflower.count() == -1 && render > 0) { // walking on reset reflower, reload
                            bm = render(reflower.rw, reflower.h, page);
                            reflower.load(bm);
                        }
                        render -= 1;
                        while (render < 0) {
                            page--;
                            if (bm != null)
                                bm.recycle();
                            bm = render(reflower.rw, reflower.h, page);
                            reflower.load(bm);
                            render = render + reflower.emptyCount();
                            reflower.page = page;
                            reflower.index = render + 1; // onScrollingFinished - 1
                        }
                        if (reflower.count() > render) {
                            if (bm != null)
                                bm.recycle();
                            bm = reflower.render(render);
                        }
                        reflower.pending = -1;
                        break;
                    case current:
                        if (reflower.count() > 0) {
                            bm = reflower.render(render);
                        } else {
                            bm = render(reflower.rw, reflower.h, page);
                            if (reflowDebug) {
                                reflower.k2.setVerbose(true);
                                reflower.k2.setShowMarkedSource(true);
                            }
                            reflower.load(bm, page, render);
                            if (reflowDebug) {
                                reflower.bm = null; // do not recycle
                                reflower.close();
                                reflower = null;
                            } else {
                                if (reflower.count() > render) { // empty source page
                                    bm.recycle();
                                    bm = reflower.render(render);
                                }
                            }
                        }
                        break;
                    case next: // next can point to many (no more then 2) pages ahead, we need to walk every page manually
                        if (reflower.count() == -1) { // walking on reset reflower, reload
                            bm = render(reflower.rw, reflower.h, page);
                            reflower.load(bm);
                        }
                        render += 1;
                        while (reflower.emptyCount() - render <= 0) {
                            page++;
                            render -= reflower.emptyCount();
                            if (bm != null)
                                bm.recycle();
                            bm = render(reflower.rw, reflower.h, page);
                            reflower.load(bm, page, render - 1); // onScrollingFinished + 1
                        }
                        if (reflower.count() > render) {
                            if (bm != null)
                                bm.recycle();
                            bm = reflower.render(render);
                        }
                        reflower.pending = 1;
                        break;
                }
                if (bm != null) {
                    if (reflower == null || reflower.bm == bm)
                        drawWallpaper(canvas); // we are about to draw original page, perapre bacgkournd
                    else
                        canvas.drawColor(wallpaperColor); // prepare white
                    drawPage(canvas, w, h, bm);
                    bm.recycle();
                    return;
                }
            }
            if (reflower != null) {
                reflower.close();
                reflower = null;
            }
            drawWallpaper(canvas);
            draw(canvas, w, h, index);
        }

        public void draw(Canvas bitmap, int w, int h, ZLView.PageIndex index, Bitmap.Config c) {
        }

        public void draw(Canvas bitmap, int w, int h, ZLView.PageIndex index) {
            draw(bitmap, w, h, index, Bitmap.Config.RGB_565);
        }

        public void drawPage(Canvas canvas, int w, int h, Bitmap bm) {
            Rect src = new Rect(0, 0, bm.getWidth(), bm.getHeight());
            float wr = w / (float) bm.getWidth();
            float hr = h / (float) bm.getHeight();
            int dh = (int) (bm.getHeight() * wr);
            int dw = (int) (bm.getWidth() * hr);
            Rect dst;
            if (dh > h) { // scaling width max makes it too high
                int mid = (w - dw) / 2;
                dst = new Rect(mid, 0, dw + mid, h); // scale it by height max and take calulated width
            } else { // take width
                int mid = (h - dh) / 2;
                dst = new Rect(0, mid, w, dh + mid); // scale it by width max and take calulated height
            }
            canvas.drawBitmap(bm, src, dst, paint);
        }

        public void close() {
        }

        public TOCTree getCurrentTOCElement(TOCTree TOCTree) {
            TOCTree treeToSelect = null;
            for (TOCTree tree : TOCTree) {
                final TOCTree.Reference reference = tree.getReference();
                if (reference == null)
                    continue;
                if (reference.ParagraphIndex > current.pageNumber)
                    break;
                treeToSelect = tree;
            }
            return treeToSelect;
        }

        public Selection select(int page) {
            return null;
        }

        public Selection select(Selection.Page p, Selection.Point point) {
            return null;
        }

        public Selection select(ZLTextPosition start, ZLTextPosition end) {
            return null;
        }

        public Selection.Page selectPage(ZLTextPosition start, Reflow.Info info, int w, int h) {
            if (reflow && info != null)
                return new View.Selection.Page(start.getParagraphIndex(), info.bm.width(), info.bm.height());
            else
                return new View.Selection.Page(start.getParagraphIndex(), w, h);
        }

        public Rect selectRect(Reflow.Info info, int x, int y) {
            x = x - info.margin.left;
            Map<Rect, Rect> dst = info.dst;
            for (Rect d : dst.keySet()) {
                if (d.contains(x, y))
                    return dst.get(d);
            }
            return null;
        }

        public Selection.Point selectPoint(Reflow.Info info, int x, int y) {
            if (reflow) {
                x = x - info.margin.left;
                for (Rect d : info.dst.keySet()) {
                    if (d.contains(x, y))
                        return new Selection.Point(info.fromDst(d, x, y));
                }
                return null;
            } else {
                return new Selection.Point(x, y);
            }
        }

        public Selection select(ZLTextPosition start, Reflow.Info info, int w, int h, int x, int y) {
            Selection.Point p = selectPoint(info, x, y);
            if (p != null)
                return select(selectPage(start, info, w, h), p);
            return null;
        }

        public Rect[] boundsUpdate(Rect[] rr, Reflow.Info info) {
            ArrayList<Rect> list = new ArrayList<>();
            for (Rect r : rr) {
                for (Rect s : info.src.keySet()) {
                    Rect i = new Rect(r);
                    if (i.intersect(s) && (i.height() * 100 / s.height() > SelectionView.ARTIFACT_PERCENTS || r.height() > 0 && i.height() * 100 / r.height() > SelectionView.ARTIFACT_PERCENTS)) { // ignore artifacts height less then 10%
                        Rect d = info.fromSrc(s, i);
                        list.add(d);
                    }
                }
            }
            for (int i = list.size() - 1; i >= 0; i--) {
                for (int j = i - 1; j >= 0; j--) {
                    Rect r = list.get(i);
                    Rect k = list.get(j);
                    if (r.intersects(k.left, k.top, k.right, k.bottom)) {
                        k.union(k);
                        list.remove(i);
                        break;
                    }
                }
            }
            return list.toArray(new Rect[0]);
        }

        public Link[] getLinks(Selection.Page page) {
            return null;
        }

        public Search search(String text) {
            return null;
        }
    }

    View create(Storage.FBook book);
}
