//package com.github.axet.bookreader.fragments;
//
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.Process;
//import android.preference.PreferenceManager;
//import android.util.Log;
//import android.view.LayoutInflater;
//import android.view.Menu;
//import android.view.MenuInflater;
//import android.view.MenuItem;
//import android.view.SubMenu;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ViewParent;
//import android.widget.AdapterView;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.widget.PopupMenu;
//import androidx.fragment.app.Fragment;
//import androidx.recyclerview.widget.GridLayoutManager;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.github.axet.androidlibrary.net.HttpClient;
//import com.github.axet.androidlibrary.services.StorageProvider;
//import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
//import com.github.axet.androidlibrary.widgets.CacheImagesRecyclerAdapter;
//import com.github.axet.androidlibrary.widgets.InvalidateOptionsMenuCompat;
//import com.github.axet.androidlibrary.widgets.OpenFileDialog;
//import com.github.axet.androidlibrary.widgets.SearchView;
//import com.github.axet.androidlibrary.widgets.TextMax;
//import com.github.axet.bookreader.R;
//import com.github.axet.bookreader.activities.MainActivity;
//import com.github.axet.bookreader.app.BookApplication;
//import com.github.axet.bookreader.app.Storage;
//import com.github.axet.bookreader.widgets.BookmarksDialog;
//import com.github.axet.bookreader.widgets.FBReaderView;
//
//import org.apache.commons.io.IOUtils;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//
//public class LibraryFragment extends Fragment implements MainActivity.SearchListener {
//    public static final String TAG = LibraryFragment.class.getSimpleName();
//
//    LibraryAdapter books;
//    Storage storage;
//    String lastSearch = "";
//    FragmentHolder holder;
//    Runnable invalidateOptionsMenu;
//
//    public static class FragmentHolder {
//        RecyclerView grid;
//
//        public int layout;
//
//        View toolbar;
//        View searchpanel;
//        LinearLayout searchtoolbar;
//        View footer;
//        View footerButtons;
//        View footerNext;
//        View footerProgress;
//        View footerStop;
//
//        Context context;
//        AdapterView.OnItemClickListener clickListener;
//        AdapterView.OnItemLongClickListener longClickListener;
//
//        public FragmentHolder(Context context) {
//            this.context = context;
//        }
//
//        public void create(View v) {
//            grid = (RecyclerView) v.findViewById(R.id.grid);
//
//            // DividerItemDecoration divider = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
//            // grid.addItemDecoration(divider);
//
//            LayoutInflater inflater = LayoutInflater.from(context);
//
//            toolbar = v.findViewById(R.id.search_header_toolbar_parent);
//            searchpanel = v.findViewById(R.id.search_panel);
//            searchtoolbar = (LinearLayout) v.findViewById(R.id.search_header_toolbar);
//
//            toolbar.setVisibility(View.GONE);
//
//            footer = inflater.inflate(R.layout.library_footer, null);
//            footerButtons = footer.findViewById(R.id.search_footer_buttons);
//            footerNext = footer.findViewById(R.id.search_footer_next);
//            footerProgress = footer.findViewById(R.id.search_footer_progress);
//            footerStop = footer.findViewById(R.id.search_footer_stop);
//
//            footerNext.setOnClickListener(v1 -> Log.d(TAG, "footer next"));
//
//            addFooterView(footer);
//
//            updateGrid();
//        }
//
//        public String getLayout() {
//            return "library";
//        }
//
//        public void updateGrid() {
//            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
//            if (shared.getString(BookApplication.PREFERENCE_LIBRARY_LAYOUT + getLayout(), "").equals("book_list_item")) {
//                setNumColumns(1);
//                layout = R.layout.book_list_item;
//            } else {
//                setNumColumns(4);
//                layout = R.layout.book_item;
//            }
//        }
//
//        void onCreateOptionsMenu(Menu menu) {
//            MenuItem grid = menu.findItem(R.id.action_grid);
//
//            updateGrid();
//
//            if (layout == R.layout.book_item)
//                grid.setIcon(R.drawable.ic_view_module_black_24dp);
//            else
//                grid.setIcon(R.drawable.ic_view_list_black_24dp);
//        }
//
//        public boolean onOptionsItemSelected(MenuItem item) {
//            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
//            int id = item.getItemId();
//            if (id == R.id.action_grid) {
//                SharedPreferences.Editor editor = shared.edit();
//                if (layout == R.layout.book_list_item)
//                    editor.putString(BookApplication.PREFERENCE_LIBRARY_LAYOUT + getLayout(), "book_item");
//                else
//                    editor.putString(BookApplication.PREFERENCE_LIBRARY_LAYOUT + getLayout(), "book_list_item");
//                editor.commit();
//                updateGrid();
//                return true;
//            }
//            return false;
//        }
//
//        public void addFooterView(View v) {
//        }
//
//        public void setNumColumns(int i) {
//            LinearLayoutManager reset = null;
//            if (i == 1) {
//                LinearLayoutManager lm = new LinearLayoutManager(context);
//                RecyclerView.LayoutManager l = grid.getLayoutManager();
//                if (l == null || l instanceof GridLayoutManager)
//                    reset = lm;
//            } else {
//                GridLayoutManager lm = new GridLayoutManager(context, i);
//                lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
//                    @Override
//                    public int getSpanSize(int position) {
//                        return FragmentHolder.this.getSpanSize(position);
//                    }
//                });
//                RecyclerView.LayoutManager l = grid.getLayoutManager();
//                if (l == null || !(l instanceof GridLayoutManager) || ((GridLayoutManager) l).getSpanCount() != i)
//                    reset = lm;
//            }
//            if (reset != null)
//                grid.setLayoutManager(reset);
//        }
//
//        public int getSpanSize(int position) {
//            return 1;
//        }
//
//        public void setOnItemClickListener(AdapterView.OnItemClickListener l) {
//            clickListener = l;
//        }
//
//        public void setOnItemLongClickListener(AdapterView.OnItemLongClickListener l) {
//            longClickListener = l;
//        }
//    }
//
//    public static class ByRecent implements Comparator<Storage.Book> {
//        @Override
//        public int compare(Storage.Book o1, Storage.Book o2) {
//            return Long.valueOf(o1.info.last).compareTo(o2.info.last);
//        }
//    }
//
//    public static class ByCreated implements Comparator<Storage.Book> {
//        @Override
//        public int compare(Storage.Book o1, Storage.Book o2) {
//            return Long.valueOf(o1.info.created).compareTo(o2.info.created);
//        }
//    }
//
//    public static class ByName implements Comparator<Storage.Book> {
//        @Override
//        public int compare(Storage.Book o1, Storage.Book o2) {
//            return Storage.getTitle(o1.info).compareTo(Storage.getTitle(o2.info));
//        }
//    }
//
//    public class LibraryAdapter extends BooksAdapter {
//        ArrayList<Storage.Book> all = new ArrayList<>();
//        ArrayList<Storage.Book> list = new ArrayList<>();
//
//        public LibraryAdapter(FragmentHolder holder) {
//            super(LibraryFragment.this.getContext(), holder);
//        }
//
//        @Override
//        public int getItemViewType(int position) {
//            return holder.layout;
//        }
//
//        @Override
//        public String getAuthors(int position) {
//            Storage.Book b = list.get(position);
//            return b.info.authors;
//        }
//
//        @Override
//        public String getTitle(int position) {
//            Storage.Book b = list.get(position);
//            return b.info.title;
//        }
//
//        @Override
//        public int getItemCount() {
//            return list.size();
//        }
//
//        public Storage.Book getItem(int position) {
//            return list.get(position);
//        }
//
//        public void load() {
//            all = storage.list();
//        }
//
//        public boolean hasBookmarks() {
//            for (Storage.Book b : all) {
//                if (b.info.bookmarks != null)
//                    return true;
//            }
//            return false;
//        }
//
//        public void delete(Storage.Book b) {
//            all.remove(b);
//            int i = list.indexOf(b);
//            list.remove(i);
//            notifyItemRemoved(i);
//        }
//
//        public void refresh() {
//            list.clear();
//            if (filter == null || filter.isEmpty()) {
//                list = new ArrayList<>(all);
//                clearTasks();
//            } else {
//                for (Storage.Book b : all) {
//                    if (SearchView.filter(filter, Storage.getTitle(b.info)))
//                        list.add(b);
//                }
//            }
//            sort();
//        }
//
//        public void sort() {
//            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
//            int selected = getContext().getResources().getIdentifier(shared.getString(BookApplication.PREFERENCE_SORT, getContext().getResources().getResourceEntryName(R.id.sort_add_ask)), "id", getContext().getPackageName());
//            if (selected == R.id.sort_name_ask) {
//                Collections.sort(list, new ByName());
//            } else if (selected == R.id.sort_name_desc) {
//                Collections.sort(list, Collections.reverseOrder(new ByName()));
//            } else if (selected == R.id.sort_add_ask) {
//                Collections.sort(list, new ByCreated());
//            } else if (selected == R.id.sort_add_desc) {
//                Collections.sort(list, Collections.reverseOrder(new ByCreated()));
//            } else if (selected == R.id.sort_open_ask) {
//                Collections.sort(list, new ByRecent());
//            } else if (selected == R.id.sort_open_desc) {
//                Collections.sort(list, Collections.reverseOrder(new ByRecent()));
//            } else {
//                Collections.sort(list, new ByCreated());
//            }
//            notifyDataSetChanged();
//        }
//
//        @Override
//        public void onBindViewHolder(final BookHolder h, int position) {
//            super.onBindViewHolder(h, position);
//
//            Storage.Book b = list.get(position);
//
//            View convertView = h.itemView;
//
//            if (b.cover == null || !b.cover.exists()) {
//                downloadTask(b, convertView);
//            } else {
//                downloadTaskClean(convertView);
//                downloadTaskUpdate(null, b, convertView);
//            }
//        }
//
//        @Override
//        public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
//            Storage.Book book = (Storage.Book) task.item;
//            Storage.FBook fbook = null;
//            try {
//                fbook = storage.read(book);
//                File cover = Storage.coverFile(getContext(), book);
//                if (!cover.exists() || cover.length() == 0)
//                    storage.createCover(fbook, cover);
//                book.cover = cover;
//                try {
//                    Bitmap bm = BitmapFactory.decodeStream(new FileInputStream(cover));
//                    return bm;
//                } catch (IOException e) {
//                    cover.delete();
//                    throw new RuntimeException(e);
//                }
//            } catch (RuntimeException e) {
//                Log.e(TAG, "Unable to load cover", e);
//            } finally {
//                if (fbook != null)
//                    fbook.close();
//            }
//            return null;
//        }
//
//
//        @Override
//        public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object item, Object view) {
//            super.downloadTaskUpdate(task, item, view);
//            BookHolder h = new BookHolder((View) view);
//            Storage.Book b = (Storage.Book) item;
//            if (b.cover != null && b.cover.exists()) {
//                try {
//                    Bitmap bm = BitmapFactory.decodeStream(new FileInputStream(b.cover));
//                    h.image.setImageBitmap(bm);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//
//    }
//
//    public static abstract class BooksAdapter extends CacheImagesRecyclerAdapter<BooksAdapter.BookHolder> {
//        String filter;
//        FragmentHolder holder;
//        HttpClient client = new HttpClient(); // images client
//
//        public static class BookHolder extends RecyclerView.ViewHolder {
//            TextView aa;
//            TextView tt;
//            ImageView image;
//            ProgressBar progress;
//
//            public BookHolder(View itemView) {
//                super(itemView);
//                aa = itemView.findViewById(R.id.book_authors);
//                tt = itemView.findViewById(R.id.book_title);
//                image = itemView.findViewById(R.id.book_cover);
//                progress = itemView.findViewById(R.id.book_progress);
//            }
//        }
//
//        public BooksAdapter(Context context, FragmentHolder holder) {
//            super(context);
//            this.holder = holder;
//        }
//
//        public Uri getCover(int position) {
//            return null;
//        }
//
//        public String getAuthors(int position) {
//            return "";
//        }
//
//        public String getTitle(int position) {
//            return "";
//        }
//
//        public void refresh() {
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public int getItemViewType(int position) {
//            return -1;
//        }
//
//        @Override
//        public BookHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//            LayoutInflater inflater = LayoutInflater.from(getContext());
//            View convertView = inflater.inflate(viewType, parent, false);
//            return new BookHolder(convertView);
//        }
//
//        @Override
//        public void onBindViewHolder(final BookHolder h, int position) {
//            h.itemView.setOnClickListener(v -> {
//                if (holder.clickListener != null)
//                    holder.clickListener.onItemClick(null, v, h.getAdapterPosition(), -1);
//            });
//            h.itemView.setOnLongClickListener(v -> {
//                if (holder.longClickListener != null)
//                    holder.longClickListener.onItemLongClick(null, v, h.getAdapterPosition(), -1);
//                return true;
//            });
//            setText(h.aa, getAuthors(position));
//            setText(h.tt, getTitle(position));
//        }
//
//        @Override
//        public Bitmap downloadImage(Uri cover, File f) throws IOException {
//            HttpClient.DownloadResponse w = client.getResponse(null, cover.toString());
//            FileOutputStream out = new FileOutputStream(f);
//            IOUtils.copy(w.getInputStream(), out);
//            w.getInputStream().close();
//            out.close();
//            Bitmap bm = CacheImagesAdapter.createScaled(new FileInputStream(f));
//            FileOutputStream os = new FileOutputStream(f);
//            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
//            os.close();
//            return bm;
//        }
//
//        @Override
//        public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object item, Object view) {
//            BookHolder h = new BookHolder((View) view);
//            updateView(task, h.image, h.progress);
//        }
//
//        @Override
//        public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
//            Uri u = (Uri) task.item;
//            return downloadImage(u);
//        }
//
//        void setText(TextView t, String s) {
//            if (t == null)
//                return;
//            TextMax m = null;
//            if (t.getParent() instanceof TextMax)
//                m = (TextMax) t.getParent();
//            ViewParent p = t.getParent();
//            if (s == null || s.isEmpty()) {
//                t.setVisibility(View.GONE);
//                if (m != null)
//                    m.setVisibility(View.GONE);
//                return;
//            }
//            t.setVisibility(View.VISIBLE);
//            t.setText(s);
//            if (m != null)
//                m.setVisibility(View.VISIBLE);
//        }
//    }
//
//    public LibraryFragment() {
//    }
//
//    public static LibraryFragment newInstance() {
//        LibraryFragment fragment = new LibraryFragment();
//        Bundle args = new Bundle();
//        fragment.setArguments(args);
//        return fragment;
//    }
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        storage = new Storage(getContext());
//        holder = new FragmentHolder(getContext());
//        books = new LibraryAdapter(holder);
//        setHasOptionsMenu(true);
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        books.load();
//        books.refresh();
//    }
//
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        View v = inflater.inflate(R.layout.fragment_library, container, false);
//
//        holder.create(v);
//        holder.footer.setVisibility(View.GONE);
//
//        final MainActivity mainActivity = (MainActivity) getActivity();
//        if (mainActivity != null) {
//            mainActivity.toolbar.setTitle(R.string.app_name);
//        }
//        holder.grid.setAdapter(books);
//        Uri uri = Uri.parse("/storage/emulated/0/Android/data/com.github.axet.bookreader/files/1984.epub");
////        mainActivity.loadBook(uri, null);
////        Storage.Book b1 = new Storage.Book(getContext(), uri);
////        mainActivity.loadBook(b1);
//        holder.setOnItemClickListener((parent, view, position, id) -> {
//            Storage.Book b = books.getItem(position);
//            mainActivity.loadBook(b);
//            Log.d("AAAA", "onCreateView: book clicked ");
//            Log.d("AAAA", "onCreateView: " + b.url.getPath());
//        });
//        holder.setOnItemLongClickListener((parent, view, position, id) -> {
//            final Storage.Book b = books.getItem(position);
//            PopupMenu popup = new PopupMenu(getContext(), view);
//            popup.inflate(R.menu.bookitem_menu);
//            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//                @Override
//                public boolean onMenuItemClick(MenuItem item) {
//                    if (item.getItemId() == R.id.action_rename) {
//                        final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
//                        e.setTitle(R.string.book_rename);
//                        e.setText(b.info.title);
//                        e.setPositiveButton(new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                String name = e.getText();
//                                b.info.title = name;
//                                storage.save(b);
//                                books.notifyDataSetChanged();
//                            }
//                        });
//                        AlertDialog d = e.create();
//                        d.show();
//                    }
//                    if (item.getItemId() == R.id.action_open) {
//                        String ext = Storage.getExt(getContext(), b.url);
//                        String n = Storage.getTitle(b.info) + "." + ext;
//                        Intent open = StorageProvider.getProvider().openIntent(b.url, n);
//                        startActivity(open);
//                    }
//                    if (item.getItemId() == R.id.action_share) {
//                        String ext = Storage.getExt(getContext(), b.url);
//                        String t = Storage.getTitle(b.info) + "." + ext;
//                        String name = Storage.getName(getContext(), b.url);
//                        String type = Storage.getTypeByName(name);
//                        Intent share = StorageProvider.getProvider().shareIntent(b.url, t, type, t);
//                        startActivity(share);
//                    }
//                    if (item.getItemId() == R.id.action_delete) {
//                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
//                        builder.setTitle(R.string.book_delete);
//                        builder.setMessage(R.string.are_you_sure);
//                        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                            }
//                        });
//                        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                storage.delete(b);
//                                books.delete(b);
//                            }
//                        });
//                        builder.show();
//                    }
//                    return true;
//                }
//            });
//            popup.show();
//            return true;
//        });
//        return v;
//    }
//
//    @Override
//    public void onStart() {
//        super.onStart();
//        MainActivity main = ((MainActivity) getActivity());
//        main.setFullscreen(false);
//    }
//
//    @Override
//    public void onAttach(Context context) {
//        super.onAttach(context);
//    }
//
//    @Override
//    public void onDetach() {
//        super.onDetach();
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        books.clearTasks();
//    }
//
//    @Override
//    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//
//        invalidateOptionsMenu = InvalidateOptionsMenuCompat.onCreateOptionsMenu(this, menu, inflater);
//
//        MenuItem homeMenu = menu.findItem(R.id.action_home);
//        MenuItem tocMenu = menu.findItem(R.id.action_toc);
//        MenuItem bookmarksMenu = menu.findItem(R.id.action_bm);
//        MenuItem searchMenu = menu.findItem(R.id.action_search);
//        MenuItem reflow = menu.findItem(R.id.action_reflow);
//        MenuItem fontsize = menu.findItem(R.id.action_fontsize);
//        MenuItem debug = menu.findItem(R.id.action_debug);
//        MenuItem rtl = menu.findItem(R.id.action_rtl);
//        MenuItem mode = menu.findItem(R.id.action_mode);
//        MenuItem sort = menu.findItem(R.id.action_sort);
//        MenuItem tts = menu.findItem(R.id.action_tts);
//
//        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
//        int selected = getContext().getResources().getIdentifier(shared.getString(BookApplication.PREFERENCE_SORT, getContext().getResources().getResourceEntryName(R.id.sort_add_ask)), "id", getContext().getPackageName());
//        SubMenu sorts = sort.getSubMenu();
//        for (int i = 0; i < sorts.size(); i++) {
//            MenuItem m = sorts.getItem(i);
//            if (m.getItemId() == selected)
//                m.setChecked(true);
//            m.setOnMenuItemClickListener(item -> false);
//        }
//
//        reflow.setVisible(false);
//        searchMenu.setVisible(true);
//        homeMenu.setVisible(false);
//        tocMenu.setVisible(false);
//        bookmarksMenu.setVisible(books.hasBookmarks());
//        fontsize.setVisible(false);
//        debug.setVisible(false);
//        rtl.setVisible(false);
//        mode.setVisible(false);
//        tts.setVisible(false);
//
//        holder.onCreateOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        if (holder.onOptionsItemSelected(item)) {
//            invalidateOptionsMenu.run();
//            return true;
//        }
//        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
//        int id = item.getItemId();
//        if (id == R.id.sort_add_ask || id == R.id.sort_add_desc || id == R.id.sort_name_ask || id == R.id.sort_name_desc || id == R.id.sort_open_ask || id == R.id.sort_open_desc) {
//            shared.edit().putString(BookApplication.PREFERENCE_SORT, getContext().getResources().getResourceEntryName(item.getItemId())).commit();
//            books.sort();
//            invalidateOptionsMenu.run();
//            return true;
//        } else if (id == R.id.action_bm) {
//            BookmarksDialog dialog = new BookmarksDialog(getContext()) {
//                @Override
//                public void onSelected(Storage.Book b, Storage.Bookmark bm) {
//                    MainActivity main = ((MainActivity) getActivity());
//                    main.openBook(b.url, new FBReaderView.ZLTextIndexPosition(bm.start, bm.end));
//                }
//
//                @Override
//                public void onSave(Storage.Book book, Storage.Bookmark bm) {
//                    storage.save(book);
//                }
//
//                @Override
//                public void onDelete(Storage.Book book, Storage.Bookmark bm) {
//                    book.info.bookmarks.remove(bm);
//                    storage.save(book);
//                }
//            };
//            dialog.load(books.all);
//            dialog.show();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
//
//    public void search(String s) {
//        books.filter = s;
//        books.refresh();
//        lastSearch = books.filter;
//    }
//
//    @Override
//    public void searchClose() {
//        search("");
//    }
//
//    @Override
//    public String getHint() {
//        return getString(R.string.search_local);
//    }
//
//    @Override
//    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
//        super.onViewStateRestored(savedInstanceState);
//    }
//
//    @Override
//    public void onSaveInstanceState(Bundle outState) {
//        super.onSaveInstanceState(outState);
//    }
//}
