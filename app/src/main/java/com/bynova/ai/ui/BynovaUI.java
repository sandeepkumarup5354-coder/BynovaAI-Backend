package com.bynova.ai.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class BynovaUI {

    private BynovaUI() {}

    public static final int BG = Color.rgb(7, 11, 24);
    public static final int CARD = Color.rgb(18, 24, 45);
    public static final int CARD_2 = Color.rgb(25, 31, 56);
    public static final int PRIMARY = Color.rgb(108, 99, 255);
    public static final int WHITE = Color.rgb(245, 246, 255);
    public static final int MUTED = Color.rgb(165, 171, 198);

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    public static TextView text(
            Activity activity,
            String value,
            float size,
            int color,
            boolean bold
    ) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            tv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        }

        return tv;
    }

    public static TextView button(
            Activity activity,
            String value,
            View.OnClickListener listener
    ) {
        TextView tv = text(activity, value, 15, WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(18, 12, 18, 12);
        tv.setBackground(rounded(CARD_2, 18));
        tv.setOnClickListener(listener);

        return tv;
    }

    public static LinearLayout vertical(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(BG);
        return layout;
    }

    public static LinearLayout horizontal(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams match(
            int width,
            int height
    ) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams weight(
            int width,
            int height,
            float weight
    ) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }
}
