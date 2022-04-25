package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.TextMax;
import com.github.axet.androidlibrary.widgets.TreeListView;
import com.github.axet.androidlibrary.widgets.TreeRecyclerView;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.Storage;

import java.util.ArrayList;
import java.util.List;

public class BookmarksDialog extends AlertDialog.Builder { // bookmarks list dialog
    BMAdapter a;
    TreeRecyclerView tree;
    AlertDialog dialog;

    public static class BMHolder extends TreeRecyclerView.TreeHolder {
        ImageView image;
        TextView text;
        TextView name;

        public BMHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
            name = (TextView) itemView.findViewById(R.id.name);
        }
    }

    public class BMAdapter extends TreeRecyclerView.TreeAdapter<BMHolder> {
        public BMAdapter() {
        }

        public BMAdapter(List<Storage.Bookmark> tree) {
            load(root, tree);
            load();
        }

        void load(TreeListView.TreeNode r, List<Storage.Bookmark> tree) {
            for (Storage.Bookmark t : tree) {
                TreeListView.TreeNode n = new TreeListView.TreeNode(r, t);
                r.nodes.add(n);
            }
        }

        @Override
        public BMHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View convertView = inflater.inflate(R.layout.bm_item, parent, false);
            return new BMHolder(convertView);
        }

        @Override
        public void onBindViewHolder(final BMHolder h, int position) {
            final TreeListView.TreeNode t = getItem(h.getAdapterPosition(this));
            final Storage.Bookmark tt = (Storage.Bookmark) t.tag;
            ImageView ex = (ImageView) h.itemView.findViewById(R.id.expand);
            if (t.nodes.isEmpty())
                ex.setVisibility(View.INVISIBLE);
            else
                ex.setVisibility(View.VISIBLE);
            ex.setImageResource(t.expanded ? R.drawable.ic_expand_less_black_24dp : R.drawable.ic_expand_more_black_24dp);
            h.itemView.setPadding(20 * t.level, 0, 0, 0);
            if (t.selected) {
                h.text.setTypeface(null, Typeface.BOLD);
                h.image.setColorFilter(null);
            } else {
                h.image.setColorFilter(Color.GRAY);
                h.text.setTypeface(null, Typeface.NORMAL);
            }
            h.text.setText(tt.text.replaceAll("\n", " "));
            if (tt.name == null || tt.name.isEmpty()) {
                ((TextMax) h.name.getParent()).setVisibility(View.GONE);
            } else {
                h.name.setText(tt.name.replaceAll("\n", " "));
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Storage.Bookmark n = (Storage.Bookmark) getItem(h.getAdapterPosition(BMAdapter.this)).tag;
                    onSelected(n);
                    dialog.dismiss();
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    PopupMenu menu = new PopupMenu(getContext(), h.itemView);
                    final MenuInflater inflater = new MenuInflater(getContext());
                    inflater.inflate(R.menu.bookmark_menu, menu.getMenu());
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            int id = item.getItemId();
                            if (id == R.id.action_edit) {
                                BookmarkPopup popup = new BookmarkPopup(h.itemView, tt, new ArrayList<View>()) {
                                    @Override
                                    public void onDismiss() {
                                        if (t.parent == root) {
                                            onSave(tt);
                                        } else {
                                            Storage.Book b = (Storage.Book) t.parent.tag;
                                            onSave(b, tt);
                                        }
                                        notifyDataSetChanged();
                                    }
                                };
                                popup.show();
                            } else if (id == R.id.action_open) {
                                if (t.parent == root) {
                                    onSave(tt);
                                } else {
                                    Storage.Book b = (Storage.Book) t.parent.tag;
                                    onSave(b, tt);
                                }
                            } else if (id == R.id.action_share) {
                                String subject;
                                String text;
                                if (t.parent == root) {
                                    subject = tt.name;
                                } else {
                                    Storage.Book b = (Storage.Book) t.parent.tag;
                                    subject = Storage.getTitle(b.info);
                                }
                                text = tt.text + "\n\n" + tt.name;
                                Intent intent = new Intent(Intent.ACTION_SEND);
                                intent.setType(HttpClient.CONTENTTYPE_TEXT);
                                intent.putExtra(Intent.EXTRA_EMAIL, "");
                                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                                intent.putExtra(Intent.EXTRA_TEXT, text);
                                if (OptimizationPreferenceCompat.isCallable(getContext(), intent))
                                    getContext().startActivity(intent);
                            } else if (id == R.id.action_delete) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setTitle(R.string.delete_bookmark);
                                builder.setMessage(R.string.are_you_sure);
                                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (t.parent == root) {
                                            onDelete(tt);
                                        } else {
                                            Storage.Book b = (Storage.Book) t.parent.tag;
                                            onDelete(b, tt);
                                        }
                                        items.remove(t);
                                        notifyDataSetChanged();
                                    }
                                });
                                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                                builder.show();
                            }
                            return true;
                        }
                    });
                    menu.show();
                    return false;
                }
            });
        }
    }

    public class BMAdapterBooks extends BMAdapter {
        public BMAdapterBooks(ArrayList<Storage.Book> books) {
            loadBooks(root, books);
            load();
        }

        void loadBooks(TreeListView.TreeNode r, List<Storage.Book> books) {
            for (Storage.Book b : books) {
                if (b.info.bookmarks != null) {
                    TreeListView.TreeNode n = new TreeListView.TreeNode(r, b);
                    r.nodes.add(n);
                    load(n, b.info.bookmarks);
                }
            }
        }

        @Override
        public void onBindViewHolder(final BMHolder h, int position) {
            final TreeListView.TreeNode t = getItem(h.getAdapterPosition(this));
            if (t.tag instanceof Storage.Bookmark) {
                super.onBindViewHolder(h, position);
                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Storage.Book tt = (Storage.Book) t.parent.tag;
                        Storage.Bookmark n = (Storage.Bookmark) getItem(h.getAdapterPosition(BMAdapterBooks.this)).tag;
                        onSelected(tt, n);
                        dialog.dismiss();
                    }
                });
            } else {
                Storage.Book tt = (Storage.Book) t.tag;
                ImageView ex = (ImageView) h.itemView.findViewById(R.id.expand);
                if (t.nodes.isEmpty())
                    ex.setVisibility(View.INVISIBLE);
                else
                    ex.setVisibility(View.VISIBLE);
                ex.setImageResource(t.expanded ? R.drawable.ic_expand_less_black_24dp : R.drawable.ic_expand_more_black_24dp);
                h.itemView.setPadding(20 * t.level, 0, 0, 0);
                if (t.selected) {
                    h.text.setTypeface(null, Typeface.BOLD);
                    h.image.setColorFilter(null);
                } else {
                    h.image.setColorFilter(Color.GRAY);
                    h.text.setTypeface(null, Typeface.NORMAL);
                }
                h.text.setText(Storage.getTitle(tt.info));
                ((TextMax) h.name.getParent()).setVisibility(View.GONE);
            }
        }
    }

    public BookmarksDialog(Context context) {
        super(context);
    }

    public void load(ArrayList<Storage.Book> all) {
        a = new BMAdapterBooks(all);
        tree = new TreeRecyclerView(getContext());
        tree.setAdapter(a);
        setView(tree);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    public void load(Storage.Bookmarks bm) {
        a = new BMAdapter(bm);
        tree = new TreeRecyclerView(getContext());
        tree.setAdapter(a);
        setView(tree);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    @Override
    public AlertDialog create() {
        dialog = super.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
            }
        });
        return dialog;
    }

    @Override
    public AlertDialog show() {
        return super.show();
    }

    public void onSelected(Storage.Bookmark b) {
    }

    public void onSelected(Storage.Book book, Storage.Bookmark bm) {
    }

    public void onSave(Storage.Book book, Storage.Bookmark bm) {
    }

    public void onSave(Storage.Bookmark bm) {
    }

    public void onDelete(Storage.Book book, Storage.Bookmark bm) {
    }

    public void onDelete(Storage.Bookmark bm) {
    }
}
