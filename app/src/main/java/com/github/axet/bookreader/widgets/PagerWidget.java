package com.github.axet.bookreader.widgets;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.github.axet.bookreader.app.Plugin;
import com.github.axet.bookreader.app.Reflow;

import org.geometerplus.fbreader.fbreader.options.PageTurningOptions;
import org.geometerplus.zlibrary.core.application.ZLApplication;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.ui.android.view.ZLAndroidWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class PagerWidget extends ZLAndroidWidget {
    FBReaderView fb;
    FBReaderView.PinchGesture pinch;
    FBReaderView.BrightnessGesture brightness;

    int x;
    int y;

    ZLTextPosition selectionPage;

    ReflowMap<Reflow.Info> infos = new ReflowMap<>();
    ReflowMap<FBReaderView.LinksView> links = new ReflowMap<>();
    ReflowMap<FBReaderView.BookmarksView> bookmarks = new ReflowMap<>();
    ReflowMap<FBReaderView.TTSView> tts = new ReflowMap<>();
    ReflowMap<FBReaderView.SearchView> searchs = new ReflowMap<>();

    public class ReflowMap<V> extends HashMap<ZLTextPosition, V> {
        ArrayList<ZLTextPosition> last = new ArrayList<>();

        @Override
        public V put(ZLTextPosition key, V value) {
            V v = super.put(key, value);
            if (fb.pluginview.reflower != null) {
                int l = fb.pluginview.reflower.emptyCount() - 1;
                if (key.getElementIndex() == l) {
                    ZLTextFixedPosition n = new ZLTextFixedPosition(key.getParagraphIndex() + 1, -1, 0);
                    super.put(n, value); // ignore result, duplicate key for same value
                    last.add(n); // (3,-1,0) == (2,2,0) when (2,1,0) is last

                    ZLTextPosition k = new ZLTextFixedPosition(key.getParagraphIndex(), l + 1, 0);
                    V kv = get(new ZLTextFixedPosition(key.getParagraphIndex() + 1, 0, 0));
                    super.put(k, kv); // ignore result, duplicate key for same value
                    last.add(k); // (2,2,0) == (3,0,0) when (2,1,0) is last
                }
                if (key.getElementIndex() == 0) {
                    int p = key.getParagraphIndex() - 1;
                    for (ZLTextPosition k : keySet()) {
                        if (k.getParagraphIndex() == p && get(k) == null)
                            super.put(k, value); // update (2,3,0) == (3,0,0)
                    }
                }
            }
            if (v != null)
                return v;
            last.add(key);
            if (last.size() > 9) { // number of possible old values + dups
                ZLTextPosition k = last.remove(0);
                return remove(k);
            }
            return null;
        }

        @Override
        public void clear() {
            super.clear();
            last.clear();
        }
    }

    public PagerWidget(final FBReaderView view) {
        super(view.getContext());
        this.fb = view;

        ZLApplication = new ZLAndroidWidget.ZLApplicationInstance() {
            public ZLApplication Instance() {
                return view.app;
            }
        };
        setFocusable(true);

        view.config.setValue(view.app.PageTurningOptions.FingerScrolling, PageTurningOptions.FingerScrollingType.byTapAndFlick);

        if (Looper.myLooper() != null) { // render view only
            pinch = new FBReaderView.PinchGesture(view) {
                @Override
                public void onScaleBegin(float x, float y) {
                    pinchOpen(view.pluginview.current.pageNumber, getPageRect());
                }
            };
        }

        brightness = new FBReaderView.BrightnessGesture(view);
    }

    public Rect getPageRect() {
        Rect dst;
        if (fb.pluginview.reflow) {
            dst = new Rect(0, 0, getWidth(), getHeight());
        } else {
            Plugin.Page p = fb.pluginview.current; // not using current.renderRect() show partial page
            if (p.pageOffset < 0) { // show empty space at beginig
                int t = (int) (-p.pageOffset / p.ratio);
                dst = new Rect(0, t, p.w, t + (int) (p.pageBox.h / p.ratio));
            } else if (p.pageOffset == 0 && p.hh > p.pageBox.h) {  // show middle vertically
                int t = (int) ((p.hh - p.pageBox.h) / p.ratio / 2);
                dst = new Rect(0, t, p.w, p.h - t);
            } else {
                int t = (int) (-p.pageOffset / p.ratio);
                dst = new Rect(0, t, p.w, t + (int) (p.pageBox.h / p.ratio));
            }
        }
        return dst;
    }

    @Override
    public void setScreenBrightness(int percent) {
        myColorLevel = brightness.setScreenBrightness(percent);
        postInvalidate();
        updateColorLevel();
    }

    @Override
    public int getScreenBrightness() {
        return brightness.getScreenBrightness();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void drawOnBitmap(Bitmap bitmap, ZLViewEnums.PageIndex index) {
        if (fb.pluginview != null) {
            fb.pluginview.drawOnBitmap(getContext(), bitmap, getWidth(), getMainAreaHeight(), index, (FBReaderView.CustomView) fb.app.BookTextView, fb.book.info);
            Reflow.Info info = null;
            ZLTextPosition position;
            if (fb.pluginview.reflow) {
                position = new ZLTextFixedPosition(fb.pluginview.reflower.page, fb.pluginview.reflower.index + fb.pluginview.reflower.pending, 0);
                info = new Reflow.Info(fb.pluginview.reflower, position.getElementIndex());
                infos.put(position, info);
            } else {
                Plugin.Page old = new Plugin.Page(fb.pluginview.current) {
                    @Override
                    public void load() {
                    }

                    @Override
                    public int getPagesCount() {
                        return fb.pluginview.current.getPagesCount();
                    }
                };
                old.load(index);
                position = new ZLTextFixedPosition(old.pageNumber, 0, 0);
            }
            Rect dst = getPageRect();
            Plugin.View.Selection.Page page = fb.pluginview.selectPage(position, info, dst.width(), dst.height());
            FBReaderView.LinksView l = new FBReaderView.LinksView(fb, fb.pluginview.getLinks(page), info);
            FBReaderView.LinksView lold = links.put(position, l);
            if (lold != null)
                lold.close();
            FBReaderView.BookmarksView b = new FBReaderView.BookmarksView(fb, page, fb.book.info.bookmarks, info);
            FBReaderView.BookmarksView bold = bookmarks.put(position, b);
            if (bold != null)
                bold.close();
            if (fb.tts != null) {
                FBReaderView.TTSView t = new FBReaderView.TTSView(fb, page, info);
                FBReaderView.TTSView told = tts.put(position, t);
                if (told != null)
                    told.close();
            }
            if (fb.search != null) {
                FBReaderView.SearchView s = new FBReaderView.SearchView(fb, fb.search.getBounds(page), info);
                FBReaderView.SearchView sold = searchs.put(position, s);
                if (sold != null)
                    sold.close();
            }
        } else {
            super.drawOnBitmap(bitmap, index);
        }
    }

    public void updateOverlaysReset() {
        updateOverlays();
        resetCache(); // need to drawonbitmap
    }

    ZLTextFixedPosition getPosition() {
        if (fb.pluginview.reflow)
            return new ZLTextFixedPosition(fb.pluginview.reflower.page, fb.pluginview.reflower.index, 0);
        else
            return new ZLTextFixedPosition(fb.pluginview.current.pageNumber, 0, 0);
    }

    public void updateOverlays() {
        fb.invalidateFooter();
        if (fb.pluginview != null) {
            final Rect dst = getPageRect();
            int x = dst.left;
            int y = dst.top;
            if (fb.pluginview.reflow) {
                Reflow.Info info = getInfo();
                if (info != null) // in onDrawInScrolling onScrollingFinished called before onDrawStatic, causing info == null
                    x += getInfo().margin.left;
            }

            ZLTextPosition position = getPosition();

            for (FBReaderView.LinksView l : links.values()) {
                if (l != null)
                    l.hide();
            }
            FBReaderView.LinksView l = links.get(position);
            if (l != null) {
                l.show();
                l.update(x, y);
            }

            for (FBReaderView.BookmarksView b : bookmarks.values()) {
                if (b != null)
                    b.hide();
            }
            FBReaderView.BookmarksView b = bookmarks.get(position);
            if (b != null) {
                b.show();
                b.update(x, y);
            }

            for (FBReaderView.TTSView t : tts.values()) {
                if (t != null)
                    t.hide();
            }
            FBReaderView.TTSView t = tts.get(position);
            if (t != null) {
                t.show();
                t.update(x, y);
            }

            for (FBReaderView.SearchView s : searchs.values()) {
                if (s != null)
                    s.hide();
            }
            FBReaderView.SearchView s = searchs.get(position);
            if (s != null) {
                s.show();
                s.update(x, y);
            }

            if (selectionPage != null && !selectionPage.samePositionAs(position)) {
                fb.post(new Runnable() {
                    @Override
                    public void run() {
                        fb.selectionClose();
                    }
                });
                selectionPage = null;
            }
        }
    }

    public void linksClose() {
        for (FBReaderView.LinksView l : links.values()) {
            if (l != null)
                l.close();
        }
        links.clear();
    }

    public void bookmarksClose() {
        for (FBReaderView.BookmarksView l : bookmarks.values()) {
            if (l != null)
                l.close();
        }
        bookmarks.clear();
    }

    public void ttsClose() {
        for (FBReaderView.TTSView l : tts.values()) {
            if (l != null)
                l.close();
        }
        tts.clear();
    }

    public void searchClose() {
        for (FBReaderView.SearchView l : searchs.values()) {
            if (l != null)
                l.close();
        }
        searchs.clear();
    }

    @SuppressWarnings("unchecked")
    public void searchPage(int page) {
        int w = getWidth();
        int h = getMainAreaHeight();

        fb.pluginview.current.w = w;
        fb.pluginview.current.h = h;
        fb.pluginview.current.load(page, 0);
        fb.pluginview.current.renderPage();

        Rect dst = getPageRect();

        if (fb.pluginview.reflow) {
            if (fb.pluginview.reflower != null && (fb.pluginview.reflower.page != page || fb.pluginview.reflower.w != w || fb.pluginview.reflower.h != h)) {
                fb.pluginview.reflower.close();
                fb.pluginview.reflower = null;
            }
            if (fb.pluginview.reflower == null) {
                fb.pluginview.reflower = new Reflow(getContext(), w, h, page, (FBReaderView.CustomView) fb.app.BookTextView, fb.book.info);
                Bitmap bm = fb.pluginview.render(fb.pluginview.reflower.rw, fb.pluginview.reflower.h, page);
                fb.pluginview.reflower.load(bm, page, 0);
            }
            for (int i = 0; i < fb.pluginview.reflower.count(); i++) {
                Reflow.Info info = new Reflow.Info(fb.pluginview.reflower, i);
                ZLTextPosition pos = new ZLTextFixedPosition(page, i, 0);
                Plugin.View.Selection.Page p = fb.pluginview.selectPage(pos, info, fb.pluginview.reflower.w, fb.pluginview.reflower.h);
                Plugin.View.Search.Bounds bb = fb.search.getBounds(p);
                if (bb.rr != null) {
                    bb.rr = fb.pluginview.boundsUpdate(bb.rr, info);
                    if (bb.highlight != null) {
                        HashSet hh = new HashSet(Arrays.asList(fb.pluginview.boundsUpdate(bb.highlight, info)));
                        for (Rect r : bb.rr) {
                            if (hh.contains(r))
                                fb.pluginview.gotoPosition(new ZLTextFixedPosition(page, i, 0));
                        }
                    }
                }
            }
            resetCache();
        } else {
            ZLTextPosition pos = new ZLTextFixedPosition(page, 0, 0);
            Plugin.View.Selection.Page p = fb.pluginview.selectPage(pos, getInfo(), dst.width(), dst.height());
            Plugin.View.Search.Bounds bb = fb.search.getBounds(p);
            if (bb.rr != null) {
                if (bb.highlight != null) {
                    HashSet hh = new HashSet(Arrays.asList(bb.highlight));
                    for (Rect r : bb.rr) {
                        if (hh.contains(r)) {
                            int offset = 0;
                            int t = r.top + dst.top;
                            int b = r.bottom + dst.top;
                            while (t - offset / fb.pluginview.current.ratio > getBottom() || b - offset / fb.pluginview.current.ratio > getBottom() && r.height() < getMainAreaHeight())
                                offset += fb.pluginview.current.pageStep;
                            fb.pluginview.gotoPosition(new ZLTextFixedPosition(page, offset, 0));
                            resetCache();
                            return;
                        }
                    }
                }
            }
        }
    }

    public void resetCache() { // do not reset reflower
        super.reset();
        repaint();
    }

    @Override
    public void reset() {
        super.reset();
        if (fb.pluginview != null) {
            if (fb.pluginview.reflower != null)
                fb.pluginview.reflower.reset();
        }
        infos.clear();
        linksClose();
        bookmarksClose();
        searchClose();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        x = (int) event.getX();
        y = (int) event.getY();
        if (fb.pluginview != null && !fb.pluginview.reflow) {
            if (pinch.onTouchEvent(event))
                return true;
        }
        return super.onTouchEvent(event);
    }

    Reflow.Info getInfo() {
        if (fb.pluginview.reflower == null)
            return null;
        return infos.get(new ZLTextFixedPosition(fb.pluginview.reflower.page, fb.pluginview.reflower.index, 0));
    }

    @Override
    public boolean onLongClick(View v) {
        if (fb.pluginview != null) {
            final Rect dst = getPageRect();
            ZLTextPosition pos = getPosition();
            final Plugin.View.Selection s = fb.pluginview.select(pos, getInfo(), dst.width(), dst.height(), x - dst.left, y - dst.top);
            if (s != null) {
                if (fb.tts != null) {
                    fb.tts.selectionOpen(s);
                } else {
                    selectionPage = pos;
                    fb.selectionOpen(s);
                    final Plugin.View.Selection.Page page = fb.pluginview.selectPage(pos, getInfo(), dst.width(), dst.height());
                    final Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            int x = dst.left;
                            int y = dst.top;
                            if (fb.pluginview.reflow)
                                x += getInfo().margin.left;
                            fb.selection.update((SelectionView.PageView) fb.selection.getChildAt(0), x, y);
                        }
                    };
                    Plugin.View.Selection.Setter setter = new Plugin.View.Selection.Setter() {
                        @Override
                        public void setStart(int x, int y) {
                            Plugin.View.Selection.Point point = fb.pluginview.selectPoint(getInfo(), x - dst.left, y - dst.top);
                            if (point != null)
                                s.setStart(page, point);
                            run.run();
                        }

                        @Override
                        public void setEnd(int x, int y) {
                            Plugin.View.Selection.Point point = fb.pluginview.selectPoint(getInfo(), x - dst.left, y - dst.top);
                            if (point != null)
                                s.setEnd(page, point);
                            run.run();
                        }

                        @Override
                        public Plugin.View.Selection.Bounds getBounds() {
                            Plugin.View.Selection.Bounds bounds = s.getBounds(page);
                            if (fb.pluginview.reflow) {
                                bounds.rr = fb.pluginview.boundsUpdate(bounds.rr, getInfo());
                                bounds.start = true;
                                bounds.end = true;
                            }
                            return bounds;
                        }
                    };
                    SelectionView.PageView view = new SelectionView.PageView(getContext(), (FBReaderView.CustomView) fb.app.BookTextView, setter);
                    fb.selection.add(view);
                    run.run();
                }
                return true;
            }
            if (fb.tts != null)
                fb.tts.selectionClose();
            else
                fb.selectionClose();
        }
        if (fb.tts != null) {
            fb.tts.selectionOpen(x, y);
            return true;
        }
        return super.onLongClick(v);
    }
}
