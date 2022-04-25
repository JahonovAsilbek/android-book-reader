package com.github.axet.bookreader.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.github.axet.androidlibrary.app.RarSAF;
import com.github.axet.androidlibrary.app.ZipSAF;
import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.bookreader.widgets.ScrollWidget;

import net.lingala.zip4j.ZipFile;

import org.apache.commons.io.IOUtils;
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
import org.geometerplus.zlibrary.ui.android.image.ZLBitmapImage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.NativeStorage;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.FileHeader;

public class ComicsPlugin extends BuiltinFormatPlugin implements Plugin {
    public static String TAG = ComicsPlugin.class.getSimpleName();

    public static final String CBZ = "cbz";
    public static final String CBR = "cbr";

    public static boolean isImage(ArchiveFile a) {
        File f = new File(a.getPath());
        return CacheImagesAdapter.isImage(f.getName());
    }

    public static Box getImageSize(InputStream is) {
        try {
            Rect size = CacheImagesAdapter.getImageSize(is);
            is.close();
            if (size == null)
                return null;
            return new Box(0, 0, size.width(), size.height());
        } catch (IOException e) {
            Log.d(TAG, "unable to close is", e);
            return null;
        }
    }

    public static class ArchiveToc {
        public int level;
        public String name;
        public int page;

        public ArchiveToc(String name, int page, int level) {
            this.name = name;
            this.page = page;
            this.level = level;
        }
    }

    public static class SortByName implements Comparator<ArchiveFile> {
        @Override
        public int compare(ArchiveFile o1, ArchiveFile o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    }

    public static class Decoder {
        public ArrayList<ArchiveToc> toc;
        public ArrayList<ArchiveFile> pages;

        public Decoder() {
        }

        public Bitmap render(int p, Bitmap.Config c) {
            ArchiveFile f = pages.get(p);
            try {
                InputStream is = f.open();
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inPreferredConfig = c;
                Bitmap bm = BitmapFactory.decodeStream(is, null, op);
                is.close();
                return bm;
            } catch (IOException e) {
                Log.d(TAG, "closing stream", e);
                return null;
            }
        }

        void load(File file) {
            pages = list(file);
            if (pages.size() == 0)
                throw new RuntimeException("no comics found!");
            Collections.sort(pages, new SortByName());
            loadTOC();
        }

        ArrayList<ArchiveFile> list(File file) {
            return null;
        }

        void loadTOC() {
            String last = "";
            ArrayList<ArchiveToc> toc = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                ArchiveFile p = pages.get(i);
                File f = new File(p.getPath());
                File n = f.getParentFile();
                if (n != null) {
                    String fn = n.getName();
                    int level = n.getPath().split(Pattern.quote(File.separator)).length - 1;
                    if (!last.equals(fn)) {
                        toc.add(new ArchiveToc(fn, i, level));
                        last = fn;
                    }
                }
            }
            if (toc.size() > 1)
                this.toc = toc;
        }

        void clear() {
        }

        void close() {
        }
    }

    public interface ArchiveFile {
        String getPath();

        InputStream open() throws IOException;

        void copy(OutputStream os) throws IOException;

        long getLength();

        Box getRect();
    }

    public static class RarDecoder extends Decoder {
        ArrayList<Archive> aa = new ArrayList<>();

        public RarDecoder(File file) {
            load(file);
        }

