package com.github.axet.bookreader.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.LineBackgroundSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormatter {

    public static class Replacement {
        public int start;
        public int end;
        public String text;

        public Replacement(int s, int e, String t) {
            start = s;
            end = e;
            text = t;
        }
    }

    public interface MatcherReplacement {
        String run(Matcher m);
    }

    public static class Sort implements Comparator<Replacement> {
        @Override
        public int compare(Replacement o1, Replacement o2) {
            return Integer.valueOf(o1.start).compareTo(o2.start);
        }
    }

    public static class HihglightLineSpan implements LineBackgroundSpan {
        Paint paint = new Paint();

        public HihglightLineSpan() {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.MAGENTA);
        }

        @Override
        public void drawBackground(Canvas c, Paint p,
                                   int left, int right,
                                   int top, int baseline, int bottom,
                                   CharSequence text, int start, int end,
                                   int lnum) {
            Rect clipRect = new Rect();
            c.getClipBounds(clipRect);
            clipRect.top = top;
            clipRect.bottom = bottom;
            c.drawRect(clipRect, paint);
        }
    }

    public static boolean find(ArrayList<Replacement> reps, Matcher m) {
        for (Replacement s : reps) {
            if (s.start <= m.start() && m.start() < s.end || s.start <= m.end() && m.end() < s.end)
                return true;
        }
        return false;
    }

    public static void replace(ArrayList<Replacement> reps, String json, String pattern, MatcherReplacement matcher) {
        Pattern p = Pattern.compile(pattern, Pattern.MULTILINE | Pattern.DOTALL);
        Matcher m = p.matcher(json);
        while (m.find()) {
            if (!find(reps, m))
                reps.add(new Replacement(m.start(), m.end(), Matcher.quoteReplacement(matcher.run(m))));
        }
    }

    public static void process(ArrayList<Replacement> reps, String json, StringBuffer sb) {
        Collections.sort(reps, new Sort());
        int pos = 0;
        for (int i = 0; i < reps.size(); i++) {
            Replacement s = reps.get(i);
            sb.append(json, pos, s.start);
            sb.append(reps.get(i).text);
            pos = s.end;
        }
        sb.append(json, pos, json.length());
    }

    public static String json2textview(String json) {
        ArrayList<Replacement> reps = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        sb.append("<tt>");
        replace(reps, json, "\"[^\"]*\"", new MatcherReplacement() {
            @Override
            public String run(Matcher m) {
                return "<font color=\"green\">" + m.group(0) + "</font>";
            }
        });
        replace(reps, json, "[0-9]+", new MatcherReplacement() {
            @Override
            public String run(Matcher m) {
                return "<b><font color=\"blue\">" + m.group(0) + "</font></b>";
            }
        });
        replace(reps, json, "\n", new MatcherReplacement() {
            @Override
            public String run(Matcher m) {
                return "<br/>";
            }
        });
        replace(reps, json, "\\s", new MatcherReplacement() {
            @Override
            public String run(Matcher m) {
                return "&ensp;";
            }
        });
        process(reps, json, sb);
        sb.append("</tt>");
        return sb.toString();
    }

    public static void highlightLine(SpannableStringBuilder h, int s, int e) {
        h.setSpan(new BackgroundColorSpan(Color.RED), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        h.setSpan(new TextFormatter.HihglightLineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
