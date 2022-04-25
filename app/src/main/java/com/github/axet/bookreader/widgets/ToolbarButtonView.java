package com.github.axet.bookreader.widgets;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.R;

@Keep
public class ToolbarButtonView extends FrameLayout {
    public AppCompatImageButton image;
    public TextView text;

    public ToolbarButtonView(@NonNull Context context) {
        super(context);
        create();
    }

    public ToolbarButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public ToolbarButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public ToolbarButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @SuppressLint("RestrictedApi")
    public void create() {
        image = new AppCompatImageButton(getContext());
        image.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        addView(image, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        text = new TextView(new ContextThemeWrapper(getContext(), R.style.toolbar_bottom_icon_text)); // missing margings
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = ThemeUtils.dp2px(getContext(), 7);
        addView(text, lp);
    }
}
