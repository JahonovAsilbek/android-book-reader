package com.github.axet.bookreader.app;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.bookreader.widgets.ScrollWidget;
import com.github.axet.pdfium.Config;
import com.github.axet.pdfium.Pdfium;

import org.geometerplus.fbreader.book.AbstractBook;
import org.geometerplus.fbreader.book.BookUtil;
import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.formats.BookReadingException;
import org.geometerplus.fbreader.formats.BuiltinFormatPlugin;
import org.geometerplus.zlibrary.core.encodings.EncodingCollection;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.view.ZLView;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.text.model.ZLTextMark;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.model.ZLTextParagraph;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.ui.android.image.ZLBitmapImage;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PDFPlugin extends BuiltinFormatPlugin implements Plugin {
    public static String TAG = PDFPlugin.class.getSimpleName();

    public static final String EXT = "pdf";

    public static PDFPlugin create(Storage.Info info) {
        if (Config.natives) {
            Natives.loadLibraries(info.context, "modpdfium", "pdfiumjni");
            Config.natives = false;
        }
        return new PDFPlugin(info);
    }

    public static class UL implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            int r = Integer.valueOf(o2.top).compareTo(Integer.valueOf(o1.top));
            if (r != 0)
                return r;
            return Integer.valueOf(o1.left).compareTo(Integer.valueOf(o2.left));
        }
    }

    public static class SelectionPage {
        int page;
        Pdfium.Page ppage;
        Pdfium.Text text;
        int index; // char index
        int count; // total symbols
        int w;
        int h;
        Rect[] sorted;

        public SelectionPage(SelectionPage s) {
            page = s.page;
            ppage = s.ppage;
            text = s.text;
            index = s.index;
            count = s.count;
            w = s.w;
            h = s.h;
            sorted = s.sorted;
        }

        public SelectionPage(Pdfium pdfium, View.Selection.Page page) {
            this(page.page, pdfium.openPage(page.page), page.w, page.h);
        }

        public SelectionPage(Pdfium pdfium, int page) {
            this(page, pdfium.openPage(page), 0, 0);
        }

        public SelectionPage(int p, Pdfium.Page page, int w, int h) {
            this.page = p;
            this.ppage = page;
            this.text = page.open();
            this.count = (int) text.getCount();
            this.w = w;
            this.h = h;
            this.index = -1;
            this.sorted = text.getBounds(0, count);
            Arrays.sort(this.sorted, new UL());
        }

        public int first() {
            for (Rect r : sorted) {
                Rect k = new Rect(r);
                int index;
                do {
                    index = text.getIndex(k.left, k.centerY());
                } while (index == -1 && ++k.left < k.right);
                if (index != -1)
                    return index;
            }
            return 0;
        }

        public void close() {
            text.close();
            ppage.close();
        }
    }

    public static class Selection extends View.Selection {
        Pdfium pdfium;
        SelectionPage start;
        SelectionPage end;
        SparseArray<SelectionPage> map = new SparseArray<>();

        public class SelectionBounds {
            SelectionPage page; // current

            SelectionPage s;
            SelectionPage e;

            int ss; // start index
            int ll; // last index
            int ee; // end index
            int cc; // count

            boolean first;
            boolean last;

            boolean reverse;

            public SelectionBounds(Page p) {
                this(p.page);
                start.w = p.w;
                start.h = p.h;
                end.w = p.w;
                end.h = p.h;
                page.w = p.w; // page can be opened by open
                page.h = p.h;
            }

            public SelectionBounds(int p) {
                this();
                if (s.page == e.page) {
                    page = s;
                    ss = s.index;
                    ee = e.index + 1;
                    cc = ee - ss;
                    first = true;
                    last = true;
                    if (reverse)
                        ss++;
                } else if (s.page == p) {
                    page = s;
                    ss = s.index;
                    ee = s.count;
                    cc = ee - ss;
                    first = true;
                    last = false;
                    if (reverse)
                        ss++;
                } else if (e.page == p) {
                    page = e;
                    ss = e.first();
                    ee = e.index + 1;
                    cc = ee - ss;
                    first = false;
                    last = true;
                } else {
                    page = open(p);
                    ss = page.first();
                    ee = page.count;
                    cc = ee - ss;
                    first = false;
                    last = false;
                }
                ll = ee - 1;
            }

            public SelectionBounds() {
                if (start.page > end.page) {
                    reverse = true;
                    s = end;
                    e = start;
                } else {
                    if (start.page == end.page) {
                        if (start.index > end.index) {
                            reverse = true;
                            s = end;
                            e = start;
                        } else {
                            s = start;
                            e = end;
                        }
                    } else {
                        s = start;
                        e = end;
                    }
                }
            }
        }

        public Selection(Pdfium pdfium, SelectionPage page, Point point) {
            this.pdfium = pdfium;
            map.put(page.page, page);
            point = new Point(page.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
            selectWord(page, point);
        }

        public Selection(Pdfium pdfium, ZLTextPosition start, ZLTextPosition end) {
            this.pdfium = pdfium;
            this.start = open(start.getParagraphIndex());
            this.start.index = start.getElementIndex();
            this.end = open(end.getParagraphIndex());
            this.end.index = end.getElementIndex();
        }

        public Selection(Pdfium pdfium, int page) {
            this.pdfium = pdfium;
            this.start = open(page);
            this.start.index = 0;
            this.end = open(page);
            this.end.index = this.end.count;
        }

        public boolean isEmpty() {
            if (start == null || end == null)
                return true;
            return start.index == -1 || end.index == -1;
        }

        boolean isWord(SelectionPage p, int i) {
            String s = p.text.getText(i, 1);
            if (s == null || s.length() != 1)
                return false;
            s = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.US); // й composed as two chars sometimes.
            s = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.US);
            return isWord(s.toCharArray()[0]);
        }

        SelectionPage open(Page page) {
            SelectionPage p = map.get(page.page);
            if (p != null) {
                p.w = page.w;
                p.h = page.h;
            }
            if (p == null) {
                p = new SelectionPage(pdfium, page);
                map.put(p.page, p);
            }
            return new SelectionPage(p);
        }

        SelectionPage open(int page) {
            SelectionPage p = map.get(page);
            if (p == null) {
                p = new SelectionPage(pdfium, page);
                map.put(p.page, p);
            }
            return new SelectionPage(p);
        }

        void selectWord(SelectionPage page, Point point) {
            start = page;
            int index = start.text.getIndex(point.x, point.y);
            if (index < 0 || index >= start.count)
                return;
            int start = index;
            while (start >= 0 && isWord(this.start, start)) {
                this.start.index = start;
                start--;
            }
            end = new SelectionPage(page);
            int end = index;
            while (end < this.end.count && isWord(this.end, end)) {
                this.end.index = end;
                end++;
            }
        }

        @Override
        public void setStart(Page page, Point point) {
            SelectionPage start = open(page);
            if (start.count > 0) {
                point = new Point(start.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
                int index = start.text.getIndex(point.x, point.y);
                if (index == -1)
                    return;
                start.index = index;
                this.start = start;
                return;
            }
        }

        @Override
        public void setEnd(Page page, Point point) {
            SelectionPage end = open(page);
            if (end.count > 0) {
                point = new Point(end.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
                int index = end.text.getIndex(point.x, point.y);
                if (index == -1)
                    return;
                end.index = index;
                this.end = end;
                return;
            }
        }

        @Override
        public String getText() {
            SelectionBounds b = new SelectionBounds();
            StringBuilder text = new StringBuilder();
            for (int i = b.s.page; i <= b.e.page; i++)
                text.append(getText(i));
            return text.toString();
        }

        String getText(int i) {
            SelectionBounds b = new SelectionBounds(i);
            return b.page.text.getText(b.ss, b.cc);
        }

        @Override
        public Rect[] getBoundsAll(Page page) {
            SelectionPage p = open(page);
            Rect[] rr = p.text.getBounds(0, p.count);
            for (int i = 0; i < rr.length; i++) {
                Rect r = rr[i];
                r = p.ppage.toDevice(0, 0, p.w, p.h, 0, r);
                rr[i] = r;
            }
            return rr;
        }

        @Override
        public Bounds getBounds(Page p) {
            Bounds bounds = new Bounds();
            SelectionBounds b = new SelectionBounds(p);
            bounds.reverse = b.reverse;
            bounds.start = b.first;
            bounds.end = b.last;
            bounds.rr = b.page.text.getBounds(b.ss, b.cc);
            for (int i = 0; i < bounds.rr.length; i++) {
                Rect r = bounds.rr[i];
                r = b.page.ppage.toDevice(0, 0, b.page.w, b.page.h, 0, r);
                bounds.rr[i] = r;
            }
            return bounds;
        }

        @Override
        public Boolean inBetween(Page page, Point start, Point end) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.s.page < page.page && page.page < b.e.page)
                return true;
            if (b.page.count > 0) {
                Point p1 = new Point(b.page.ppage.toPage(0, 0, page.w, page.h, 0, start.x, start.y));
                int i1 = b.page.text.getIndex(p1.x, p1.y);
                if (i1 == -1)
                    return null;
                Point p2 = new Point(b.page.ppage.toPage(0, 0, page.w, page.h, 0, end.x, end.y));
                int i2 = b.page.text.getIndex(p2.x, p2.y);
                if (i2 == -1)
                    return null;
                if (i2 < i1)
                    return null; // document incorrectly marked (last symbol appears at the end of page)
                return i1 <= b.ss && b.ss <= i2 || i1 <= b.ll && b.ll <= i2;
            }
            return null;
        }

        @Override
        public boolean isValid(Page page, Point point) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.page.count > 0) {
                point = new Point(b.page.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
                int index = b.page.text.getIndex(point.x, point.y);
                if (index == -1)
                    return false;
                return true;
            }
            return false;
        }

        @Override
        public boolean isSelected(int page) {
            SelectionBounds b = new SelectionBounds(page);
            return b.s.page <= page && page <= b.e.page;
        }

        @Override
        public Boolean isAbove(Page page, Point point) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.s.page < page.page)
                return true;
            if (b.page.count > 0) {
                point = new Point(b.page.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
                int index = b.page.text.getIndex(point.x, point.y);
                if (index == -1)
                    return null;
                return b.ss < index || b.ll < index;
            }
            return null;
        }

        @Override
        public Boolean isBelow(Page page, Point point) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.e.page > page.page)
                return true;
            if (b.page.count > 0) {
                point = new Point(b.page.ppage.toPage(0, 0, page.w, page.h, 0, point.x, point.y));
                int index = b.page.text.getIndex(point.x, point.y);
                if (index == -1)
                    return null;
                return index < b.ss || index < b.ll;
            }
            return null;
        }

        @Override
        public void close() {
            if (start != null) {
                start.close();
                start = null;
            }
            if (end != null) {
                end.close();
                end = null;
            }
            for (int i = 0; i < map.size(); i++) {
                SelectionPage page = map.valueAt(i);
                page.close();
            }
            map.clear();
        }

        @Override
        public ZLTextFixedPosition getStart() {
            return new ZLTextFixedPosition(start.page, start.index, 0);
        }

        @Override
        public ZLTextFixedPosition getEnd() {
            return new ZLTextFixedPosition(end.page, end.index, 0);
        }
    }

    public static class SearchResult {
        public int page;
        public int start;
        public int end;

        public SearchResult(int p, int i, int c) {
            page = p;
            start = i;
            end = i + c;
        }

        public int count() {
            return end - start;
        }
    }

    public static class PdfSearch extends View.Search {
        Pdfium pdfium;
        ArrayList<SearchResult> all = new ArrayList<>();
        SparseArray<ArrayList<SearchResult>> pages = new SparseArray<>();
        int index;
        String str;
        int page; // inital page to show

        public PdfSearch(Pdfium pdfium, String str) {
            this.pdfium = pdfium;
            this.index = -1;
            this.page = -1;
            this.str = str;
        }

        boolean hasText(int page) {
            Pdfium.Page p = pdfium.openPage(page);
            if (p == null)
                return false;
            Pdfium.Text t = p.open();
            try {
                if (t != null && t.getCount() > 0)
                    return true;
                return false;
            } finally {
                if (t != null)
                    t.close();
                p.close();
            }
        }

        ArrayList<SearchResult> search(int i) {
            Pdfium.Page page = pdfium.openPage(i);
            Pdfium.Text text = page.open();
            String pattern = str.toLowerCase(Locale.US);
            ArrayList<SearchResult> rr = new ArrayList<>();
            if (text.getCount() > 0) {
                String str = text.getText(0, (int) text.getCount());
                str = str.toLowerCase(Locale.US);
                int index = str.indexOf(pattern);
                while (index != -1) {
                    SearchResult ss = new SearchResult(i, index, pattern.length());
                    rr.add(ss);
                    index = str.indexOf(pattern, index + 1);
                }
            }
            pages.put(i, rr);
            text.close();
            page.close();
            return rr;
        }

        @Override
        public Bounds getBounds(View.Selection.Page page) {
            Bounds bounds = new Bounds();
            ArrayList<SearchResult> list = pages.get(page.page);
            if (list == null)
                return null;
            Pdfium.Page p = pdfium.openPage(page.page);
            Pdfium.Text t = p.open();
            ArrayList<Rect> rr = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                SearchResult r = list.get(i);
                ArrayList<Rect> hh = new ArrayList<>();
                Rect[] bb = t.getBounds(r.start, r.count());
                for (Rect b : bb) {
                    b = p.toDevice(0, 0, page.w, page.h, 0, b);
                    rr.add(b);
                    hh.add(b);
                }
                if (index >= 0 && r == all.get(index)) {
                    bounds.highlight = hh.toArray(new Rect[0]);
                }
            }
            bounds.rr = rr.toArray(new Rect[0]);
            t.close();
            p.close();
            return bounds;
        }

        @Override
        public int getCount() {
            return all.size();
        }

        @Override
        public int next() {
            if (all.size() == 0)
                return -1;
            if (index == -1 && page != -1) {
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).page >= page) {
                        index = i;
                        return all.get(i).page;
                    }
                }
            }
            index++;
            if (index >= all.size()) {
                for (int i = all.get(index - 1).page + 1; i < pdfium.getPagesCount(); i++) {
                    all.addAll(search(i));
                    if (index < all.size())
                        return all.get(index).page;
                }
                index = all.size() - 1;
            }
            return all.get(index).page;
        }

        @Override
        public int prev() {
            if (all.size() == 0)
                return -1;
            if (index == -1 && page != -1) {
                for (int i = all.size() - 1; i >= 0; i--) {
                    if (all.get(i).page <= page) {
                        for (; i >= 0 && all.get(i).page == page; i--)
                            index = i;
                        return all.get(index).page;
                    }
                }
            }
            index--;
            if (index < 0) {
                SearchResult r = all.get(0);
                for (int i = r.page - 1; i > 0; i--) {
                    all.addAll(0, search(i));
                    index = all.indexOf(r) - 1;
                    if (index >= 0)
                        return all.get(index).page;
                }
                index = 0;
            }
            return all.get(index).page;
        }

        @Override
        public void setPage(int page) {
            this.page = page;
            if (str == null || str.isEmpty())
                return;
            for (int i = 0; i < pdfium.getPagesCount(); i++) {
                all.addAll(search(View.Selection.odd(page, i, pdfium.getPagesCount())));
                if (all.size() > 0)
                    return;
            }
        }

        @Override
        public void close() {
        }
    }

    @TargetApi(21)
    public static class NativePage extends Page {
        public PdfRenderer doc;
        public PdfRenderer.Page page;

        public NativePage(NativePage r) {
            super(r);
            doc = r.doc;
        }

        public NativePage(NativePage r, ZLViewEnums.PageIndex index, int w, int h) {
            this(r);
            this.w = w;
            this.h = h;
            load(index);
            if (index == ZLViewEnums.PageIndex.current) {
                load();
                renderPage();
            }
        }

        public NativePage(PdfRenderer d) {
            doc = d;
        }

        @Override
        public int getPagesCount() {
            return doc.getPageCount();
        }

        public void load() {
            if (page != null)
                page.close();
            page = doc.openPage(pageNumber);
            pageBox = new Box(0, 0, page.getWidth(), page.getHeight());
        }
    }

    @TargetApi(21)
    public static class NativeView extends View {
        public PdfRenderer doc;

        public NativeView(ZLFile f) {
            try {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(f.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
                doc = new PdfRenderer(fd);
                current = new NativePage(doc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            doc.close();
        }

        @Override
        public void draw(Canvas canvas, int w, int h, ZLView.PageIndex index, Bitmap.Config c) {
            NativePage r = new NativePage((NativePage) current, index, w, h);
            if (index == ZLViewEnums.PageIndex.current)
                current.updatePage(r);

            r.scale(w, h);
            RenderRect render = r.renderRect();

            Bitmap bm = Bitmap.createBitmap(r.pageBox.w, r.pageBox.h, c);
            bm.eraseColor(FBReaderView.PAGE_PAPER_COLOR);
            r.page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            canvas.drawBitmap(bm, render.toRect(bm.getWidth(), bm.getHeight()), render.dst, paint);
            bm.recycle();
            r.page.close();
            r.page = null;
        }

    }

    public static class PdfiumPage extends Page {
        public Pdfium doc;

        public PdfiumPage(PdfiumPage r) {
            super(r);
            doc = r.doc;
        }

        public PdfiumPage(PdfiumPage r, ZLViewEnums.PageIndex index, int w, int h) {
            this(r);
            this.w = w;
            this.h = h;
            load(index);
            if (index == ZLViewEnums.PageIndex.current) {
                load();
                renderPage();
            }
        }

        public PdfiumPage(Pdfium d, int page, int w, int h) {
            this.doc = d;
            this.w = w;
            this.h = h;
            pageNumber = page;
            pageOffset = 0;
            load();
            renderPage();
        }

        public PdfiumPage(Pdfium d) {
            doc = d;
            load();
        }

        @Override
        public int getPagesCount() {
            return doc.getPagesCount();
        }

        public void load() {
            load(pageNumber);
        }

        void load(int index) {
            Pdfium.Size s = doc.getPageSize(index);
            pageBox = new Box(0, 0, s.width, s.height);
            dpi = 72; // default Pdifium resolution
        }
    }

    public static class PdfiumView extends View {
        ParcelFileDescriptor fd;
        public Pdfium doc;

        public PdfiumView(ZLFile f) {
            try {
                doc = new Pdfium();
                fd = ParcelFileDescriptor.open(new File(f.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
                doc.open(fd.getFileDescriptor());
                current = new PdfiumPage(doc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            doc.close();
            try {
                fd.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Page getPageInfo(int w, int h, ScrollWidget.ScrollAdapter.PageCursor c) {
            int page;
            if (c.start == null)
                page = c.end.getParagraphIndex() - 1;
            else
                page = c.start.getParagraphIndex();
            return new PdfiumPage(doc, page, w, h);
        }

        @Override
        public Bitmap render(int w, int h, int page, Bitmap.Config c) {
            PdfiumPage r = new PdfiumPage(doc, page, w, h);
            r.scale(w * 2, h * 2);
            Bitmap bm = Bitmap.createBitmap(r.pageBox.w, r.pageBox.h, c);
            Pdfium.Page p = doc.openPage(r.pageNumber);
            p.render(bm, 0, 0, bm.getWidth(), bm.getHeight());
            p.close();
            bm.setDensity(r.dpi);
            return bm;
        }

        @Override
        public void draw(Canvas canvas, int w, int h, ZLView.PageIndex index, Bitmap.Config c) {
            PdfiumPage r = new PdfiumPage((PdfiumPage) current, index, w, h);
            if (index == ZLViewEnums.PageIndex.current)
                current.updatePage(r);
            r.scale(w, h);
            RenderRect render = r.renderRect();
            Pdfium.Page p = doc.openPage(r.pageNumber);
            Bitmap bm = Bitmap.createBitmap(r.pageBox.w, r.pageBox.h, c);
            bm.eraseColor(FBReaderView.PAGE_PAPER_COLOR);
            p.render(bm, 0, 0, bm.getWidth(), bm.getHeight());
            p.close();
            canvas.drawBitmap(bm, render.toRect(bm.getWidth(), bm.getHeight()), render.dst, paint);
            bm.recycle();
        }

        @Override
        public Selection select(Selection.Page page, Selection.Point point) {
            SelectionPage start = new SelectionPage(doc, page);
            if (start.count > 0) {
                PDFPlugin.Selection s = new PDFPlugin.Selection(doc, start, point);
                if (s.isEmpty()) {
                    s.close();
                    return null;
                }
                return s;
            }
            start.close();
            return null;
        }

        @Override
        public Selection select(ZLTextPosition start, ZLTextPosition end) {
            PDFPlugin.Selection s = new PDFPlugin.Selection(doc, start, end);
            if (s.isEmpty()) {
                s.close();
                return null;
            }
            return s;
        }

        @Override
        public Selection select(int page) {
            PDFPlugin.Selection s = new PDFPlugin.Selection(doc, page);
            if (s.isEmpty()) {
                s.close();
                return null;
            }
            return s;
        }

        @Override
        public Link[] getLinks(Selection.Page page) {
            Pdfium.Page p = doc.openPage(page.page);
            Pdfium.Link[] ll = p.getLinks();
            Link[] rr = new Link[ll.length];
            for (int i = 0; i < ll.length; i++) {
                Pdfium.Link l = ll[i];
                rr[i] = new Link(l.uri, l.index, p.toDevice(0, 0, page.w, page.h, 0, l.bounds));
            }
            return rr;
        }

        @Override
        public Search search(String text) {
            PdfSearch s = new PdfSearch(doc, text);
            for (int i = 0; i < doc.getPagesCount(); i++) {
                if (s.hasText(i))
                    return s;
            }
            s.close();
            return null;
        }

    }

    public static class PDFTextModel extends PdfiumView implements ZLTextModel {
        public PDFTextModel(ZLFile f) {
            super(f);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getLanguage() {
            return null;
        }

        @Override
        public int getParagraphsNumber() {
            return doc.getPagesCount();
        }

        @Override
        public ZLTextParagraph getParagraph(int index) {
            return new ZLTextParagraph() {
                @Override
                public EntryIterator iterator() {
                    return null;
                }

                @Override
                public byte getKind() {
                    return Kind.END_OF_TEXT_PARAGRAPH;
                }
            };
        }

        @Override
        public void removeAllMarks() {
        }

        @Override
        public ZLTextMark getFirstMark() {
            return null;
        }

        @Override
        public ZLTextMark getLastMark() {
            return null;
        }

        @Override
        public ZLTextMark getNextMark(ZLTextMark position) {
            return null;
        }

        @Override
        public ZLTextMark getPreviousMark(ZLTextMark position) {
            return null;
        }

        @Override
        public List<ZLTextMark> getMarks() {
            return new ArrayList<>();
        }

        @Override
        public int getTextLength(int index) {
            return index; // index - page
        }

        @Override
        public int findParagraphByTextLength(int length) {
            return 0;
        }

        @Override
        public int search(String text, int startIndex, int endIndex, boolean ignoreCase) {
            return 0;
        }
    }

    public PDFPlugin(Storage.Info info) {
        super(info, EXT);
    }

    @Override
    public View create(Storage.FBook fbook) {
        return new PDFPlugin.PdfiumView(BookUtil.fileByBook(fbook.book));
    }

    @Override
    public void readMetainfo(AbstractBook book) throws BookReadingException {
        ZLFile f = BookUtil.fileByBook(book);
        try {
            Pdfium doc = new Pdfium();
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(f.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            doc.open(fd.getFileDescriptor());
            book.addAuthor(doc.getMeta(Pdfium.META_AUTHOR));
            book.setTitle(doc.getMeta(Pdfium.META_TITLE));
            doc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readUids(AbstractBook book) throws BookReadingException {
    }

    @Override
    public void detectLanguageAndEncoding(AbstractBook book) throws BookReadingException {
    }

    @Override
    public ZLImage readCover(ZLFile f) {
        PdfiumView view = new PdfiumView(f);
        view.current.scale(CacheImagesAdapter.COVER_SIZE, CacheImagesAdapter.COVER_SIZE); // reduce render memory footprint
        Bitmap bm = Bitmap.createBitmap(view.current.pageBox.w, view.current.pageBox.h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bm);
        view.drawWallpaper(canvas);
        view.draw(canvas, bm.getWidth(), bm.getHeight(), ZLViewEnums.PageIndex.current);
        view.close();
        return new ZLBitmapImage(bm);
    }

    @Override
    public String readAnnotation(ZLFile file) {
        return null;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public EncodingCollection supportedEncodings() {
        return null;
    }

    @Override
    public void readModel(BookModel model) throws BookReadingException {
        PDFTextModel m = new PDFTextModel(BookUtil.fileByBook(model.Book));
        model.setBookTextModel(m);
        Pdfium.Bookmark[] bookmarks = m.doc.getTOC();
        loadTOC(0, 0, bookmarks, model.TOCTree);
    }

    int loadTOC(int pos, int level, Pdfium.Bookmark[] bb, TOCTree tree) {
        int count = 0;
        TOCTree last = null;
        for (int i = pos; i < bb.length; ) {
            Pdfium.Bookmark b = bb[i];
            String tt = b.title;
            if (tt == null || tt.isEmpty())
                continue;
            if (b.level > level) {
                int c = loadTOC(i, b.level, bb, last);
                i += c;
                count += c;
            } else if (b.level < level) {
                break;
            } else {
                TOCTree t = new TOCTree(tree);
                t.setText(tt);
                t.setReference(null, b.page);
                last = t;
                i++;
                count++;
            }
        }
        return count;
    }
}
