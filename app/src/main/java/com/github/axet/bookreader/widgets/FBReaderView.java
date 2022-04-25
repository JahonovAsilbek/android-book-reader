package com.github.axet.bookreader.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.ClipboardManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.PinchView;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.BookApplication;
import com.github.axet.bookreader.app.Plugin;
import com.github.axet.bookreader.app.Reflow;
import com.github.axet.bookreader.app.Storage;
import com.github.axet.bookreader.services.ImagesProvider;
import com.github.johnpersano.supertoasts.SuperActivityToast;
import com.github.johnpersano.supertoasts.SuperToast;
import com.github.johnpersano.supertoasts.util.OnClickWrapper;
import com.github.johnpersano.supertoasts.util.OnDismissWrapper;

import org.geometerplus.android.fbreader.NavigationPopup;
import org.geometerplus.android.fbreader.PopupPanel;
import org.geometerplus.android.fbreader.SelectionPopup;
import org.geometerplus.android.fbreader.TextSearchPopup;
import org.geometerplus.android.fbreader.dict.DictionaryUtil;
import org.geometerplus.android.fbreader.libraryService.BookCollectionShadow;
import org.geometerplus.android.util.UIMessageUtil;
import org.geometerplus.android.util.UIUtil;
import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.bookmodel.FBHyperlinkType;
import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.fbreader.ActionCode;
import org.geometerplus.fbreader.fbreader.FBAction;
import org.geometerplus.fbreader.fbreader.FBView;
import org.geometerplus.fbreader.fbreader.options.ColorProfile;
import org.geometerplus.fbreader.fbreader.options.FooterOptions;
import org.geometerplus.fbreader.fbreader.options.ImageOptions;
import org.geometerplus.fbreader.fbreader.options.MiscOptions;
import org.geometerplus.fbreader.fbreader.options.PageTurningOptions;
import org.geometerplus.fbreader.formats.FormatPlugin;
import org.geometerplus.fbreader.util.AutoTextSnippet;
import org.geometerplus.fbreader.util.TextSnippet;
import org.geometerplus.zlibrary.core.application.ZLApplication;
import org.geometerplus.zlibrary.core.application.ZLApplicationWindow;
import org.geometerplus.zlibrary.core.options.Config;
import org.geometerplus.zlibrary.core.options.StringPair;
import org.geometerplus.zlibrary.core.options.ZLOption;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.util.ZLColor;
import org.geometerplus.zlibrary.core.view.ZLPaintContext;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.core.view.ZLViewWidget;
import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.ZLTextControlElement;
import org.geometerplus.zlibrary.text.view.ZLTextElement;
import org.geometerplus.zlibrary.text.view.ZLTextElementArea;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextHighlighting;
import org.geometerplus.zlibrary.text.view.ZLTextHyperlink;
import org.geometerplus.zlibrary.text.view.ZLTextHyperlinkRegionSoul;
import org.geometerplus.zlibrary.text.view.ZLTextImageElement;
import org.geometerplus.zlibrary.text.view.ZLTextImageRegionSoul;
import org.geometerplus.zlibrary.text.view.ZLTextParagraphCursor;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextRegion;
import org.geometerplus.zlibrary.text.view.ZLTextSimpleHighlighting;
import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;
import org.geometerplus.zlibrary.text.view.ZLTextWordRegionSoul;
import org.geometerplus.zlibrary.ui.android.view.ZLAndroidPaintContext;
import org.geometerplus.zlibrary.ui.android.view.ZLAndroidWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FBReaderView extends RelativeLayout {
    public static final String ACTION_MENU = FBReaderView.class.getCanonicalName() + ".ACTION_MENU";

    public static final int PAGE_OVERLAP_PERCENTS = 5; // percents
    public static final int PAGE_PAPER_COLOR = 0x80ffffff;

    public enum Widgets {PAGING, CONTINUOUS}

    public FBReaderApp app;
    public ConfigShadow config;
    public ZLViewWidget widget;
    public int battery;
    public Storage.FBook book;
    public Plugin.View pluginview;
    public Listener listener;
    String title;
    Window w;
    FBFooterView footer;
    SelectionView selection;
    ZLTextPosition scrollDelayed;
    DrawerLayout drawer;
    Plugin.View.Search search;
    public TTSPopup tts;
    int searchPagePending;

    public static void showControls(final ViewGroup p, final View areas) {
        p.removeCallbacks((Runnable) areas.getTag());
        p.addView(areas);
        Runnable hide = new Runnable() {
            @Override
            public void run() {
                hideControls(p, areas);
            }
        };
        areas.setTag(hide);
        p.postDelayed(hide, 3000);
    }

    public static void hideControls(final ViewGroup p, final View areas) {
        p.removeCallbacks((Runnable) areas.getTag());
        areas.setTag(null);
        if (Build.VERSION.SDK_INT >= 11) {
            ValueAnimator v = ValueAnimator.ofFloat(1f, 0f);
            v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                @TargetApi(11)
                public void onAnimationUpdate(ValueAnimator animation) {
                    ViewCompat.setAlpha(areas, (float) animation.getAnimatedValue());
                }
            });
            v.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    p.removeView(areas);
                }
            });
            v.setDuration(500);
            v.start();
        } else {
            p.removeView(areas);
        }
    }

    public static Intent translateIntent(String text) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PROCESS_TEXT);
        intent.setType(HttpClient.CONTENTTYPE_TEXT);
        intent.setPackage("com.google.android.apps.translate"); // only known translator
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        return intent;
    }

    public static Rect findUnion(List<ZLTextElementArea> areas, Storage.Bookmark bm) {
        Rect union = null;
        for (ZLTextElementArea a : areas) {
            if (bm.start.compareTo(a) <= 0 && bm.end.compareTo(a) >= 0) {
                if (union == null)
                    union = new Rect(a.XStart, a.YStart, a.XEnd, a.YEnd);
                else
                    union.union(a.XStart, a.YStart, a.XEnd, a.YEnd);
            }
        }
        return union;
    }

    public static class ZLTextIndexPosition extends ZLTextFixedPosition implements Parcelable {
        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
            public ZLTextIndexPosition createFromParcel(Parcel in) {
                return new ZLTextIndexPosition(in);
            }

            public ZLTextIndexPosition[] newArray(int size) {
                return new ZLTextIndexPosition[size];
            }
        };

        public ZLTextPosition end;

        static ZLTextPosition read(Parcel in) {
            int p = in.readInt();
            int e = in.readInt();
            int c = in.readInt();
            return new ZLTextFixedPosition(p, e, c);
        }

        public ZLTextIndexPosition(ZLTextPosition p, ZLTextPosition e) {
            super(p);
            end = new ZLTextFixedPosition(e);
        }

        public ZLTextIndexPosition(Parcel in) {
            super(read(in));
            end = read(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(getParagraphIndex());
            dest.writeInt(getElementIndex());
            dest.writeInt(getCharIndex());
            dest.writeInt(end.getParagraphIndex());
            dest.writeInt(end.getElementIndex());
            dest.writeInt(end.getCharIndex());
        }
    }

    public static class ZLBookmark extends ZLTextSimpleHighlighting {
        public FBView view;
        public Storage.Bookmark b;

        public ZLBookmark(FBView view, Storage.Bookmark b) {
            super(view, b.start, b.end);
            this.view = view;
            this.b = b;
        }

        @Override
        public ZLColor getForegroundColor() {
            return null;
        }

        @Override
        public ZLColor getBackgroundColor() {
            if (b.color != 0)
                return new ZLColor(b.color);
            return view.getHighlightingBackgroundColor();
        }

        @Override
        public ZLColor getOutlineColor() {
            return null;
        }
    }

    public static class ZLTTSMark extends ZLBookmark {
        public ZLTTSMark(FBView view, Storage.Bookmark m) {
            super(view, m);
        }
    }

    public interface Listener {
        void onScrollingFinished(ZLViewEnums.PageIndex index);

        void onSearchClose();

        void onBookmarksUpdate();

        void onDismissDialog();

        void ttsStatus(boolean speaking);
    }

    public class CustomView extends FBView {

        public class FooterNew extends FooterNewStyle {
            @Override
            protected String buildInfoString(PagePosition pagePosition, String separator) {
                return "";
            }
        }

        public class FooterOld extends FooterOldStyle {
            @Override
            protected String buildInfoString(PagePosition pagePosition, String separator) {
                return "";
            }
        }

        public CustomView(FBReaderApp reader) {
            super(reader);
        }

        public ZLAndroidPaintContext createContext(Canvas c) {
            return new ZLAndroidPaintContext(
                    app.SystemInfo,
                    c,
                    new ZLAndroidPaintContext.Geometry(
                            getWidth(),
                            getHeight(),
                            getWidth(),
                            getHeight(),
                            0,
                            0
                    ),
                    getVerticalScrollbarWidth()
            );
        }

        public Footer getFooter() {
            int type = SCROLLBAR_SHOW_AS_FOOTER; // app.ViewOptions.ScrollbarType.getValue();
            if (type == SCROLLBAR_SHOW_AS_FOOTER)
                return new FooterNew();
            if (type == SCROLLBAR_SHOW_AS_FOOTER_OLD_STYLE)
                return new FooterOld();
            return null;
        }

        public ZLAndroidPaintContext setContext() {
            ZLAndroidPaintContext context = createContext(new Canvas());
            setContext(context);
            return context;
        }

        @Override
        public Animation getAnimationType() {
            PowerManager pm = (PowerManager) FBReaderView.this.getContext().getSystemService(Context.POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 21 && pm.isPowerSaveMode())
                return Animation.none;
            else
                return super.getAnimationType();
        }

        public void setScalingType(ZLTextImageElement imageElement, ZLPaintContext.ScalingType s) {
            book.info.scales.put(imageElement.Id, s);
        }

        @Override
        protected ZLPaintContext.ScalingType getScalingType(ZLTextImageElement imageElement) {
            ZLPaintContext.ScalingType s = book.info.scales.get(imageElement.Id);
            if (s != null)
                return s;
            return super.getScalingType(imageElement);
        }

        @Override
        public void hideOutline() {
            super.hideOutline();
            if (widget instanceof ScrollWidget) {
                ((ScrollWidget) widget).adapter.processInvalidate();
                ((ScrollWidget) widget).adapter.processClear();
            }
        }

        @Override
        protected ZLTextRegion findRegion(int x, int y, int maxDistance, ZLTextRegion.Filter filter) {
            return super.findRegion(x, y, maxDistance, filter);
        }

        @Override
        public void onFingerSingleTap(int x, int y) {
            final ZLTextHighlighting highlighting = findHighlighting(x, y, maxSelectionDistance());
            if (highlighting instanceof ZLBookmark) {
                app.runAction(ActionCode.SELECTION_BOOKMARK, ((ZLBookmark) highlighting).b);
                return;
            }
            super.onFingerSingleTap(x, y);
        }

        @Override
        public void onFingerSingleTapLastResort(int x, int y) {
            if (widget instanceof ScrollWidget)
                onFingerSingleTapLastResort(((ScrollWidget) widget).gesturesListener.e);
            else
                super.onFingerSingleTapLastResort(x, y);
        }

        public void onFingerSingleTapLastResort(MotionEvent e) {
            setContext();
            super.onFingerSingleTapLastResort((int) e.getX(), (int) e.getY());
        }

        @Override
        public boolean twoColumnView() {
            if (widget instanceof ScrollWidget)
                return false;
            return super.twoColumnView();
        }

        @Override
        public boolean canScroll(PageIndex index) {
            if (pluginview != null)
                return pluginview.canScroll(index);
            else
                return super.canScroll(index);
        }

        @Override
        public synchronized void onScrollingFinished(PageIndex pageIndex) {
            if (pluginview != null) {
                if (pluginview.onScrollingFinished(pageIndex))
                    widget.reset();
            } else {
                super.onScrollingFinished(pageIndex);
            }
            FBReaderView.this.onScrollingFinished(pageIndex);
            if (widget instanceof ZLAndroidWidget)
                ((PagerWidget) widget).updateOverlays();
        }

        @Override
        public synchronized PagePosition pagePosition() { // Footer draw
            if (pluginview != null)
                return pluginview.pagePosition();
            else
                return super.pagePosition();
        }

        @Override
        public void gotoHome() {
            if (pluginview != null)
                pluginview.gotoPosition(new ZLTextFixedPosition(0, 0, 0));
            else
                super.gotoHome();
            resetNewPosition();
        }

        @Override
        public synchronized void gotoPage(int page) {
            if (pluginview != null)
                pluginview.gotoPosition(new ZLTextFixedPosition(page - 1, 0, 0));
            else
                super.gotoPage(page);
            resetNewPosition();
        }

        @Override
        public synchronized void paint(ZLPaintContext context, PageIndex pageIndex) {
            super.paint(context, pageIndex);
        }

        @Override
        public int getSelectionStartY() {
            if (selection != null)
                return selection.getSelectionStartY();
            return super.getSelectionStartY();
        }

        @Override
        public int getSelectionEndY() {
            if (selection != null)
                return selection.getSelectionEndY();
            return super.getSelectionEndY();
        }
    }

    public class FBApplicationWindow implements ZLApplicationWindow {
        @Override
        public void setWindowTitle(String title) {
            FBReaderView.this.title = title;
        }

        @Override
        public void showErrorMessage(String resourceKey) {
        }

        @Override
        public void showErrorMessage(String resourceKey, String parameter) {
        }

        @Override
        public ZLApplication.SynchronousExecutor createExecutor(String key) {
            return null;
        }

        @Override
        public void processException(Exception e) {
        }

        @Override
        public void refresh() {
            if (widget instanceof PagerWidget)
                ((PagerWidget) widget).updateOverlays();
        }

        @Override
        public ZLViewWidget getViewWidget() {
            return widget;
        }

        @Override
        public void close() {
        }

        @Override
        public int getBatteryLevel() {
            return battery;
        }
    }

    public class FBReaderApp extends org.geometerplus.fbreader.fbreader.FBReaderApp {
        public FBReaderApp(Context context) {
            super(new Storage.Info(context), new BookCollectionShadow());
        }

        @Override
        public TOCTree getCurrentTOCElement() {
            if (pluginview != null)
                return pluginview.getCurrentTOCElement(Model.TOCTree);
            else
                return super.getCurrentTOCElement();
        }
    }

    public static class ConfigShadow extends Config { // disable config changes across this app view instancies and fbreader
        public Map<String, String> map = new TreeMap<>();

        public ConfigShadow() {
        }

        public void setValue(ZLOption opt, int i) {
            apply(opt);
            setValue(opt.myId, String.valueOf(i));
        }

        public void setValue(ZLOption opt, boolean b) {
            apply(opt);
            setValue(opt.myId, String.valueOf(b));
        }

        public void setValue(ZLOption opt, Enum v) {
            apply(opt);
            setValue(opt.myId, String.valueOf(v));
        }

        public void setValue(ZLOption opt, String v) {
            apply(opt);
            setValue(opt.myId, v);
        }

        public void apply(ZLOption opt) {
            opt.Config = new ZLOption.ConfigInstance() {
                public Config Instance() {
                    return ConfigShadow.this;
                }
            };
        }

        @Override
        public String getValue(StringPair id, String defaultValue) {
            String v = map.get(id.Group + ":" + id.Name);
            if (v != null)
                return v;
            return super.getValue(id, defaultValue);
        }

        @Override
        public void setValue(StringPair id, String value) {
            map.put(id.Group + ":" + id.Name, value);
        }

        @Override
        public void unsetValue(StringPair id) {
            map.remove(id.Group + ":" + id.Name);
        }

        @Override
        protected void setValueInternal(String group, String name, String value) {
        }

        @Override
        protected void unsetValueInternal(String group, String name) {
        }

        @Override
        protected Map<String, String> requestAllValuesForGroupInternal(String group) throws NotAvailableException {
            return null;
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void runOnConnect(Runnable runnable) {
        }

        @Override
        public List<String> listGroups() {
            return null;
        }

        @Override
        public List<String> listNames(String group) {
            return null;
        }

        @Override
        public void removeGroup(String name) {
        }

        @Override
        public boolean getSpecialBooleanValue(String name, boolean defaultValue) {
            return false;
        }

        @Override
        public void setSpecialBooleanValue(String name, boolean value) {
        }

        @Override
        public String getSpecialStringValue(String name, String defaultValue) {
            return null;
        }

        @Override
        public void setSpecialStringValue(String name, String value) {
        }

        @Override
        protected String getValueInternal(String group, String name) throws NotAvailableException {
            throw new NotAvailableException("default");
        }
    }

    public static class BrightnessGesture {
        FBReaderView fb;
        int myStartY;
        boolean myIsBrightnessAdjustmentInProgress;
        int myStartBrightness;
        int areaWidth;
        Integer myColorLevel;

        public BrightnessGesture(FBReaderView view) {
            fb = view;
            areaWidth = ThemeUtils.dp2px(fb.getContext(), 36); // 24dp - icon; 48dp - button
        }

        public boolean onTouchEvent(MotionEvent e) {
            int x = (int) e.getX();
            int y = (int) e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (fb.app.MiscOptions.AllowScreenBrightnessAdjustment.getValue() && x < areaWidth) {
                        myIsBrightnessAdjustmentInProgress = true;
                        myStartY = y;
                        myStartBrightness = fb.widget.getScreenBrightness();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (myIsBrightnessAdjustmentInProgress) {
                        if (x >= areaWidth * 2) {
                            myIsBrightnessAdjustmentInProgress = false;
                            return false;
                        } else {
                            final int delta = (myStartBrightness + 30) * (myStartY - y) / fb.getHeight();
                            fb.widget.setScreenBrightness(myStartBrightness + delta);
                            return true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (myIsBrightnessAdjustmentInProgress) {
                        myIsBrightnessAdjustmentInProgress = false;
                        return true;
                    }
                    break;
            }
            return false;
        }

        public Integer setScreenBrightness(int percent) {
            if (percent < 1)
                percent = 1;
            else if (percent > 100)
                percent = 100;

            final float level;
            final Integer oldColorLevel = myColorLevel;
            if (percent >= 25) {
                // 100 => 1f; 25 => .01f
                level = .01f + (percent - 25) * .99f / 75;
                myColorLevel = null;
            } else {
                level = .01f;
                myColorLevel = 0x60 + (0xFF - 0x60) * Math.max(percent, 0) / 25;
            }

            final WindowManager.LayoutParams attrs = fb.w.getAttributes();
            attrs.screenBrightness = level;
            fb.w.setAttributes(attrs);

            return myColorLevel;
        }

        public int getScreenBrightness() {
            if (myColorLevel != null)
                return (myColorLevel - 0x60) * 25 / (0xFF - 0x60);

            float level = fb.w.getAttributes().screenBrightness;
            level = level >= 0 ? level : .5f;

            // level = .01f + (percent - 25) * .99f / 75;
            return 25 + (int) ((level - .01f) * 75 / .99f);
        }
    }

    public static class PinchGesture extends com.github.axet.androidlibrary.widgets.PinchGesture {
        FBReaderView fb;

        public PinchGesture(FBReaderView view) {
            super(view.getContext());
            this.fb = view;
        }

        public boolean isScaleTouch(MotionEvent e) {
            if (fb.pluginview == null || fb.pluginview.reflow)
                return false;
            return super.isScaleTouch(e);
        }

        public void pinchOpen(int page, Rect v) {
            Bitmap bm = fb.pluginview.render(v.width(), v.height(), page);
            pinch = new PinchView(context, v, bm) {
                public int clip;

                {
                    if (fb.widget instanceof ScrollWidget)
                        clip = ((ScrollWidget) fb.widget).getMainAreaHeight();
                    else
                        clip = ((ZLAndroidWidget) fb.widget).getMainAreaHeight();
                }

                @Override
                public void pinchClose() {
                    PinchGesture.this.pinchClose();
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    Rect c = canvas.getClipBounds();
                    c.bottom = clip - getTop();
                    canvas.clipRect(c);
                    super.dispatchDraw(canvas);
                }
            };
            if (fb.pluginview != null) {
                pinch.image.setColorFilter(fb.pluginview.paint.getColorFilter());
            } else {
                int wallpaperColor = (0xff << 24) | fb.app.BookTextView.getBackgroundColor().intValue();
                if (ColorUtils.calculateLuminance(wallpaperColor) < 0.5f)
                    pinch.image.setColorFilter(new ColorMatrixColorFilter(Plugin.View.NEGATIVE));
                else
                    pinch.image.setColorFilter(null);
            }
            fb.addView(pinch, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        public void pinchClose() {
            if (pinch != null)
                fb.removeView(pinch);
            super.pinchClose();
        }
    }

    public static class LinksView {
        FBReaderView fb;
        public ArrayList<View> links = new ArrayList<>();

        public LinksView(final FBReaderView view, Plugin.View.Link[] ll, Reflow.Info info) {
            this.fb = view;
            if (ll == null)
                return;
            for (int i = 0; i < ll.length; i++) {
                final Plugin.View.Link l = ll[i];
                Rect[] rr;
                if (fb.pluginview.reflow) {
                    rr = fb.pluginview.boundsUpdate(new Rect[]{l.rect}, info);
                    if (rr.length == 0)
                        continue;
                } else {
                    rr = new Rect[]{l.rect};
                }
                for (Rect r : rr) {
                    MarginLayoutParams lp = new MarginLayoutParams(r.width(), r.height());
                    View v = new View(fb.getContext());
                    v.setLayoutParams(lp);
                    v.setTag(r);
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (l.index != -1)
                                fb.app.runAction(ActionCode.PROCESS_HYPERLINK, new BookModel.Label(null, l.index));
                            else
                                AboutPreferenceCompat.openUrlDialog(fb.getContext(), l.url);
                        }
                    });
                    links.add(v);
                    fb.addView(v);
                }
            }
        }

        public void update(int x, int y) {
            for (View v : links) {
                Rect l = (Rect) v.getTag();
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                lp.leftMargin = x + l.left;
                lp.topMargin = y + l.top;
                lp.width = l.width();
                lp.height = l.height();
                v.requestLayout();
            }
        }

        public void hide() {
            for (View v : links)
                v.setVisibility(GONE);
        }

        public void show() {
            for (View v : links)
                v.setVisibility(VISIBLE);
        }

        public void close() {
            final ArrayList<View> old = new ArrayList<>(links);
            fb.post(new Runnable() { // can be called during RelativeLayout onLayout
                @Override
                public void run() {
                    for (View v : old)
                        fb.removeView(v);
                }
            });
            links.clear();
        }
    }

    public static class BookmarksView {
        FBReaderView fb;
        public ArrayList<View> bookmarks = new ArrayList<>();
        int clip;

        public class WordView extends View {
            public WordView(Context context) {
                super(context);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect c = canvas.getClipBounds();
                c.bottom = clip - getTop();
                canvas.clipRect(c);
                super.draw(canvas);
            }
        }

        public BookmarksView(final FBReaderView view, Plugin.View.Selection.Page page, Storage.Bookmarks bms, Reflow.Info info) {
            this.fb = view;
            if (fb.widget instanceof ScrollWidget)
                clip = ((ScrollWidget) fb.widget).getMainAreaHeight();
            else
                clip = ((ZLAndroidWidget) fb.widget).getMainAreaHeight();
            if (bms == null)
                return;
            ArrayList<Storage.Bookmark> ll = bms.getBookmarks(page);
            if (ll == null)
                return;
            for (int i = 0; i < ll.size(); i++) {
                final ArrayList<View> bmv = new ArrayList<>();
                final Storage.Bookmark l = ll.get(i);
                Plugin.View.Selection s = fb.pluginview.select(l.start, l.end);
                if (s == null)
                    return;
                Plugin.View.Selection.Bounds bb = s.getBounds(page);
                s.close();
                Rect[] rr;
                if (fb.pluginview.reflow)
                    rr = fb.pluginview.boundsUpdate(bb.rr, info);
                else
                    rr = bb.rr;
                List<Rect> kk;
                kk = SelectionView.lines(rr);
                for (Rect r : kk) {
                    MarginLayoutParams lp = new MarginLayoutParams(r.width(), r.height());
                    WordView v = new WordView(fb.getContext());
                    v.setLayoutParams(lp);
                    v.setTag(r);
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            BookmarkPopup b = new BookmarkPopup(v, l, bmv) {
                                @Override
                                public void onDelete(Storage.Bookmark l) {
                                    fb.book.info.bookmarks.remove(l);
                                    fb.bookmarksUpdate();
                                    if (fb.listener != null)
                                        fb.listener.onBookmarksUpdate();
                                }
                            };
                            b.show();
                        }
                    });
                    int color = l.color == 0 ? fb.app.BookTextView.getHighlightingBackgroundColor().intValue() : l.color;
                    v.setBackgroundColor(SelectionView.SELECTION_ALPHA << 24 | (color & 0xffffff));
                    bmv.add(v);
                    addView(v);
                }
            }
        }

        public void addView(View v) {
            bookmarks.add(v);
            fb.addView(v);
        }

        public void update(int x, int y) {
            for (View v : bookmarks) {
                Rect l = (Rect) v.getTag();
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                lp.leftMargin = x + l.left;
                lp.topMargin = y + l.top;
                lp.width = l.width();
                lp.height = l.height();
                v.requestLayout();
            }
        }

        public void hide() {
            for (View v : bookmarks)
                v.setVisibility(GONE);
        }

        public void show() {
            for (View v : bookmarks)
                v.setVisibility(VISIBLE);
        }

        public void close() {
            final ArrayList<View> old = new ArrayList<>(bookmarks); // can be called during RelativeLayout onLayout
            fb.post(new Runnable() {
                @Override
                public void run() {
                    for (View v : old)
                        fb.removeView(v);
                }
            });
            bookmarks.clear();
        }
    }

    public static class TTSView extends BookmarksView {
        public TTSView(final FBReaderView view, Plugin.View.Selection.Page page, Reflow.Info info) {
            super(view, page, view.tts.marks, info);
        }

        @Override
        public void addView(View v) {
            super.addView(v);
            v.setOnClickListener(null);
        }
    }

    public static class SearchView {
        FBReaderView fb;
        public ArrayList<View> words = new ArrayList<>();
        int padding;
        int clip;

        public class WordView extends View {
            public WordView(Context context) {
                super(context);
            }

            @Override
            public void draw(Canvas canvas) {
                Rect c = canvas.getClipBounds();
                c.bottom = clip - getTop();
                canvas.clipRect(c);
                super.draw(canvas);
            }
        }

        @SuppressWarnings("unchecked")
        public SearchView(FBReaderView view, Plugin.View.Search.Bounds bb, Reflow.Info info) {
            this.fb = view;
            if (fb.widget instanceof ScrollWidget)
                clip = ((ScrollWidget) fb.widget).getMainAreaHeight();
            else
                clip = ((ZLAndroidWidget) fb.widget).getMainAreaHeight();
            padding = ThemeUtils.dp2px(fb.getContext(), SelectionView.SELECTION_PADDING);
            if (bb == null || bb.rr == null)
                return;
            if (fb.pluginview.reflow)
                bb.rr = fb.pluginview.boundsUpdate(bb.rr, info);
            HashSet hh = null;
            if (bb.highlight != null) {
                if (fb.pluginview.reflow)
                    hh = new HashSet(Arrays.asList(fb.pluginview.boundsUpdate(bb.highlight, info)));
                else
                    hh = new HashSet(Arrays.asList(bb.highlight));
            }
            for (int i = 0; i < bb.rr.length; i++) {
                final Rect l = bb.rr[i];
                MarginLayoutParams lp = new MarginLayoutParams(l.width(), l.height());
                WordView v = new WordView(fb.getContext());
                v.setLayoutParams(lp);
                v.setTag(l);
                if (hh != null && hh.contains(l))
                    v.setBackgroundColor(SelectionView.SELECTION_ALPHA << 24 | 0x990000);
                else
                    v.setBackgroundColor(SelectionView.SELECTION_ALPHA << 24 | fb.app.BookTextView.getHighlightingBackgroundColor().intValue());
                words.add(v);
                fb.addView(v);
            }
        }

        public void update(int x, int y) {
            for (View v : words) {
                Rect l = (Rect) v.getTag();
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                lp.leftMargin = x + l.left - padding;
                lp.topMargin = y + l.top - padding;
                lp.width = l.width() + 2 * padding;
                lp.height = l.height() + 2 * padding;
                v.requestLayout();
            }
        }

        public void hide() {
            for (View v : words)
                v.setVisibility(GONE);
        }

        public void show() {
            for (View v : words)
                v.setVisibility(VISIBLE);
        }

        public void close() {
            final ArrayList<View> old = new ArrayList<>(words);
            fb.post(new Runnable() { // can be called during RelativeLayout onLayout
                @Override
                public void run() {
                    for (View v : old)
                        fb.removeView(v);
                }
            });
            words.clear();
        }
    }

    public FBReaderView(Context context) { // create child view
        super(context);
        create();
    }

    public FBReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public FBReaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public FBReaderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    public void create() {
        config = new ConfigShadow();
        app = new FBReaderApp(getContext());

        app.setWindow(new FBApplicationWindow());
        app.initWindow();

        if (app.getPopupById(TextSearchPopup.ID) == null) {
            new TextSearchPopup(app);
        }
        if (app.getPopupById(NavigationPopup.ID) == null) {
            new NavigationPopup(app);
        }
        if (app.getPopupById(SelectionPopup.ID) == null) {
            new SelectionPopup(app) {
                @Override
                public void createControlPanel(Activity activity, RelativeLayout root) {
                    super.createControlPanel(activity, root);
                    View t = myWindow.findViewById(org.geometerplus.zlibrary.ui.android.R.id.selection_panel_translate);
                    PackageManager packageManager = getContext().getPackageManager();
                    List<ResolveInfo> rr = packageManager.queryIntentActivities(translateIntent(null), 0);
                    if (rr.isEmpty())
                        t.setVisibility(View.GONE);
                    else
                        t.setVisibility(View.VISIBLE);
                }
            };
        }

        config();

        app.BookTextView = new CustomView(app);
        app.setView(app.BookTextView);

        footer = new FBFooterView(getContext(), this);

        setWidget(Widgets.PAGING);
    }

    public void configColorProfile(SharedPreferences shared) {
        if (shared.getString(BookApplication.PREFERENCE_THEME, "").equals(getContext().getString(R.string.Theme_Dark))) {
            config.setValue(app.ViewOptions.ColorProfileName, ColorProfile.NIGHT);
        } else {
            config.setValue(app.ViewOptions.ColorProfileName, ColorProfile.DAY);
            ColorProfile p = ColorProfile.get(ColorProfile.DAY);
            config.setValue(p.BackgroundOption, 0xF5E5CC);
            config.setValue(p.WallpaperOption, "");
        }
    }

    public void configWidget(SharedPreferences shared) {
        String mode = shared.getString(BookApplication.PREFERENCE_VIEW_MODE, "");
        setWidget(mode.equals(FBReaderView.Widgets.CONTINUOUS.toString()) ? FBReaderView.Widgets.CONTINUOUS : FBReaderView.Widgets.PAGING);
    }

    public void config() {
        SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
        configColorProfile(shared);

        int d = shared.getInt(BookApplication.PREFERENCE_FONTSIZE_FBREADER, app.ViewOptions.getTextStyleCollection().getBaseStyle().FontSizeOption.getValue());
        config.setValue(app.ViewOptions.getTextStyleCollection().getBaseStyle().FontSizeOption, d);

        String f = shared.getString(BookApplication.PREFERENCE_FONTFAMILY_FBREADER, app.ViewOptions.getTextStyleCollection().getBaseStyle().FontFamilyOption.getValue());
        config.setValue(app.ViewOptions.getTextStyleCollection().getBaseStyle().FontFamilyOption, f);

        config.setValue(app.MiscOptions.AllowScreenBrightnessAdjustment, false);
        config.setValue(app.ViewOptions.ScrollbarType, 0); // FBView.SCROLLBAR_SHOW_AS_FOOTER
        config.setValue(app.ViewOptions.getFooterOptions().ShowProgress, FooterOptions.ProgressDisplayType.asPages);

        config.setValue(app.ImageOptions.TapAction, ImageOptions.TapActionEnum.openImageView);
        config.setValue(app.ImageOptions.FitToScreen, FBView.ImageFitting.covers);

        config.setValue(app.MiscOptions.WordTappingAction, MiscOptions.WordTappingActionEnum.startSelecting);
    }

    public void setWidget(Widgets w) {
        switch (w) {
            case CONTINUOUS:
                setWidget(new ScrollWidget(this));
                break;
            case PAGING:
                setWidget(new PagerWidget(this));
                break;
        }
    }

    public void setWidget(ZLViewWidget v) {
        overlaysClose();
        ZLTextPosition pos = null;
        if (widget != null) {
            pos = getPosition();
            removeView((View) widget);
        }
        widget = v;
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.ABOVE, footer.getId());
        addView((View) v, 0, lp);
        if (pos != null)
            gotoPosition(pos);
        if (footer != null)
            removeView(footer);
        lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        addView(footer, lp);
    }

    public void loadBook(Storage.FBook fbook) {
        try {
            this.book = fbook;
            if (book.info == null)
                book.info = new Storage.RecentInfo();
            FormatPlugin plugin = Storage.getPlugin((Storage.Info) app.SystemInfo, fbook);
            if (plugin instanceof Plugin) {
                pluginview = ((Plugin) plugin).create(fbook);
                BookModel Model = BookModel.createModel(fbook.book, plugin);
                app.BookTextView.setModel(Model.getTextModel());
                app.Model = Model;
                if (book.info.position != null)
                    gotoPluginPosition(book.info.position);
            } else {
                BookModel Model = BookModel.createModel(fbook.book, plugin);
                ZLTextHyphenator.Instance().load(fbook.book.getLanguage());
                app.BookTextView.setModel(Model.getTextModel());
                app.Model = Model;
                if (book.info.position != null)
                    app.BookTextView.gotoPosition(book.info.position);
                if (book.info.scale != null)
                    config.setValue(app.ImageOptions.FitToScreen, book.info.scale);
                if (book.info.fontsize != null)
                    config.setValue(app.ViewOptions.getTextStyleCollection().getBaseStyle().FontSizeOption, book.info.fontsize);
                bookmarksUpdate();
            }
            widget.repaint();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void closeBook() {
        if (pluginview != null) {
            pluginview.close();
            pluginview = null;
        }
        app.BookTextView.setModel(null);
        app.Model = null;
        book = null;
        if (tts != null) {
            tts.close();
            tts = null;
        }
    }

    public ZLTextPosition getPosition() {
        if (pluginview != null) {
            if (widget instanceof ScrollWidget) {
                int first = ((ScrollWidget) widget).findFirstPage();
                if (first != -1) {
                    RecyclerView.ViewHolder h = ((ScrollWidget) widget).findViewHolderForAdapterPosition(first);
                    ScrollWidget.ScrollAdapter.PageView p = (ScrollWidget.ScrollAdapter.PageView) h.itemView;
                    ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) widget).adapter.pages.get(first);
                    Plugin.Page info = pluginview.getPageInfo(p.getWidth(), p.getHeight(), c);
                    if (p.info != null) { // reflow can be true but reflower == null
                        ArrayList<Rect> rr = new ArrayList<>(p.info.dst.keySet());
                        Collections.sort(rr, new SelectionView.UL());
                        int top = -p.getTop();
                        for (Rect r : rr) {
                            if (r.top > top || (r.top < top && r.bottom > top)) {
                                int screen = r.top - top; // offset from top screen to top element
                                double ratio = p.info.bm.width() / (float) p.getWidth();
                                int offset = (int) (p.info.dst.get(r).top / ratio - screen); // recommended page offset (element - current screen offset)
                                offset *= info.ratio;
                                return new ZLTextFixedPosition(pluginview.current.pageNumber, offset, 0);
                            }
                        }
                    } else {
                        int top = -p.getTop();
                        if (top < 0)
                            top = 0;
                        int offset = (int) (top * info.ratio);
                        return new ZLTextFixedPosition(pluginview.current.pageNumber, offset, 0);
                    }
                }
            }
            return pluginview.getPosition();
        } else {
            if (widget instanceof ScrollWidget) {
                int first = ((ScrollWidget) widget).findFirstPage();
                if (first != -1) {
                    ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) widget).adapter.pages.get(first);
                    RecyclerView.ViewHolder h = ((ScrollWidget) widget).findViewHolderForAdapterPosition(first);
                    ScrollWidget.ScrollAdapter.PageView p = (ScrollWidget.ScrollAdapter.PageView) h.itemView;
                    if (p.text != null) { // happens when view invalidate / recycled before calling getPosition()
                        int top = -p.getTop();
                        for (ZLTextElementArea a : p.text.areas()) {
                            if (a.YStart > top || (a.YStart < top && a.YEnd > top)) {
                                ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(app.Model.getTextModel(), a.getParagraphIndex());
                                ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
                                wordCursor.moveTo(a);
                                ZLTextFixedPosition last;
                                ZLTextElement e;
                                do {
                                    last = new ZLTextFixedPosition(wordCursor);
                                    wordCursor.previousWord();
                                    e = wordCursor.getElement();
                                } while (e instanceof ZLTextControlElement && wordCursor.compareTo(c.start) >= 0);
                                return last;
                            }
                        }
                    }
                }
            }
            return new ZLTextFixedPosition(app.BookTextView.getStartCursor());
        }
    }

    public void setWindow(Window w) {
        this.w = w;
        config.setValue(app.MiscOptions.AllowScreenBrightnessAdjustment, true);
    }

    public void setActivity(final Activity a) {
        PopupPanel.removeAllWindows(app, a);

        app.addAction(ActionCode.SEARCH, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                app.hideActivePopup();
                final String pattern = (String) params[0];
                final Runnable runnable = new Runnable() {
                    public void run() {
                        final TextSearchPopup popup = (TextSearchPopup) app.getPopupById(TextSearchPopup.ID);
                        popup.initPosition();
                        config.setValue(app.MiscOptions.TextSearchPattern, pattern);
                        if (pluginview != null) {
                            searchClose();
                            search = pluginview.search(pattern);
                            search.setPage(getPosition().getParagraphIndex());
                            a.runOnUiThread(new Runnable() {
                                public void run() {
                                    if (search.getCount() == 0) {
                                        UIMessageUtil.showErrorMessage(a, "textNotFound");
                                        popup.StartPosition = null;
                                    } else {
                                        if (widget instanceof ScrollWidget)
                                            ((ScrollWidget) widget).updateOverlays();
                                        if (widget instanceof PagerWidget)
                                            ((PagerWidget) widget).updateOverlaysReset();
                                        app.showPopup(popup.getId());
                                    }
                                }
                            });
                            return;
                        }
                        if (app.getTextView().search(pattern, true, false, false, false) != 0) {
                            a.runOnUiThread(new Runnable() {
                                public void run() {
                                    app.showPopup(popup.getId());
                                    if (widget instanceof ScrollWidget)
                                        reset();
                                }
                            });
                        } else {
                            a.runOnUiThread(new Runnable() {
                                public void run() {
                                    UIMessageUtil.showErrorMessage(a, "textNotFound");
                                    popup.StartPosition = null;
                                }
                            });
                        }
                    }
                };
                UIUtil.wait("search", runnable, getContext());
            }
        });

        app.addAction(ActionCode.DISPLAY_BOOK_POPUP, new FBAction(app) { //  new DisplayBookPopupAction(this, myFBReaderApp))
            @Override
            protected void run(Object... params) {
            }
        });
        app.addAction(ActionCode.PROCESS_HYPERLINK, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                if (pluginview != null) {
                    BookModel.Label l = (BookModel.Label) params[0];
                    showHyperlink(l);
                    return;
                }
                final ZLTextRegion region = app.BookTextView.getOutlinedRegion();
                if (region == null) {
                    return;
                }
                final ZLTextRegion.Soul soul = region.getSoul();
                if (soul instanceof ZLTextHyperlinkRegionSoul) {
                    app.BookTextView.hideOutline();
                    final ZLTextHyperlink hyperlink = ((ZLTextHyperlinkRegionSoul) soul).Hyperlink;
                    switch (hyperlink.Type) {
                        case FBHyperlinkType.EXTERNAL:
                            AboutPreferenceCompat.openUrlDialog(getContext(), hyperlink.Id);
                            break;
                        case FBHyperlinkType.INTERNAL:
                        case FBHyperlinkType.FOOTNOTE: {
                            final AutoTextSnippet snippet = app.getFootnoteData(hyperlink.Id);
                            if (snippet == null)
                                break;
                            app.Collection.markHyperlinkAsVisited(app.getCurrentBook(), hyperlink.Id);
                            final boolean showToast;
                            switch (app.MiscOptions.ShowFootnoteToast.getValue()) {
                                default:
                                case never:
                                    showToast = false;
                                    break;
                                case footnotesOnly:
                                    showToast = hyperlink.Type == FBHyperlinkType.FOOTNOTE;
                                    break;
                                case footnotesAndSuperscripts:
                                    showToast =
                                            hyperlink.Type == FBHyperlinkType.FOOTNOTE ||
                                                    region.isVerticallyAligned();
                                    break;
                                case allInternalLinks:
                                    showToast = true;
                                    break;
                            }
                            if (showToast) {
                                final SuperActivityToast toast;
                                if (snippet.IsEndOfText) {
                                    toast = new SuperActivityToast(a, SuperToast.Type.STANDARD);
                                } else {
                                    toast = new SuperActivityToast(a, SuperToast.Type.BUTTON);
                                    toast.setButtonIcon(
                                            android.R.drawable.ic_menu_more,
                                            ZLResource.resource("toast").getResource("more").getValue()
                                    );
                                    toast.setOnClickWrapper(new OnClickWrapper("ftnt", new SuperToast.OnClickListener() {
                                        @Override
                                        public void onClick(View view, Parcelable token) {
                                            showHyperlink(hyperlink);
                                        }
                                    }));
                                }
                                toast.setText(snippet.getText());
                                toast.setDuration(app.MiscOptions.FootnoteToastDuration.getValue().Value);
                                toast.setOnDismissWrapper(new OnDismissWrapper("ftnt", new SuperToast.OnDismissListener() {
                                    @Override
                                    public void onDismiss(View view) {
                                        app.BookTextView.hideOutline();
                                    }
                                }));
                                app.BookTextView.outlineRegion(region);
                                showToast(toast);
                            } else {
                                book.info.position = getPosition();
                                showHyperlink(hyperlink);
                            }
                            break;
                        }
                    }
                } else if (soul instanceof ZLTextImageRegionSoul) {
                    final ZLTextImageRegionSoul image = ((ZLTextImageRegionSoul) soul);
                    final View anchor = new View(getContext());
                    LayoutParams lp = new LayoutParams(region.getRight() - region.getLeft(), region.getBottom() - region.getTop());
                    lp.leftMargin = region.getLeft();
                    lp.topMargin = region.getTop();
                    if (widget instanceof ScrollWidget) {
                        ScrollWidget.ScrollAdapter.PageView p = ((ScrollWidget) widget).findRegionView(soul);
                        lp.leftMargin += p.getLeft();
                        lp.topMargin += p.getTop();
                    }
                    FBReaderView.this.addView(anchor, lp);
                    final PopupMenu menu = new PopupMenu(getContext(), anchor, Gravity.BOTTOM);
                    menu.inflate(R.menu.image_menu);
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            int id = item.getItemId();
                            if (id == R.id.action_open) {
                                String name = image.ImageElement.Id;
                                Uri uri = Uri.parse(image.ImageElement.URL);
                                Intent intent = ImagesProvider.getProvider().openIntent(uri, name);
                                getContext().startActivity(intent);
                            } else if (id == R.id.action_share) {
                                String name = image.ImageElement.Id;
                                String type = Storage.getTypeByExt(ImagesProvider.EXT);
                                Uri uri = Uri.parse(image.ImageElement.URL);
                                Intent intent = ImagesProvider.getProvider().shareIntent(uri, name, type, Storage.getTitle(book.info) + " (" + name + ")");
                                getContext().startActivity(intent);
                            } else if (id == R.id.action_original) {
                                ((CustomView) app.BookTextView).setScalingType(image.ImageElement, ZLPaintContext.ScalingType.OriginalSize);
                                resetCaches();
                            } else if (id == R.id.action_zoom) {
                                ((CustomView) app.BookTextView).setScalingType(image.ImageElement, ZLPaintContext.ScalingType.FitMaximum);
                                resetCaches();
                            } else if (id == R.id.action_original_all) {
                                book.info.scales.clear();
                                book.info.scale = FBView.ImageFitting.covers;
                                config.setValue(app.ImageOptions.FitToScreen, FBView.ImageFitting.covers);
                                resetCaches();
                            } else if (id == R.id.action_zoom_all) {
                                book.info.scales.clear();
                                book.info.scale = FBView.ImageFitting.all;
                                config.setValue(app.ImageOptions.FitToScreen, FBView.ImageFitting.all);
                                resetCaches();
                            }
                            return true;
                        }
                    });
                    menu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                        @Override
                        public void onDismiss(PopupMenu menu) {
                            app.BookTextView.hideOutline();
                            widget.repaint();
                            FBReaderView.this.removeView(anchor);
                        }
                    });
                    FBReaderView.this.post(new Runnable() { // allow anchor view to be placed
                        @Override
                        public void run() {
                            menu.show();
                        }
                    });
                } else if (soul instanceof ZLTextWordRegionSoul) {
                    DictionaryUtil.openTextInDictionary(
                            a,
                            ((ZLTextWordRegionSoul) soul).Word.getString(),
                            true,
                            region.getTop(),
                            region.getBottom(),
                            new Runnable() {
                                public void run() {
                                    // a.outlineRegion(soul);
                                }
                            }
                    );
                }
            }
        });
        app.addAction(ActionCode.SHOW_MENU, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                a.sendBroadcast(new Intent(ACTION_MENU));
            }
        });
        app.addAction(ActionCode.SHOW_NAVIGATION, new FBAction(app) {
            @Override
            public boolean isVisible() {
                if (pluginview != null)
                    return true;
                final ZLTextModel textModel = app.BookTextView.getModel();
                return textModel != null && textModel.getParagraphsNumber() != 0;
            }

            @Override
            protected void run(Object... params) {
                ((NavigationPopup) app.getPopupById(NavigationPopup.ID)).runNavigation();
            }
        });
        app.addAction(ActionCode.SELECTION_SHOW_PANEL, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                final ZLTextView view = app.getTextView();
                ((SelectionPopup) app.getPopupById(SelectionPopup.ID)).move(view.getSelectionStartY(), view.getSelectionEndY());
                app.showPopup(SelectionPopup.ID);
            }
        });
        app.addAction(ActionCode.SELECTION_HIDE_PANEL, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                final FBReaderApp.PopupPanel popup = app.getActivePopup();
                if (popup != null && popup.getId() == SelectionPopup.ID) {
                    app.hideActivePopup();
                }
            }
        });
        app.addAction(ActionCode.SELECTION_COPY_TO_CLIPBOARD, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                String text;

                if (selection != null) {
                    text = selection.selection.getText();
                } else {
                    TextSnippet snippet = app.BookTextView.getSelectedSnippet();
                    if (snippet == null)
                        return;
                    text = snippet.getText();
                }

                app.BookTextView.clearSelection();
                selectionClose();

                final ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Application.CLIPBOARD_SERVICE);
                clipboard.setText(text);
                UIMessageUtil.showMessageText(a, clipboard.getText().toString());

                if (widget instanceof ScrollWidget) {
                    ((ScrollWidget) widget).adapter.processInvalidate();
                    ((ScrollWidget) widget).adapter.processClear();
                }
            }
        });
        app.addAction(ActionCode.SELECTION_SHARE, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                String text;

                if (selection != null) {
                    text = selection.selection.getText();
                } else {
                    TextSnippet snippet = app.BookTextView.getSelectedSnippet();
                    if (snippet == null)
                        return;
                    text = snippet.getText();
                }

                app.BookTextView.clearSelection();
                selectionClose();

                final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType(HttpClient.CONTENTTYPE_TEXT);
                intent.putExtra(android.content.Intent.EXTRA_SUBJECT, Storage.getTitle(book.info));
                intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                a.startActivity(Intent.createChooser(intent, null));

                if (widget instanceof ScrollWidget) {
                    FBReaderView.this.post(new Runnable() { // do not clear immidiallty. let onPause to be called before p.text = null
                        @Override
                        public void run() {
                            ((ScrollWidget) widget).adapter.processInvalidate();
                            ((ScrollWidget) widget).adapter.processClear();
                        }
                    });
                }
            }
        });
        app.addAction(ActionCode.SELECTION_TRANSLATE, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                final String text;

                if (selection != null) {
                    text = selection.selection.getText();
                } else {
                    TextSnippet snippet = app.BookTextView.getSelectedSnippet();
                    if (snippet == null)
                        return;
                    text = snippet.getText();
                }

                Intent intent = translateIntent(text);
                getContext().startActivity(intent);

                app.BookTextView.clearSelection();
                selectionClose();
            }
        });
        app.addAction(ActionCode.SELECTION_BOOKMARK, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                if (params.length != 0) {
                    Storage.Bookmark bm = (Storage.Bookmark) params[0];
                    Rect union;
                    final View anchor = new View(getContext());
                    if (widget instanceof ScrollWidget)
                        union = ((ScrollWidget) widget).findUnion(bm);
                    else
                        union = findUnion(app.BookTextView.myCurrentPage.TextElementMap.areas(), bm);
                    MarginLayoutParams lp = new MarginLayoutParams(union.width(), union.height());
                    lp.leftMargin = union.left;
                    lp.topMargin = union.top;
                    FBReaderView.this.addView(anchor, lp);
                    final BookmarkPopup b = new BookmarkPopup(anchor, bm, new ArrayList<View>()) {
                        @Override
                        public void onDelete(Storage.Bookmark l) {
                            book.info.bookmarks.remove(l);
                            bookmarksUpdate();
                            if (listener != null)
                                listener.onBookmarksUpdate();
                        }

                        @Override
                        public void onSelect(int color) {
                            super.onSelect(color);
                            bookmarksUpdate();
                        }

                        @Override
                        public void onDismiss() {
                            super.onDismiss();
                            FBReaderView.this.removeView(anchor);
                            bookmarksUpdate();
                        }
                    };
                    FBReaderView.this.post(new Runnable() {
                        @Override
                        public void run() {
                            b.show();
                        }
                    });
                } else {
                    if (book.info.bookmarks == null)
                        book.info.bookmarks = new Storage.Bookmarks();
                    if (selection != null) {
                        book.info.bookmarks.add(new Storage.Bookmark(selection.selection.getText(), selection.selection.getStart(), selection.selection.getEnd()));
                    } else {
                        TextSnippet snippet = app.BookTextView.getSelectedSnippet();
                        book.info.bookmarks.add(new Storage.Bookmark(snippet.getText(), snippet.getStart(), snippet.getEnd()));
                    }
                    bookmarksUpdate();
                    if (listener != null)
                        listener.onBookmarksUpdate();
                    app.BookTextView.clearSelection();
                    selectionClose();
                }
            }
        });
        app.addAction(ActionCode.SELECTION_CLEAR, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                app.BookTextView.clearSelection();
                selectionClose();
                if (widget instanceof ScrollWidget) {
                    ((ScrollWidget) widget).adapter.processInvalidate();
                    ((ScrollWidget) widget).adapter.processClear();
                }
            }
        });

        app.addAction(ActionCode.FIND_PREVIOUS, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                if (search != null) {
                    Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            final int page = search.prev();
                            if (page == -1)
                                return;
                            FBReaderView.this.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (widget instanceof ScrollWidget) {
                                        ((ScrollWidget) widget).searchPage(page);
                                        return;
                                    }
                                    if (widget instanceof PagerWidget) {
                                        ((PagerWidget) widget).searchPage(page);
                                        return;
                                    }
                                }
                            });
                        }
                    };
                    UIUtil.wait("search", run, getContext());
                    return;
                } else {
                    app.BookTextView.findPrevious();
                }
                resetNewPosition();
            }
        });
        app.addAction(ActionCode.FIND_NEXT, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                if (search != null) {
                    Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            final int page = search.next();
                            if (page == -1)
                                return;
                            FBReaderView.this.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (widget instanceof ScrollWidget) {
                                        ((ScrollWidget) widget).searchPage(page);
                                        return;
                                    }
                                    if (widget instanceof PagerWidget) {
                                        ((PagerWidget) widget).searchPage(page);
                                        return;
                                    }
                                }
                            });
                        }
                    };
                    UIUtil.wait("search", run, getContext());
                    return;
                } else {
                    app.BookTextView.findNext();
                }
                resetNewPosition();
            }
        });
        app.addAction(ActionCode.CLEAR_FIND_RESULTS, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                if (listener != null)
                    listener.onSearchClose();
                searchClose();
                app.BookTextView.clearFindResults();
                resetNewPosition();
            }
        });

        app.addAction(ActionCode.VOLUME_KEY_SCROLL_FORWARD, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                scrollNextPage();
            }
        });
        app.addAction(ActionCode.VOLUME_KEY_SCROLL_BACK, new FBAction(app) {
            @Override
            protected void run(Object... params) {
                scrollPrevPage();
            }
        });

        ((PopupPanel) app.getPopupById(TextSearchPopup.ID)).setPanelInfo(a, this);
        ((NavigationPopup) app.getPopupById(NavigationPopup.ID)).setPanelInfo(a, this);
        ((PopupPanel) app.getPopupById(SelectionPopup.ID)).setPanelInfo(a, this);
    }

    public void scrollNextPage() {
        final PageTurningOptions preferences = app.PageTurningOptions;
        widget.startAnimatedScrolling(
                FBView.PageIndex.next,
                preferences.Horizontal.getValue()
                        ? FBView.Direction.rightToLeft : FBView.Direction.up,
                preferences.AnimationSpeed.getValue()
        );
    }

    public void scrollPrevPage() {
        final PageTurningOptions preferences = app.PageTurningOptions;
        widget.startAnimatedScrolling(
                FBView.PageIndex.previous,
                preferences.Horizontal.getValue()
                        ? FBView.Direction.rightToLeft : FBView.Direction.up,
                preferences.AnimationSpeed.getValue()
        );
    }

    public void setDrawer(DrawerLayout drawer) {
        this.drawer = drawer;
    }

    void showHyperlink(final ZLTextHyperlink hyperlink) {
        final BookModel.Label label = app.Model.getLabel(hyperlink.Id);
        showHyperlink(label);
    }

    void showHyperlink(final BookModel.Label label) {
        Context context = getContext();

        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        WallpaperLayout f = new WallpaperLayout(context);
        ImageButton c = new ImageButton(context);
        c.setImageResource(R.drawable.ic_close_black_24dp);
        c.setColorFilter(ThemeUtils.getThemeColor(context, R.attr.colorAccent));
        f.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP));

        final FBReaderView r = new FBReaderView(context) {
            @Override
            public void config() {
                super.config();
                config.setValue(app.ViewOptions.ScrollbarType, 0);
                config.setValue(app.MiscOptions.WordTappingAction, MiscOptions.WordTappingActionEnum.doNothing);
                config.setValue(app.ImageOptions.TapAction, ImageOptions.TapActionEnum.doNothing);
            }
        };

        r.app.addAction(ActionCode.PROCESS_HYPERLINK, new FBAction(r.app) {
            @Override
            protected void run(Object... params) {
                if (r.pluginview != null) {
                    BookModel.Label l = (BookModel.Label) params[0];
                    r.app.BookTextView.gotoPosition(l.ParagraphIndex, 0, 0);
                    r.resetNewPosition();
                    return;
                }
                final ZLTextRegion region = r.app.BookTextView.getOutlinedRegion();
                if (region == null) {
                    return;
                }
                final ZLTextRegion.Soul soul = region.getSoul();
                if (soul instanceof ZLTextHyperlinkRegionSoul) {
                    r.app.BookTextView.hideOutline();
                    r.widget.repaint();
                    final ZLTextHyperlink hyperlink = ((ZLTextHyperlinkRegionSoul) soul).Hyperlink;
                    switch (hyperlink.Type) {
                        case FBHyperlinkType.EXTERNAL:
                            AboutPreferenceCompat.openUrlDialog(getContext(), hyperlink.Id);
                            break;
                        case FBHyperlinkType.INTERNAL:
                        case FBHyperlinkType.FOOTNOTE: {
                            final BookModel.Label label = r.app.Model.getLabel(hyperlink.Id);
                            r.app.BookTextView.gotoPosition(label.ParagraphIndex, 0, 0);
                            r.resetNewPosition();
                        }
                    }
                }
            }
        });

        SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
        r.configWidget(shared);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        ll.addView(f, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.addView(r, rlp);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(ll);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                listener.onDismissDialog();
            }
        });
        if (label.ModelId == null) {
            builder.setNeutralButton(R.string.keep_reading_position, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    gotoPosition(r.getPosition());
                }
            });
        }
        builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                Window w = dialog.getWindow();
                w.setLayout(getWidth(), getHeight()); // fixed size after creation
                r.loadBook(book);
                if (label.ModelId == null) {
                    r.app.BookTextView.gotoPosition(label.ParagraphIndex, 0, 0);
                    r.app.setView(r.app.BookTextView);
                } else {
                    final ZLTextModel model = r.app.Model.getFootnoteModel(label.ModelId);
                    r.app.BookTextView.setModel(model);
                    r.app.setView(r.app.BookTextView);
                    r.app.BookTextView.gotoPosition(label.ParagraphIndex, 0, 0);
                }
            }
        });
        c.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    void showToast(SuperActivityToast toast) {
        toast.show();
    }

    void gotoPluginPosition(ZLTextPosition p) {
        if (p == null)
            return;
        if (widget instanceof ScrollWidget) {
            if (p.getElementIndex() != 0) {
                scrollDelayed = p;
                p = new ZLTextFixedPosition(p.getParagraphIndex(), 0, 0);
            }
        }
        pluginview.gotoPosition(p);
    }

    public void gotoPosition(TOCTree.Reference p) {
        if (p.Model != null)
            app.BookTextView.setModel(p.Model);
        gotoPosition(new ZLTextFixedPosition(p.ParagraphIndex, 0, 0));
    }

    public void gotoPosition(ZLTextPosition p) {
        if (pluginview != null)
            gotoPluginPosition(p);
        else
            app.BookTextView.gotoPosition(p);
        resetNewPosition();
    }

    public void resetNewPosition() { // get position from new loaded page, then reset
        if (widget instanceof ScrollWidget) {
            ((ScrollWidget) widget).adapter.reset();
        } else {
            widget.reset();
            widget.repaint();
        }
    }

    public void reset() { // keep current position, then reset
        if (widget instanceof ScrollWidget) {
            ((ScrollWidget) widget).updatePosition();
            ((ScrollWidget) widget).adapter.reset();
            if (pluginview != null)
                ((ScrollWidget) widget).updateOverlays();
        } else {
            widget.reset();
            widget.repaint();
        }
    }

    public void updateTheme() {
        if (pluginview != null)
            pluginview.updateTheme();
        if (widget instanceof ScrollWidget) {
            ((ScrollWidget) widget).requestLayout(); // repaint views
            ((ScrollWidget) widget).reset();
        } else {
            widget.reset();
            widget.repaint();
        }
    }

    public void resetCaches() {
        app.clearTextCaches();
        reset();
    }

    public void invalidateFooter() {
        if (footer == null) {
            if (widget instanceof ScrollWidget)
                ((ScrollWidget) widget).invalidate();
            else
                widget.repaint();
        } else {
            footer.invalidate();
        }
    }

    public void clearReflowPage() {
        pluginview.current.pageOffset = 0;
        if (pluginview.reflower != null)
            pluginview.reflower.index = 0;
    }

    public void selectionOpen(Plugin.View.Selection s) {
        selectionClose();
        selection = new SelectionView(getContext(), (CustomView) app.BookTextView, s) {
            @Override
            public void onTouchLock() {
                if (drawer != null)
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                app.runAction(ActionCode.SELECTION_HIDE_PANEL);
            }

            @Override
            public void onTouchUnlock() {
                if (drawer != null)
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                app.runAction(ActionCode.SELECTION_SHOW_PANEL);
            }
        };
        addView(selection);
        if (widget instanceof ScrollWidget) {
            ((ScrollWidget) widget).updateOverlays();
            selection.setClipHeight(((ScrollWidget) widget).getMainAreaHeight());
        } else {
            selection.setClipHeight(((ZLAndroidWidget) widget).getMainAreaHeight());
        }
        app.runAction(ActionCode.SELECTION_SHOW_PANEL);
    }

    public void selectionClose() {
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).selectionClose();
        if (selection != null) {
            selection.close();
            removeView(selection);
            selection = null;
        }
        app.runAction(ActionCode.SELECTION_HIDE_PANEL);
    }

    public void linksClose() {
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).linksClose();
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).linksClose();
    }

    public void bookmarksClose() {
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).bookmarksClose();
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).bookmarksClose();
    }

    public void bookmarksUpdate() {
        if (pluginview == null) {
            app.BookTextView.removeHighlightings(ZLBookmark.class);
            ArrayList<ZLTextHighlighting> hi = new ArrayList<>();
            if (book.info.bookmarks != null) {
                for (int i = 0; i < book.info.bookmarks.size(); i++) {
                    final Storage.Bookmark b = book.info.bookmarks.get(i);
                    ZLBookmark h = new ZLBookmark(app.BookTextView, b);
                    hi.add(h);
                }
            }
            app.BookTextView.addHighlightings(hi);
        }
        if (widget instanceof ScrollWidget) {
            if (pluginview == null) {
                for (ScrollWidget.ScrollAdapter.PageHolder h : ((ScrollWidget) widget).adapter.holders) {
                    h.page.recycle();
                    h.page.invalidate();
                }
            } else {
                ((ScrollWidget) widget).bookmarksUpdate();
            }
        }
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).updateOverlaysReset();
    }

    public void ttsClose() {
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).ttsClose();
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).ttsClose();
        if (pluginview == null) {
            app.BookTextView.removeHighlightings(ZLTTSMark.class);
            if (widget instanceof ScrollWidget) {
                for (ScrollWidget.ScrollAdapter.PageHolder h : ((ScrollWidget) widget).adapter.holders) {
                    h.page.recycle();
                    h.page.invalidate();
                }
            }
        }
    }

    public void ttsUpdate() {
        if (tts == null)
            return; // already closed, run from handler
        if (pluginview == null) {
            app.BookTextView.removeHighlightings(ZLTTSMark.class);
            if (tts != null) {
                ArrayList<ZLTextHighlighting> hi = new ArrayList<>();
                for (Storage.Bookmark m : tts.marks) {
                    ZLTTSMark h = new ZLTTSMark(app.BookTextView, m);
                    hi.add(h);
                }
                app.BookTextView.addHighlightings(hi);
            }
        }
        if (widget instanceof ScrollWidget) {
            if (pluginview == null) {
                for (ScrollWidget.ScrollAdapter.PageHolder h : ((ScrollWidget) widget).adapter.holders) {
                    h.page.recycle();
                    h.page.invalidate();
                }
            } else {
                ((ScrollWidget) widget).ttsUpdate();
            }
        }
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).updateOverlaysReset();
        tts.view.bringToFront();
    }

    public void ttsOpen() {
        tts = new TTSPopup(this);
        tts.show();
        ttsUpdate();
    }

    public void searchClose() {
        app.hideActivePopup();
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).searchClose();
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).searchClose();
        if (search != null) {
            search.close();
            search = null;
        }
        searchPagePending = -1;
    }

    public void overlaysClose() {
        pinchClose();
        selectionClose();
        linksClose();
        bookmarksClose();
        searchClose();
    }

    public boolean isPinch() {
        if (widget instanceof ScrollWidget)
            return ((ScrollWidget) widget).gesturesListener.pinch.isPinch();
        if (widget instanceof PagerWidget)
            return ((PagerWidget) widget).pinch.isPinch();
        return false;
    }

    public void pinchClose() {
        if (widget instanceof ScrollWidget)
            ((ScrollWidget) widget).gesturesListener.pinch.pinchClose();
        if (widget instanceof PagerWidget)
            ((PagerWidget) widget).pinch.pinchClose();
    }

    public void showControls() {
        ActiveAreasView areas = new ActiveAreasView(getContext());
        int w = getWidth();
        if (w == 0)
            return; // activity closed
        areas.create(app, w);
        showControls(this, areas);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ZLTextPosition scrollDelayed = getPosition();
        reset();
        gotoPosition(scrollDelayed);
        pinchClose();
    }

    public boolean isReflow() {
        return pluginview.reflow;
    }

    public void setReflow(boolean b) {
        pluginview.reflow = b;
        ZLTextPosition scrollDelayed = getPosition();
        reset();
        gotoPosition(scrollDelayed);
    }

    public int getFontsizeFB() {
        if (book.info.fontsize != null)
            return book.info.fontsize;
        return app.ViewOptions.getTextStyleCollection().getBaseStyle().FontSizeOption.getValue();
    }

    public void setFontsizeFB(int p) {
        book.info.fontsize = p;
        config.setValue(app.ViewOptions.getTextStyleCollection().getBaseStyle().FontSizeOption, p);
        resetCaches();
    }

    public void setFontFB(String f) {
        config.setValue(app.ViewOptions.getTextStyleCollection().getBaseStyle().FontFamilyOption, f);
        resetCaches();
    }

    public Float getFontsizeReflow() {
        if (book.info.fontsize != null)
            return book.info.fontsize / 100f;
        return null;
    }

    public void setFontsizeReflow(float p) {
        book.info.fontsize = (int) (p * 100f);
        if (pluginview.reflower != null && pluginview.reflower.k2 != null)
            pluginview.reflower.k2.setFontSize(p);
        ZLTextPosition scrollDelayed = getPosition();
        reset();
        gotoPosition(scrollDelayed);
    }

    public void onScrollingFinished(ZLViewEnums.PageIndex pageIndex) {
        if (listener != null)
            listener.onScrollingFinished(pageIndex);
    }
}
