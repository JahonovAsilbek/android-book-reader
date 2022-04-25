package com.github.axet.bookreader.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.axet.androidlibrary.preferences.ScreenlockPreference;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.InvalidateOptionsMenuCompat;
import com.github.axet.androidlibrary.widgets.PopupWindowCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.TreeListView;
import com.github.axet.androidlibrary.widgets.TreeRecyclerView;
import com.github.axet.bookreader.BuildConfig;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.activities.FullscreenActivity;
import com.github.axet.bookreader.activities.MainActivity;
import com.github.axet.bookreader.app.BookApplication;
import com.github.axet.bookreader.app.ComicsPlugin;
import com.github.axet.bookreader.app.Plugin;
import com.github.axet.bookreader.app.Storage;
import com.github.axet.bookreader.widgets.BookmarksDialog;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.bookreader.widgets.ScrollWidget;
import com.github.axet.bookreader.widgets.ToolbarButtonView;

import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.fbreader.ActionCode;
import org.geometerplus.zlibrary.core.util.ZLTTFInfoDetector;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.ui.android.view.AndroidFontUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

public class ReaderFragment extends Fragment implements MainActivity.SearchListener, SharedPreferences.OnSharedPreferenceChangeListener, FullscreenActivity.FullscreenListener, MainActivity.OnBackPressed {
    public static final String TAG = ReaderFragment.class.getSimpleName();

    public static final File FONTS = new File(Environment.getExternalStorageDirectory(), "Fonts");

    public static final int FONT_START = 15;
    public static final int FONT_END = 100;
    public static final int REFLOW_START = 3;
    public static final int REFLOW_END = 15;

    Handler handler = new Handler();
    Storage storage;
    Storage.Book book;
    Storage.FBook fbook;
    FBReaderView fb;
    AlertDialog tocdialog;
    boolean showRTL;
    FontsPopup fontsPopup;
    MenuItem searchMenu;
    BroadcastReceiver battery;
    Runnable invalidateOptionsMenu;
    Runnable time = new Runnable() {
        @Override
        public void run() {
            long s60 = 60 * 1000;
            long secs = System.currentTimeMillis() % s60;
            handler.removeCallbacks(this);
            long d = s60 - secs;
            if (d < 1000)
                d = s60 + d;
            handler.postDelayed(this, d);
            fb.invalidateFooter();
            savePosition();
        }
    };

    public static View getOverflowMenuButton(Activity a) {
        return getOverflowMenuButton((ViewGroup) a.findViewById(R.id.toolbar));
    }

    public static View getOverflowMenuButton(ViewGroup p) {
        for (int i = 0; i < p.getChildCount(); i++) {
            View v = p.getChildAt(i);
            if (v.getClass().getCanonicalName().contains("OverflowMenuButton"))
                return v;
            if (v instanceof ViewGroup) {
                v = getOverflowMenuButton((ViewGroup) v);
                if (v != null)
                    return v;
            }
        }
        return null;
    }

    public static class FontsPopup extends PopupWindow {
        FontAdapter fonts;
        View fontsFrame;
        RecyclerView fontsList;
        TextView fontsText;
        View fontsize_popup;
        TextView fontsizepopup_text;
        SeekBar fontsizepopup_seek;
        View fontsizepopup_minus;
        View fontsizepopup_plus;

