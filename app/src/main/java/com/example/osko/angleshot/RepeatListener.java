package com.example.osko.angleshot;

/**
 * Created by kyeon on 2016-02-06.
 */

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

/**

 * A class, that can be used as a TouchListener on any view (e.g. a Button).

 * It cyclically runs a clickListener, emulating keyboard-like behaviour. First

 * click is fired immediately, next after initialInterval, and subsequent after

 * normalInterval.

 *

 * <p>Interval is scheduled after the onClick completes, so it has to run fast.

 * If it runs slow, it does not generate skipped onClicks.

 */

public class RepeatListener implements View.OnTouchListener {
    private Handler handler = new Handler();
    private int initialInterval;
    private final int normalInterval;
    private final View.OnClickListener clickListener;


    private Runnable handlerRunnable = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, normalInterval);
            clickListener.onClick(downView);
        }
    };




    private View downView;
    /**

     * @param initialInterval The interval after first click event

     * @param normalInterval The interval after second and subsequent click

     *       events

     * @param clickListener The OnClickListener, that will be called

     *       periodically

     */
    public RepeatListener(int initialInterval, int normalInterval, View.OnClickListener clickListener) {
        if (clickListener == null)
            throw new IllegalArgumentException("null runnable");
        if (initialInterval < 0 || normalInterval < 0)
            throw new IllegalArgumentException("negative interval");
        this.initialInterval = initialInterval;
        this.normalInterval = normalInterval;
        this.clickListener = clickListener;

    }




    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handler.removeCallbacks(handlerRunnable);
                handler.postDelayed(handlerRunnable, initialInterval);
                downView = view;
                clickListener.onClick(view);
                break;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(handlerRunnable);
                downView = null;
                break;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(handlerRunnable);
                downView = null;
                break;
        }
        return false;
    }
}

