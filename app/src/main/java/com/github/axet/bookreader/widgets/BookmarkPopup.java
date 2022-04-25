package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.github.axet.androidlibrary.widgets.PopupWindowCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.Storage;

import java.util.ArrayList;

public class BookmarkPopup { // bookmark click popup
    public static int COLORS[] = new int[]{0xffff0000, 0xffFF8000, 0xffFFFF00, 0xff00FF00, 0xff0000FF, 0xff3F00FF, 0xff7F00FF};

    public Context context;
    public PopupWindow w;
    public View v;

    public BookmarkPopup(View v, final Storage.Bookmark l, final ArrayList<View> bmv) {
        this.context = v.getContext();
        this.v = v;

        LayoutInflater inflater = LayoutInflater.from(getContext());
        final EditText name = new EditText(getContext());
        name.setText(l.name);
        if (l.name != null)
            name.setSelection(l.name.length());
        LinearLayout ll = new LinearLayout(getContext()) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                l.name = name.getText().toString();
                l.last = System.currentTimeMillis();
                onDismiss();
            }
        };
        ll.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout hh = new LinearLayout(getContext());
        int dp5 = ThemeUtils.dp2px(getContext(), 5);
        hh.setPadding(dp5, dp5, dp5, dp5);
        int dp1 = ThemeUtils.dp2px(getContext(), 1);
        int dp2 = ThemeUtils.dp2px(getContext(), 2);
        for (int c : COLORS) {
            final View color = inflater.inflate(R.layout.bm_color, null);
            ImageView image = (ImageView) color.findViewById(R.id.color);
            image.setColorFilter(c);
            final ImageView check = (ImageView) color.findViewById(R.id.checkbox);
            check.setVisibility(l.color == c ? View.VISIBLE : View.GONE);
            check.setColorFilter(ColorUtils.calculateLuminance(c) < 0.5f ? Color.WHITE : Color.GRAY);
            color.setTag(c);
            color.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    l.color = (int) color.getTag();
                    l.last = System.currentTimeMillis();
                    for (View b : bmv)
                        b.setBackgroundColor(SelectionView.SELECTION_ALPHA << 24 | (l.color & 0xffffff));
                    for (int i = 0; i < hh.getChildCount(); i++) {
                        View color = hh.getChildAt(i);
                        final ImageView check = (ImageView) color.findViewById(R.id.checkbox);
                        if (check != null) // trash icon
                            check.setVisibility((int) color.getTag() == l.color ? View.VISIBLE : View.GONE);
                    }
                    onSelect(l.color);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp1, dp1, dp1, dp1);
            hh.addView(color, lp);
        }
        w = new PopupWindow();
        ImageView trash = new ImageView(getContext());
        trash.setImageResource(R.drawable.ic_close_black_24dp);
        trash.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        trash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_bookmark);
                builder.setMessage(R.string.are_you_sure);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onDelete(l);
                        w.dismiss();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.setMargins(dp1, dp1, dp1, dp1);
        hh.addView(trash, lp);
        ll.addView(hh);
        ll.addView(name);
        w.setContentView(ll);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    public Context getContext() {
        return context;
    }

    public void show() {
        PopupWindowCompat.showAsTooltip(w, v, Gravity.BOTTOM);
    }

    public void onDelete(Storage.Bookmark l) {
    }

    public void onDismiss() {
    }

    public void onSelect(int color) {
    }
}