        @Override
        public ArrayList<ArchiveFile> list(File file) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                final Archive archive = new Archive(new NativeStorage(file));
                List<FileHeader> list = archive.getFileHeaders();
                for (FileHeader h : list) {
                    if (h.isDirectory())
                        continue;
                    final FileHeader header = h;
                    ArchiveFile a = new ArchiveFile() {
                        Box r = null;

                        @Override
                        public Box getRect() {
                            try {
                                if (r == null)
                                    r = getImageSize(open());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return r;
                        }

                        @Override
                        public String getPath() {
                            return RarSAF.getRarFileName(header);
                        }

                        @Override
                        public InputStream open() throws IOException {
                            return new ParcelFileDescriptor.AutoCloseInputStream(new StorageProvider.ParcelInputStream() {
                                @Override
                                public void copy(OutputStream os) throws IOException {
                                    try {
                                        archive.extractFile(header, os);
                                    } catch (RarException e) {
                                        throw new IOException(e);
                                    }
                                }

                                @Override
                                public long getStatSize() {
                                    return header.getFullUnpackSize();
                                }
                            });
                        }

                        @Override
                        public void copy(OutputStream os) throws IOException {
                            try {
                                archive.extractFile(header, os);
                            } catch (RarException e) {
                                throw new IOException(e);
                            }
                        }

                        @Override
                        public long getLength() {
                            return header.getFullUnpackSize();
                        }
                    };
                    if (isImage(a))
                        ff.add(a);
                }
                return ff;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clear() {
            try {
                for (Archive a : aa)
                    a.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            aa.clear();
        }
    }

    public static class ZipDecoder extends Decoder {
        ArrayList<ZipFile> aa = new ArrayList<>();

        public ZipDecoder(File file) {
            load(file);
        }

        @Override
        public ArrayList<ArchiveFile> list(File file) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                final ZipFile zip = new ZipFile(new net.lingala.zip4j.NativeStorage(file));
                aa.add(zip);
                List list = zip.getFileHeaders();
                for (Object o : list) {
                    final net.lingala.zip4j.model.FileHeader zipEntry = (net.lingala.zip4j.model.FileHeader) o;
                    if (zipEntry.isDirectory())
                        continue;
                    ArchiveFile a = new ArchiveFile() {
                        Box r = null;

                        @Override
                        public Box getRect() {
                            if (r == null)
                                r = getImageSize(open());
                            return r;
                        }

                        @Override
                        public String getPath() {
                            return zipEntry.getFileName();
                        }

                        @Override
                        public InputStream open() {
                            try {
                                return new ZipSAF.ZipInputStreamSafe(zip.getInputStream(zipEntry));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        public void copy(OutputStream os) {
                            try {
                                InputStream is = zip.getInputStream(zipEntry);
                                IOUtils.copy(is, os);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public long getLength() {
                            return zipEntry.getUncompressedSize();
                        }
                    };
                    if (isImage(a))
                        ff.add(a);
                }
                return ff;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clear() {
            aa.clear();
        }
    }

    public static class ComicsPage extends Page {
        public Decoder doc;

        public ComicsPage(ComicsPage r) {
            super(r);
            doc = r.doc;
        }

        public ComicsPage(ComicsPage r, ZLViewEnums.PageIndex index, int w, int h) {
            this(r);
            this.w = w;
            this.h = h;
            load(index);
            if (index == ZLViewEnums.PageIndex.current) {
                load();
                renderPage();
            }
        }

        public ComicsPage(Decoder d, int page, int w, int h) {
            this.doc = d;
            this.w = w;
            this.h = h;
            pageNumber = page;
            pageOffset = 0;
            load();
            renderPage();
        }

        public ComicsPage(Decoder d) {
            doc = d;
            load();
        }

        public void load() {
            ArchiveFile f = doc.pages.get(pageNumber);
            pageBox = f.getRect();
            if (pageBox == null)
                pageBox = new Box(0, 0, 100, 100);
            dpi = 72;
        }

        @Override
        public int getPagesCount() {
            return doc.pages.size();
        }
    }

    public static class ComicsView extends View {
        public Decoder doc;

        public ComicsView(ZLFile f) {
            File file = new File(f.getPath());
            if (file.getPath().toLowerCase().endsWith("." + CBZ))
                doc = new ZipDecoder(file);
            if (file.getPath().toLowerCase().endsWith("." + CBR))
                doc = new RarDecoder(file);
            current = new ComicsPage(doc);
        }

        @Override
        public Page getPageInfo(int w, int h, ScrollWidget.ScrollAdapter.PageCursor c) {
            int page;
            if (c.start == null)
                page = c.end.getParagraphIndex() - 1;
            else
                page = c.start.getParagraphIndex();
            return new ComicsPage(doc, page, w, h);
        }

        @Override
        public Bitmap render(int w, int h, int page, Bitmap.Config c) {
            ComicsPage r = new ComicsPage(doc, page, w, h);
            Bitmap bm = doc.render(r.pageNumber, c);
            bm.setDensity(r.dpi);
            return bm;
        }

        @Override
        public void draw(Canvas canvas, int w, int h, ZLView.PageIndex index, Bitmap.Config c) {
            ComicsPage r = new ComicsPage((ComicsPage) current, index, w, h);
            if (index == ZLViewEnums.PageIndex.current)
                current.updatePage(r);
            RenderRect render = r.renderRect();
            Bitmap bm = doc.render(r.pageNumber, c);
            if (bm != null) {
                canvas.drawBitmap(bm, render.toRect(r.pageBox.w, r.pageBox.h), render.dst, paint);
                bm.recycle();
            }
        }
    }

    public static class ComicsTextModel extends ComicsView implements ZLTextModel {
        public ComicsTextModel(ZLFile f) {
            super(f);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            doc.close();
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
            return doc.pages.size();
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

    public ComicsPlugin(Storage.Info info) {
        super(info, CBZ);
    }

    @Override
    public View create(Storage.FBook fbook) {
        return new ComicsPlugin.ComicsView(BookUtil.fileByBook(fbook.book));
    }

    @Override
    public void readMetainfo(AbstractBook book) throws BookReadingException {
    }

    @Override
    public void readUids(AbstractBook book) throws BookReadingException {
    }

    @Override
    public void detectLanguageAndEncoding(AbstractBook book) throws BookReadingException {
    }

    @Override
    public ZLImage readCover(ZLFile file) {
        ComicsView view = new ComicsView(file);
        int m = Math.max(view.current.pageBox.w, view.current.pageBox.h);
        double ratio = CacheImagesAdapter.COVER_SIZE / (double) m;
        int w = (int) (view.current.pageBox.w * ratio);
        int h = (int) (view.current.pageBox.h * ratio);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
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
        ComicsTextModel m = new ComicsTextModel(BookUtil.fileByBook(model.Book));
        model.setBookTextModel(m);
        if (m.doc.toc == null)
            return;
        loadTOC(0, 0, m.doc.toc, model.TOCTree);
    }

    int loadTOC(int pos, int level, ArrayList<ArchiveToc> bb, TOCTree tree) {
        int count = 0;
        TOCTree last = null;
        for (int i = pos; i < bb.size(); ) {
            ArchiveToc b = bb.get(i);
            String tt = b.name;
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
