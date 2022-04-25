package com.github.axet.bookreader.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.SparseArray;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.bookreader.widgets.ScrollWidget;
import com.github.axet.djvulibre.Config;

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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DjvuPlugin extends BuiltinFormatPlugin implements Plugin {
    public static String TAG = DjvuPlugin.class.getSimpleName();

    public static final String EXT = "djvu";

    public static int[] TYPES = new int[]{DjvuLibre.ZONE_CHARACTER, DjvuLibre.ZONE_WORD, DjvuLibre.ZONE_LINE,
            DjvuLibre.ZONE_PARAGRAPH, DjvuLibre.ZONE_REGION, DjvuLibre.ZONE_COLUMN,
            DjvuLibre.ZONE_PAGE};

    public static DjvuPlugin create(Storage.Info info) {
        if (Config.natives) {
            Natives.loadLibraries(info.context, "djvu", "djvulibrejni");
            Config.natives = false;
        }
        return new DjvuPlugin(info);
    }

    public static View.Selection.Point toPage(DjvuLibre.Page info, int w, int h, View.Selection.Point point) {
        return new View.Selection.Point(point.x * info.width / w, info.height - point.y * info.height / h);
    }

    public static View.Selection.Point toDevice(DjvuLibre.Page info, int w, int h, View.Selection.Point point) {
        return new View.Selection.Point(point.x * w / info.width, (info.height - point.y) * h / info.height);
    }

    public static Rect toDevice(DjvuLibre.Page info, int w, int h, Rect rect) {
        View.Selection.Point p1 = toDevice(info, w, h, new View.Selection.Point(rect.left, rect.top));
        View.Selection.Point p2 = toDevice(info, w, h, new View.Selection.Point(rect.right, rect.bottom));
        return new Rect(p1.x, p2.y, p2.x, p1.y);
    }

    public static class DjvuLibre extends com.github.axet.djvulibre.DjvuLibre {
        SparseArray<DjvuLibre.Page> cache = new SparseArray<>();

        public DjvuLibre(FileDescriptor fd) {
            super(fd);
        }

        @Override
        public Page getPageInfo(int index) {
            DjvuLibre.Page p = cache.get(index);
            if (p == null) {
                p = super.getPageInfo(index);
                cache.put(index, p);
            }
            return p;
        }
    }

    public static class SelectionPage {
        public DjvuLibre.Page info;
        public int page;
        public int w;
        public int h;
        public int index = -1;
        public DjvuLibre.Text text;

        public SelectionPage() {
        }

        public SelectionPage(SelectionPage p) {
            this.page = p.page;
            this.info = p.info;
            this.w = p.w;
            this.h = p.h;
            this.index = p.index;
            this.text = p.text;
        }

        String getText(int b) {
            return text.text[b];
        }

        int find(View.Selection.Point point) {
            if (text == null)
                return -1;
            for (int i = 0; i < text.bounds.length; i++) {
                Rect b = text.bounds[i];
                if (b.contains(point.x, point.y))
                    return i;
            }
            return -1;
        }

        int first() {
            return 0;
        }

        int last() {
            return text.bounds.length - 1;
        }
    }

    public static class DjvuSelection extends View.Selection {
        DjvuLibre doc;

        SelectionPage start;
        SelectionPage end;

        SparseArray<SelectionPage> map = new SparseArray<>();

        public class SelectionBounds {
            SelectionPage page; // current

            SelectionPage s;
            SelectionPage e;

            int ss; // start index
            int ee; // end index
            int ll; // last index

            boolean first;
            boolean last;

            boolean reverse;

            public SelectionBounds(View.Selection.Page p) {
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
                    ee = e.index;
                    first = true;
                    last = true;
                    if (reverse)
                        ss++;
                } else if (s.page == p) {
                    page = s;
                    ss = s.index;
                    ee = s.last();
                    first = true;
                    last = false;
                    if (reverse)
                        ss++;
                } else if (e.page == p) {
                    page = e;
                    ss = e.first();
                    ee = e.index;
                    first = false;
                    last = true;
                } else {
                    page = new SelectionPage(open(p));
                    ss = page.first();
                    ee = page.last();
                    first = false;
                    last = false;
                }
                ll = ee;
                ee++;
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

            String getText() {
                StringBuilder bb = new StringBuilder();
                for (int b = ss; b != ee; b++)
                    bb.append(page.getText(b));
                return bb.toString();
            }
        }

        public DjvuSelection(DjvuLibre doc, Page page, Point point) {
            this.doc = doc;
            SelectionPage p = open(page);
            point = toPage(p.info, page.w, page.h, point);
            selectWord(p, point);
        }

        public DjvuSelection(DjvuLibre doc, ZLTextPosition start, ZLTextPosition end) {
            this.doc = doc;
            this.start = open(start.getParagraphIndex());
            this.start.index = start.getElementIndex();
            this.end = open(end.getParagraphIndex());
            this.end.index = end.getElementIndex();
        }

        public DjvuSelection(DjvuLibre doc, int page) {
            this.doc = doc;
            this.start = open(page);
            this.start.index = 0;
            this.end = open(page);
            this.end.index = end.text.text.length;
        }

        SelectionPage open(Page page) {
            SelectionPage pp = open(page.page);
            pp.w = page.w;
            pp.h = page.h;
            return new SelectionPage(pp);
        }

        SelectionPage open(int page) {
            SelectionPage pp = map.get(page);
            if (pp == null) {
                pp = new SelectionPage();
                map.put(page, pp);

                pp.page = page;
                pp.info = doc.getPageInfo(page);

                for (int type : TYPES) {
                    pp.text = doc.getText(page, type);
                    if (pp.text != null && pp.text.bounds.length != 0)
                        break;
                }
            }
            return new SelectionPage(pp);
        }

        public boolean isEmpty() {
            return start == null || end == null;
        }

        boolean isWord(SelectionPage pp, int start, int b) {
            if (start == -1) {
                String s = pp.getText(b);
                for (char c : s.toCharArray()) {
                    if (isWord(c))
                        return true;
                }
            } else {
                String s = pp.getText(b);
                for (char c : s.toCharArray()) {
                    if (!isWord(c))
                        return false;
                }
            }
            return false;
        }

        void selectWord(SelectionPage pp, Point point) {
            int b = pp.find(point);
            if (b == -1)
                return;
            SelectionPage start = new SelectionPage(pp);
            int s = b;
            while (s != -1 && isWord(start, start.index, s)) {
                start.index = s;
                s++;
            }
            SelectionPage end = new SelectionPage(pp);
            int e = b;
            while (e != -1 && isWord(end, end.index, e)) {
                end.index = e;
                e++;
            }
            if (start.index == -1 || end.index == -1)
                return;
            this.start = start;
            this.end = end;
        }

        @Override
        public void setStart(Page page, Point point) {
            SelectionPage pp = open(page);
            point = toPage(pp.info, page.w, page.h, point);
            int b = pp.find(point);
            if (b == -1)
                return;
            pp.index = b;
            start = pp;
        }

        @Override
        public void setEnd(Page page, Point point) {
            SelectionPage pp = open(page);
            point = toPage(pp.info, page.w, page.h, point);
            int b = pp.find(point);
            if (b == -1)
                return;
            pp.index = b;
            end = pp;
        }

        @Override
        public String getText() {
            SelectionBounds b = new SelectionBounds();
            StringBuilder text = new StringBuilder();
            for (int i = b.s.page; i <= b.e.page; i++) {
                text.append(getText(i));
            }
            return text.toString();
        }

        String getText(int i) {
            SelectionBounds b = new SelectionBounds(i);
            return b.getText();
        }

        @Override
        public Rect[] getBoundsAll(Page page) {
            SelectionPage pp = open(page);
            Rect[] rr = new Rect[pp.text.bounds.length];
            for (int i = 0; i < rr.length; i++)
                rr[i] = toDevice(pp.info, page.w, page.h, pp.text.bounds[i]);
            return rr;
        }

        @Override
        public Bounds getBounds(Page p) {
            Bounds bounds = new Bounds();
            SelectionBounds b = new SelectionBounds(p);
            bounds.reverse = b.reverse;
            bounds.start = b.first;
            bounds.end = b.last;
            ArrayList<Rect> rr = new ArrayList<>();
            for (int i = b.ss; i != b.ee; i++)
                rr.add(toDevice(b.page.info, b.page.w, b.page.h, b.page.text.bounds[i]));
            bounds.rr = rr.toArray(new Rect[0]);
            return bounds;
        }

        @Override
        public Boolean inBetween(Page page, Point start, Point end) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.s.page < page.page && page.page < b.e.page)
                return true;
            Point p1 = toPage(b.page.info, page.w, page.h, start);
            int i1 = b.page.find(p1);
            if (i1 == -1)
                return null;
            Point p2 = toPage(b.page.info, page.w, page.h, end);
            int i2 = b.page.find(p2);
            if (i2 == -1)
                return null;
            if (i2 < i1)
                return null; // document incorrectly marked (last symbol appears at the end of page)
            return i1 <= b.ss && b.ss <= i2 || i1 <= b.ll && b.ll <= i2;
        }

        @Override
        public boolean isValid(Page page, Point point) {
            SelectionPage pp = open(page);
            point = toPage(pp.info, page.w, page.h, point);
            return pp.find(point) != -1;
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
            point = toPage(b.page.info, page.w, page.h, point);
            int index = b.page.find(point);
            if (index == -1)
                return null;
            return b.ss < index || b.ll < index;
        }

        @Override
        public Boolean isBelow(Page page, Point point) {
            SelectionBounds b = new SelectionBounds(page);
            if (b.e.page > page.page)
                return true;
            point = toPage(b.page.info, page.w, page.h, point);
            int index = b.page.find(point);
            if (index == -1)
                return null;
            return index < b.ss || index < b.ll;
        }

        @Override
        public void close() {
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

    public static class DjvuSearch extends View.Search {
        DjvuLibre doc;
        ArrayList<SearchResult> all = new ArrayList<>();
        int index;
        String str;
        int page; // inital page to show
        SparseArray<SearchPage> pages = new SparseArray<>();

        public static class SearchPage {
            int page;
            DjvuLibre.Text text;
            DjvuLibre.Page info;
            ArrayList<SearchResult> rr = new ArrayList<>();
            ArrayList<SearchMap> map = new ArrayList<>();

            int find(int i) {
                for (SearchMap p : map) {
                    if (i >= p.start && i < p.end)
                        return p.index;
                }
                return -1;
            }
        }

        public static class SearchResult {
            public int page;
            public int start;
            public int end;

            public SearchResult(int p, int i, int e) {
                page = p;
                start = i;
                end = e;
            }
        }

        public static class SearchMap {
            int index;
            int start;
            int end;

            public SearchMap(int index, int s, int e) {
                this.index = index;
                this.start = s;
                this.end = e;
            }
        }

        public DjvuSearch(DjvuLibre doc, String str) {
            this.doc = doc;
            this.index = -1;
            this.page = -1;
            this.str = str;
        }

        boolean hasText(int page) {
            for (int type : TYPES) {
                DjvuLibre.Text text = doc.getText(page, type);
                if (text != null && text.bounds.length != 0)
                    return true;
            }
            return false;
        }

        SearchPage search(int page) {
            SearchPage pp = pages.get(page);
            if (pp != null)
                return pp;
            pp = new SearchPage();
            pages.put(page, pp);
            pp.page = page;
            pp.info = doc.getPageInfo(page);
            for (int type : TYPES) {
                pp.text = doc.getText(page, type);
                if (pp.text != null && pp.text.bounds.length != 0)
                    break;
            }
            if (pp.text == null)
                return pp;
            String find = str.toLowerCase(Locale.US);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < pp.text.text.length; i++) {
                int s = b.length();
                b.append(pp.text.text[i]);
                int e = b.length();
                pp.map.add(new SearchMap(i, s, e));
            }
            String str = b.toString();
            str = str.toLowerCase(Locale.US);
            int start = str.indexOf(find);
            while (start != -1) {
                int end = start + find.length();
                SearchResult ss = new SearchResult(page, pp.find(start), pp.find(end) + 1);
                pp.rr.add(ss);
                start = str.indexOf(find, start + 1);
            }
            return pp;
        }

        @Override
        public Bounds getBounds(View.Selection.Page page) {
            Bounds bounds = new Bounds();
            SearchPage p = pages.get(page.page);
            if (p == null)
                return null;
            ArrayList<Rect> rr = new ArrayList<>();
            for (int i = 0; i < p.rr.size(); i++) {
                SearchResult r = p.rr.get(i);
                ArrayList<Rect> hh = new ArrayList<>();
                for (int k = r.start; k < r.end; k++) {
                    Rect b = toDevice(p.info, page.w, page.h, p.text.bounds[k]);
                    rr.add(b);
                    hh.add(b);
                }
                if (index >= 0 && r == all.get(index))
                    bounds.highlight = hh.toArray(new Rect[0]);
            }
            bounds.rr = rr.toArray(new Rect[0]);
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
                for (int i = all.get(index - 1).page + 1; i < doc.getPagesCount(); i++) {
                    all.addAll(search(i).rr);
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
                    all.addAll(0, search(i).rr);
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
            for (int i = 0; i < doc.getPagesCount(); i++) {
                all.addAll(search(PDFPlugin.Selection.odd(page, i, doc.getPagesCount())).rr);
                if (all.size() != 0)
                    return;
            }
        }

        @Override
        public void close() {
        }
    }

    public static class DjvuPage extends Page {
        public DjvuLibre doc;

        public DjvuPage(DjvuPage r) {
            super(r);
            doc = r.doc;
        }

        public DjvuPage(DjvuPage r, ZLViewEnums.PageIndex index, int w, int h) {
            this(r);
            this.w = w;
            this.h = h;
            load(index);
            if (index == ZLViewEnums.PageIndex.current) {
                load();
                renderPage();
            }
        }

        public DjvuPage(DjvuLibre d, int page, int w, int h) {
            this.doc = d;
            this.w = w;
            this.h = h;
            pageNumber = page;
            pageOffset = 0;
            load();
            renderPage();
        }

        public DjvuPage(DjvuLibre d) {
            doc = d;
            load();
        }

        public void load() {
            DjvuLibre.Page p = doc.getPageInfo(pageNumber);
            pageBox = new Box(0, 0, p.width, p.height);
            dpi = p.dpi;
        }

        @Override
        public int getPagesCount() {
            return doc.getPagesCount();
        }
    }

    public static class DjvuView extends View {
        public DjvuLibre doc;
        FileInputStream is;

        public DjvuView(ZLFile f) {
            try {
                is = new FileInputStream(new File(f.getPath()));
                doc = new DjvuLibre(is.getFD());
                current = new DjvuPage(doc);
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
            return new DjvuPage(doc, page, w, h);
        }

        @Override
        public Bitmap render(int w, int h, int page, Bitmap.Config c) {
            DjvuPage r = new DjvuPage(doc, page, w, h);
            r.scale(w * 2, h * 2);
            Bitmap bm = Bitmap.createBitmap(r.pageBox.w, r.pageBox.h, c);
            doc.renderPage(bm, r.pageNumber, 0, 0, r.pageBox.w, r.pageBox.h, 0, 0, r.pageBox.w, r.pageBox.h);
            bm.setDensity(r.dpi);
            return bm;
        }

        @Override
        public void draw(Canvas canvas, int w, int h, ZLView.PageIndex index, Bitmap.Config c) {
            DjvuPage r = new DjvuPage((DjvuPage) current, index, w, h);
            if (index == ZLViewEnums.PageIndex.current)
                current.updatePage(r);
            r.scale(w, h);
            RenderRect render = r.renderRect();
            Bitmap bm = Bitmap.createBitmap(r.pageBox.w, r.pageBox.h, c);
            bm.eraseColor(FBReaderView.PAGE_PAPER_COLOR);
            doc.renderPage(bm, r.pageNumber, 0, 0, r.pageBox.w, r.pageBox.h, render.x, render.y, render.w, render.h);
            canvas.drawBitmap(bm, render.src, render.dst, paint);
            bm.recycle();
        }

        @Override
        public Selection select(Selection.Page page, Selection.Point point) {
            DjvuSelection s = new DjvuSelection(doc, page, point);
            if (s.isEmpty())
                return null;
            return s;
        }

        @Override
        public Selection select(ZLTextPosition start, ZLTextPosition end) {
            DjvuSelection s = new DjvuSelection(doc, start, end);
            if (s.isEmpty())
                return null;
            return s;
        }

        @Override
        public Selection select(int page) {
            DjvuSelection s = new DjvuSelection(doc, page);
            if (s.isEmpty())
                return null;
            return s;
        }

        @Override
        public Search search(String text) {
            DjvuSearch s = new DjvuSearch(doc, text);
            for (int i = 0; i < doc.getPagesCount(); i++) {
                if (s.hasText(i))
                    return s;
            }
            s.close();
            return null;
        }
    }

    public static class DjvuTextModel extends DjvuView implements ZLTextModel {
        public DjvuTextModel(ZLFile f) {
            super(f);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            doc.close();
            is.close();
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
            return index;
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

    public DjvuPlugin(Storage.Info info) {
        super(info, EXT);
    }

    @Override
    public View create(Storage.FBook fbook) {
        return new DjvuPlugin.DjvuView(BookUtil.fileByBook(fbook.book));
    }

    @Override
    public void readMetainfo(AbstractBook book) throws BookReadingException {
        ZLFile f = BookUtil.fileByBook(book);
        try {
            FileInputStream is = new FileInputStream(f.getPath());
            DjvuLibre doc = new DjvuLibre(is.getFD());
            book.setTitle(doc.getMeta(DjvuLibre.META_TITLE));
            book.addAuthor(doc.getMeta(DjvuLibre.META_AUTHOR));
            doc.close();
            is.close();
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
    public ZLImage readCover(ZLFile file) {
        DjvuView view = new DjvuView(file);
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
        DjvuTextModel m = new DjvuTextModel(BookUtil.fileByBook(model.Book));
        model.setBookTextModel(m);
        DjvuLibre.Bookmark[] bookmarks = m.doc.getBookmarks();
        if (bookmarks == null)
            return;
        loadTOC(0, 0, bookmarks, model.TOCTree);
    }

    int loadTOC(int pos, int level, DjvuLibre.Bookmark[] bb, TOCTree tree) {
        int count = 0;
        TOCTree last = null;
        for (int i = pos; i < bb.length; ) {
            DjvuLibre.Bookmark b = bb[i];
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
