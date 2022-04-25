package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.github.axet.androidlibrary.sound.TTS;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.BookApplication;
import com.github.axet.bookreader.app.Plugin;
import com.github.axet.bookreader.app.Reflow;
import com.github.axet.bookreader.app.Storage;

import org.geometerplus.fbreader.fbreader.TextBuildTraverser;
import org.geometerplus.zlibrary.core.view.ZLViewEnums;
import org.geometerplus.zlibrary.text.view.ZLTextElement;
import org.geometerplus.zlibrary.text.view.ZLTextElementArea;
import org.geometerplus.zlibrary.text.view.ZLTextElementAreaVector;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextParagraphCursor;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextWord;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class TTSPopup {
    public static String[] EOL = {"\n", "\r"};
    public static String[] STOPS = {".", ";"}; // ",", "\"", "'", "!", "?", "“", "”", ":", "(", ")"};
    public static int MAX_COUNT = getMaxSpeechInputLength(200);
    public static int TTS_BG_COLOR = 0xaaaaaa00;
    public static int TTS_BG_ERROR_COLOR = 0xaaff0000;
    public static int TTS_WORD_COLOR = 0x33333333;

    public Context context;
    public TTS tts;
    public FBReaderView fb;
    Fragment fragment;
    public Storage.Bookmarks marks = new Storage.Bookmarks();
    public View panel;
    public View view;
    ImageView play;
    ArrayList<Runnable> onScrollFinished = new ArrayList<>();
    Handler handler = new Handler();
    int gravity;
    Runnable updateGravity = new Runnable() {
        @Override
        public void run() {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
            lp.gravity = gravity;
            panel.setLayoutParams(lp);
        }
    };
    Runnable speakNext = new Runnable() {
        @Override
        public void run() {
            selectNext();
            speakNext();
        }
    };
    Runnable speakRetry = null;

    public static int getMaxSpeechInputLength(int max) {
        if (Build.VERSION.SDK_INT >= 18) {
            if (max > TextToSpeech.getMaxSpeechInputLength())
                return TextToSpeech.getMaxSpeechInputLength();
        }
        return max;
    }

    public static Rect getRect(Plugin.View pluginview, ScrollWidget.ScrollAdapter.PageView v, Storage.Bookmark bm) {
        Plugin.View.Selection.Page page = pluginview.selectPage(bm.start, v.info, v.getWidth(), v.getHeight());
        Plugin.View.Selection s = pluginview.select(bm.start, bm.end);
        if (s != null) {
            Plugin.View.Selection.Bounds bb = s.getBounds(page);
            s.close();
            return SelectionView.union(Arrays.asList(bb.rr));
        } else {
            return null;
        }
    }

    public static boolean isStopSymbol(ZLTextElement e) {
        if (e instanceof ZLTextWord) {
            String str = ((ZLTextWord) e).getString();
            return isStopSymbol(str);
        }
        return false;
    }

    public static boolean isStopSymbol(String str) {
        if (str == null || str.isEmpty())
            return true;
        for (String s : STOPS) {
            if (str.contains(s))
                return true;
        }
        return false;
    }

    public static boolean isEOL(Plugin.View.Selection s) {
        String str = s.getText();
        for (String e : EOL) {
            if (str.equals(e))
                return true;
        }
        return false;
    }

    public static boolean stopOnLeft(ZLTextElement e) {
        if (e instanceof ZLTextWord) {
            String str = ((ZLTextWord) e).getString();
            return stopOnLeft(str);
        }
        return false;
    }

    public static boolean stopOnLeft(String str) {
        if (str == null || str.length() <= 1)
            return false;
        for (String s : STOPS) {
            if (str.startsWith(s))
                return true;
        }
        return false;
    }

    public static boolean stopOnRight(ZLTextElement e) {
        if (e instanceof ZLTextWord) {
            String str = ((ZLTextWord) e).getString();
            return stopOnRight(str);
        }
        return false;
    }

    public static boolean stopOnRight(String str) {
        if (str == null || str.isEmpty())
            return false;
        for (String s : STOPS) {
            if (str.endsWith(s))
                return true;
        }
        return false;
    }

    public static boolean isEmpty(Storage.Bookmark bm) {
        if (bm == null)
            return true;
        return bm.start == null || bm.end == null;
    }

    public class Fragment {
        public Storage.Bookmark fragment; // paragraph or line
        public String fragmentText;
        public ArrayList<Bookmark> fragmentWords;
        public Storage.Bookmark word = new Storage.Bookmark();
        public int retry; // retry count
        public long last; // last time text was played

        public class Bookmark extends Storage.Bookmark {
            public int strStart;
            public int strEnd;

            public Bookmark(String z, ZLTextPosition s, ZLTextPosition e) {
                super(z, s, e);
                color = TTS_WORD_COLOR;
            }
        }

        public Fragment(Storage.Bookmark bm) {
            String str = "";
            ArrayList<Bookmark> list = new ArrayList<>();
            if (fb.pluginview != null) {
                ZLTextPosition start = bm.start;
                ZLTextPosition end = bm.end;
                PluginWordCursor k = new PluginWordCursor(start);
                if (k.nextWord()) {
                    while (k.compareTo(end) <= 0) {
                        Bookmark b = new Bookmark(k.getText(), new ZLTextFixedPosition(start), new ZLTextFixedPosition(k));
                        b.strStart = str.length();
                        str += k.getText();
                        b.strEnd = str.length();
                        str += " ";
                        list.add(b);
                        start = new ZLTextFixedPosition(k);
                        k.nextWord();
                    }
                }
                k.close();
            } else {
                ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(fb.app.Model.getTextModel(), bm.start.getParagraphIndex());
                ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
                wordCursor.moveTo(bm.start);
                for (ZLTextElement e = wordCursor.getElement(); wordCursor.compareTo(bm.end) < 0; e = wordCursor.getElement()) {
                    if (e instanceof ZLTextWord) {
                        String z = ((ZLTextWord) e).getString();
                        Bookmark b = new Bookmark(z, new ZLTextFixedPosition(wordCursor), new ZLTextFixedPosition(wordCursor.getParagraphIndex(), wordCursor.getElementIndex(), wordCursor.getCharIndex() + ((ZLTextWord) e).Length));
                        b.strStart = str.length();
                        str += z;
                        b.strEnd = str.length();
                        str += " ";
                        list.add(b);
                    }
                    wordCursor.nextWord();
                }
            }
            fragmentText = str;
            fragmentWords = list;
            fragment = new Storage.Bookmark(bm);
            fragment.color = TTS_BG_COLOR;
            word = null;
        }

        public Storage.Bookmark findWord(int start, int end) {
            for (Bookmark bm : fragmentWords) {
                if (bm.strStart == start)
                    return bm;
            }
            return null;
        }

        public boolean isEmpty() {
            if (fragment == null || fragmentText == null)
                return true;
            return fragmentText.trim().length() == 0;
        }
    }

    public class PluginWordCursor extends ZLTextPosition {
        int p, e, c; // points to symbol
        Plugin.View.Selection all;
        String allText;
        String text;

        public PluginWordCursor(ZLTextPosition k) {
            p = k.getParagraphIndex();
            e = k.getElementIndex();
            c = k.getCharIndex();
        }

        public ZLTextPosition getCurrent() {
            return new ZLTextFixedPosition(p, e, c);
        }

        public boolean left() {
            if (all == null)
                return false;
            e--;
            if (e < 0) {
                e = 0;
                p--;
                if (p < 0) {
                    p = 0;
                    return false;
                } else {
                    all();
                    e = all.getEnd().getElementIndex() - 1;
                }
            }
            return true;
        }

        public boolean right() {
            if (all == null)
                return false;
            e++;
            if (e >= all.getEnd().getElementIndex()) {
                e = 0;
                p++;
                int last = fb.pluginview.pagePosition().Total - 1;
                if (p > last) {
                    p = last;
                    return false;
                } else {
                    all();
                }
            }
            return true;
        }

        public void all() {
            close();
            all = fb.pluginview.select(p);
            if (all != null) // empty page
                allText = all.getText();
        }

        public void close() {
            if (all != null)
                all.close();
            all = null;
        }

        public String select() {
            return allText.substring(getCurrent().getElementIndex(), getCurrent().getElementIndex() + 1);
        }

        public boolean prevWord() {
            all();
            if (all == null)
                return false;
            int sp = p;
            int se = e;
            String s;
            do {
                if (!left())
                    break;
                s = select();
            } while (!isWord(s));
            int k = e;
            if (sp != p)
                k = se;
            int last;
            do {
                last = e;
                if (!left())
                    break;
                s = select();
            } while (isWord(s) && !stopOnLeft(s));
            e = last;
            Plugin.View.Selection m = fb.pluginview.select(new ZLTextFixedPosition(p, e, 0), new ZLTextFixedPosition(sp, k, 0));
            if (m != null) {
                text = m.getText();
                m.close();
            }
            return true;
        }

        public boolean isWord(String str) {
            if (str.length() != 1)
                return false;
            for (String z : STOPS) {
                if (str.contains(z))
                    return true;
            }
            return all.isWord(str.charAt(0));
        }

        public boolean nextWord() {
            all();
            if (all == null)
                return false;
            int sp = p;
            int se = e;
            String s;
            do {
                if (!right())
                    break;
                s = select();
            } while (!isWord(s));
            int k = e;
            if (sp != p)
                k = se;
            int last;
            do {
                last = e;
                if (!right())
                    break;
                s = select();
            } while (isWord(s) && !stopOnRight(s));
            e = last;
            Plugin.View.Selection m = fb.pluginview.select(new ZLTextFixedPosition(sp, k, 0), new ZLTextFixedPosition(p, e, 0));
            if (m != null) {
                text = m.getText();
                m.close();
            }
            return true;
        }

        public String getText() {
            return text;
        }

        @Override
        public int getParagraphIndex() {
            return p;
        }

        @Override
        public int getElementIndex() {
            return e;
        }

        @Override
        public int getCharIndex() {
            return c;
        }
    }

    public TTSPopup(FBReaderView v) {
        this.context = v.getContext();
        fb = v;
        tts = new TTS(context) {
            @Override
            public Locale getUserLocale() {
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

                String lang = shared.getString(BookApplication.PREFERENCE_LANGUAGE, ""); // take user lang preferences

                Locale locale;

                if (lang.isEmpty()) // use system locale (system language)
                    locale = Locale.getDefault();
                else
                    locale = new Locale(lang);

                return locale;
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                if (fb.tts == null)
                    return;
                marks.clear();
                marks.add(fragment.fragment);
                Storage.Bookmark bm = fragment.findWord(start, end);
                if (bm != null) {// words starting with STOP symbols are missing
                    marks.add(bm);
                    fragment.word = bm;
                } // else do not clear 'word', to prevent page scroll jumping
                if (fb.widget instanceof ScrollWidget && ((ScrollWidget) fb.widget).getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    Storage.Bookmark page = isEmpty(fragment.word) ? fragment.fragment : fragment.word;
                    int pos = ((ScrollWidget) fb.widget).adapter.findPage(page.start);
                    if (pos != -1) {
                        ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
                        int first = ((ScrollWidget) fb.widget).findFirstPage();
                        if (first != -1) {
                            ScrollWidget.ScrollAdapter.PageCursor cur = ((ScrollWidget) fb.widget).adapter.pages.get(first);
                            if (!c.equals(cur)) {
                                Runnable gravity = new Runnable() {
                                    @Override
                                    public void run() {
                                        updateGravity();
                                    }
                                };
                                if (c.end != null && cur.start != null && c.end.compareTo(cur.start) <= 0) {
                                    onScrollFinished.add(gravity);
                                    fb.scrollPrevPage();
                                }
                                if (c.start != null && cur.end != null && c.start.compareTo(cur.end) >= 0) {
                                    onScrollFinished.add(gravity);
                                    fb.scrollNextPage();
                                }
                            } else {
                                ensureVisible(page);
                            }
                        }
                    }
                }
                fb.ttsUpdate();
            }

            @Override
            public void onError(String utteranceId, Runnable done) {
                if (!fragment.isEmpty() && fragment.retry < 2) {
                    dones.remove(delayed);
                    handler.removeCallbacks(delayed);
                    delayed = null;
                    dones.remove(speakNext); // remove done
                    fragment.retry++;
                    ttsShowError("TTS Unknown error", 2000, new Runnable() {
                        @Override
                        public void run() {
                            speakNext();
                        }
                    });
                } else {
                    done.run(); // speakNext
                }
            }
        };
        tts.ttsCreate();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.tts_popup, null);
        View left = view.findViewById(R.id.tts_left);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
                selectPrev();
            }
        });
        View right = view.findViewById(R.id.tts_right);
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
                selectNext();
            }
        });
        play = (ImageView) view.findViewById(R.id.tts_play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tts.dones.contains(speakNext) || speakRetry != null) {
                    stop();
                } else {
                    resetColor();
                    speakNext();
                }
            }
        });
        View close = view.findViewById(R.id.tts_close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        int dp20 = ThemeUtils.dp2px(context, 20);
        FrameLayout f = new FrameLayout(context);
        FrameLayout round = new FrameLayout(context);
        round.setBackgroundResource(R.drawable.panel);
        round.addView(view);
        gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        f.addView(round, new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT, gravity));
        f.setPadding(dp20, dp20, dp20, dp20);
        this.view = f;
        this.panel = round;
    }

    void stop() {
        tts.close();
        tts.dones.remove(speakNext);
        handler.removeCallbacks(speakRetry);
        speakRetry = null;
        updatePlay();
    }

    public Context getContext() {
        return context;
    }

    public void speakNext() {
        if (fragment == null)
            selectNext();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                fragment.last = System.currentTimeMillis();
                tts.playSpeech(new TTS.Speak(tts.getTTSLocale(), fragment.fragmentText), speakNext);
                updatePlay();
            }
        };
        if (fb.widget instanceof ScrollWidget) {
            if (((ScrollWidget) fb.widget).getScrollState() == RecyclerView.SCROLL_STATE_IDLE)
                onScrollingFinished(ZLViewEnums.PageIndex.current);
        }
        if (onScrollFinished.isEmpty())
            r.run();
        else
            onScrollFinished.add(r);
    }

    public void updatePlay() {
        boolean p = tts.dones.contains(speakNext) || speakRetry != null;
        play.setImageResource(p ? R.drawable.ic_outline_pause_24 : R.drawable.ic_outline_play_arrow_24);
        fb.listener.ttsStatus(p);
    }

    public void selectPrev() {
        marks.clear();
        if (fragment == null) {
            if (fb.widget instanceof ScrollWidget) {
                int first = ((ScrollWidget) fb.widget).findFirstPage();
                ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(first);
                Storage.Bookmark bm = expandWord(new Storage.Bookmark("", c.start, c.start));
                fragment = new Fragment(bm);
            }
            if (fb.widget instanceof PagerWidget) {
                ZLTextPosition position = fb.getPosition();
                Storage.Bookmark bm = expandWord(new Storage.Bookmark("", position, position));
                fragment = new Fragment(bm);
            }
        } else {
            Storage.Bookmark bm = selectPrev(fragment.fragment);
            fragment = new Fragment(bm);
        }
        marks.add(fragment.fragment);
        if (fb.widget instanceof ScrollWidget) {
            ScrollWidget.ScrollAdapter.PageCursor nc;
            int pos = ((ScrollWidget) fb.widget).adapter.findPage(fragment.fragment.start);
            if (pos == -1)
                return;
            nc = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
            int first = ((ScrollWidget) fb.widget).findFirstPage();
            ScrollWidget.ScrollAdapter.PageCursor cur = ((ScrollWidget) fb.widget).adapter.pages.get(first);
            if (!nc.equals(cur)) {
                onScrollFinished.add(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
                fb.scrollPrevPage();
            } else {
                ensureVisible(fragment.fragment);
            }
            updateGravity();
        }
        if (fb.widget instanceof PagerWidget) {
            if (fb.pluginview == null) {
                ZLTextPosition start = fb.app.BookTextView.getStartCursor();
                if (start.compareTo(fragment.fragment.end) >= 0) {
                    onScrollFinished.add(new Runnable() {
                        @Override
                        public void run() {
                            updateGravity();
                        }
                    });
                    fb.scrollPrevPage();
                } else {
                    updateGravity();
                }
            } else {
                Plugin.View.Selection s = fb.pluginview.select(fragment.fragment.start, fragment.fragment.end);
                Rect dst = ((PagerWidget) fb.widget).getPageRect();
                ZLTextPosition px = fb.pluginview.getPosition();
                if (px.getParagraphIndex() > fragment.fragment.start.getParagraphIndex()) {
                    onScrollFinished.add(new Runnable() {
                        @Override
                        public void run() {
                            updateGravity();
                        }
                    });
                    fb.scrollPrevPage();
                } else {
                    Plugin.View.Selection.Page page = fb.pluginview.selectPage(px, ((PagerWidget) fb.widget).getInfo(), dst.width(), dst.height());
                    Plugin.View.Selection.Bounds bounds = s.getBounds(page);
                    if (fb.pluginview.reflow) {
                        bounds.rr = fb.pluginview.boundsUpdate(bounds.rr, ((PagerWidget) fb.widget).getInfo());
                        bounds.start = true;
                        bounds.end = true;
                    }
                    ArrayList<Rect> ii = new ArrayList<>(Arrays.asList(bounds.rr));
                    Collections.sort(ii, new SelectionView.LinesUL(ii));
                    s.close();
                    if (ii.get(ii.size() - 1).bottom < ((PagerWidget) fb.widget).getTop() + fb.pluginview.current.pageOffset / fb.pluginview.current.ratio) {
                        onScrollFinished.add(new Runnable() {
                            @Override
                            public void run() {
                                updateGravity();
                            }
                        });
                        fb.scrollPrevPage();
                    } else {
                        Rect r = SelectionView.union(Arrays.asList(bounds.rr));
                        if (((PagerWidget) fb.widget).getHeight() / 2 < r.centerY())
                            updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                        else
                            updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                    }
                }
            }
        }
        fb.ttsUpdate();
    }

    public void selectNext() {
        marks.clear();
        if (fragment == null) {
            if (fb.widget instanceof ScrollWidget) {
                int first = ((ScrollWidget) fb.widget).findFirstPage();
                ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(first);
                Storage.Bookmark bm = expandWord(new Storage.Bookmark("", c.start, c.start));
                fragment = new Fragment(bm);
            }
            if (fb.widget instanceof PagerWidget) {
                ZLTextPosition position = fb.getPosition();
                Storage.Bookmark bm = expandWord(new Storage.Bookmark("", position, position));
                fragment = new Fragment(bm);
            }
        } else {
            Storage.Bookmark bm = selectNext(fragment.fragment);
            fragment = new Fragment(bm);
        }
        marks.add(fragment.fragment);
        if (fb.widget instanceof ScrollWidget) {
            int pos = ((ScrollWidget) fb.widget).adapter.findPage(fragment.fragment.start);
            if (pos == -1)
                return;
            ScrollWidget.ScrollAdapter.PageCursor nc = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
            int first = ((ScrollWidget) fb.widget).findFirstPage();
            ScrollWidget.ScrollAdapter.PageCursor cur = ((ScrollWidget) fb.widget).adapter.pages.get(first);
            if (!nc.equals(cur)) {
                int page = ((ScrollWidget) fb.widget).adapter.findPage(nc);
                onScrollFinished.add(new Runnable() {
                    @Override
                    public void run() {
                        updateGravity();
                    }
                });
                ((ScrollWidget) fb.widget).smoothScrollToPosition(page);
            } else {
                ensureVisible(fragment.fragment);
                updateGravity();
            }
        }
        if (fb.widget instanceof PagerWidget) {
            if (fb.pluginview == null) {
                ZLTextPosition end;
                end = fb.app.BookTextView.getEndCursor();
                if (end.compareTo(fragment.fragment.start) <= 0) {
                    onScrollFinished.add(new Runnable() {
                        @Override
                        public void run() {
                            updateGravity();
                        }
                    });
                    fb.scrollNextPage();
                } else {
                    updateGravity();
                }
            } else {
                Plugin.View.Selection s = fb.pluginview.select(fragment.fragment.start, fragment.fragment.end);
                Rect dst = ((PagerWidget) fb.widget).getPageRect();
                ZLTextPosition px = fb.pluginview.getPosition();
                if (px.getParagraphIndex() < fragment.fragment.start.getParagraphIndex()) {
                    fb.scrollNextPage();
                } else {
                    Plugin.View.Selection.Page page = fb.pluginview.selectPage(px, ((PagerWidget) fb.widget).getInfo(), dst.width(), dst.height());
                    Plugin.View.Selection.Bounds bounds = s.getBounds(page);
                    if (fb.pluginview.reflow) {
                        bounds.rr = fb.pluginview.boundsUpdate(bounds.rr, ((PagerWidget) fb.widget).getInfo());
                        bounds.start = true;
                        bounds.end = true;
                    }
                    ArrayList<Rect> ii = new ArrayList<>(Arrays.asList(bounds.rr));
                    Collections.sort(ii, new SelectionView.LinesUL(ii));
                    s.close();
                    if (ii.get(0).bottom > ((PagerWidget) fb.widget).getBottom() + fb.pluginview.current.pageOffset / fb.pluginview.current.ratio) {
                        onScrollFinished.add(new Runnable() {
                            @Override
                            public void run() {
                                updateGravity();
                            }
                        });
                        fb.scrollNextPage();
                    } else {
                        Rect r = SelectionView.union(Arrays.asList(bounds.rr));
                        if (((PagerWidget) fb.widget).getHeight() / 2 < r.centerY())
                            updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                        else
                            updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                    }
                }
            }
        }
        fb.ttsUpdate();
    }

    public Storage.Bookmark selectNext(Storage.Bookmark bm) {
        if (fb.pluginview != null) {
            ZLTextPosition start = bm.end;
            PluginWordCursor k = new PluginWordCursor(start);
            if (k.nextWord()) {
                ZLTextPosition end = expandRight(k);
                bm = new Storage.Bookmark(k.getText(), start, end);
            }
            k.close();
            return bm;
        } else {
            ZLTextPosition start = bm.end;
            ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(fb.app.Model.getTextModel(), start.getParagraphIndex());
            ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
            wordCursor.moveTo(start);
            if (wordCursor.isEndOfParagraph())
                wordCursor.nextParagraph();
            else
                wordCursor.nextWord();
            start = wordCursor;
            ZLTextPosition end = expandRight(start);
            return new Storage.Bookmark(bm.text, start, end);
        }
    }

    public Storage.Bookmark selectPrev(Storage.Bookmark bm) {
        if (fb.pluginview != null) {
            ZLTextPosition end = bm.start;
            PluginWordCursor k = new PluginWordCursor(end);
            if (k.prevWord()) {
                ZLTextPosition start = expandLeft(k);
                bm = new Storage.Bookmark(k.getText(), start, end);
            }
            k.close();
            return bm;
        } else {
            ZLTextPosition end = bm.start;
            ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(fb.app.Model.getTextModel(), end.getParagraphIndex());
            ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
            wordCursor.moveTo(end);
            wordCursor.previousWord();
            if (wordCursor.getElementIndex() < 0) {
                if (!wordCursor.previousParagraph()) {
                    wordCursor.moveTo(0, 0);
                } else {
                    wordCursor.moveToParagraphEnd();
                    wordCursor.previousWord();
                }
            }
            end = wordCursor;
            ZLTextElement e = wordCursor.getElement();
            if (e instanceof ZLTextWord)
                wordCursor.setCharIndex(((ZLTextWord) e).Length - 1);
            ZLTextPosition start = expandLeft(end);
            return new Storage.Bookmark(bm.text, start, end);
        }
    }

    public void show() {
        fb.addView(view, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setVisibility(View.VISIBLE);
    }

    public void close() {
        if (fb.listener != null)
            fb.listener.ttsStatus(false);
        view.setVisibility(View.GONE);
        fb.removeView(view);
        fb.ttsClose();
        tts.close();
    }

    public void dismiss() {
        close();
        fb.tts = null;
    }

    public void ensureVisible(Storage.Bookmark bm) { // same page
        int pos = ((ScrollWidget) fb.widget).adapter.findPage(bm.start);
        ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
        ScrollWidget.ScrollAdapter.PageView v = ((ScrollWidget) fb.widget).findViewPage(c);
        int bottom = fb.getTop() + ((ScrollWidget) fb.widget).getMainAreaHeight();
        Rect rect;
        if (fb.pluginview != null) {
            rect = getRect(fb.pluginview, v, bm);
            if (rect == null)
                return;
        } else {
            if (v.text == null)
                return;
            rect = FBReaderView.findUnion(v.text.areas(), bm);
            if (rect == null)
                return;
        }
        rect.top += v.getTop();
        rect.bottom += v.getTop();
        rect.left += v.getLeft();
        rect.right += v.getLeft();
        int dy = 0;
        if (rect.bottom > bottom)
            dy = rect.bottom - bottom;
        if (rect.top < fb.getTop())
            dy = rect.top - fb.getTop();
        ((ScrollWidget) fb.widget).smoothScrollBy(0, dy);
    }

    public void scrollVerticallyBy(int dy) {
        if (dy > 0)
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        else
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        updateGravity();
    }

    public void updateGravity(int g) {
        gravity = g;
        handler.removeCallbacks(updateGravity);
        handler.postDelayed(updateGravity, 200);
    }

    public void updateGravity() {
        if (fragment == null || marks.isEmpty()) {
            updateGravity(gravity);
            return;
        }
        if (fb.pluginview == null) {
            View view = null;
            ZLTextElementAreaVector text = null;
            if (fb.widget instanceof ScrollWidget) {
                int pos = ((ScrollWidget) fb.widget).adapter.findPage(fragment.fragment.start);
                if (pos == -1)
                    return;
                ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
                ScrollWidget.ScrollAdapter.PageView v = ((ScrollWidget) fb.widget).findViewPage(c);
                view = v;
                if (v != null)
                    text = v.text;
            }
            if (fb.widget instanceof PagerWidget) {
                view = (View) fb.widget;
                text = fb.app.BookTextView.myCurrentPage.TextElementMap;
            }
            if (view == null || text == null) { // happens when page just invalidated
                updateGravity(gravity);
            } else {
                ArrayList<Rect> rr = new ArrayList<>();
                for (ZLTextElementArea a : text.areas()) {
                    if (a.compareTo(fragment.fragment.start) >= 0 && a.compareTo(fragment.fragment.end) <= 0)
                        rr.add(new Rect(a.XStart, a.YStart, a.XEnd, a.YEnd));
                }
                if (rr.size() == 0) {
                    updateGravity(gravity);
                } else {
                    Rect r = SelectionView.union(rr);
                    if (((View) fb.widget).getHeight() / 2 < view.getTop() + r.centerY())
                        updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                    else
                        updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                }
            }
        } else {
            View view = null;
            Reflow.Info info = null;
            if (fb.widget instanceof ScrollWidget) {
                int pos = ((ScrollWidget) fb.widget).adapter.findPage(fragment.fragment.start);
                if (pos == -1)
                    return;
                ScrollWidget.ScrollAdapter.PageCursor c = ((ScrollWidget) fb.widget).adapter.pages.get(pos);
                ScrollWidget.ScrollAdapter.PageView v = ((ScrollWidget) fb.widget).findViewPage(c);
                view = v;
                info = v.info;
            }
            if (fb.widget instanceof PagerWidget) {
                view = (View) fb.widget;
                info = ((PagerWidget) fb.widget).getInfo();
            }
            Plugin.View.Selection s = fb.pluginview.select(fragment.fragment.start, fragment.fragment.end);
            if (s != null) {
                Plugin.View.Selection.Page page = fb.pluginview.selectPage(fragment.fragment.start, info, view.getWidth(), view.getHeight());
                Plugin.View.Selection.Bounds bounds = s.getBounds(page);
                Rect r = SelectionView.union(Arrays.asList(bounds.rr));
                s.close();
                if (((View) fb.widget).getHeight() / 2 < view.getTop() + r.centerY())
                    updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                else
                    updateGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            }
        }
    }

    public String getText(ZLTextPosition start, ZLTextPosition end) {
        if (fb.pluginview != null) {
            Plugin.View.Selection s = fb.pluginview.select(start, end);
            if (s != null) {
                String str = s.getText();
                s.close();
                return str;
            }
            return null;
        } else {
            TextBuildTraverser tt = new TextBuildTraverser(fb.app.BookTextView);
            tt.traverse(start, end);
            return tt.getText();
        }
    }

    public void selectionOpen(Plugin.View.Selection s) {
        marks.clear();
        Storage.Bookmark bm = new Storage.Bookmark(s.getText(), s.getStart(), s.getEnd());
        bm = expandWord(bm);
        fragment = new Fragment(bm);
        marks.add(fragment.fragment);
        updateGravity();
        fb.ttsUpdate();
    }

    public void selectionOpen(int x, int y) { // pager only
        Storage.Bookmark bm = selectWord(fb.app.BookTextView.myCurrentPage.TextElementMap, x, y);
        bm = expandWord(bm);
        marks.clear();
        if (!isEmpty(bm)) {
            fragment = new Fragment(bm);
            marks.add(fragment.fragment);
        }
        updateGravity();
        fb.ttsUpdate();
    }

    public void selectionOpen(ScrollWidget.ScrollAdapter.PageCursor c, int x, int y) { // scrollwidget only
        ScrollWidget.ScrollAdapter.PageView v = ((ScrollWidget) fb.widget).findViewPage(c);
        Storage.Bookmark bm = selectWord(v.text, x, y);
        bm = expandWord(bm);
        marks.clear();
        if (!isEmpty(bm)) {
            fragment = new Fragment(bm);
            marks.add(fragment.fragment);
        }
        updateGravity();
        fb.ttsUpdate();
    }

    public void selectionClose() {
        marks.clear();
        fb.ttsUpdate();
    }

    public void onScrollingFinished(ZLViewEnums.PageIndex pageIndex) {
        for (Runnable r : onScrollFinished)
            r.run();
        onScrollFinished.clear();
        updateGravity();
    }

    public Storage.Bookmark selectWord(ZLTextElementAreaVector text, int x, int y) {
        ZLTextPosition start = null;
        ZLTextPosition end = null;
        for (ZLTextElementArea a : text.areas()) {
            if (a.XStart < x && a.XEnd > x && a.YStart < y && a.YEnd > y) {
                if (start == null)
                    start = a;
                if (end == null)
                    end = a;
                if (start.compareTo(a) > 0)
                    start = a;
                if (end.compareTo(a) < 0)
                    end = a;
            }
        }
        if (start == null || end == null)
            return new Storage.Bookmark();
        else
            return new Storage.Bookmark(getText(start, end), start, end);
    }

    public ZLTextPosition expandLeft(ZLTextPosition start) {
        if (fb.pluginview != null) {
            ZLTextPosition last;
            PluginWordCursor k = new PluginWordCursor(start);
            int count = 0;
            do {
                last = new ZLTextFixedPosition(k);
                k.prevWord();
                count++;
            } while (!isStopSymbol(k.getText()) && count < MAX_COUNT);
            if (stopOnLeft(k.getText()))
                last = new ZLTextFixedPosition(k);
            k.close();
            return last;
        } else {
            ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(fb.app.Model.getTextModel(), start.getParagraphIndex());
            ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
            wordCursor.moveTo(start);
            wordCursor.setCharIndex(0);
            ZLTextPosition last;
            ZLTextElement e = null;
            int count = 0;
            do {
                last = new ZLTextFixedPosition(wordCursor);
                wordCursor.previousWord();
                if (wordCursor.getElementIndex() < 0) {
                    if (!wordCursor.previousParagraph())
                        wordCursor.moveTo(0, 0);
                    break;
                }
                e = wordCursor.getElement();
                count++;
            } while (!isStopSymbol(e) && count < MAX_COUNT);
            if (stopOnLeft(e))
                last = wordCursor;
            return last;
        }
    }

    public ZLTextPosition expandRight(ZLTextPosition end) {
        if (fb.pluginview != null) {
            PluginWordCursor k = new PluginWordCursor(end);
            int count = 0;
            do {
                k.nextWord();
                count++;
            } while (!(isStopSymbol(k.getText()) && stopOnRight(k.getText())) && count < MAX_COUNT);
            end = new ZLTextFixedPosition(k);
            k.close();
            return end;
        } else {
            ZLTextParagraphCursor paragraphCursor = new ZLTextParagraphCursor(fb.app.Model.getTextModel(), end.getParagraphIndex());
            ZLTextWordCursor wordCursor = new ZLTextWordCursor(paragraphCursor);
            wordCursor.moveTo(end);
            int count = 0;
            ZLTextElement e;
            for (e = wordCursor.getElement(); !(isStopSymbol(e) && stopOnRight(e)) && count < MAX_COUNT; e = wordCursor.getElement()) {
                wordCursor.nextWord();
                count++;
            }
            e = wordCursor.getElement();
            if (e instanceof ZLTextWord)
                wordCursor.setCharIndex(((ZLTextWord) e).Length - 1);
            return wordCursor;
        }
    }

    public Storage.Bookmark expandWord(Storage.Bookmark bm) {
        if (isEmpty(bm))
            return bm;
        ZLTextPosition start = expandLeft(bm.start);
        ZLTextPosition end = expandRight(bm.end);
        return new Storage.Bookmark(getText(start, end), start, end);
    }

    public void ttsShowError(String text, int delay, final Runnable done) {
        for (Storage.Bookmark m : marks)
            m.color = TTS_BG_ERROR_COLOR;
        fb.ttsUpdate();
        handler.removeCallbacks(speakRetry);
        speakRetry = new Runnable() {
            @Override
            public void run() {
                resetColor();
                done.run();
                speakRetry = null;
            }
        };
        handler.postDelayed(speakRetry, delay);
        Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
    }

    public void resetColor() {
        for (Storage.Bookmark m : marks)
            m.color = TTS_BG_COLOR;
        fb.ttsUpdate();
    }
}
