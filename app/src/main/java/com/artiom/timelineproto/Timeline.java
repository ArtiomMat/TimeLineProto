package com.artiom.timelineproto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

@SuppressLint({"ViewConstructor"})
public class Timeline extends View {
    // A moment literally represents a moment in time.
    // Usually a more suitable name would be task.
    public class Moment implements Comparable<Moment> {
        public static final int MAX_TAGS = 4;
        // In minutes
        public int t;

//        public String name;
        public byte[] tags;
//        public int iconIndex;
        public int color;

        public void setupTags(int numTags) {
            // tags
            if (numTags < 1) {
                Toast.makeText(getContext(), "Tags are important, you need one tag at least.", Toast.LENGTH_LONG).show();
                numTags = 1;
            }
            else if (numTags > MAX_TAGS) {

                Toast.makeText(getContext(), String.format(Locale.ENGLISH, "Up to %d tags please.", MAX_TAGS), Toast.LENGTH_SHORT).show();
                numTags = MAX_TAGS;
            }

            tags = new byte[numTags];
        }

        public void setupTime(int t) {
            // tags
            if (t < 0) {
                t = 0;
                Toast.makeText(getContext(), "I'm planning to make negative time a feature, but not now.", Toast.LENGTH_LONG).show();
            }
            else if (t > 24*60) {
                Toast.makeText(getContext(), "I'm planning to make time overflow a feature, but not now.", Toast.LENGTH_LONG).show();
                t = 24*60;
            }

            this.t = t;
        }

//        public Moment(int t, int numTags) {
//            // Randomize color
//            float r = ThreadLocalRandom.current().nextFloat();
//            float g = ThreadLocalRandom.current().nextFloat();
//            float b = ThreadLocalRandom.current().nextFloat();
//            color = Color.valueOf(r, g, b).toArgb();
//
//            // numTags
//            setupTags(numTags);
//
//            // t
//            setupTime(t);
//        }

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

    // In SP units initially here, but becomes
    public float timelineStroke = 2.5f, momentRadius = timelineStroke*2.5f, momentTouchRadius = 20;


    private final ArrayList<Moment> moments;
    private final Drawable[] tagDrawables; // TODO: Maybe make it static?

    private final Paint linePaint, momentPaint;

    public static int inactiveTimelineColor = 0;
    public static int timelineBackgroundColor = 0;
    public int firstVisibleMoment = -1, lastVisibleMoment = -1;

    public static int padding = 30; // In DP

    public float displayDensity = getResources().getDisplayMetrics().density;

    // NOTE: We need the MainActivity context
    public Timeline(Context context, View parentView) {
        super(context);

        this.moments = new ArrayList<>();

        // Setup stuff that is in DP:
        timelineStroke *= displayDensity;
        momentRadius *= displayDensity;
        momentTouchRadius *= displayDensity;
        padding *= displayDensity;

        // Load tag drawables
        TypedArray tagIconsArray = getResources().obtainTypedArray(R.array.tag_icons_array);
        tagDrawables = new Drawable[tagIconsArray.length()];
        for (int i = 0; i < tagDrawables.length; i++)
            tagDrawables[i] = tagIconsArray.getDrawable(i);
        tagIconsArray.recycle();

        // linePaint
        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(timelineStroke);
        // momentPaint
        momentPaint = new Paint();
        momentPaint.setStyle(Paint.Style.FILL);

        // Use hardware rendering
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Setup the colors...
        inactiveTimelineColor = Util.getColorAttr(getContext(), R.attr.inactiveTimelineColor);
        timelineBackgroundColor = Util.getColorAttr(getContext(), R.attr.timelineBackgroundColor);
        parentView.setBackgroundColor(timelineBackgroundColor);

        Log.d("Timeline()", "Called.");
    }

    void replaceMomentWithPrev(int i) {
        Moment prev = moments.get(i-1);
        Moment moment = moments.get(i);
        moments.set(i-1, moment);
        moments.set(i, prev);
    }

    // This is a sort function that should be avoided if possible.
//    void sortMoments() {
//        Collections.sort(moments);
//    }

    void sortMomentToFirst(int i) {
        for (; i > 0; i--) {
            if (moments.get(i).t < moments.get(i-1).t)
                replaceMomentWithPrev(i);
            else
                break;
        }
    }

