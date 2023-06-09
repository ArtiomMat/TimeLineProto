package com.artiom.timelineproto;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final int TIME_START_SB_MAX = 500; // Maximum value "progress" of sb can give.

    public static final int TIME_SCALE_MIN = 5; // Minimum time scale in minutes
    public static final int TIME_SCALE_SB_MAX = 500; // Maximum value "progress" of sb can give.
    // The factor we multiply the "progress" by to get the scale.
    // Derrived from:
    // min_scale + (max_sb)*(max_sb*x)=24*60
    public static final float TIME_SCALE_SB_FACTOR = (24.0f*60 - TIME_SCALE_MIN)/(TIME_SCALE_SB_MAX*TIME_SCALE_SB_MAX*TIME_SCALE_SB_MAX);

    // timeScale is in minutes, and represents how big the time section can be on the screen.
    public static float timeScale = TIME_SCALE_MIN;
    // timeStart is in minutes and represents where we begin to see the current section on the screen.
    public static float timeStart = 0;

    // Outside because setupTimeStart and setupTimeScale use it both.
    TextView timeStartTextView;
    SeekBar timeStartSeekBar;

    Timeline tl;

    private void setTimeText(String prefix, TextView timeScaleTextView, float minutes) {
        String text;

        if (minutes <= 60)
            text = String.format(Locale.ENGLISH, "%s: %.1f %s", prefix, minutes, "minutes");
        else
            text = String.format(Locale.ENGLISH, "%s: %.1f %s", prefix, minutes/60, "hours");

        timeScaleTextView.setText(text);
    }

    private void setupTimeStart() {

        timeStartTextView = findViewById(R.id.timeStartTextView);
        setTimeText("Start",timeStartTextView,timeStart);

        // Seek bar
        timeStartSeekBar = findViewById(R.id.timeStartSeekBar);
        timeStartSeekBar.setMax(TIME_START_SB_MAX);
        timeStartSeekBar.setMin(0);

        // Add a listener to the seek bar
        timeStartSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            // Progress is 0 to 100
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timeStart = ((24*60)-timeScale) * (progress*1.0f/TIME_START_SB_MAX);
                setTimeText("Start",timeStartTextView,timeStart);

                // Make tl redraw stuff, since we changed the start, moments need to move.
                tl.invalidate();
            }
        });
    }

    private void setupTimeScale() {
        TextView timeScaleTextView;
        SeekBar timeScaleSeekBar;

        // Text stuff
        timeScaleTextView = findViewById(R.id.timeScaleTextView);
        setTimeText("Scale", timeScaleTextView,timeScale);

        // Seek bar
        timeScaleSeekBar = findViewById(R.id.timeScaleSeekBar);
        timeScaleSeekBar.animate();
        timeScaleSeekBar.setMax(TIME_SCALE_SB_MAX);
        timeScaleSeekBar.setMin(0);

        // Add a listener to the seek bar
        timeScaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // If timescale is changed it should also modify the current time start seekbar, as time start still assumes it is in the up to date scale.
                // We modify it here so we use less CPU power.

                timeStartSeekBar.setProgress((int) (TIME_START_SB_MAX * (timeStart / (24 * 60 - timeScale))), false);
            }

            @Override
            // Progress is 0 to 100
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timeScale = TIME_SCALE_SB_FACTOR * progress * progress * progress + TIME_SCALE_MIN;
                setTimeText("Scale", timeScaleTextView,timeScale);

                // Make tl redraw stuff, since we changed the scale, moments need to move.
                tl.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        LinearLayout tlLayout = findViewById(R.id.timelineLayout);
        tl = new Timeline(this, tlLayout);
        tlLayout.addView(
                tl,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
        tl.addMoment(3 * 60, 1, ContextCompat.getColor(getApplicationContext(), R.color.momentCyan));
        tl.addMoment(4 * 60 + 5, 1, ContextCompat.getColor(getApplicationContext(), R.color.momentRed));
        tl.addMoment(2 * 60 + 10, 1, ContextCompat.getColor(getApplicationContext(), R.color.momentYellow));
        tl.addMoment(15*60, 1, ContextCompat.getColor(getApplicationContext(), R.color.momentPurple));

        setupTimeScale();
        setupTimeStart();
    }

}