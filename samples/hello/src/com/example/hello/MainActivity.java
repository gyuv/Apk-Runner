package com.example.hello;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private int count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Hello from Android");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 24);
        root.setBackgroundColor(0xFFF7F2FA);

        TextView title = new TextView(this);
        title.setText("Running an Android app without a VM");
        title.setTextSize(22);
        title.setTypeface(null, 1);
        root.addView(title);

        final TextView counter = new TextView(this);
        counter.setText("Taps: 0");
        counter.setTextSize(18);
        counter.setPadding(0, 16, 0, 16);
        root.addView(counter);

        Button button = new Button(this);
        button.setText("Tap me");
        button.setId(1);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count++;
                counter.setText("Taps: " + count);
                Toast.makeText(MainActivity.this, "Clicked " + count + "x", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(button, lp);

        TextView hint = new TextView(this);
        hint.setText("Draw on the canvas below:");
        hint.setPadding(0, 24, 0, 8);
        root.addView(hint);

        root.addView(new DrawView(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    static class DrawView extends View {
        private final ArrayList<float[]> dots = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawView(Activity a) {
            super(a);
            setBackgroundColor(Color.WHITE);
            dots.add(new float[] {60, 60});
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            dots.add(new float[] {e.getX(), e.getY()});
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF79747E);
            canvas.drawRect(0, 0, getWidth() - 1, getHeight() - 1, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF6750A4);
            for (float[] d : dots) canvas.drawCircle(d[0], d[1], 14, paint);
        }
    }
}
