package com.kunato.imagestitching;
import android.app.ActionBar;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

public class MainActivity extends FragmentActivity {

    public static Point screenParametersPoint = new Point();
    public static final String TAG = MainActivity.class.getName();
    CameraSurfaceView view;
    private void initComponent(){
        LinearLayout ll = new LinearLayout(this);
        final Button b = new Button(this);
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
                view.runProcess();
                b.setText("Stop");
            }
        });
        ll.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        this.addContentView(ll,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindowManager().getDefaultDisplay().getSize(screenParametersPoint);
        view = new CameraSurfaceView(this);
        setContentView(view);
        initComponent();
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
        view.Resume();
    }
}