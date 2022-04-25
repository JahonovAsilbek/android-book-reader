package com.github.axet.bookreader.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.R;

import org.geometerplus.fbreader.fbreader.FBView;
import org.geometerplus.fbreader.fbreader.options.ColorProfile;
import org.geometerplus.fbreader.fbreader.options.FooterOptions;
import org.geometerplus.zlibrary.core.fonts.FontEntry;
import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.ui.android.view.AndroidFontUtil;
import org.geometerplus.zlibrary.ui.android.view.ZLAndroidPaintContext;

import java.util.concurrent.atomic.AtomicInteger;

public class FBFooterView extends LinearLayout {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    FBReaderView fb;
    FBReaderView.CustomView customview;
    FBView.Footer footer;
    ZLTextView.PagePosition pagePosition;
    String family;
    Typeface tf;

    public static int generateViewId() { // ViewCompat API27
        if (Build.VERSION.SDK_INT >= 17)
            return View.generateViewId();
        for (; ; ) {
            final int result = sNextGeneratedId.get();
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue))
                return result;
        }
    }

    public class TOCMarks extends View {
        public TOCMarks(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int w = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            int h = footer.getHeight();
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            ZLAndroidPaintContext context = new ZLAndroidPaintContext(
                    fb.app.SystemInfo,
                    c,
                    new ZLAndroidPaintContext.Geometry(
                            getWidth(),
                            getHeight(),
                            getWidth(),
                            footer.getHeight(),
                            0,
                            getHeight()
                    ),
                    0
            );
            footer.paint(context);
        }
    }

    public class FontTextView extends View {
        String text;
        Paint paint = new Paint();
        Rect r = new Rect();

        public FontTextView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            w = r.width() + getPaddingLeft() + getPaddingRight();
            int h = footer.getHeight();
            setMeasuredDimension(w, h);
        }

        public void update() {
            paint.setTypeface(tf);
            paint.setTextSize(footer.getHeight() + 2);
            final ColorProfile cProfile = fb.app.ViewOptions.getColorProfile();
            paint.setColor(0xffffff & cProfile.FooterNGForegroundOption.getValue().intValue() | 0xff000000);
        }

        public void setText(String str) {
            text = str;
            Rect r = new Rect();
            paint.getTextBounds(text, 0, text.length(), r);
            if (!this.r.equals(r)) {
                this.r = r;
                requestLayout();
            } else {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawText(text, getPaddingLeft(), getHeight() / 2 - ((paint.descent() + paint.ascent()) / 2), paint);
        }
    }

    public class ProgressAsPages extends FontTextView {
        public ProgressAsPages(Context context) {
            super(context);
        }

        public void update() {
            super.update();
            setText(pagePosition.Current + "/" + pagePosition.Total);
        }
    }

    public class ProgressAsPercentage extends FontTextView {
        public ProgressAsPercentage(Context context) {
            super(context);
        }

        public void update() {
            super.update();
            setText(100 * pagePosition.Current / pagePosition.Total + "%");
        }
    }

    public class Clock extends FontTextView {
        public Clock(Context context) {
            super(context);
        }

        public void update() {
            super.update();
            setText(ZLibrary.Instance().getCurrentTimeString());
        }
    }

    public class Battery extends FontTextView {
        public Battery(Context context) {
            super(context);
        }

        public void update() {
            super.update();
            setText(fb.app.getBatteryLevel() + "%");
        }
    }

    public FBFooterView(Context context) {
        super(context);
    }

    public FBFooterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(11)
    public FBFooterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public FBFooterView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public FBFooterView(Context context, FBReaderView fb) {
        this(context);
        setId(FBFooterView.generateViewId());
        create(fb);
    }

    public void create(FBReaderView fb) {
        this.fb = fb;
        update();
        setOrientation(HORIZONTAL);
        final ColorProfile cProfile = fb.app.ViewOptions.getColorProfile();
        setBackgroundColor(0xffffff & cProfile.FooterNGBackgroundOption.getValue().intValue() | 0xff000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.gravity = Gravity.CENTER;
        addView(new TOCMarks(getContext()), lp);
        FooterOptions footerOptions = fb.app.ViewOptions.getFooterOptions();
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, footer.getHeight());
        lpText.gravity = Gravity.CENTER;
        if (footerOptions.showProgressAsPages())
            addView(new ProgressAsPages(getContext()), lpText);
        if (footerOptions.showProgressAsPercentage() && pagePosition.Total != 0)
            addView(new ProgressAsPercentage(getContext()), lpText);
        if (footerOptions.ShowClock.getValue()) {
            Clock clock = new Clock(getContext());
            int dp4 = ThemeUtils.dp2px(getContext(), 4);
            int dp2 = ThemeUtils.dp2px(getContext(), 2);
            clock.setPadding(dp4, 0, dp2, 0);
            addView(clock, lpText);
        }
        if (footerOptions.ShowBattery.getValue()) {
            AppCompatImageView image = new AppCompatImageView(getContext());
            image.setImageResource(R.drawable.ic_battery_std_24);
            image.setColorFilter(0xffffff & cProfile.FooterNGForegroundOption.getValue().intValue() | 0xff000000);
            LinearLayout.LayoutParams lpImage = new LinearLayout.LayoutParams(footer.getHeight(), footer.getHeight());
            lpImage.gravity = Gravity.CENTER;
            addView(image, lpImage);
            addView(new Battery(getContext()), lpText);
        }
        setPadding(0, 0, customview.getRightMargin(), 0);
    }

    public void update() {
        customview = (FBReaderView.CustomView) fb.app.BookTextView;
        footer = customview.getFooter();
        pagePosition = customview.pagePosition();
        family = fb.app.ViewOptions.getFooterOptions().Font.getValue();
        tf = AndroidFontUtil.typeface(fb.app.SystemInfo, FontEntry.systemEntry(family), footer.getHeight() > 10, false);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof FontTextView)
                ((FontTextView) v).update();
            if (v instanceof TOCMarks)
                v.invalidate();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        update();
    }
}
