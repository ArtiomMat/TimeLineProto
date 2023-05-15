package com.artiom.timelineproto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@SuppressLint({"ViewConstructor"})
public class Timeline extends View {

    public static final float MOMENT_RADIUS = 30;

    // TODO:
    /*
        // Step 1: Create a white circle drawable
        ShapeDrawable whiteCircle = new ShapeDrawable(new OvalShape());
        whiteCircle.getPaint().setColor(Color.WHITE);

        // Step 2: Cache the white circle drawable

        // You can store the white circle drawable as a class member or use any caching mechanism like LruCache or HashMap.

        // Step 3: Tint the cached drawable
        Drawable cachedDrawable = whiteCircle.getConstantState().newDrawable().mutate();
        cachedDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        // 'color' should be the desired color you want to apply to the circle.

        // Now you can use the 'cachedDrawable' to draw the colored circle on the canvas using the drawCircle() method.

        // Example usage:
        canvas.drawCircle(centerX, centerY, radius, cachedDrawable);
     */

    private final View parentView;
    private final ArrayList<Moment> moments;

    private Bitmap timelineBitmap; // We cache the timeline as it doesn't move.
    private final Paint paint;
    Canvas timelineCanvas;

    // A moment literally represents a moment in time.
    // Usually a more suitable name would be task.
    public class Moment implements Comparable<Moment> {
        public static final int MAX_TAGS = 4;
        // In minutes
        public int t;

        public String name;
        public byte[] tags;
        public int iconIndex;
        public int color;

        public void setupTags(int numTags) {
            // tags
            if (numTags < 1) {
                Snackbar.make(parentView, "Tags are important, you need one tag at least.", Snackbar.LENGTH_SHORT).show();
                numTags = 1;
            }
            else if (numTags > MAX_TAGS) {
                Snackbar.make(parentView, String.format(Locale.ENGLISH, "Up to %d tags please.", MAX_TAGS), Snackbar.LENGTH_SHORT).show();
                numTags = MAX_TAGS;
            }

            tags = new byte[numTags];
        }

        public void setupTime(int t) {
            // tags
            if (t < 0) {
                t = 0;
                Snackbar.make(parentView, "I'm planning to make negative time a feature, but not now.", Snackbar.LENGTH_SHORT).show();
            }
            else if (t > 24*60) {
                Snackbar.make(parentView, "I'm planning to make time overflow a feature, but not now.", Snackbar.LENGTH_SHORT).show();
                t = 24*60;
            }

            this.t = t;
        }

        public Moment(int t, int numTags) {
            // Randomize color
            float r = ThreadLocalRandom.current().nextFloat();
            float g = ThreadLocalRandom.current().nextFloat();
            float b = ThreadLocalRandom.current().nextFloat();
            color = Color.valueOf(r, g, b).toArgb();

            // numTags
            setupTags(numTags);

            // t
            setupTime(t);
        }

        public Moment(int t, int numTags, int color) {
            // color
            this.color = color;
            // numTags
            setupTags(numTags);
            // t
            setupTime(t);
        }

        @Override
        public int compareTo(Moment o) {
            return t - o.t;
        }
    }

    // So we order the list.
    void onMomentsTimeUpdated() {
        Collections.sort(moments);
    }

    public void addMoment(int t, int tags) {
        moments.add(new Moment(t, tags));
        onMomentsTimeUpdated();
    }
    public void addMoment(int t, int tags, int color) {
        moments.add(new Moment(t, tags, color));
        onMomentsTimeUpdated();
    }

    public Timeline(Context context, View parentView) {
        super(context);
        this.moments = new ArrayList<>();
        this.parentView = parentView;


        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(20f);

        invalidate();
    }

    float calcPosX(Moment moment) {
        return ((moment.t - MainActivity.timeStart) / MainActivity.timeScale) * getWidth();
    }

    // This is here because getWidth and getHeight obviously wont work immidetly
    // when we create the MF, we need to wait until we attach it to a layout.
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);



        // Create a cache bitmap after the size is determined
//        timelineBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
//        timelineCanvas = new Canvas(timelineBitmap);
//
//        float startY = timelineBitmap.getHeight() / 2f;
//        float endY = timelineBitmap.getHeight() / 2f;
//
//        timelineCanvas.drawLine(0, startY, (float) w, endY, paint);

        paint.setStyle(Paint.Style.FILL);
    }

    // -1 for below 0, 1 for above the width, 0 for nothing.
    // TODO: Includes radius

    float drawMoment(Canvas canvas, int i, float posX) {
        paint.setColor(moments.get(i).color);
        canvas.drawCircle(posX, getHeight() / 2.0f, MOMENT_RADIUS, paint);

        // Draw a line with the moment's color up until the next moment, if there is a next moment
        if (i < moments.size()-1) {
            float nextPosX = calcPosX(moments.get(i+1));

            canvas.drawLine(posX, getHeight() / 2.0f, nextPosX, getHeight() / 2.0f, paint);

            // posX is now the next posX(updated)
            return nextPosX;
        }
        return posX;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float posX = calcPosX(moments.get(0)); // Updated to next at the end of the loop so we put it here
        // If already the first one is too far then draw a line to it
        if (posX + MOMENT_RADIUS > 0) {
            // TODO: Find out how to have a theme dependant deactivated color
            paint.setColor(MainActivity.theme_color);
            canvas.drawLine(0, getHeight() / 2.0f, posX, getHeight() / 2.0f, paint);
        }
        int lastOutsideStatus = 0;

        for (int i = 0; i < moments.size(); i++) {
            if (posX - MOMENT_RADIUS > getWidth()) { // Above width
                if (lastOutsideStatus == -1) {
                    paint.setColor(moments.get(i - 1).color);
                    canvas.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, paint);
                }
                break;
            }
            else if (posX + MOMENT_RADIUS < 0) { // Below 0
                lastOutsideStatus = -1;
                if (i < moments.size()-1)
                    posX = calcPosX(moments.get(i + 1));
                else {
                    // Draw a line of the last moment's color if it is indeed the LAST MOMENT.
                    paint.setColor(moments.get(i).color);
                    canvas.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, paint);
                }
            }
            else { // Inside
                // If the last one was below 0 draw it's line
                if (lastOutsideStatus == -1) {
                    lastOutsideStatus = 0;
                    paint.setColor(moments.get(i - 1).color);
                    canvas.drawLine(0, getHeight() / 2.0f, posX, getHeight() / 2.0f, paint);
                }

                // If this is the last one draw a line with this one's color to the end.
                if (i == moments.size()-1 && posX - MOMENT_RADIUS < getWidth()) {
                    paint.setColor(moments.get(i).color);
                    canvas.drawLine(posX, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, paint);
                }

                posX = drawMoment(canvas, i, posX);
            }
        }
    }

}
