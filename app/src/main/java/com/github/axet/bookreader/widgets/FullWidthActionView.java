package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.github.axet.bookreader.R;

public class FullWidthActionView extends FrameLayout {
    public FullWidthActionView(Context context) {
        super(context);
    }

    public FullWidthActionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FullWidthActionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FullWidthActionView(Context context, int id) {
        super(context);
        LayoutInflater c = LayoutInflater.from(context);
        c.inflate(id, this);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ViewGroup f = (ViewGroup) getParent(); // FrameLayout
        ViewGroup m = (ViewGroup) f.getParent(); // NavigationMenuItemView
        View t = m.findViewById(R.id.design_menu_item_text);
        if (t != null)
            t.setVisibility(GONE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

}