package com.kunato.imagestitching;
import android.app.ActionBar;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

public class MainActivity extends FragmentActivity {
    boolean mFirstTime = true;
    MainController mView;
    Button b;

    private void initComponent(){
        b = new Button(this);
        LinearLayout linearLayout = new LinearLayout(this);
        SeekBar isoSeek = new SeekBar(this);
        SeekBar fSeek = new SeekBar(this);
        b.setText("AE LOCK");
        isoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mView.ESeekBarChanged(progress);
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
                mView.FSeekBarChanged(progress);
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

        linearLayout.addView(fSeek);
        linearLayout.addView(b);
        linearLayout.addView(isoSeek);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mView.runProcess(mFirstTime);
                if(mFirstTime){
                    b.setText("Capture : 0");
                    mFirstTime = false;
//                    b.setBackgroundColor(Color.RED);
                }
                else{
                    Log.d("Activity","Click");
//                    b.setText("Capture : " + mView.mNumPicture);
                    b.setBackgroundColor(Color.RED);
                }

            }
        });
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        this.addContentView(linearLayout,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

    }
    public Button getButton(){
        return b;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mView = new MainController(this);
        setContentView(mView);
        initComponent();
    }

    @Override
    protected void onPause() {
        mView.Pause();
        mView.onPause();

        super.onPause();

    }

    @Override
    protected void onResume() {

        super.onResume();
        mView.onResume();
        //mView.Resume();
    }

}