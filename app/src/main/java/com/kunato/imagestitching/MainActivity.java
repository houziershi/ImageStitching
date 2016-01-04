package com.kunato.imagestitching;
import android.app.ActionBar;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

/**
 * MainActivity.class
 * Created by Mihail on 10/21/15 <mihail@breadwallet.com>.
 * Copyright (c) 2015 breadwallet llc.
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class MainActivity extends FragmentActivity {

    public static Point screenParametersPoint = new Point();
    public static final String TAG = MainActivity.class.getName();
    public RelativeLayout layout;
    CameraSurfaceView view;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindowManager().getDefaultDisplay().getSize(screenParametersPoint);
        view = new CameraSurfaceView(this);
        setContentView(view);
        LinearLayout ll = new LinearLayout(this);
        Button b = new Button(this);
        SeekBar isoSeek = new SeekBar(this);
        SeekBar fSeek = new SeekBar(this);
        b.setText("Start");
        isoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                view.ESeekBarChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        fSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                view.FSeekBarChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        isoSeek.setLayoutParams(new ActionBar.LayoutParams(400,100));
        fSeek.setLayoutParams(new ActionBar.LayoutParams(400,100));

        ll.addView(fSeek);
        ll.addView(b);
        ll.addView(isoSeek);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                view.startProcess();
            }
        });
        ll.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        this.addContentView(ll,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
    }

    @Override
    protected void onPause() {
        view.Pause();
        view.onPause();

        super.onPause();

    }

    @Override
    protected void onResume() {

        super.onResume();

        view.onResume();
        //view.Resume();
    }
}