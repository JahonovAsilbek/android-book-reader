package com.github.axet.bookreader.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.R;

@Keep
public class ToolbarFontSizeView extends ToolbarButtonView {
    public ToolbarFontSizeView(@NonNull Context context) {
        super(context);
    }

    public ToolbarFontSizeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ToolbarFontSizeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public ToolbarFontSizeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void create() {
        super.create();
        image.setClickable(false);
        image.setFocusable(false);
        image.setImageResource(R.drawable.ic_format_size_white_24dp);
        image.setColorFilter(ThemeUtils.getColor(getContext(), R.color.white));
        image.setBackgroundDrawable(null);
        text.setText("0.8");
    }
}
