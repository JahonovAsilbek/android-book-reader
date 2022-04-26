package com.github.axet.bookreader.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.app.RarSAF;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.wget.SpeedInfo;

import org.apache.commons.io.IOUtils;
import org.geometerplus.fbreader.book.BookUtil;
import org.geometerplus.fbreader.fbreader.FBView;
import org.geometerplus.fbreader.formats.BookReadingException;
import org.geometerplus.fbreader.formats.FormatPlugin;
import org.geometerplus.fbreader.formats.PluginCollection;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.image.ZLFileImageProxy;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.image.ZLStreamImage;
import org.geometerplus.zlibrary.core.util.SystemInfo;
import org.geometerplus.zlibrary.core.view.ZLPaintContext;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.ui.android.image.ZLBitmapImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.NativeStorage;
import de.innosystec.unrar.rarfile.FileHeader;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static String TAG = Storage.class.getCanonicalName();

    public static final int MD5_SIZE = 32;
    public static final String JSON_EXT = "json";
    public static final String ZIP_EXT = "zip";

    public static FileTypeDetector.Detector[] supported() {
        return new FileTypeDetector.Detector[]{new FileTypeDetector.FileFB2(), new FileTypeDetector.FileFB2Zip(),
                new FileTypeDetector.FileEPUB(), new FileTypeDetector.FileHTML(), new FileTypeDetector.FileHTMLZip(),
                new FileTypeDetector.FilePDF(), new FileTypeDetector.FileDjvu(), new FileTypeDetector.FileRTF(),
                new FileTypeDetector.FileRTFZip(), new FileTypeDetector.FileDoc(), new FileTypeDetector.FileMobi(),
                new FileTypeDetector.FileTxt(), new FileTypeDetector.FileTxtZip(), new FileCbz(), new FileCbr()};
    }

    public static FormatPlugin getPlugin(Storage.Info info, Storage.FBook b) {
        PluginCollection c = PluginCollection.Instance(info);
        ZLFile f = BookUtil.fileByBook(b.book);
        switch (f.getExtension()) {
            case PDFPlugin.EXT:
                return PDFPlugin.create(info);
            case DjvuPlugin.EXT:
                return DjvuPlugin.create(info);
            case ComicsPlugin.CBZ:
            case ComicsPlugin.CBR:
                return new ComicsPlugin(info);
        }
        try {
            return BookUtil.getPlugin(c, b.book);
        } catch (BookReadingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAndroidId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty())
            id = Build.SERIAL;
        return id;
    }

    public static Bitmap renderView(View v) {
        DisplayMetrics m = v.getContext().getResources().getDisplayMetrics();
        int w = (int) (720 * m.density / 2);
        int h = (int) (1280 * m.density / 2);
        int ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        v.measure(ws, hs);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bm);
        v.draw(c);
        return bm;
    }

    public static class Info implements SystemInfo {
        public Context context;

        public Info(Context context) {
            this.context = context;
        }

        @Override
        public String tempDirectory() {
            return context.getCacheDir().getPath();
        }

        @Override
        public String networkCacheDirectory() {
            return context.getCacheDir().getPath();
        }
    }

    public static class Progress {
        public SpeedInfo info = new SpeedInfo();
        public long last;

        public Progress() {
            info.start(0);
        }

        public void update(long read, long total) {
            long time = System.currentTimeMillis();
            if (last + AlarmManager.SEC1 < time) {
                info.step(read);
                last = time;
                progress(read, total);
            }
        }

        public void progress(long read, long total) {
        }
    }

    public static class ProgresInputstream extends InputStream {
        long read;
        long total;
        InputStream is;
        Progress progress;

        public ProgresInputstream(InputStream is, long total, Progress progress) {
            this.is = is;
            this.total = total;
            this.progress = progress;
            this.progress.update(0, total);
        }

        @Override
        public int read() throws IOException {
            read++;
            progress.update(read, total);
            return is.read();
        }
    }

    public static String getTitle(Book book, FBook fbook) {
        String t = fbook.book.getTitle();
        if (t.equals(book.md5))
            t = null;
        return t;
    }

    public static String getTitle(RecentInfo info) {
        String s = "";
        if (info.authors != null && !info.authors.isEmpty())
            s += info.authors;
        if (info.title != null && !info.title.isEmpty()) {
            if (!s.isEmpty())
                s += " - ";
            s += info.title;
        }
        return s;
    }

    public static File coverFile(Context context, Book book) {
        return CacheImagesAdapter.cacheUri(context, book.url);
    }

    public static File recentFile(Book book) {
        File f = getFile(book.url);
        File p = f.getParentFile();
        return new File(p, book.md5 + "." + JSON_EXT);
    }

    public Uri recentUri(Book book) {
        String s = book.url.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id = book.md5 + "." + JSON_EXT;
            Uri doc = Storage.getDocumentParent(context, book.url);
            return child(context, doc, id);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            return Uri.fromFile(recentFile(book));
        } else {
            throw new UnknownUri();
        }
    }

    public List<Uri> recentUris(final Book book) {
        List<Uri> list = new ArrayList<>();
        Uri storage = getStoragePath();
        String s = storage.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(storage, DocumentsContract.getTreeDocumentId(storage));
            Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
            if (childCursor != null) {
                try {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        String e = getExt(t).toLowerCase();
                        if (t.startsWith(book.md5) && e.equals(JSON_EXT)) { // delete all but json
                            Uri k = DocumentsContract.buildDocumentUriUsingTree(storage, id);
                            list.add(k);
                        }
                    }
                } finally {
                    childCursor.close();
                }
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File dir = getFile(storage);
            File[] ff = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    String e = getExt(name).toLowerCase();
                    return name.startsWith(book.md5) && e.equals(JSON_EXT);
                }
            });
            if (ff != null) {
                for (File f : ff)
                    list.add(Uri.fromFile(f));
            }
        } else {
            throw new UnknownUri();
        }
        return list;
    }

    public static ZLTextPosition loadPosition(String s) {
        if (s == null || s.isEmpty())
            return null;
        try {
            JSONArray o = new JSONArray(s);
            return loadPosition(o);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static ZLTextPosition loadPosition(JSONArray a) throws JSONException {
        if (a == null || a.length() == 0)
            return null;
        return new ZLTextFixedPosition(a.getInt(0), a.getInt(1), a.getInt(2));
    }

    public static JSONArray savePosition(ZLTextPosition position) {
        if (position == null)
            return null;
        JSONArray a = new JSONArray();
        a.put(position.getParagraphIndex());
        a.put(position.getElementIndex());
        a.put(position.getCharIndex());
        return a;
    }

    public static class FileCbz extends FileTypeDetector.FileZip { // we not treating all zip archives as comics, ext must be cbz
        public FileCbz() {
            super(ComicsPlugin.CBZ);
        }
    }

    public static class FileCbr extends FileTypeDetector.FileRar { // we not treating all rar archives as comics, ext must be cbr
        public FileCbr() {
            super(ComicsPlugin.CBR);
        }
    }

    public static class FBook {
        public File tmp; // tmp file
        public org.geometerplus.fbreader.book.Book book;
        public RecentInfo info;

        public void close() {
            if (tmp != null) {
                tmp.delete();
                tmp = null;
            }
            book = null;
        }
    }

    public static class Book {
        public Uri url;
        public String ext;
        public String md5; // can be filename if user renamed file
        public RecentInfo info;
        public File cover;

        public Book() {
        }

        public Book(Context context, Uri u) {
            String name = Storage.getName(context, u);
            url = u;
            md5 = Storage.getNameNoExt(name);
            ext = Storage.getExt(name);
        }
    }

    public static class RecentInfo {
        public long created; // date added to the my readings
        public long last; // last write time
        public ZLTextPosition position;
        public String authors;
        public String title;
        public Map<String, ZLPaintContext.ScalingType> scales = new HashMap<>(); // individual scales
        public FBView.ImageFitting scale; // all images
        public Integer fontsize; // FBView size or Reflow / 100
        public Map<String, Integer> fontsizes = new TreeMap<>(); // per device fontsize
        public Bookmarks bookmarks;

        public RecentInfo() {
        }

        public RecentInfo(RecentInfo info) {
            created = info.created;
            last = info.last;
            if (info.position != null)
                position = new ZLTextFixedPosition(info.position);
            authors = info.authors;
            title = info.title;
            scale = info.scale;
            scales = new HashMap<>(info.scales);
            fontsize = info.fontsize;
            if (info.bookmarks != null)
                bookmarks = new Bookmarks(info.bookmarks);
        }

        public RecentInfo(Context context, File f) {
            try {
                FileInputStream is = new FileInputStream(f);
                load(context, is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public RecentInfo(Context context, Uri u) {
            try {
                ContentResolver resolver = context.getContentResolver();
                InputStream is;
                String s = u.getScheme();
                if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    is = resolver.openInputStream(u);
                } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                    is = new FileInputStream(Storage.getFile(u));
                } else {
                    throw new UnknownUri();
                }
                load(context, is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public RecentInfo(Context context, JSONObject o) throws JSONException {
            load(context, o);
        }

        public void load(Context context, InputStream is) throws Exception {
            String json = IOUtils.toString(is, Charset.defaultCharset());
            JSONObject j = new JSONObject(json);
            load(context, j);
            is.close();
        }

        public void load(Context context, JSONObject o) throws JSONException {
            created = o.optLong("created", 0);
            last = o.getLong("last");
            authors = o.optString("authors", null);
            title = o.optString("title", null);
            position = loadPosition(o.optJSONArray("position"));
            String scale = o.optString("scale");
            if (scale != null && !scale.isEmpty())
                this.scale = FBView.ImageFitting.valueOf(scale);
            Object scales = o.opt("scales");
            if (scales != null) {
                Map<String, Object> map = WebViewCustom.toMap((JSONObject) scales);
                for (String key : map.keySet()) {
                    String v = (String) map.get(key);
                    this.scales.put(key, ZLPaintContext.ScalingType.valueOf(v));
                }
            }
            String fontsizeId = "fontsize_" + getAndroidId(context);
            fontsize = o.optInt(fontsizeId, -1);
            if (fontsize == -1)
                fontsize = null;
            Iterator<String> kk = o.keys();
            while (kk.hasNext()) {
                String k = kk.next();
                if (k.startsWith("fontsize_") && !k.equals(fontsizeId))
                    fontsizes.put(k, o.optInt(k));
            }
            JSONArray b = o.optJSONArray("bookmarks");
            if (b != null && b.length() > 0)
                bookmarks = new Bookmarks(b);
        }

        public JSONObject save(Context context) throws JSONException {
            JSONObject o = new JSONObject();
            o.put("created", created);
            o.put("last", last);
            o.put("authors", authors);
            o.put("title", title);
            JSONArray p = savePosition(position);
            if (p != null)
                o.put("position", p);
            if (scale != null)
                o.put("scale", scale.name());
            if (!scales.isEmpty())
                o.put("scales", WebViewCustom.toJSON(scales));
            if (fontsize != null)
                o.put("fontsize_" + getAndroidId(context), fontsize);
            for (String k : fontsizes.keySet())
                o.put(k, fontsizes.get(k));
            if (bookmarks != null && bookmarks.size() > 0)
                o.put("bookmarks", bookmarks.save());
            return o;
        }

        public void merge(RecentInfo info) {
            if (created > info.created)
                created = info.created;
            if (position == null || last < info.last)
                position = new ZLTextFixedPosition(info.position);
            if (authors == null || last < info.last)
                authors = info.authors;
            if (title == null || last < info.last)
                title = info.title;
            if (scale == null || last < info.last)
                scale = info.scale;
            for (String k : info.scales.keySet()) {
                ZLPaintContext.ScalingType v = info.scales.get(k);
                if (last < info.last) // replace with new values
                    scales.put(k, v);
                else if (!scales.containsKey(k)) // only add non existent values to the list
                    scales.put(k, v);
            }
            if (fontsize == null || last < info.last)
                fontsize = info.fontsize;
            merge(info.fontsizes, info.last);
            if (bookmarks == null) {
                bookmarks = info.bookmarks;
            } else if (info.bookmarks != null) {
                for (Bookmark b : info.bookmarks) {
                    boolean found = false;
                    for (int i = 0; i < bookmarks.size(); i++) {
                        Bookmark m = bookmarks.get(i);
                        if (b.start.samePositionAs(m.start) && b.end.samePositionAs(m.end) && m.last < b.last) {
                            found = true;
                            bookmarks.set(i, b);
                        }
                    }
                    if (!found)
                        bookmarks.add(b);
                }
            }
        }

        public void merge(Map<String, Integer> fontsizes, long last) {
            for (String k : fontsizes.keySet()) {
                if (!this.fontsizes.containsKey(k) || this.last < last)
                    this.fontsizes.put(k, fontsizes.get(k));
            }
        }

        public boolean equals(Map<String, Integer> fontsizes) {
            if (this.fontsizes.size() != fontsizes.size())
                return false;
            for (String k : fontsizes.keySet()) {
                if (!this.fontsizes.containsKey(k))
                    return false;
                if (!this.fontsizes.get(k).equals(fontsizes.get(k)))
                    return false;
            }
            return true;
        }
    }

    public static class Bookmark {
        public long last; // last change event
        public String name;
        public String text;
        public int color;
        public ZLTextPosition start;
        public ZLTextPosition end;

        public Bookmark() {
        }

        public Bookmark(Bookmark b) {
            last = b.last;
            name = b.name;
            text = b.text;
            color = b.color;
            start = b.start;
            end = b.end;
        }

        public Bookmark(String t, ZLTextPosition s, ZLTextPosition e) {
            last = System.currentTimeMillis();
            text = t;
            start = s;
            end = e;
        }

        public Bookmark(JSONObject j) throws JSONException {
            load(j);
        }

        public void load(JSONObject j) throws JSONException {
            last = j.optLong("last");
            name = j.optString("name");
            text = j.optString("text");
            color = j.optInt("color");
            start = loadPosition(j.optJSONArray("start"));
            end = loadPosition(j.optJSONArray("end"));
        }

        public JSONObject save() throws JSONException {
            JSONObject j = new JSONObject();
            j.put("last", last);
            if (name != null)
                j.put("name", name);
            j.put("text", text);
            j.put("color", color);
            JSONArray s = savePosition(start);
            if (s != null)
                j.put("start", s);
            JSONArray e = savePosition(end);
            if (e != null)
                j.put("end", e);
            return j;
        }

        public boolean equals(Bookmark b) {
            if (name != null && b.name != null) {
                if (!name.equals(b.name))
                    return false;
            } else {
                return false;
            }
            if (color != b.color)
                return false;
            if (!text.equals(b.text))
                return false;
            return start.samePositionAs(b.start) && end.samePositionAs(b.end);
        }
    }

    public static class Bookmarks extends ArrayList<Bookmark> {
        public Bookmarks() {
        }

        public Bookmarks(Bookmarks bb) {
            for (Bookmark b : bb)
                add(new Bookmark(b));
        }

        public Bookmarks(JSONArray a) throws JSONException {
            load(a);
        }

        public JSONArray save() throws JSONException {
            JSONArray a = new JSONArray();
            for (Bookmark b : this)
                a.put(b.save());
            return a;
        }

        public void load(JSONArray json) throws JSONException {
            for (int i = 0; i < json.length(); i++)
                add(new Bookmark(json.getJSONObject(i)));
        }

        public int indexOf(Bookmark b) {
            for (int i = 0; i < size(); i++) {
                if (get(i).equals(b))
                    return i;
            }
            return -1;
        }

        public ArrayList<Bookmark> getBookmarks(Plugin.View.Selection.Page page) {
            ArrayList<Bookmark> list = new ArrayList<>();
            for (Bookmark b : this) {
                if (b.start.getParagraphIndex() == page.page || b.end.getParagraphIndex() == page.page)
                    list.add(b);
            }
            return list;
        }

        public boolean equals(Bookmarks b) {
            int s = b.size();
            if (size() != s)
                return false;
            for (int i = 0; i < s; i++) {
                if (!get(i).equals(b.get(i)))
                    return false;
            }
            return true;
        }
    }

    public Storage(Context context) {
        super(context);
    }

    public void save(Book book) {
        book.info.last = System.currentTimeMillis();
        Uri u = recentUri(book);
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri root = Storage.getDocumentTreeUri(u);
            String id = DocumentsContract.getTreeDocumentId(u);
            String path;
            if (!id.contains(COLON)) // downloads/home/etc
                path = getDocumentName(context, u);
            else
                path = Storage.getDocumentChildPath(u);
            Uri o = createFile(context, root, path);
            ContentResolver resolver = context.getContentResolver();
            ParcelFileDescriptor fd;
            try {
                fd = resolver.openFileDescriptor(o, "rw");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            FileDescriptor out = fd.getFileDescriptor();
            try {
                String json = book.info.save(context).toString(2);
                Writer w = new FileWriter(out);
                IOUtils.write(json, w);
                w.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            try {
                File f = Storage.getFile(u);
                String json = book.info.save(context).toString(2);
                Writer w = new FileWriter(f);
                IOUtils.write(json, w);
                w.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnknownUri();
        }
    }

    public Book load(Uri uri) {
        return load(uri, null);
    }

    public Book load(Uri uri, Progress progress) {
        Book book;
        String contentDisposition = null;
//        String s = uri.getScheme();
//        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
//            ContentResolver resolver = context.getContentResolver();
//            try {
//                Cursor meta = resolver.query(uri, null, null, null, null);
//                if (meta != null) {
//                    try {
//                        if (meta.moveToFirst()) {
//                            contentDisposition = meta.getString(meta.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
//                            contentDisposition = Storage.getNameNoExt(contentDisposition);
//                        }
//                    } finally {
//                        meta.close();
//                    }
//                }
//                AssetFileDescriptor fd = resolver.openAssetFileDescriptor(uri, "r");
//                InputStream is = new AssetFileDescriptor.AutoCloseInputStream(fd);
//                long len = fd.getLength();
//                is = new BufferedInputStream(is);
//                if (progress != null)
//                    is = new ProgresInputstream(is, len, progress);
//                book = load(is, uri);
//                is.close();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        } else if (s.startsWith(WebViewCustom.SCHEME_HTTP)) {
//            try {
//                InputStream is;
//                HttpClient client = new HttpClient();
//                HttpClient.DownloadResponse w = client.getResponse(null, uri.toString());
//                if (w.getError() != null)
//                    throw new RuntimeException(w.getError() + ": " + uri);
//                if (w.contentDisposition != null) {
//                    Pattern cp = Pattern.compile("filename=[\"]*([^\"]*)[\"]*");
//                    Matcher cm = cp.matcher(w.contentDisposition);
//                    if (cm.find()) {
//                        contentDisposition = cm.group(1);
//                        contentDisposition = Storage.getNameNoExt(contentDisposition);
//                    }
//                }
//                is = new BufferedInputStream(w.getInputStream());
//                if (progress != null)
//                    is = new ProgresInputstream(is, w.contentLength, progress);
//                book = load(is, uri);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        } else { // file:// or /path/file
        File f = getFile(uri);
        try {
            FileInputStream fis = new FileInputStream(f);
            InputStream is = fis;
            is = new BufferedInputStream(is);
            if (progress != null)
                is = new ProgresInputstream(is, fis.getChannel().size(), progress);
            book = load(is, Uri.fromFile(f));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        }
        Uri r = recentUri(book);
        if (exists(context, r)) {
            try {
                book.info = new RecentInfo(context, r);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to load info", e);
            }
        }
        if (book.info == null) {
            book.info = new RecentInfo();
            book.info.created = System.currentTimeMillis();
        }
        load(book);
        if (book.info.title == null || book.info.title.isEmpty() || book.info.title.equals(book.md5)) {
            if (contentDisposition != null && !contentDisposition.isEmpty())
                book.info.title = contentDisposition;
            else
                book.info.title = Storage.getNameNoExt(uri.getLastPathSegment());
        }
        if (!exists(context, r))
            save(book);
        return book;
    }

    public File getCache() {
        File cache = context.getExternalCacheDir();
        if (cache == null || !canWrite(cache))
            cache = context.getCacheDir();
        return cache;
    }

    public File createTempBook(String ext) throws IOException {
        return File.createTempFile("book", "." + ext, getCache());
    }

    public Book load(InputStream is, Uri u) {
        Uri storage = getStoragePath();

        String s = storage.getScheme();

        if (s.equals(ContentResolver.SCHEME_CONTENT) && DocumentsContract.isDocumentUri(context, u)) {
            if (DocumentsContract.getDocumentId(u).startsWith(DocumentsContract.getTreeDocumentId(storage))) // else we can't get from content://storage to real path
                return new Book(context, DocumentsContract.buildDocumentUriUsingTree(storage, DocumentsContract.getDocumentId(u)));
        }
        if (s.equals(ContentResolver.SCHEME_FILE) && relative(storage.getPath(), u.getPath()) != null)
            return new Book(context, u);

        boolean tmp = false;
        File file = null;

        final Book book = new Book();
        try {
            OutputStream os = null;

            if (u.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                file = Storage.getFile(u);
            } else {
                file = createTempBook("tmp");
                os = new FileOutputStream(file);
                os = new BufferedOutputStream(os);
                tmp = true;
            }

            FileTypeDetector.Detector[] dd = supported();

            book.md5 = FileTypeDetector.detecting(context, dd, is, os, u);

            for (FileTypeDetector.Detector d : dd) {
                if (d.detected) {
                    book.ext = d.ext;
                    if (d instanceof FileTypeDetector.FileTypeDetectorZipExtract.Handler) {
                        FileTypeDetector.FileTypeDetectorZipExtract.Handler e = (FileTypeDetector.FileTypeDetectorZipExtract.Handler) d;
                        if (!tmp) { // !tmp
                            File z = file;
                            file = createTempBook("tmp");
                            book.md5 = e.extract(z, file);
                            tmp = true; // force to delete 'fbook.file'
                        } else { // tmp
                            File tt = createTempBook("tmp");
                            book.md5 = e.extract(file, tt);
                            file.delete(); // delete old
                            file = tt; // tmp = true
                        }
                    }
                    break; // priority first - more imporant
                }
            }

            if (book.ext == null)
                throw new RuntimeException("Unsupported format");

            if (book.ext.equals(ComicsPlugin.CBR)) { // handling cbz solid archives
                File cbz = null;
                try {
                    final Archive archive = new Archive(new NativeStorage(file));
                    if (archive.getMainHeader().isSolid()) {
                        cbz = createTempBook("tmp");
                        OutputStream zos = new FileOutputStream(cbz);
                        zos = new BufferedOutputStream(zos);
                        ZipOutputStream out = new ZipOutputStream(zos);
                        List<FileHeader> list = archive.getFileHeaders();
                        for (FileHeader h : list) {
                            if (h.isDirectory())
                                continue;

                            ZipEntry entry = new ZipEntry(RarSAF.getRarFileName(h));
                            out.putNextEntry(entry);

                            archive.extractFile(h, out);
                        }
                        out.close();
                        if (tmp)
                            file.delete();
                        book.ext = ComicsPlugin.CBZ;
                        file = cbz;
                        tmp = true;
                    }
                } catch (Exception e) {
                    if (cbz != null)
                        cbz.delete();
                    throw new RuntimeException("unsupported rar", e);
                }
            }

            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver contentResolver = context.getContentResolver();
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(storage, DocumentsContract.getTreeDocumentId(storage));
                Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
                if (childCursor != null) {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        String n = Storage.getNameNoExt(t);
                        String e = Storage.getExt(t);
                        if (n.equals(book.md5) && !e.equals(JSON_EXT)) { // delete all but book and json
                            Uri k = DocumentsContract.buildDocumentUriUsingTree(childrenUri, id);
                            try {
                                delete(context, k);
                            } catch (RuntimeException e1) {
                                Log.w(TAG, e1);
                            }
                        }
                    }
                }
                String id = book.md5 + "." + book.ext;
                Uri o = createFile(context, storage, id);
                ContentResolver resolver = context.getContentResolver();

                ParcelFileDescriptor fd = resolver.openFileDescriptor(o, "rw");

                FileDescriptor out = fd.getFileDescriptor();
                FileInputStream fis = new FileInputStream(file);
                OutputStream fos = new FileOutputStream(out);
                fos = new BufferedOutputStream(fos);
                IOUtils.copy(fis, fos);
                fis.close();
                fos.close();

                book.url = o;

                if (tmp)
                    file.delete();
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                File f = getFile(storage);
                File[] ff = f.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith(book.md5);
                    }
                });
                if (ff != null) {
                    for (File k : ff) {
                        if (!getExt(k).toLowerCase().equals(JSON_EXT))
                            k.delete();
                    }
                }
                File to = new File(f, book.md5 + "." + book.ext);
                if (tmp)
                    Storage.move(file, to);
                else
                    Storage.copy(file, to);
                book.url = Uri.fromFile(to);
            } else {
                throw new UnknownUri();
            }
        } catch (RuntimeException e) {
            if (tmp && file != null)
                file.delete();
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            if (tmp && file != null)
                file.delete();
            throw new RuntimeException(e);
        }
        return book;
    }

    public ZLImage loadCover(FBook book) {
        try {
            FormatPlugin plugin = getPlugin(new Info(context), book);
            ZLFile file = BookUtil.fileByBook(book.book);
            return plugin.readCover(file);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void load(Book book) {
        if (book.info == null) {
            Uri r = recentUri(book);
            if (exists(context, r))
                try {
                    book.info = new RecentInfo(context, r);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Unable to load info", e);
                }
        }
        if (book.info == null) {
            book.info = new RecentInfo();
            book.info.created = System.currentTimeMillis();
        }
        FBook fbook = null;
        if (book.info.authors == null || book.info.authors.isEmpty()) {
            if (fbook == null)
                fbook = read(book);
            book.info.authors = fbook.book.authorsString(", ");
        }
        if (book.info.title == null || book.info.title.isEmpty() || book.info.title.equals(book.md5)) {
            if (fbook == null)
                fbook = read(book);
            book.info.title = getTitle(book, fbook);
        }
        if (fbook != null)
            fbook.close();
    }

    public void createCover(FBook fbook, File cover) {
        ZLImage image = loadCover(fbook);
        if (image != null) {
            Bitmap bm = null;
            if (image instanceof ZLFileImageProxy) {
                ZLFileImageProxy p = (ZLFileImageProxy) image;
                if (!p.isSynchronized())
                    p.synchronize();
                image = p.getRealImage();
            }
            if (image instanceof ZLStreamImage) {
                bm = CacheImagesAdapter.createScaled(((ZLStreamImage) image).inputStream());
            }
            if (image instanceof ZLBitmapImage) {
                bm = ((ZLBitmapImage) image).getBitmap();
            }
            boolean a = fbook.book.authors() != null && !fbook.book.authors().isEmpty();
            boolean t = fbook.book.getTitle() != null && !fbook.book.getTitle().isEmpty();
            if (bm == null && (a || t)) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                View v = inflater.inflate(R.layout.cover_generate, null);
                TextView aa = (TextView) v.findViewById(R.id.author);
                aa.setText(fbook.book.authorsString(", "));
                TextView tt = (TextView) v.findViewById(R.id.title);
                tt.setText(fbook.book.getTitle());
                bm = renderView(v);
            }
            if (bm == null) {
                FBReaderView fb = new FBReaderView(getContext());
                fb.loadBook(fbook);
                bm = renderView(fb);
            }
            if (bm == null)
                return;
            try {
                bm = CacheImagesAdapter.createScaled(bm);
                OutputStream os = new FileOutputStream(cover);
                os = new BufferedOutputStream(os);
                bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
                bm.recycle();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void list(ArrayList<Book> list, File storage) {
        if (storage == null)
            return;
        File[] ff = storage.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String n = Storage.getNameNoExt(name);
                String e = getExt(name);
                e = e.toLowerCase();
                if (n.length() != MD5_SIZE)
                    return false;
                FileTypeDetector.Detector[] dd = supported();
                for (FileTypeDetector.Detector d : dd) {
                    if (e.equals(d.ext))
                        return true;
                }
                return false;
            }
        });
        if (ff == null)
            return;
        for (File f : ff) {
            Book b = new Book();
            b.md5 = getNameNoExt(f);
            b.url = Uri.fromFile(f);
            File cover = coverFile(context, b);
            if (cover.exists())
                b.cover = cover;
            File r = recentFile(b);
            if (r.exists()) {
                try {
                    b.info = new RecentInfo(context, r);
                } catch (RuntimeException e) {
                    Log.d(TAG, "Unable to load info", e);
                }
            }
            if (b.info == null) {
                b.info = new RecentInfo();
                b.info.created = System.currentTimeMillis();
            }
            list.add(b);
        }
    }

    public ArrayList<Book> list() {
        Uri uri = getStoragePath();
        ArrayList<Book> list = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
            if (childCursor != null) {
                try {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        long size = childCursor.getLong(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                        if (size > 0) {
                            t = t.toLowerCase();
                            String n = Storage.getNameNoExt(t);
                            if (n.length() != MD5_SIZE) // prevent scan *.fb2 and other books but only sync related files
                                continue;
                            FileTypeDetector.Detector[] dd = supported();
                            for (FileTypeDetector.Detector d : dd) {
                                if (t.endsWith("." + d.ext)) {
                                    Uri k = DocumentsContract.buildDocumentUriUsingTree(uri, id);
                                    Book b = new Book();
                                    b.md5 = getNameNoExt(context, k);
                                    b.url = k;
                                    File cover = coverFile(context, b);
                                    if (cover.exists())
                                        b.cover = cover;
                                    Uri r = recentUri(b);
                                    if (exists(context, r)) {
                                        try {
                                            b.info = new RecentInfo(context, r);
                                        } catch (RuntimeException e) {
                                            Log.e(TAG, "Unable to load info", e);
                                        }
                                    }
                                    if (b.info == null) {
                                        b.info = new RecentInfo();
                                        b.info.created = System.currentTimeMillis();
                                    }
                                    list.add(b);
                                    break; // break dd
                                }
                            }
                        }
                    }
                } finally {
                    childCursor.close();
                }
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File dir = getFile(uri);
            list(list, dir);
        } else {
            throw new UnknownUri();
        }
        return list;
    }

    public void delete(final Book book) {
        delete(context, book.url);
        if (book.cover != null)
            book.cover.delete();
        try {
            delete(context, recentUri(book));
        } catch (RuntimeException e) {
            Log.e(TAG, "failed to delete json", e); // not exists? IllegalArgument if not exists
        }
        // delete all md5.* files (old, cover images, and sync conflicts files)
        Uri storage = getStoragePath();
        String s = storage.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(storage, DocumentsContract.getTreeDocumentId(storage));
            Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
            if (childCursor != null) {
                try {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        if (t.startsWith(book.md5)) { // delete all but json
                            Uri k = DocumentsContract.buildDocumentUriUsingTree(storage, id);
                            delete(context, k);
                        }
                    }
                } finally {
                    childCursor.close();
                }
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File dir = getFile(storage);
            File[] ff = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(book.md5);
                }
            });
            if (ff != null) {
                for (File f : ff) {
                    f.delete();
                }
            }
        } else {
            throw new UnknownUri();
        }
    }

    public FBook read(Book b) {
        try {
            FBook fbook = new FBook();
            if (b.info != null)
                fbook.info = new RecentInfo(b.info);

            File file;

            String s = b.url.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                String ext = getExt(context, b.url);
                fbook.tmp = createTempBook(ext);
                OutputStream os = new FileOutputStream(fbook.tmp);
                os = new BufferedOutputStream(os);
                ContentResolver resolver = getContext().getContentResolver();
                InputStream is = resolver.openInputStream(b.url);
                IOUtils.copy(is, os);
                file = fbook.tmp;
                is.close();
                os.close();
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                file = getFile(b.url);
            } else {
                throw new UnknownUri();
            }

            String ext = getExt(file).toLowerCase();
            if (ext.equals(Storage.ZIP_EXT)) { // handle zip files manually, better perfomance
                FileTypeDetector.Detector[] dd = supported();
                try {
                    InputStream is = new FileInputStream(file);
                    FileTypeDetector.detecting(context, dd, is, null, Uri.fromFile(file));
                } catch (IOException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                for (FileTypeDetector.Detector d : dd) {
                    if (d.detected) {
                        if (d instanceof FileTypeDetector.FileTypeDetectorZipExtract.Handler) {
                            FileTypeDetector.FileTypeDetectorZipExtract.Handler e = (FileTypeDetector.FileTypeDetectorZipExtract.Handler) d;
                            if (fbook.tmp == null) { // !tmp
                                File z = file;
                                file = createTempBook(d.ext);
                                e.extract(z, file);
                                fbook.tmp = file;
                            } else { // tmp
                                File tt = createTempBook(d.ext);
                                e.extract(file, tt);
                                file.delete(); // delete old
                                fbook.tmp = tt;
                                file = tt;
                            }
                        }
                        break; // priority first - more imporant
                    }
                }
            }

            fbook.book = new org.geometerplus.fbreader.book.Book(-1, file.getPath(), null, null, null);
            FormatPlugin plugin = Storage.getPlugin(new Info(context), fbook);
            try {
                plugin.readMetainfo(fbook.book);
            } catch (BookReadingException e) {
                throw new RuntimeException(e);
            }

            return fbook;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Uri getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(BookApplication.PREFERENCE_STORAGE, null);
        if (path == null)
            return Uri.fromFile(getLocalStorage());
        else
            return getStoragePath(path);
    }

    @Override
    public void migrateLocalStorage() {
        migrateLocalStorage(getLocalInternal());
        migrateLocalStorage(getLocalExternal());
    }

    public void migrateLocalStorage(File l) {
        if (l == null)
            return;

        if (!canWrite(l))
            return;

        Uri path = getStoragePath();

        String s = path.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File p = getFile(path);
            if (!canWrite(p))
                return;
            if (l.equals(p)) // same storage path
                return;
        }

        Uri u = Uri.fromFile(l);
        if (u.equals(path)) // same storage path
            return;

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            if (!f.isFile())
                continue;
            boolean m = false;
            String e = Storage.getExt(f).toLowerCase();
            if (e.equals(JSON_EXT))
                m = true;
            else {
                FileTypeDetector.Detector[] dd = supported();
                for (FileTypeDetector.Detector d : dd) {
                    if (e.equals(d.ext))
                        m = true;
                }
            }
            if (m)
                migrate(context, f, path);
        }
    }

    public Uri move(Uri u, Uri dir) {
        try {
            Uri n = getNextFile(context, getStoragePath(), getName(context, u), Storage.JSON_EXT);
            InputStream is;
            OutputStream os;
            String s = u.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver resolver = getContext().getContentResolver();
                is = resolver.openInputStream(u);
                n = createFile(context, dir, Storage.getDocumentChildPath(n));
                os = resolver.openOutputStream(n);
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                is = new FileInputStream(Storage.getFile(u));
                os = new FileOutputStream(Storage.getFile(n));
                os = new BufferedOutputStream(os);
            } else {
                throw new UnknownUri();
            }
            IOUtils.copy(is, os);
            is.close();
            os.close();
            delete(context, u);
            return n;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
