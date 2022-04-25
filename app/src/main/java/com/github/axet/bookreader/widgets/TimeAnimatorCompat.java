package com.github.axet.bookreader.widgets;

import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;

public class TimeAnimatorCompat {
    Handler handler = new Handler();
    TimeListener listener;
    ValueAnimator v;
    Runnable run = new Runnable() {
        @Override
        public void run() {
            if (listener != null)
                listener.onTimeUpdate(TimeAnimatorCompat.this, 0, 0);
            handler.postDelayed(run, 1000 / 24); // 24 FPS
        }
    };

    interface TimeListener {
        void onTimeUpdate(TimeAnimatorCompat animation, long totalTime, long deltaTime);
    }

    public TimeAnimatorCompat() {
        if (Build.VERSION.SDK_INT >= 16)
            v = new TimeAnimator();
        else if (Build.VERSION.SDK_INT >= 11)
            v = ValueAnimator.ofFloat(0f, 1f);
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= 11)
            v.start();
        else
            run.run();
    }

    public void cancel() {
        if (Build.VERSION.SDK_INT >= 11)
            v.cancel();
        else
            handler.removeCallbacks(run);
    }

    public void setTimeListener(TimeListener l) {
        if (Build.VERSION.SDK_INT >= 16) {
            ((TimeAnimator) v).setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
                    if (listener != null)
                        listener.onTimeUpdate(TimeAnimatorCompat.this, totalTime, deltaTime);
                }
            });
        } else if (Build.VERSION.SDK_INT >= 11) {
            v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @TargetApi(11)
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (listener != null)
                        listener.onTimeUpdate(TimeAnimatorCompat.this, 0, 0);
                }
            });
        }
        listener = l;
    }
}