    void sortMomentToLast(int i) {
        for (; i < moments.size()-1; i++) {
            if (moments.get(i).t > moments.get(i+1).t)
                replaceMomentWithPrev(i+1);
            else
                break;
        }
    }

    // This version of sortMoments let's you sort if only a single moment was added.
    // This should be the more common function that you use.
    void sortMoments(int outlierIndex) {
        Moment outlier = moments.get(outlierIndex);
        if (outlierIndex == moments.size()-1)
            sortMomentToFirst(outlierIndex);
        else if (outlierIndex == 0)
            sortMomentToLast(outlierIndex);
        else if (outlier.t > moments.get(outlierIndex+1).t) {
            replaceMomentWithPrev(outlierIndex+1);
            sortMomentToLast(outlierIndex+1);
        }
        else if (outlier.t < moments.get(outlierIndex-1).t) {
            replaceMomentWithPrev(outlierIndex);
            sortMomentToFirst(outlierIndex-1);
        }
    }

    public void addMoment(int t, int tags, int color) {
        Moment m = new Moment(t, tags, color);
        moments.add(m);
        for (int i = 0; i < m.tags.length; i++) {
            m.tags[i] = (byte) (Math.random()*tagDrawables.length);
        }
        sortMomentToFirst(moments.size()-1);
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
    private Drawable touchTagDrawable;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float touchX = event.getX();

        switch (action) {
            case MotionEvent.ACTION_DOWN: // Finger just pressed the screen
                // -1 indicates nothing is visible
                if (firstVisibleMoment == -1)
                    break;

                // Loop through all moments visible and find the one being touched
                for (int i = firstVisibleMoment; i <= lastVisibleMoment; i++) {
                    float momentX = calcPosX(moments.get(i));

                    // Checks if within range of the moment
                    if (touchX < momentX + momentTouchRadius && touchX > momentX - momentTouchRadius) {
                        touchedMomentIndex = i;
                        touchedMomentPreT = moments.get(i).t; // Save the moment time

                        // Setup the drawable
                        touchTagDrawable = tagDrawables[moments.get(i).tags[0]];
                        touchTagDrawable.setTint(moments.get(touchedMomentIndex).color);

                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE: { // Finger is moving on the screen
//                Log.d("performClick", "OMG MOVE!");
                // touchedMomentIndex = -1 indicates that no moment was even touched, the user is just fucking moving their finger...
                if (touchedMomentIndex == -1)
                    break;

                // Cap touch to padding
                if (touchX > getWidth()-padding)
                    touchX = getWidth()-padding;
                else if (touchX < padding)
                    touchX = padding;

                int newT = calcT((int) touchX);

                moments.get(touchedMomentIndex).t = newT;

                if (touchedMomentIndex > 0 && newT < moments.get(touchedMomentIndex - 1).t)
                    replaceMomentWithPrev(touchedMomentIndex--);
                // As long as we are over the moment that is SUPPOSED to be NEXT to the moment we are replacing, move on an replace this moment instead.
                else if (touchedMomentIndex < moments.size() - 1 && newT > moments.get(touchedMomentIndex + 1).t)
                    replaceMomentWithPrev(++touchedMomentIndex);

                // Update the drawable
                int size = (int) momentRadius*3;
                int x = (int) (touchX-size/2.0f);
                // TODO: Round the X, so it snaps together with the moment. Right now it's smooth at small scales.
                int y = (int) (getHeight() / 2 - size - momentRadius);
                touchTagDrawable.setBounds(x, y, (int) (size+x), size+y);

                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP: // Finger just released the screen
                // Sort the moments again.
                if (touchedMomentIndex > -1) {

                    // Check if we CAN overlap THEN if we overlapped, if we did, place the moment back!
                    if (
                        (touchedMomentIndex < moments.size() - 1 && moments.get(touchedMomentIndex).t == moments.get(touchedMomentIndex+1).t)
                            ||
                            (touchedMomentIndex > 0 && moments.get(touchedMomentIndex).t == moments.get(touchedMomentIndex-1).t)
                    ) {
                        Toast.makeText(getContext(), "Moment overlap!", Toast.LENGTH_SHORT).show();

                        moments.get(touchedMomentIndex).t = touchedMomentPreT;

                    }

                    sortMoments(touchedMomentIndex);
                    touchedMomentIndex = -1;
                    touchTagDrawable = null;
                    invalidate();
                }

                performClick();
                break;

            default:
                return super.onTouchEvent(event);
        }

        return true;
    }

    // Should be used exclusively for calculating the position of a moment.
    float calcPosX(Moment moment) {
        int paddedWidth = getWidth()-(padding*2);
        float pos = ((moment.t - MainActivity.timeStart) / MainActivity.timeScale) * paddedWidth;
        pos += padding;

        return pos;
    }

    int calcT(int x) {
        int paddedWidth = getWidth()-(padding*2);

        return (int) (((x-padding)*1.0f/paddedWidth) * MainActivity.timeScale + MainActivity.timeStart);
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

            drawMomentLine(canvas, i, posX, Math.min(ret, getWidth()-padding));
        }

        momentPaint.setColor(moments.get(i).color);
        canvas.drawCircle(posX, getHeight() / 2.0f, momentRadius, momentPaint);

        return ret;
    }

    // This one draws a line with a moment's color
    void drawMomentLine(Canvas canvas, int momentIndex, float startX, float endX) {
        drawLine(canvas, moments.get(momentIndex).color, startX, endX);
    }

    void drawLine(Canvas canvas, int color, float startX, float endX) {
        linePaint.setColor(color);
        canvas.drawLine(startX, getHeight() / 2.0f, endX, getHeight() / 2.0f, linePaint);
    }

    // TODO: This is one of the most expensive functions probably, find ways to optimize it to the level of a normal SeekBar.
    // UPDATE May 18 2023: onDraw is hacked as fuck. Removed some duplicated code though, using drawLine.
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // First of all, draw the tag Drawable if it isn't null.
        if (touchTagDrawable != null)
            touchTagDrawable.draw(canvas);

        firstVisibleMoment = -1;
        lastVisibleMoment = moments.size() - 1;

        float posX = calcPosX(moments.get(0)); // Updated to next at the end of the loop so we put it here
        // If already the first one is too far then draw an inactive line to it(padded line)
        if (posX + momentRadius > padding) {
            firstVisibleMoment = 0;
            // hackey as fuck again, but we cap the position of the line to the padded location
            drawLine(canvas, inactiveTimelineColor, padding, Math.min(posX, getWidth()-padding));
        }

        // Bruh. This is actually just a boolean, stating whether or not the last moment was outside the visible area.
        // Not just a regular boolean, either -1 or 0, now that's cutting edge Android dev right there.
        // I don't know what I was on when implementing this variable, but I am leaving it as is.
        int lastOutsideStatus = 0;

        for (int i = 0; i < moments.size(); i++) {
            // Moment is above width
            if (posX - momentRadius > getWidth()-padding) {
                // Only set lastVisibleMoment to the last visible if there was a first visible
                if (firstVisibleMoment > -1)
                    lastVisibleMoment = i - 1;

                if (lastOutsideStatus == -1) // So if last one was below 0 too, then we simply draw the last one's line to the end(padded)
                    drawMomentLine(canvas, i-1, padding, getWidth()-padding);

                break;
            }
            // Moment is below 0
            else if (posX + momentRadius < padding) {
                lastOutsideStatus = -1;
                if (i < moments.size()-1)
                    posX = calcPosX(moments.get(i + 1));
                else {
                    // Draw a full line of the last moment's color if it is indeed the LAST MOMENT(padded).
                    drawMomentLine(canvas, i, padding, getWidth()-padding);
                }
            }
            // Moment is in the visible area
            else {
                // If the last moment was below 0 draw it's line to the current one, and do other shit.
                if (lastOutsideStatus == -1) {
                    firstVisibleMoment = i;
                    lastOutsideStatus = 0;
                    drawMomentLine(canvas, i-1, padding, posX);
                }

                // If this is the last one draw a line with this one's color to the end of the visible area.
                if (i == moments.size()-1 && posX - momentRadius < getWidth()) {
                    drawMomentLine(canvas, i, Math.max(padding, posX), getWidth() - padding);
                }

                posX = drawMoment(canvas, i, posX);
            }
        }
    }

}