        public FontsPopup(Context context) {
            fontsize_popup = LayoutInflater.from(context).inflate(R.layout.font_popup, new FrameLayout(context), false);
            fontsizepopup_text = (TextView) fontsize_popup.findViewById(R.id.fontsize_text);
            fontsizepopup_plus = fontsize_popup.findViewById(R.id.fontsize_plus);
            fontsizepopup_minus = fontsize_popup.findViewById(R.id.fontsize_minus);
            fontsizepopup_seek = (SeekBar) fontsize_popup.findViewById(R.id.fontsize_seek);
            fonts = new FontAdapter(context);
            fonts.clickListener = new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    fonts.select(position);
                    setFont(fonts.ff.get(position).name);
                }
            };
            fontsFrame = fontsize_popup.findViewById(R.id.fonts_frame);
            fontsText = (TextView) fontsize_popup.findViewById(R.id.fonts_text);
            fontsText.setText(context.getString(R.string.add_more_fonts_to, FONTS.toString()));
            fontsList = (RecyclerView) fontsize_popup.findViewById(R.id.fonts_list);
            fontsList.setLayoutManager(new LinearLayoutManager(context));
            setContentView(fontsize_popup);
        }

        public void setFont(String str) {
        }

        public void setFontsize(int f) {
        }

        public void updateFontsize(int f) {
        }

        public void loadFonts() {
            fontsFrame.setVisibility(View.VISIBLE);
            fontsList.setAdapter(fonts);
            fonts.addBasics();
            List<File> files = new ArrayList<>();
            for (String f : enumerateFonts().keySet())
                files.add(new File(f));
            AndroidFontUtil.ourFileSet = new TreeSet<>();
            AndroidFontUtil.ourFontFileMap = new ZLTTFInfoDetector().collectFonts(files);
            for (String s : AndroidFontUtil.ourFontFileMap.keySet()) {
                File[] ff = AndroidFontUtil.ourFontFileMap.get(s);
                for (File f : ff) {
                    if (f != null) {
                        fonts.ff.add(new FontView(s, f));
                        break; // regular first
                    }
                }
            }
        }

        public void updateFontsize(final int start, final int end, int f) {
            fontsizepopup_seek.setMax(end - start);
            fontsizepopup_seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateFontsize(progress + start);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int p = fontsizepopup_seek.getProgress();
                    setFontsize(start + p);
                }
            });
            fontsizepopup_seek.setProgress(f - start);
            fontsizepopup_minus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int p = fontsizepopup_seek.getProgress();
                    p--;
                    if (p < 0)
                        p = 0;
                    fontsizepopup_seek.setProgress(p);
                    setFontsize(start + p);
                }
            });
            fontsizepopup_plus.setOnClickListener(v -> {
                int p = fontsizepopup_seek.getProgress();
                p++;
                if (p >= end - start)
                    p = end - start;
                fontsizepopup_seek.setProgress(p);
                setFontsize(start + p);
            });
        }
    }

    public static class FontView {
        public String name;
        public Typeface font;
        public File file;

        public FontView(String name, File f) {
            this.name = name;
            this.file = f;
            this.font = Typeface.createFromFile(file);
        }

        public FontView(String name) {
            this.name = name;
            this.font = Typeface.create(name, Typeface.NORMAL);
        }
    }

    public static class FontHolder extends RecyclerView.ViewHolder {
        public CheckedTextView tv;

        public FontHolder(View itemView) {
            super(itemView);
            tv = (CheckedTextView) itemView.findViewById(android.R.id.text1);
        }
    }

    public static class FontAdapter extends RecyclerView.Adapter<FontHolder> {
        Context context;
        public ArrayList<FontView> ff = new ArrayList<>();
        public int selected;
        public AdapterView.OnItemClickListener clickListener;

        public FontAdapter(Context context) {
            this.context = context;
        }

        public void addBasics() {
            add("sans-serif"); // "normal"
            add("serif");
            add("monospace");
        }

        public void loadTTF() {
            addBasics();
            HashMap<String, String> hh = enumerateFonts();
            for (String k : hh.keySet()) {
                String v = hh.get(k);
                ff.add(new FontView(v, new File(k)));
            }
        }

        public void select(String f) {
            for (int i = 0; i < ff.size(); i++) {
                if (ff.get(i).name.equals(f)) {
                    selected = i;
                }
            }
            notifyDataSetChanged();
        }

        public void select(int i) {
            selected = i;
            notifyDataSetChanged();
        }

        public void add(String f) {
            ff.add(new FontView(f));
        }

        @Override
        public int getItemCount() {
            return ff.size();
        }

        @Override
        public FontHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(android.R.layout.select_dialog_singlechoice, parent, false);
            return new FontHolder(view);
        }

        @Override
        public void onBindViewHolder(final FontHolder holder, int position) {
            holder.itemView.setOnClickListener(v -> clickListener.onItemClick(null, null, holder.getAdapterPosition(), -1));
            holder.tv.setChecked(selected == position);
            holder.tv.setTypeface(ff.get(position).font);
            holder.tv.setText(ff.get(position).name);
        }
    }

    public static class TOCHolder extends TreeRecyclerView.TreeHolder {
        ImageView i;
        TextView textView;

        public TOCHolder(View itemView) {
            super(itemView);
            i = (ImageView) itemView.findViewById(R.id.image);
            textView = (TextView) itemView.findViewById(R.id.text);
        }
    }

    public class TOCAdapter extends TreeRecyclerView.TreeAdapter<TOCHolder> {
        TOCTree current;

        public TOCAdapter(List<TOCTree> ll, TOCTree current) {
            this.current = current;
            loadTOC(root, ll);
            load();
        }

        void loadTOC(TreeListView.TreeNode r, List<TOCTree> tree) {
            for (TOCTree t : tree) {
                TreeListView.TreeNode n = new TreeListView.TreeNode(r, t);
                r.nodes.add(n);
                if (equals(t, current)) {
                    n.selected = true; // current selected
                    r.expanded = true; // parent expanded
                }
                if (t.hasChildren()) {
                    loadTOC(n, t.subtrees());
                    if (n.expanded) {
                        n.selected = true;
                        r.expanded = true;
                    }
                }
            }
        }

        public int getCurrent() {
            for (int i = 0; i < getItemCount(); i++) {
                TOCTree t = (TOCTree) getItem(i).tag;
                if (equals(t, current))
                    return i;
            }
            return -1;
        }

        @Override
        public TOCHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View convertView = inflater.inflate(R.layout.toc_item, null);
            return new TOCHolder(convertView);
        }

        @Override
        public void onBindViewHolder(final TOCHolder h, int position) {
            TreeListView.TreeNode t = getItem(h.getAdapterPosition(this));
            TOCTree tt = (TOCTree) t.tag;
            ImageView ex = (ImageView) h.itemView.findViewById(R.id.expand);
            if (t.nodes.isEmpty())
                ex.setVisibility(View.INVISIBLE);
            else
                ex.setVisibility(View.VISIBLE);
            ex.setImageResource(t.expanded ? R.drawable.ic_expand_less_black_24dp : R.drawable.ic_expand_more_black_24dp);
            h.itemView.setPadding(20 * t.level, 0, 0, 0);
            if (t.selected) {
                h.textView.setTypeface(null, Typeface.BOLD);
                h.i.setColorFilter(null);
            } else {
                h.i.setColorFilter(Color.GRAY);
                h.textView.setTypeface(null, Typeface.NORMAL);
            }
            h.textView.setText(tt.getText());
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TOCTree n = (TOCTree) getItem(h.getAdapterPosition(TOCAdapter.this)).tag;
                    if (n.hasChildren())
                        return;
                    fb.gotoPosition(n.getReference());
                    tocdialog.dismiss();
                }
            });
        }

        boolean equals(TOCTree t, TOCTree t2) {
            if (t == null || t2 == null)
                return false;
            TOCTree.Reference r1 = t.getReference();
            TOCTree.Reference r2 = t2.getReference();
            if (r1 == null || r2 == null)
                return false;
            return r1.ParagraphIndex == r2.ParagraphIndex;
        }
    }

    // http://www.ulduzsoft.com/2012/01/enumerating-the-fonts-on-android-platform/
    public static class TTFAnalyzer {
        // This function parses the TTF file and returns the font name specified in the file
        public String getTtfFontName(File fontFilename) {
            try {
                // Parses the TTF file format.
                // See http://developer.apple.com/fonts/ttrefman/rm06/Chap6.html
                m_file = new RandomAccessFile(fontFilename, "r");

                // Read the version first
                int version = readDword();

                // The version must be either 'true' (0x74727565) or 0x00010000
                if (version != 0x74727565 && version != 0x00010000 && version != 0x4f54544f)
                    return null;

                // The TTF file consist of several sections called "tables", and we need to know how many of them are there.
                int numTables = readWord();

                // Skip the rest in the header
                readWord(); // skip searchRange
                readWord(); // skip entrySelector
                readWord(); // skip rangeShift

                // Now we can read the tables
                for (int i = 0; i < numTables; i++) {
                    // Read the table entry
                    int tag = readDword();
                    readDword(); // skip checksum
                    int offset = readDword();
                    int length = readDword();

                    // Now here' the trick. 'name' field actually contains the textual string name.
                    // So the 'name' string in characters equals to 0x6E616D65
                    if (tag == 0x6E616D65) {
                        // Here's the name section. Read it completely into the allocated buffer
                        byte[] table = new byte[length];

                        m_file.seek(offset);
                        read(table);

                        // This is also a table. See http://developer.apple.com/fonts/ttrefman/rm06/Chap6name.html
                        // According to Table 36, the total number of table records is stored in the second word, at the offset 2.
                        // Getting the count and string offset - remembering it's big endian.
                        int count = getWord(table, 2);
                        int string_offset = getWord(table, 4);

                        // Record starts from offset 6
                        for (int record = 0; record < count; record++) {
                            // Table 37 tells us that each record is 6 words -> 12 bytes, and that the nameID is 4th word so its offset is 6.
                            // We also need to account for the first 6 bytes of the header above (Table 36), so...
                            int nameid_offset = record * 12 + 6;
                            int platformID = getWord(table, nameid_offset);
                            int nameid_value = getWord(table, nameid_offset + 6);

                            // Table 42 lists the valid name Identifiers. We're interested in 4 but not in Unicode encoding (for simplicity).
                            // The encoding is stored as PlatformID and we're interested in Mac encoding
                            if (nameid_value == 4 && platformID == 1) {
                                // We need the string offset and length, which are the word 6 and 5 respectively
                                int name_length = getWord(table, nameid_offset + 8);
                                int name_offset = getWord(table, nameid_offset + 10);

                                // The real name string offset is calculated by adding the string_offset
                                name_offset = name_offset + string_offset;

                                // Make sure it is inside the array
                                if (name_offset >= 0 && name_offset + name_length < table.length)
                                    return new String(table, name_offset, name_length);
                            }
                        }
                    }
                }

                return null;
            } catch (FileNotFoundException e) {
                // Permissions?
                return null;
            } catch (IOException e) {
                // Most likely a corrupted font file
                return null;
            }
        }

        // Font file; must be seekable
        private RandomAccessFile m_file = null;

        // Helper I/O functions
        private int readByte() throws IOException {
            return m_file.read() & 0xFF;
        }

        private int readWord() throws IOException {
            int b1 = readByte();
            int b2 = readByte();

            return b1 << 8 | b2;
        }

        private int readDword() throws IOException {
            int b1 = readByte();
            int b2 = readByte();
            int b3 = readByte();
            int b4 = readByte();

            return b1 << 24 | b2 << 16 | b3 << 8 | b4;
        }

        private void read(byte[] array) throws IOException {
            if (m_file.read(array) != array.length)
                throw new IOException();
        }

        // Helper
        private int getWord(byte[] array, int offset) {
            int b1 = array[offset] & 0xFF;
            int b2 = array[offset + 1] & 0xFF;

            return b1 << 8 | b2;
        }
    }

    public ReaderFragment() {
    }

    static public HashMap<String, String> enumerateFonts() {
        String[] fontdirs = {"/system/fonts", "/system/font", "/data/fonts", FONTS.toString()};
        HashMap<String, String> fonts = new HashMap<>();
        TTFAnalyzer a = new TTFAnalyzer();

        for (String fontdir : fontdirs) {
            File dir = new File(fontdir);

            if (!dir.exists())
                continue;

            File[] files = dir.listFiles();

            if (files == null)
                continue;

            for (File file : files) {
                String n = a.getTtfFontName(file);
                if (n != null)
                    fonts.put(file.getAbsolutePath(), n);
            }
        }

        return fonts.isEmpty() ? null : fonts;
    }

    public static ReaderFragment newInstance(Uri uri) {
        ReaderFragment fragment = new ReaderFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReaderFragment newInstance(Uri uri, FBReaderView.ZLTextIndexPosition pos) {
        ReaderFragment fragment = new ReaderFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        args.putParcelable("pos", pos);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new Storage(getContext());
        setHasOptionsMenu(true);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_reader, container, false);

        final MainActivity main = (MainActivity) getActivity();
        fb = (FBReaderView) v.findViewById(R.id.main_view);

        fb.listener = new FBReaderView.Listener() {
            @Override
            public void onScrollingFinished(ZLViewEnums.PageIndex index) {
                if (fontsPopup != null) {
                    fontsPopup.dismiss();
                    fontsPopup = null;
                }
                updateToolbar();
            }

            @Override
            public void onSearchClose() {
                MenuItemCompat.collapseActionView(searchMenu);
            }

            @Override
            public void onBookmarksUpdate() {
                updateToolbar();
            }

            @Override
            public void onDismissDialog() {
                if (main.fullscreen)
                    main.hideSystemUI();
            }

            @Override
            public void ttsStatus(boolean speaking) {
                MainActivity main = (MainActivity) getActivity();
                main.volumeEnabled = !speaking;
            }
        };

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        String mode = shared.getString(BookApplication.PREFERENCE_VIEW_MODE, "");
        fb.setWidget(mode.equals(FBReaderView.Widgets.CONTINUOUS.toString()) ? FBReaderView.Widgets.CONTINUOUS : FBReaderView.Widgets.PAGING);

        fb.setWindow(getActivity().getWindow());
        fb.setActivity(getActivity());

        Uri uri = getArguments().getParcelable("uri");
        FBReaderView.ZLTextIndexPosition pos = getArguments().getParcelable("pos");

        try {
            book = storage.load(uri);
            fbook = storage.read(book);
            fb.loadBook(fbook);
            if (pos != null)
                fb.gotoPosition(pos);
        } catch (RuntimeException e) {
            ErrorDialog.Error(main, e);
            handler.post(new Runnable() { // or openLibrary crash with java.lang.IllegalStateException on FragmentActivity.onResume
                @Override
                public void run() {
                    if (!main.isFinishing())
                        main.openLibrary();
                }
            });
            return v; // ignore post called
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (getActivity().isFinishing())
                    return;
                updateToolbar(); // update toolbar after page been drawn to detect RTL
                fb.showControls(); //  update toolbar after page been drawn, getWidth() == 0
            }
        });

        return v;
    }

    void updateToolbar() {
        if (invalidateOptionsMenu != null)
            invalidateOptionsMenu.run();
    }

    @Override
    public void onResume() {
        super.onResume();
        ScreenlockPreference.onResume(getActivity(), BookApplication.PREFERENCE_SCREENLOCK);

        battery = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                fb.battery = level * 100 / scale;
                fb.invalidateFooter();
            }
        };
        battery.onReceive(getContext(), getContext().registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));

        time.run();

        updateTheme(); // MainActivity.restartActivity() not called when double change while ReaderFragment active
    }

    @Override
    public void onPause() {
        super.onPause();
        savePosition();
        ScreenlockPreference.onPause(getActivity(), BookApplication.PREFERENCE_SCREENLOCK);

        if (battery != null) {
            getContext().unregisterReceiver(battery);
            battery = null;
        }

        handler.removeCallbacks(time);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        savePosition();
    }

    public float getFontsizeReflow() {
        Float fontsize = fb.getFontsizeReflow();
        if (fontsize != null)
            return fontsize;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        return shared.getFloat(BookApplication.PREFERENCE_FONTSIZE_REFLOW, BookApplication.PREFERENCE_FONTSIZE_REFLOW_DEFAULT);
    }

    void savePosition() {
        if (book == null)
            return;
        if (fb.book == null) // when book isn't loaded and view closed
            return;
        Storage.RecentInfo save = new Storage.RecentInfo(fb.book.info);
        save.position = fb.getPosition();
        Uri u = storage.recentUri(book);
        if (Storage.exists(getContext(), u)) { // file can be changed during sync, check for conflicts
            try {
                Storage.RecentInfo info = new Storage.RecentInfo(getContext(), u);
                if (info.position != null && save.position.samePositionAs(info.position)) {
                    if (save.fontsize == null || info.fontsize != null && save.fontsize.equals(info.fontsize)) {
                        if (save.equals(info.fontsizes))
                            if (save.bookmarks == null || info.bookmarks != null && save.bookmarks.equals(info.bookmarks))
                                return; // nothing to save
                    }
                }
                if (book.info.last != info.last) // file changed between saves?
                    storage.move(u, storage.getStoragePath()); // yes. create conflict (1)
                save.merge(info.fontsizes, info.last);
            } catch (RuntimeException e) {
                Log.d(TAG, "Unable to load JSON", e);
            }
        }
        book.info = save;
        storage.save(book);
        Log.d(TAG, "savePosition " + save.position);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacks(time);
        ScreenlockPreference.onUserInteractionRemove();
        if (fb != null) // onDestory without onCreate
            fb.closeBook();
        if (fontsPopup != null) {
            fontsPopup.dismiss();
            fontsPopup = null;
        }
        if (fbook != null) {
            fbook.close();
            fbook = null;
        }
        book = null;
    }

    @Override
    public void onUserInteraction() {
        ScreenlockPreference.onUserInteraction(getActivity(), BookApplication.PREFERENCE_SCREENLOCK);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (fontsPopup != null) {
            fontsPopup.dismiss();
            fontsPopup = null;
        }
        int id = item.getItemId();
        if (id == R.id.action_toc) {
            showTOC();
            return true;
        }
        if (id == R.id.action_bm) {
            BookmarksDialog dialog = new BookmarksDialog(getContext()) {
                @Override
                public void onSelected(Storage.Bookmark b) {
                    fb.gotoPosition(new FBReaderView.ZLTextIndexPosition(b.start, b.end));
                }

                @Override
                public void onSave(Storage.Bookmark bm) {
                    fb.bookmarksUpdate();
                    savePosition();
                }

                @Override
                public void onDelete(Storage.Bookmark bm) {
                    int i = book.info.bookmarks.indexOf(bm);
                    book.info.bookmarks.remove(i);
                    i = fb.book.info.bookmarks.indexOf(bm);
                    fb.book.info.bookmarks.remove(i);
                    fb.bookmarksUpdate();
                    savePosition();
                }
            };
            dialog.load(fb.book.info.bookmarks);
            dialog.show();
            return true;
        }
        if (id == R.id.action_reflow) {
            fb.setReflow(!fb.isReflow());
            updateToolbar();
        }
        if (id == R.id.action_debug) {
            fb.pluginview.reflowDebug = !fb.pluginview.reflowDebug;
            if (fb.pluginview.reflowDebug) {
                fb.pluginview.reflow = true;
                fb.setWidget(FBReaderView.Widgets.PAGING);
            }
            fb.reset();
            updateToolbar();
        }
        if (id == R.id.action_fontsize) {
            if (fb.pluginview == null) {
                fontsPopup = new FontsPopup(getContext()) {
                    @Override
                    public void setFont(String f) {
                        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                        SharedPreferences.Editor edit = shared.edit();
                        edit.putString(BookApplication.PREFERENCE_FONTFAMILY_FBREADER, f);
                        edit.apply();
                        fb.setFontFB(f);
                        updateToolbar();
                    }

                    @Override
                    public void setFontsize(int f) {
                        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                        SharedPreferences.Editor edit = shared.edit();
                        edit.putInt(BookApplication.PREFERENCE_FONTSIZE_FBREADER, f);
                        edit.apply();
                        fb.setFontsizeFB(f);
                        updateToolbar();
                    }

                    @Override
                    public void updateFontsize(int f) {
                        fontsizepopup_text.setText(Integer.toString(f));
                    }
                };
                fontsPopup.loadFonts();
                fontsPopup.fonts.select(fb.app.ViewOptions.getTextStyleCollection().getBaseStyle().FontFamilyOption.getValue());
                fontsPopup.fontsList.scrollToPosition(fontsPopup.fonts.selected);
                fontsPopup.updateFontsize(FONT_START, FONT_END, fb.getFontsizeFB());
            } else {
                fontsPopup = new FontsPopup(getContext()) {
                    @Override
                    public void setFontsize(int f) {
                        float p = f / 10f;
                        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                        SharedPreferences.Editor editor = shared.edit();
                        editor.putFloat(BookApplication.PREFERENCE_FONTSIZE_REFLOW, p);
                        editor.apply();
                        fb.setFontsizeReflow(p);
                        updateToolbar();
                    }

                    @Override
                    public void updateFontsize(int f) {
                        fontsizepopup_text.setText(String.format("%.1f", f / 10f));
                    }
                };
                fontsPopup.fontsFrame.setVisibility(View.GONE);
                fontsPopup.updateFontsize(REFLOW_START, REFLOW_END, (int) (getFontsizeReflow() * 10));
            }
            View v = MenuItemCompat.getActionView(item);
            if (v == null || !ViewCompat.isAttachedToWindow(v))
                v = getOverflowMenuButton(getActivity());
            PopupWindowCompat.showAsTooltip(fontsPopup, v, Gravity.BOTTOM,
                    ThemeUtils.getThemeColor(getContext(), R.attr.colorButtonNormal), // v has overflow ThemedContext
                    ThemeUtils.dp2px(getContext(), 300));
        }
        if (id == R.id.action_rtl) {
            fb.app.BookTextView.rtlMode = !fb.app.BookTextView.rtlMode;
            fb.reset();
            updateToolbar();
        }
        if (id == R.id.action_mode) {
            FBReaderView.Widgets m = fb.widget instanceof ScrollWidget ? FBReaderView.Widgets.PAGING : FBReaderView.Widgets.CONTINUOUS;
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor edit = shared.edit();
            edit.putString(BookApplication.PREFERENCE_VIEW_MODE, m.toString());
            edit.apply();
            fb.setWidget(m);
            fb.reset();
            updateToolbar();
        }
        if (id == R.id.action_tts) {
            if (fb.tts != null) {
                fb.tts.dismiss();
                fb.tts = null;
            } else {
                fb.ttsOpen();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void updateTheme() {
        fb.updateTheme();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        invalidateOptionsMenu = InvalidateOptionsMenuCompat.onCreateOptionsMenu(this, menu, inflater);

        MenuItem homeMenu = menu.findItem(R.id.action_home);
        MenuItem tocMenu = menu.findItem(R.id.action_toc);
        searchMenu = menu.findItem(R.id.action_search);
        MenuItem reflow = menu.findItem(R.id.action_reflow);
        MenuItem debug = menu.findItem(R.id.action_debug);
        MenuItem bookmarksMenu = menu.findItem(R.id.action_bm);
        final MenuItem fontsize = menu.findItem(R.id.action_fontsize);
        final MenuItem rtl = menu.findItem(R.id.action_rtl);
        MenuItem grid = menu.findItem(R.id.action_grid);
        MenuItem mode = menu.findItem(R.id.action_mode);
        MenuItem theme = menu.findItem(R.id.action_theme);
        MenuItem sort = menu.findItem(R.id.action_sort);
        MenuItem tts = menu.findItem(R.id.action_tts);

        boolean search;

        if (fb.pluginview == null) {
            search = true;
        } else {
            Plugin.View.Search s = fb.pluginview.search("");
            if (s == null) {
                search = false;
            } else {
                s.close();
                search = true;
            }
            if (fb.pluginview.reflow || fb.pluginview instanceof ComicsPlugin.ComicsView)
                tts.setVisible(false); // TODO reflow - possible and can be very practical
        }

        grid.setVisible(false);
        homeMenu.setVisible(false);
        sort.setVisible(false);
        tocMenu.setVisible(fb.app.Model != null && fb.app.Model.TOCTree != null && fb.app.Model.TOCTree.hasChildren());
        searchMenu.setVisible(search);
        reflow.setVisible(fb.pluginview != null && !(fb.pluginview instanceof ComicsPlugin.ComicsView));

        if (BuildConfig.DEBUG && fb.pluginview != null && !(fb.pluginview instanceof ComicsPlugin.ComicsView))
            debug.setVisible(true);
        else
            debug.setVisible(false);

        fontsize.setVisible(fb.pluginview == null || fb.pluginview.reflow);
        if (fb.pluginview == null)
            ((ToolbarButtonView) MenuItemCompat.getActionView(fontsize)).text.setText("" + (fb.book == null ? "" : fb.getFontsizeFB())); // call before onCreateView
        else
            ((ToolbarButtonView) MenuItemCompat.getActionView(fontsize)).text.setText(String.format("%.1f", getFontsizeReflow()));
        MenuItemCompat.getActionView(fontsize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(fontsize);
            }
        });

        mode.setIcon(fb.widget instanceof ScrollWidget ? R.drawable.ic_view_day_black_24dp : R.drawable.ic_view_carousel_black_24dp); // icon current
        mode.setTitle(fb.widget instanceof ScrollWidget ? R.string.view_mode_paging : R.string.view_mode_continuous); // text next

        showRTL |= !fb.app.BookTextView.rtlMode && fb.app.BookTextView.rtlDetected;
        if (showRTL)
            rtl.setVisible(true);
        else
            rtl.setVisible(false);
        MenuItemCompat.getActionView(rtl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(rtl);
            }
        });
        rtl.setTitle(fb.app.BookTextView.rtlMode ? "RTL" : "LTR");
        ((ToolbarButtonView) MenuItemCompat.getActionView(rtl)).text.setText(fb.app.BookTextView.rtlMode ? "RTL" : "LTR");
        if (fb.book != null) // call before onCreateView
            bookmarksMenu.setVisible(fb.book.info.bookmarks != null && fb.book.info.bookmarks.size() > 0);

        if (fb.pluginview instanceof ComicsPlugin.ComicsView)
            theme.setVisible(false);
    }

    void showTOC() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final TOCTree current = fb.app.getCurrentTOCElement();
        final TOCAdapter a = new TOCAdapter(fb.app.Model.TOCTree.subtrees(), current);
        final TreeRecyclerView tree = new TreeRecyclerView(getContext());
        tree.setAdapter(a);
        builder.setView(tree);
        builder.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        tocdialog = builder.create();
        tocdialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final int i = a.getCurrent() - 1;
                if (i > 0)
                    tree.setSelection(i);
            }
        });
        tocdialog.show();
    }

    @Override
    public void search(String s) {
        fb.app.runAction(ActionCode.SEARCH, s);
    }

    @Override
    public void searchClose() {
        fb.searchClose();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(BookApplication.PREFERENCE_VIEW_MODE)) {
            fb.configWidget(sharedPreferences);
            fb.showControls();
        }
        if (key.equals(BookApplication.PREFERENCE_THEME)) {
            fb.configColorProfile(sharedPreferences);
        }
    }

    @Override
    public String getHint() {
        return getString(R.string.search_book);
    }

    @Override
    public void onFullscreenChanged(boolean f) {
        fb.onConfigurationChanged(null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            fb.app.runAction(ActionCode.VOLUME_KEY_SCROLL_FORWARD);
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            fb.app.runAction(ActionCode.VOLUME_KEY_SCROLL_BACK);
            return true;
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))
            return true;
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP))
            return true;
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (fb.isPinch()) {
            fb.pinchClose();
            return true;
        }
        return false;
    }
}
