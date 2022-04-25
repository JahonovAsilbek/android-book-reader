package com.github.axet.bookreader.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;

import androidx.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.k2pdfopt.Config;
import com.github.axet.k2pdfopt.K2PdfOpt;

import org.geometerplus.zlibrary.core.view.ZLViewEnums;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Reflow {
    public K2PdfOpt k2;
    public int page; // document page
    public int index = 0; // current view position
    public int pending = 0; // pending action, so we can cancel it
    public int w;
    public int h;
    public int rw;
    Context context;
    public Bitmap bm; // source bm, in case or errors, recycled otherwise
    public Storage.RecentInfo info;
    FBReaderView.CustomView custom; // font size

    public static void K2PdfOptInit(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "willus", "k2pdfopt", "k2pdfoptjni");
            Config.natives = false;
        }
    }

    public static class Info {
        public Rect bm; // source bitmap size
        public Rect margin; // page margins
        public Map<Rect, Rect> src;
        public Map<Rect, Rect> dst;

        public Info(Reflow reflower, int page) {
            bm = new Rect(0, 0, reflower.bm.getWidth(), reflower.bm.getHeight());
            margin = new Rect(reflower.getLeftMargin(), 0, reflower.getRightMargin(), 0);
            if (reflower.k2.getCount() > 0)
                src = reflower.k2.getRectMaps(page);
            else // zero pages
                src = new HashMap<>();
            dst = new HashMap<>();
            for (Rect k : src.keySet()) {
                Rect v = src.get(k);
                dst.put(v, k);
            }
        }

        public Point fromDst(Rect d, int x, int y) {
            Rect s = dst.get(d);
            double kx = s.width() / (double) d.width();
            double ky = s.height() / (double) d.height();
            return new Point(s.left + (int) ((x - d.left) * kx), s.top + (int) ((y - d.top) * ky));
        }

        public Rect fromSrc(Rect s, Rect r) {
            Rect d = src.get(s);
            double kx = d.width() / (double) s.width();
            double ky = d.height() / (double) s.height();
            return new Rect(d.left + (int) ((r.left - s.left) * kx), d.top + (int) ((r.top - s.top) * ky), d.right + (int) ((r.right - s.right) * kx), d.bottom + (int) ((r.bottom - s.bottom) * ky));
        }
    }

    public Reflow(Context context, int w, int h, int page, FBReaderView.CustomView custom, Storage.RecentInfo info) {
        K2PdfOptInit(context);
        this.context = context;
        this.page = page;
        this.info = info;
        this.custom = custom;
        reset(w, h);
    }

    public void reset() {
        w = 0;
        h = 0;
        if (k2 != null) {
            k2.close();
            k2 = null;
        }
    }

    public int getLeftMargin() {
        return custom.getLeftMargin();
    }

    public int getRightMargin() {
        return custom.getRightMargin();
    }

    public void reset(int w, int h) {
        if (this.w != w || this.h != h) {
            int rw = w - getLeftMargin() - getRightMargin();
            this.w = w;
            this.h = h;
            this.rw = rw;
            this.index = 0; // size changed, reflow page can overflow total pages
            create();
        }
        if (k2 == null)
            create();
    }

    void create() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Float old = shared.getFloat(BookApplication.PREFERENCE_FONTSIZE_REFLOW, BookApplication.PREFERENCE_FONTSIZE_REFLOW_DEFAULT);
        if (info.fontsize != null)
            old = info.fontsize / 100f;
        if (k2 != null) {
            old = k2.getFontSize();
            k2.close();
        }
        k2 = new K2PdfOpt();
        DisplayMetrics d = context.getResources().getDisplayMetrics();
        k2.create(rw, h, d.densityDpi);
        k2.setFontSize(old);
    }

    public void load(Bitmap bm) {
        if (this.bm != null)
            this.bm.recycle();
        this.bm = bm;
        index = 0;
        k2.load(bm);
    }

    public void load(Bitmap bm, int page, int index) {
        if (this.bm != null)
            this.bm.recycle();
        this.bm = bm;
        this.page = page;
        this.index = index;
        k2.load(bm);
    }

    public int count() {
        if (k2 == null)
            return -1;
        if (bm == null)
            return -1;
        return k2.getCount();
    }

    public int emptyCount() {
        int c = count();
        if (c == 0)
            c = 1;
        return c;
    }

    public Bitmap render(int page) {
        return k2.renderPage(page);
    }

    public boolean canScroll(ZLViewEnums.PageIndex pos) {
        switch (pos) {
            case previous:
                return index > 0;
            case next:
                return index + 1 < count();
            default:
                return true; // current???
        }
    }

    public void onScrollingFinished(ZLViewEnums.PageIndex pos) {
        switch (pos) {
            case next:
                index++;
                pending = 0;
                break;
            case current: // cancel
                pending = 0;
                if (count() == -1)
                    return;
                if (index < 0) {
                    index = -1;
                    recycle();
                    return;
                }
                if (index >= count()) { // current points to next page +1
                    page += 1;
                    index = 0;
                    recycle();
                    return;
                }
                break;
            case previous:
                index--;
                pending = 0;
                break;
        }
    }

    public void recycle() {
        if (k2 != null) {
            k2.close();
            k2 = null;
        }
        if (bm != null) {
            bm.recycle();
            bm = null;
        }
    }

    public void close() {
        recycle();
    }

    public Bitmap drawSrc(Plugin.View pluginview, Info info, Rect r) {
        Bitmap bm = drawSrc(pluginview, info);
        Canvas c = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(Color.MAGENTA);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0);
        drawRect(c, r, paint);
        return bm;
    }

    public Bitmap drawSrc(Plugin.View pluginview, Info info, Point p) {
        Bitmap bm = drawSrc(pluginview, info);
        Canvas c = new Canvas(bm);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.MAGENTA);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(0);
        c.drawCircle(p.x, p.y, 3, paint);
        return bm;
    }

    public Bitmap drawSrc(Plugin.View pluginview, Info info) {
        Bitmap b = pluginview.render(w, h, Reflow.this.page);
        Canvas canvas = new Canvas(b);
        draw(canvas, info.src.keySet());
        return b;
    }

    public Bitmap drawDst(Info info, Rect r) {
        Bitmap bm = drawDst(info);
        if (bm == null)
            return null;
        Canvas c = new Canvas(bm);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.MAGENTA);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0);
        drawRect(c, r, paint);
        return bm;
    }

    public Bitmap drawDst(Info info, Point p) {
        Bitmap bm = drawDst(info);
        if (bm == null)
            return null;
        Canvas c = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(Color.MAGENTA);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(3);
        c.drawCircle(p.x, p.y, 3, paint);
        return bm;
    }

    int findPage(Info info) {
        for (int i = 0; i < count(); i++) {
            if (info.src.equals(k2.getRectMaps(i)))
                return i;
        }
        return -1;
    }

    public Bitmap drawDst(Info info) {
        int page = findPage(info);
        if (page == -1)
            return null;
        Bitmap b = render(page);
        Canvas canvas = new Canvas(b);
        draw(canvas, info.dst.keySet());
        return b;
    }

    public void draw(Canvas canvas, Set<Rect> keys) {
        Rect[] kk = keys.toArray(new Rect[0]);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0);
        Paint text = new Paint();
        text.setColor(Color.RED);
        text.setStrokeWidth(0);
        for (int i = 0; i < kk.length; i++) {
            Rect k = kk[i];
            drawRect(canvas, k, paint);

            String t = "" + i;

            int size = 0;
            Rect bounds = new Rect();
            do {
                text.setTextSize(size);
                text.getTextBounds(t, 0, t.length(), bounds);
                size++;
            } while (bounds.height() < (k.height()));

            float m = text.measureText(t);
            canvas.drawText(t, k.centerX() - m / 2, k.top + k.height(), text);
        }
    }

    public static void drawRect(Canvas canvas, Rect rect, Paint paint) {
        canvas.drawLine(rect.left, rect.top, rect.right, rect.top, paint);
        canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, paint);
        canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, paint);
        canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, paint);
    }

}
