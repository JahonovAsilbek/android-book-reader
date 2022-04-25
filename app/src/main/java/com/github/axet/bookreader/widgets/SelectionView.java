package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.app.PDFPlugin;
import com.github.axet.bookreader.app.Plugin;

import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.view.SelectionCursor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SelectionView extends FrameLayout {
    public static final int ARTIFACT_PERCENTS = 15;
    public static final int SELECTION_ALPHA = 0x99;
    public static final int SELECTION_PADDING = 1; // dp

    public Plugin.View.Selection selection;
    PageView touch;
    HandleRect startRect = new HandleRect();
    HandleRect endRect = new HandleRect();
    Paint handles;
    Rect margin; // absolut coords
    int clip;

    public static void relativeTo(Rect thiz, Rect rect) { // make child of rect, abs coords == rect.x + this.x
        thiz.offset(-rect.left, -rect.top);
    }

    public static void absTo(Rect thiz, Rect rect) {
        thiz.offset(rect.left, rect.top);
    }

    public static Rect circleRect(int x, int y, int r) {
        return new Rect(x - r, y - r, x + r, y + r);
    }

    public static HotRect rectHandle(SelectionCursor.Which which, int x, int y) {
        final int dpi = ZLibrary.Instance().getDisplayDPI();
        final int unit = dpi / 120;
        final int xCenter = which == SelectionCursor.Which.Left ? x - unit - 1 : x + unit + 1;
        HotRect rect = new HotRect(xCenter - unit, y - dpi / 8, xCenter + unit, y + dpi / 8, x, y);
        if (which == SelectionCursor.Which.Left)
            rect.rect.union(circleRect(xCenter, y - dpi / 8, unit * 6));
        else
            rect.rect.union(circleRect(xCenter, y + dpi / 8, unit * 6));
        return rect;
    }

    public static void drawHandle(Canvas canvas, SelectionCursor.Which which, int x, int y, Paint handles) { // SelectionCursor.draw
        final int dpi = ZLibrary.Instance().getDisplayDPI();
        final int unit = dpi / 120;
        final int xCenter = which == SelectionCursor.Which.Left ? x - unit - 1 : x + unit + 1;
        canvas.drawRect(xCenter - unit, y - dpi / 8, xCenter + unit, y + dpi / 8, handles);
        if (which == SelectionCursor.Which.Left)
            canvas.drawCircle(xCenter, y - dpi / 8, unit * 6, handles);
        else
            canvas.drawCircle(xCenter, y + dpi / 8, unit * 6, handles);
    }

    public static Rect union(List<Rect> rr) {
        int i = 0;
        Rect bounds = new Rect(rr.get(i++));
        for (; i < rr.size(); i++) {
            Rect r = rr.get(i);
            bounds.union(r);
        }
        return bounds;
    }

    public static boolean lineIntersects(Rect r1, Rect r2) {
        return r1.top < r2.bottom && r2.top < r1.bottom;
    }

    public static List<Rect> lines(Rect[] rr) {
        return lines(Arrays.asList(rr));
    }

    public static List<Rect> lines(List<Rect> rr) {
        ArrayList<Rect> lines = new ArrayList<>();
        for (Rect r : rr) {
            for (Rect l : lines) {
                if (lineIntersects(l, r)) {
                    l.union(r);
                    r = null;
                    break;
                }
            }
            if (r != null)
                lines.add(new Rect(r));
            for (Rect l : new ArrayList<>(lines)) { // merge lines
                for (Rect ll : lines) {
                    if (l != ll && lineIntersects(ll, l)) {
                        ll.union(l);
                        lines.remove(l);
                        break;
                    }
                }
            }
        }
        Collections.sort(lines, new UL());
        return lines;
    }

    public static class LinesUL implements Comparator<Rect> {
        List<Rect> lines;

        public LinesUL(Rect[] rr) {
            lines = lines(rr);
        }

        public LinesUL(List<Rect> rr) {
            lines = lines(rr);
        }

        Integer getLine(Rect r) {
            for (int i = 0; i < lines.size(); i++) {
                Rect l = lines.get(i);
                if (r.intersect(l))
                    return i;
            }
            return -1;
        }

        @Override
        public int compare(Rect o1, Rect o2) {
            int r = getLine(o1).compareTo(getLine(o2));
            if (r != 0)
                return r;
            return Integer.valueOf(o1.left).compareTo(Integer.valueOf(o2.left));
        }
    }

    public static class UL implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            int r = Integer.valueOf(o1.top).compareTo(Integer.valueOf(o2.top));
            if (r != 0)
                return r;
            return Integer.valueOf(o1.left).compareTo(Integer.valueOf(o2.left));
        }
    }

    public static class HotRect {
        Rect rect;
        public int hotx;
        public int hoty;

        public HotRect(HotRect r) {
            this.rect = new Rect(r.rect);
            hotx = r.hotx;
            hoty = r.hoty;
        }

        public HotRect(int left, int top, int right, int bottom, int x, int y) {
            this.rect = new Rect(left, top, right, bottom);
            this.hotx = x;
            this.hoty = y;
        }

        public void relativeTo(Rect rect) { // make child of rect, abs coords == rect.x + this.x
            SelectionView.relativeTo(this.rect, rect);
            hotx = hotx - rect.left;
            hoty = hoty - rect.top;
        }

        public HotPoint makePoint(int x, int y) {
            return new HotPoint(x, y, hotx - x, hoty - y);
        }
    }

    public static class TouchRect {
        public HotRect rect;
        public HotPoint touch;

        public TouchRect() {
        }

        public TouchRect(TouchRect r) {
            rect = new HotRect(r.rect);
            if (r.touch != null)
                touch = new HotPoint(r.touch);
        }

        public void relativeTo(Rect rect) {
            this.rect.relativeTo(rect);
            if (touch != null)
                touch.relativeTo(rect);
        }

        public boolean onTouchEvent(int a, int x, int y) {
            if (a == MotionEvent.ACTION_DOWN && rect.rect.contains(x, y) || touch != null) {
                if (touch == null)
                    touch = rect.makePoint(x, y);
                else
                    touch = new HotPoint(x, y, touch);
                return true;
            }
            return false;
        }

        public void onTouchRelease(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
                touch = null;
        }
    }

    public static class HotPoint extends Point {
        public int offx;
        public int offy;

        public HotPoint(HotPoint r) {
            super(r);
            offx = r.offx;
            offy = r.offy;
        }

        public HotPoint(int x, int y, int hx, int hy) {
            super(x + hx, y + hy);
            offx = hx;
            offy = hy;
        }

        public HotPoint(int x, int y, HotPoint h) {
            super(x + h.offx, y + h.offy);
            offx = h.offx;
            offy = h.offy;
        }

        public void relativeTo(Rect r) { // make child of rect, abs coords == r.x + p.x
            x = x - r.left;
            y = y - r.top;
        }

        public void absTo(Rect r) { // make abs coords
            x = x + r.left;
            y = y + r.top;
        }
    }

    public static class HandleRect extends TouchRect {
        public SelectionCursor.Which which;
        public PageView page;
        public Point draw;

        public HandleRect() {
        }

        public HandleRect(HandleRect r, Rect re) {
            super(r);
            which = r.which;
            page = r.page;
            relativeTo(re);
        }

        public void makeUnion(Rect rect) {
            rect.union(this.rect.rect);
            if (touch != null)
                rect.union(rectHandle(which, touch.x, touch.y).rect);
        }

        public void drawRect(Rect rect) {
            if (touch != null)
                draw = new HotPoint(touch);
            else
                draw = new Point(this.rect.hotx, this.rect.hoty);
            draw.x -= rect.left;
            draw.y -= rect.top;
        }
    }

    public static class PageView extends View {
        Rect bounds = new Rect(); // view size
        Rect margin = new Rect(); // absolute coords (parent of SelectionView coords)
        Plugin.View.Selection.Bounds selection;

        List<Rect> lines;

        PDFPlugin.Selection.Setter setter;

        Paint paint;
        int padding;

        public PageView(Context context, FBReaderView.CustomView custom, PDFPlugin.Selection.Setter setter) {
            super(context);
            this.paint = new Paint();
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(SELECTION_ALPHA << 24 | custom.getSelectionBackgroundColor().intValue());

            this.setter = setter;

            padding = ThemeUtils.dp2px(getContext(), SELECTION_PADDING);

            // setBackgroundColor(0x33 << 24 | (0xffffff & Color.BLUE));

            setLayoutParams(new MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        public void update(int x, int y) {
            update();
            margin.left = bounds.left + x;
            margin.right = bounds.right + x;
            margin.top = bounds.top + y;
            margin.bottom = bounds.bottom + y;
        }

        public void update() {
            selection = setter.getBounds();

            if (selection.rr == null || selection.rr.length == 0)
                return;

            lines = lines(selection.rr);

            bounds = union(lines);

            bounds.inset(-padding, -padding);

            for (Rect r : lines) {
                r.inset(-padding, -padding);
                relativeTo(r, bounds);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (Rect r : lines)
                canvas.drawRect(r, paint);
        }
    }

    public SelectionView(Context context, FBReaderView.CustomView custom, Plugin.View.Selection s) {
        super(context);

        this.selection = s;

        this.handles = new Paint();
        this.handles.setStyle(Paint.Style.FILL);
        this.handles.setColor(0xff << 24 | custom.getSelectionBackgroundColor().intValue());

        setLayoutParams(new MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // setBackgroundColor(0x33 << 24 | (0xffffff & Color.GREEN));
    }

    public void setClipHeight(int h) {
        clip = h;
    }

    PageView findView(int x, int y) {
        for (int i = 0; i < getChildCount(); i++) {
            PageView view = (PageView) getChildAt(i);
            if (view.getLeft() < view.getRight() && view.getTop() < view.getBottom() && x >= view.getLeft() && x < view.getRight() && y >= view.getTop() && y < view.getBottom())
                return view;
        }
        return null;
    }

    public void add(PageView page) {
        addView(page);
    }

    public void remove(PageView page) {
        if (touch == page)
            touch = null;
        removeView(page);
        update();
    }

    public void update(PageView page, int x, int y) {
        page.update(x, y);
        update();
    }

    public void update() {
        Rect margin = this.margin = null;

        boolean reverse = false;
        Rect first = null;
        PageView firstPage = null;
        Rect last = null;
        PageView lastPage = null;

        for (int i = 0; i < getChildCount(); i++) {
            PageView v = (PageView) getChildAt(i);

            if (margin == null)
                margin = new Rect(v.margin);
            else
                margin.union(v.margin);

            reverse = v.selection.reverse;

            if (v.selection.start) {
                first = new Rect(v.lines.get(0));
                absTo(first, v.margin);
                firstPage = v;
            }
            if (v.selection.end) {
                last = new Rect(v.lines.get(v.lines.size() - 1));
                absTo(last, v.margin);
                lastPage = v;
            }
        }

        if (margin == null || first == null && last == null)
            return; // broken

        if (first == null) { // reflow selection can has artifacts / small symbols in between pages, ignore it
            first = new Rect(lastPage.lines.get(0));
            absTo(first, lastPage.margin);
            firstPage = lastPage;
        }
        if (last == null) { // reflow selection can has artifacts / small symbols in between pages, ignore it
            last = new Rect(firstPage.lines.get(firstPage.lines.size() - 1));
            absTo(last, firstPage.margin);
            lastPage = firstPage;
        }

        HotRect left = rectHandle(SelectionCursor.Which.Left, first.left, first.top + first.height() / 2);
        HotRect right = rectHandle(SelectionCursor.Which.Right, last.right, last.top + last.height() / 2);

        if (reverse) {
            startRect.rect = right;
            startRect.which = SelectionCursor.Which.Right;
            startRect.page = lastPage;
            endRect.rect = left;
            endRect.which = SelectionCursor.Which.Left;
            endRect.page = firstPage;
        } else {
            startRect.rect = left;
            startRect.which = SelectionCursor.Which.Left;
            startRect.page = firstPage;
            endRect.rect = right;
            endRect.which = SelectionCursor.Which.Right;
            endRect.page = lastPage;
        }

        startRect.makeUnion(margin);
        endRect.makeUnion(margin);

        startRect.drawRect(margin);
        endRect.drawRect(margin);

        this.margin = margin;

        for (int i = 0; i < getChildCount(); i++) {
            PageView v = (PageView) getChildAt(i);
            MarginLayoutParams vlp = (MarginLayoutParams) v.getLayoutParams();
            vlp.leftMargin = v.margin.left - margin.left;
            vlp.topMargin = v.margin.top - margin.top;
            vlp.width = v.margin.width();
            vlp.height = v.margin.height();
            v.requestLayout();
        }

        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.leftMargin = margin.left;
        lp.topMargin = margin.top;
        lp.width = margin.width();
        lp.height = margin.height();
        requestLayout();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect c = canvas.getClipBounds();
        c.bottom = clip - getTop();
        canvas.clipRect(c);
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.margin != null) { // // broken / empty window
            drawHandle(canvas, startRect.which, startRect);
            drawHandle(canvas, endRect.which, endRect);
        }
    }

    public void drawHandle(Canvas canvas, SelectionCursor.Which which, HandleRect rect) { // SelectionCursor.draw
        SelectionView.drawHandle(canvas, which, rect.draw.x, rect.draw.y, handles);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.margin != null) { // broken / empty window
            int x = (int) event.getX() + getLeft();
            int y = (int) event.getY() + getTop();
            if (startRect.onTouchEvent(event.getAction(), x, y)) {
                onTouchLock();
                x += startRect.touch.offx;
                y += startRect.touch.offy;
                startRect.onTouchRelease(event);
                startRect.page.setter.setStart(x, y);
                if (startRect.touch == null)
                    onTouchUnlock();
                return true;
            }
            if (endRect.onTouchEvent(event.getAction(), x, y)) {
                onTouchLock();
                x += endRect.touch.offx;
                y += endRect.touch.offy;
                endRect.onTouchRelease(event);
                endRect.page.setter.setEnd(x, y);
                if (endRect.touch == null)
                    onTouchUnlock();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public void close() {
        if (selection != null) {
            selection.close();
            selection = null;
        }
    }

    public void onTouchLock() {
    }

    public void onTouchUnlock() {
    }

    public int getSelectionStartY() {
        if (this.margin == null) // broken / empty window
            return 0;
        return startRect.rect.rect.top;
    }

    public int getSelectionEndY() {
        if (this.margin == null) // broken / empty window
            return 0;
        return endRect.rect.rect.bottom;
    }
}
