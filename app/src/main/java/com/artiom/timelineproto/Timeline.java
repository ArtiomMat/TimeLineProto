package com.artiom.timelineproto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@SuppressLint({"ViewConstructor"})
public class Timeline extends View {
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

    public static final float MOMENT_RADIUS = 40;

    private final View parentView;
    private final ArrayList<Moment> moments;

    private Bitmap timelineBitmap; // We cache the timeline as it doesn't move.
    private final Paint linePaint, momentPaint;
    Canvas timelineCanvas;

    public int inactiveTimelineColor;
    public int firstVisibleMoment = -1, lastVisibleMoment = -1;

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

    // NOTE: We need the MainActivity context
    public Timeline(Context context, View parentView) {
        super(context);

        this.moments = new ArrayList<>();
        this.parentView = parentView;

        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(30f);

        momentPaint = new Paint();
        momentPaint.setStyle(Paint.Style.FILL);
        momentPaint.setShadowLayer(10f, 0f, 0f, Color.BLACK); // Add a shadow

        // Use hardware rendering
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Setup the color of an inactive region

        TypedValue typedValue = new TypedValue();
        boolean resolved = getContext().getTheme().resolveAttribute(R.attr.inactiveTimelineColor, typedValue, true);
        if (resolved) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                // The attribute was resolved to a color value
                int color = typedValue.data;
                Log.d("onCreate()", String.valueOf(color));
                inactiveTimelineColor = color;
            } else {
                // The attribute was resolved to a color reference, you need to resolve it to an actual color value
                int colorRes = typedValue.resourceId;
                int color = ContextCompat.getColor(getContext(), colorRes);
                Log.d("onCreate()", String.valueOf(color));
                inactiveTimelineColor = color;
            }
        } else {
            Log.d("onCreate()", "Failed to get theme_color.");
        }
    }

    @Override
    public boolean performClick() {
//        Log.d("performClick", "OMG CLICK!");
        // Handle the click event here
        // Perform any necessary actions
        return super.performClick();
    }

    private int touchedMomentIndex = -1;
    private int touchedMomentPreT = -1;
    // This is the index of the moment the touched moment finna replace.
    // This index is used for 2 things:
    // 1. Checking if the moment overlaps with any other moment.
    // 2. Re-sorting more efficiently because we know exactly where it wants to go.
    // Calculation is in the ACTION_MOVE case, where we simply check if we went over of below neighbor.
    private int touchedMomentReplaceIndex = -1;
    private boolean touchedMomentOverlap = false;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float touchX = event.getX();

        switch (action) {
            case MotionEvent.ACTION_DOWN: // Finger just pressed the screen
                Log.d("performClick", "MotionEvent.ACTION_DOWN");
                // -1 indicates nothing is visible
                if (firstVisibleMoment == -1) {
                    Log.d("performClick", "Nothing visible ");
                    break;
                }

                // Loop through all moments visible and find the one being touched
                for (int i = firstVisibleMoment; i <= lastVisibleMoment; i++) {
                    float momentX = calcPosX(moments.get(i));

                    // Checks if within range of the moment
                    if (touchX < momentX + MOMENT_RADIUS && touchX > momentX - MOMENT_RADIUS) {
                        touchedMomentIndex = touchedMomentReplaceIndex = i;
                        touchedMomentPreT = moments.get(i).t;
                        Log.d("performClick", "Selected "+i);
                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE: // Finger is moving on the screen
//                Log.d("performClick", "OMG MOVE!");
                // touchedMomentIndex = -1 indicates that no moment was even touched, the user is just fucking moving their finger...
                if (touchedMomentIndex == -1)
                    break;

                int newT = calcT((int) touchX);
                moments.get(touchedMomentIndex).t = newT;

                // TODO: Touch overlap, what if it overlaps itself though *brain emoji*, make sure that is handled.

                // As long as we are over the moment that is SUPPOSED to be PREVIOUS to the moment we are replacing, move on an replace this moment instead.
                while (touchedMomentReplaceIndex > 0 && newT < moments.get(touchedMomentReplaceIndex-1).t)
                    touchedMomentReplaceIndex--;
                // As long as we are over the moment that is SUPPOSED to be NEXT to the moment we are replacing, move on an replace this moment instead.
                while (touchedMomentReplaceIndex < moments.size()-1 && newT > moments.get(touchedMomentReplaceIndex+1).t)
                    touchedMomentReplaceIndex++;

                invalidate();
                break;
            case MotionEvent.ACTION_UP: // Finger just released the screen
                // Sort the moments again.
                if (touchedMomentIndex > -1) {
                    // Check if overlapped, if we did, place the moment back!
                    if (touchedMomentOverlap) {
                        Snackbar.make(parentView, "Moment overlap!", Snackbar.LENGTH_SHORT).show();
                        moments.get(touchedMomentIndex).t = touchedMomentPreT;
                    }
                    // We are good, no overlapping...
                    else {
                        Moment backup = moments.get(touchedMomentIndex);

                        // If we moved it back in time then we need one procedure, and another for moving forward
                        if (touchedMomentReplaceIndex < touchedMomentIndex) {
                            // Essentially shift all the moments after the moved moment.
                            for (int i = touchedMomentIndex; i > touchedMomentReplaceIndex; i--)
                                moments.set(i, moments.get(i-1));
                            // Put the moved moment in it's place.
                            moments.set(touchedMomentReplaceIndex, backup);
                        }
                        else {
                            // Essentially shift all the moments before the moved moment.
                            for (int i = touchedMomentIndex; i < touchedMomentReplaceIndex; i++)
                                moments.set(i, moments.get(i+1));
                            // Put the moved moment in it's place.
                            moments.set(touchedMomentReplaceIndex, backup);
                        }
                    }
                }

                touchedMomentIndex = -1;
                performClick();
                break;

            default:
                return super.onTouchEvent(event);
        }

        return true;
    }

    float calcPosX(Moment moment) {
        return ((moment.t - MainActivity.timeStart) / MainActivity.timeScale) * getWidth();
    }

    int calcT(int x) {
        return (int) ((x*1.0f/getWidth()) * MainActivity.timeScale + MainActivity.timeStart);
    }

    // This is here because getWidth and getHeight obviously wont work immidetly
    // when we create the MF, we need to wait until we attach it to a layout.
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        invalidate();
    }

    // -1 for below 0, 1 for above the width, 0 for nothing.
    // TODO: Includes radius

    float drawMoment(Canvas canvas, int i, float posX) {
        float ret = posX;

        // Draw a line with the moment's color up until the next moment, if there is a next moment
        if (i < moments.size()-1) {
            ret = calcPosX(moments.get(i+1));
            drawLine(canvas, i, posX, ret);
        }

        momentPaint.setColor(moments.get(i).color);
        canvas.drawCircle(posX, getHeight() / 2.0f, MOMENT_RADIUS, momentPaint);

        return ret;
    }

    void drawLine(Canvas canvas, int momentIndex, float startX, float endX) {
        linePaint.setColor(moments.get(momentIndex).color);
        canvas.drawLine(startX, getHeight() / 2.0f, endX, getHeight() / 2.0f, linePaint);
    }

    // TODO: This is one of the most expensive functions probably, find ways to optimize it to the level of a normal SeekBar.
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        firstVisibleMoment = -1;
        lastVisibleMoment = moments.size() - 1;

        float posX = calcPosX(moments.get(0)); // Updated to next at the end of the loop so we put it here
        // If already the first one is too far then draw a line to it
        if (posX + MOMENT_RADIUS > 0) {
            firstVisibleMoment = 0;
            // TODO: Find out how to have a theme dependant deactivated color
            linePaint.setColor(inactiveTimelineColor);
            canvas.drawLine(0, getHeight() / 2.0f, posX, getHeight() / 2.0f, linePaint);
        }
        int lastOutsideStatus = 0;

        for (int i = 0; i < moments.size(); i++) {
            if (posX - MOMENT_RADIUS > getWidth()) { // Above width
                // Only set lastVisibleMoment to the last visible if there was a first visible
                if (firstVisibleMoment > -1)
                    lastVisibleMoment = i - 1;

                if (lastOutsideStatus == -1) {
                    linePaint.setColor(moments.get(i - 1).color);
                    canvas.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, linePaint);
                }
                break;
            }
            else if (posX + MOMENT_RADIUS < 0) { // Below 0
                lastOutsideStatus = -1;
                if (i < moments.size()-1)
                    posX = calcPosX(moments.get(i + 1));
                else {
                    // Draw a line of the last moment's color if it is indeed the LAST MOMENT.
                    linePaint.setColor(moments.get(i).color);
                    canvas.drawLine(0, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, linePaint);
                }
            }
            else { // Inside
                // If the last one was below 0 draw it's line
                if (lastOutsideStatus == -1) {
                    firstVisibleMoment = i; // This is the first visible!
                    lastOutsideStatus = 0;
                    linePaint.setColor(moments.get(i - 1).color);
                    canvas.drawLine(0, getHeight() / 2.0f, posX, getHeight() / 2.0f, linePaint);
                }

                // If this is the last one draw a line with this one's color to the end.
                if (i == moments.size()-1 && posX - MOMENT_RADIUS < getWidth()) {
                    linePaint.setColor(moments.get(i).color);
                    canvas.drawLine(posX, getHeight() / 2.0f, getWidth(), getHeight() / 2.0f, linePaint);
                }

                posX = drawMoment(canvas, i, posX);
            }
        }
    }

}
