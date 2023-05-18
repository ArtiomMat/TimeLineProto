package com.artiom.timelineproto;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

public class Util {
    public static int getColorAttr(Context context, int resID) {

        int color = 0;
        TypedValue typedValue = new TypedValue();
        boolean resolved = context.getTheme().resolveAttribute(resID, typedValue, true);
        if (resolved) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                // The attribute was resolved to a color value
                color = typedValue.data;
            } else {
                // The attribute was resolved to a color reference, you need to resolve it to an actual color value
                color = ContextCompat.getColor(context, typedValue.resourceId);
            }
        } else
            Log.d("getColorAttr()", "Failed to get color.");

        return color;
    }
}
