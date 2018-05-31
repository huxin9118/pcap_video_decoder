package com.example.pcapdecoder.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pcapdecoder.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
    SDL Activity
*/
public class SDLActivity extends AppCompatActivity {
    private static Toast mToast;
    private static final String TAG = "SDL";
	public static final int ZOOM_INSIDE = 0;//适应屏幕
	public static final int ZOOM_ORIGINAL = 1;//原始
	public static final int ZOOM_STRETCH = 2;//拉伸

    public static final int CODEC_TYPE_FFMPEG = 0;//ffmpeg
    public static final int CODEC_TYPE_MEDIACODEC = 1;//mediacodec

    static boolean isStreamMedia = false;
    static boolean isYUV = false;
    static int  codec_type = 0;
    static double time_base = 0;
    public static final int forward_offset_step = 2;

    public RelativeLayout root;
    private Timer updateShowUITimer;
    static Handler showUIHandler = new ShowUIHandler();

    private LinearLayout bottomBar;
    private ImageView imgPause;
    private ImageView imgBackward;
    private ImageView imgForward;
    private ImageView imgBack;
    private ImageView imgZoom;
    private RelativeLayout btnPause;
    private RelativeLayout btnBackward;
    private RelativeLayout btnForward;
    private RelativeLayout btnBack;
    private RelativeLayout btnZoom;
    private TextView btnCodecType;
    private int zoom = ZOOM_INSIDE;
    private boolean isPause = false;

    private RelativeLayout topBar;
    private TextView title;
    private static Handler progressRateHandler = new ProgressRateHandler();

    private LinearLayout progressBar;
    private TextView timeStart;
    private TextView timeEnd;
    private SeekBar progressRate;
    private ImageView btnRotate;
    private boolean isRotateWhenPause = false;
    private int frameCountWhenRotate = 0;

    private TextView textStatus;
    private Timer updateStatusTimer;

    private ImageView loading;
    private AnimationDrawable loadingAnimation;

    static String input_url = null;
    static int yuv_pixel_w = -1;
    static int yuv_pixel_h = -1;
    static int yuv_pixel_type = 0;
    static int yuv_fps = 0;
    private AlertDialog.Builder builder;
    static boolean isSetPixel = false;
    static boolean isSetPixelWandH = false;

    // Keep track of the paused state
    public static boolean mIsPaused, mIsSurfaceReady, mHasFocus;
    public static boolean mExitCalledFromJava;

    /** If shared libraries (e.g. SDL or the native application) could not be loaded. */
    public static boolean mBrokenLibraries;

    // If we want to separate mouse and touch events.
    //  This is only toggled in native code when a hint is set!
    public static boolean mSeparateMouseAndTouch;

    // Main components
    protected static SDLActivity mSingleton;
    protected static SDLSurface mSurface;
    protected static View mTextEdit;
    protected static RelativeLayout mLayout;
    protected static SDLJoystickHandler mJoystickHandler;

    // This is what SDL runs in. It invokes SDL_main(), eventually
    protected static Thread mSDLThread;

    // Audio
    protected static AudioTrack mAudioTrack;
    protected static AudioRecord mAudioRecord;

    // Load the .so
    static {
        System.loadLibrary("SDL2");
        //System.loadLibrary("SDL2_image");
        //System.loadLibrary("SDL2_mixer");
        //System.loadLibrary("SDL2_net");
        //System.loadLibrary("SDL2_ttf");
        System.loadLibrary("SDL2main");
    }


    public static void initialize() {
        // The static nature of the singleton and Android quirkyness force us to initialize everything here
        // Otherwise, when exiting the app and returning to it, these variables *keep* their pre exit values
        mSingleton = null;
        mSurface = null;
        mTextEdit = null;
        mLayout = null;
        mJoystickHandler = null;
        mSDLThread = null;
        mAudioTrack = null;
        mAudioRecord = null;
        mExitCalledFromJava = false;
        mBrokenLibraries = false;
        mIsPaused = false;
        mIsSurfaceReady = false;
        mHasFocus = true;
	    isStreamMedia = false;
	    isYUV = false;
        codec_type = 0;
	    time_base = 0;
	    input_url = null;
	    yuv_pixel_w = -1;
	    yuv_pixel_h = -1;
        yuv_pixel_type = 0;
        yuv_fps = 0;
	    isSetPixel = false;
        isSetPixelWandH = false;
    }

    // Setup
    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v("SDL", "mSingleton:" + mSingleton);
        Log.v("SDL", "mSDLThread:" + mSDLThread);
        super.onCreate(savedInstanceState);

        SDLActivity.initialize();
        // So we can call stuff from static callbacks
        mSingleton = this;

        setContentView(R.layout.activity_sdl);
        mLayout = (RelativeLayout) findViewById(R.id.root);
        // Set up the surface
        mSurface = (SDLSurface) findViewById(R.id.mSurface);

        if (Build.VERSION.SDK_INT >= 12) {
            mJoystickHandler = new SDLJoystickHandler_API12();
        } else {
            mJoystickHandler = new SDLJoystickHandler();
        }

        Intent intent = getIntent();
        input_url = intent.getStringExtra("input_url");
//        input_url ="/storage/emulated/0/testvideo/cwssm_848x480.yuv";
        Log.i(TAG, "input_url: " + input_url);
        isYUV = intent.getBooleanExtra("isYUV",false);
        isStreamMedia = intent.getBooleanExtra("isStreamMedia",false);
        codec_type = intent.getIntExtra("codec_type",0);

        root = (RelativeLayout) findViewById(R.id.root);
        bottomBar = (LinearLayout) findViewById(R.id.bottomBar);
        topBar = (RelativeLayout) findViewById(R.id.topBar);
        progressBar = (LinearLayout) findViewById(R.id.progressBar);
        btnRotate = (ImageView) findViewById(R.id.btnRotate);
        root.setVisibility(View.GONE);

        imgPause = (ImageView) findViewById(R.id.imgPause);
        imgBackward = (ImageView) findViewById(R.id.imgBackward);
        imgForward = (ImageView) findViewById(R.id.imgForward);
        imgBack = (ImageView) findViewById(R.id.imgBack);
        imgZoom = (ImageView) findViewById(R.id.imgZoom);

        btnPause = (RelativeLayout) findViewById(R.id.btnPause);
        btnBackward = (RelativeLayout) findViewById(R.id.btnBackward);
        btnForward = (RelativeLayout) findViewById(R.id.btnForward);
        btnBack = (RelativeLayout) findViewById(R.id.btnBack);
        btnZoom = (RelativeLayout) findViewById(R.id.btnZoom);

        textStatus = (TextView) findViewById(R.id.textStatus);
        textStatus.setVisibility(View.GONE);

        title = (TextView) findViewById(R.id.title);
        btnCodecType = (TextView) findViewById(R.id.btnCodecType);
        if(isStreamMedia) {
            title.setText(input_url);

            loading = (ImageView) findViewById(R.id.loading);
            loading.setImageResource(R.drawable.animation_list_loading_blue);
            loadingAnimation = (AnimationDrawable) loading.getDrawable();
            loadingAnimation.start();
        } else {
            title.setText(input_url.substring(input_url.lastIndexOf("/") + 1));
        }

        timeStart = (TextView) findViewById(R.id.timeStart);
        timeEnd = (TextView) findViewById(R.id.timeEnd);
        progressRate = (SeekBar) findViewById(R.id.progressRate);

        if(isYUV) {
            if(updatePixelData(input_url)) {
                isSetPixel = true;
                File file = new File(input_url);
                int frameCount = (int) ((file.length() * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                Log.i(TAG, "frameCount: " + frameCount);
                timeStart.setText("0");
                timeEnd.setText("" + frameCount);
                progressRate.setProgress(0);
                progressRate.setMax(frameCount);
            }
            else {
                isSetPixel = false;
                showSetPixelDialog();
            }
        }
        else{
            isSetPixel = true;
            timeStart.setText("00:00:00");
            timeEnd.setText("00:00:00");
            progressRate.setProgress(0);
            progressRate.setMax(0);
        }

        if(isStreamMedia){
            btnPause.setAlpha(0.5f);
            btnBackward.setAlpha(0.5f);
            btnForward.setAlpha(0.5f);
            btnZoom.setAlpha(0.5f);
            btnRotate.setAlpha(0.5f);
            progressRate.setAlpha(0.5f);
            btnPause.setEnabled(false);
            btnBackward.setEnabled(false);
            btnForward.setEnabled(false);
            btnZoom.setEnabled(false);
            btnRotate.setEnabled(false);
            progressRate.setEnabled(false);
        }

        progressRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    updateShowUI();
                    updateSeekStatus(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                nativeSeekSDLThread(seekBar.getProgress());
            }
        });

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPause) {
                    updateShowUITimer.cancel();
                    updateShowUITimer = null;
                    isPause = true;
                    imgPause.setImageResource(R.drawable.ic_button_play);
                    nativePauseSDLThread();
                    handlePause();
                } else {
                    updateShowUI();
                    isPause = false;
                    imgPause.setImageResource(R.drawable.ic_button_pause);
                    nativePlaySDLThread();
                    handleResume();
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(isPause) {
                    nativePlaySDLThread();
                    handleResume();//暂停时退出会卡住
                }
                if(updateShowUITimer!=null)
                updateShowUITimer.cancel();
                if(updateStatusTimer!=null)
                updateStatusTimer.cancel();
                cancelToast();
                nativeBackSDLThread();
            }
        });

        btnZoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                switch (zoom){
                    case ZOOM_INSIDE:
                        zoom = ZOOM_ORIGINAL;
                        imgZoom.setImageResource(R.drawable.ic_zoom_stretch);
                        break;
                    case ZOOM_ORIGINAL:
                        zoom = ZOOM_STRETCH;
                        imgZoom.setImageResource(R.drawable.ic_zoom_inside);
                        break;
                    case ZOOM_STRETCH:
                        zoom = ZOOM_INSIDE;
                        imgZoom.setImageResource(R.drawable.ic_zoom_original);
                        break;
                }
                updateZoomStatus(zoom);
                nativeZoomSDLThread(zoom);
            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(isYUV) {
                    nativeForwardSDLThread(25);//前进25帧
                    updateSkipStatus(25,false);
                }
                else{
                    nativeForwardSDLThread(5);//前进5秒
                    updateSkipStatus(5,false);
                }
            }
        });

        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(isYUV) {
                    nativeBackwardSDLThread(-25);//后退25帧
                    updateSkipStatus(-25,false);
                }
                else{
                    nativeBackwardSDLThread(-5);//后退5秒
                    updateSkipStatus(-5,false);
                }
            }
        });

        btnRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                else if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                else{//SCREEN_ORIENTATION_UNSPECIFIED
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                if(isPause){
                    if(isYUV) {
                        frameCountWhenRotate = Integer.parseInt(timeStart.getText().toString());
                    }
                    isRotateWhenPause = true;
                    btnPause.performClick();
                }
                btnRotate.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        nativeZoomSDLThread(zoom);
                    }
                }, 100);
                if(isRotateWhenPause){
                    btnRotate.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(isYUV) {
                                int frameCountAfterRotate = Integer.parseInt(timeStart.getText().toString());
                                if (frameCountWhenRotate != frameCountAfterRotate) {
                                    nativeSeekSDLThread(frameCountWhenRotate);
                                }
                            }
                            btnPause.performClick();
                        }
                    }, 250);
                    isRotateWhenPause = false;
                }
            }
        });

        btnCodecType.setText(codec_type == 0 ? "软解" : "硬解");
        btnCodecType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isPause) {
                    nativePauseSDLThread();
                }
                handlePause();
                showCodecTypeDialog();
            }
        });
        if(isYUV){
            btnCodecType.setVisibility(View.GONE);
        }

//        loading = (ImageView) findViewById(R.id.loading);
//        loading.setImageResource(R.drawable.animation_list_loading_blue);
//        AnimationDrawable animationDrawable = (AnimationDrawable) loading.getDrawable();
//        animationDrawable.start();
    }

    private void showSetPixelDialog(){
        builder = new AlertDialog.Builder(this);
        builder.setTitle("YUV格式设置");
        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogView = layoutInflater.inflate(R.layout.dialog_set_pixel,null);
        final EditText textWdith = (EditText) dialogView.findViewById(R.id.textWdith);
        final EditText textHeight = (EditText) dialogView.findViewById(R.id.textHeight);
        final EditText textFps = (EditText) dialogView.findViewById(R.id.textFps);
        final CheckBox checkBox = (CheckBox) dialogView.findViewById(R.id.rename);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.spinner);

        if(yuv_pixel_w != -1){
            textWdith.setText(yuv_pixel_w +"");
        }
        if(yuv_pixel_h != -1){
            textHeight.setText(yuv_pixel_h +"");
        }
        if(yuv_fps != 0){
            textFps.setText(yuv_fps +"");
        }

        final ArrayList<String> items = new ArrayList<String>();
        items.add("I420");
        items.add("YV12");
        items.add("YUY2");
        items.add("UYVY");
        items.add("YVYU");
        items.add("NV12");
        items.add("NV21");
        items.add("请选择YUV像素类型"); // Last item

        SpinnerAdapter adapter = new SpinnerAdapter(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(items.size() - 1);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                yuv_pixel_type = position % (items.size()-1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                yuv_pixel_type = 0;
            }
        });


        builder.setView(dialogView);
        builder.setPositiveButton("确定", null);
        builder.setNegativeButton("使用缺省值(640x360)", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                yuv_pixel_w = 640;
                yuv_pixel_h = 480;
                yuv_pixel_type = 0;
                yuv_fps = 25;
                isSetPixel = true;
                File file = new File(input_url);
                int frameCount = (int) ((file.length() * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                Log.i(TAG, "frameCount: " + frameCount);
                timeStart.setText("0");
                timeEnd.setText("" + frameCount);
                progressRate.setProgress(0);
                progressRate.setMax(frameCount);

                mSurface.startSDLThread();
                dialog.dismiss();
            }
        });
        builder.setNeutralButton("返回",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelToast();
                        handleNativeExit();
                    }
        });
        builder.setCancelable(false);
        final AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondaryColor));
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(input_url);
                try {
                    long file_length = file.length();
                    yuv_pixel_w = Integer.parseInt(textWdith.getText().toString().trim());
                    yuv_pixel_h = Integer.parseInt(textHeight.getText().toString().trim());
                    yuv_fps = Integer.parseInt(textFps.getText().toString().trim());
                    if(checkBox.isChecked()){
                        String old_url = input_url;
                        if(isSetPixelWandH){
                            int _index = input_url.lastIndexOf("_");
                            input_url = input_url.substring(0, _index) + "&" + yuv_pixel_type + "@" + yuv_fps + "_" + yuv_pixel_w + "x" + yuv_pixel_h + ".yuv";
                        }
                        else {
                            input_url = input_url.substring(0, input_url.length() - 4) + "&" + yuv_pixel_type + "@" + yuv_fps + "_" + yuv_pixel_w + "x" + yuv_pixel_h + ".yuv";
                        }
                        if(file.renameTo(new File(input_url))) {
                            showToast("YUV重命名成功!",Toast.LENGTH_SHORT);
                        }
                        else{
                            showToast("YUV重命名失败!",Toast.LENGTH_SHORT);
                        }
                    }
                    isSetPixel = true;
                    int frameCount = (int) ((file_length * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                    Log.i(TAG, "frameCount: " + frameCount);
                    timeStart.setText("0");
                    timeEnd.setText("" + frameCount);
                    progressRate.setProgress(0);
                    progressRate.setMax(frameCount);

                    mSurface.startSDLThread();
                    dialog.dismiss();
                }
                catch (Exception e){
                    showToast("请输入正确的YUV解码参数~~",Toast.LENGTH_SHORT);
                }
            }
        });
    }

    private void showCodecTypeDialog(){
        final SharedPreferences sp = getSharedPreferences("user",Context.MODE_PRIVATE);
        Log.i(TAG, "showCodecTypeDialog: codec_type = "+codec_type);

        builder = new AlertDialog.Builder(this);
        builder.setTitle("解码器");
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if(!isPause) {
                    nativePlaySDLThread();
                }
                handleResume();
            }
        });

        final String[] codec_type_items = new String[]{"软件解码器","硬件解码器"};

        builder.setSingleChoiceItems(codec_type_items, codec_type, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                nativeCodecType(which);
                if(codec_type != which) {
                    codec_type = which;
                    updateShowUI();
                    nativeBackwardSDLThread(-1);//后退5秒
                    updateSkipStatus(-1,true);
                }
                if(!isPause) {
                    nativePlaySDLThread();
                }
                handleResume();
                btnCodecType.setText(codec_type == 0 ? "软解" : "硬解");
                dialog.dismiss();
            }
        });
        builder.show();
    }

    class SpinnerAdapter extends ArrayAdapter<String> {
        public SpinnerAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public int getCount() {
            return super.getCount() - 1; // This makes the trick: do not show last item
        }

        @Override
        public String getItem(int position) {
            return super.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return super.getItemId(position);
        }

    }

    void updateZoomStatus(int zoom){
        String textZoomStatus = "";
        switch (zoom){
            case ZOOM_INSIDE:
                textZoomStatus = "适应屏幕";
                break;
            case ZOOM_ORIGINAL:
                textZoomStatus = "原始";
                break;
            case ZOOM_STRETCH:
                textZoomStatus = "拉伸";
                break;
        }
        textStatus.setText(textZoomStatus);
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1500);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1500);
        }
    }

    void updateSkipStatus(int skipFrame, boolean isChangeCodec){
        String textSkipStatus;
        if(isYUV) {
            if (skipFrame > 0) {
                textSkipStatus = "前进 " + skipFrame + " 帧";
            } else {
                textSkipStatus = "后退 " + -1 * skipFrame + " 帧";
            }
        }
        else {
            if (skipFrame > 0) {
                textSkipStatus = "前进 " + skipFrame + " s";
            } else {
                textSkipStatus = "后退 " + -1 * skipFrame + " s";
            }
        }
        if(isChangeCodec){
            textStatus.setText("更换解码器，重新定位");
        }
        else {
            textStatus.setText(textSkipStatus);
        }
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
    }

    void updateSeekStatus(int seekFrame){
        String textSeekFrame;
        if(isYUV) {
            textSeekFrame = "跳转到 第 " + seekFrame + " 帧";
        }
        else{
            int time = (int)((double)seekFrame * time_base);
            int hour = time/3600;
            int minute = time/60 % 60;
            int second = time % 60;
            textSeekFrame = "跳转到 " + String.format("[%02d:%02d:%02d]",hour,minute,second);
        }
        textStatus.setText(textSeekFrame);
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
    }

    void updateShowUI(){
        root.setVisibility(View.VISIBLE);
        if(updateShowUITimer == null){
            updateShowUITimer = new Timer();
            updateShowUITimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(1);
                }
            },4000);
        }
        else{
            updateShowUITimer.cancel();
            updateShowUITimer = null;
            updateShowUITimer = new Timer();
            updateShowUITimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(1);
                }
            },4000);
        }
    }


    boolean updatePixelData(String input_url){
        int typeIndex = input_url.lastIndexOf("&");
        int fpsIndex = input_url.lastIndexOf("@");
        int _Index = input_url.lastIndexOf("_");
        int xIndex = input_url.lastIndexOf("x");
        int pointIndex = input_url.lastIndexOf(".");
        if (_Index < xIndex && xIndex < pointIndex) {
            try {
                yuv_pixel_w = Integer.parseInt(input_url.substring(_Index + 1, xIndex));
                yuv_pixel_h = Integer.parseInt(input_url.substring(xIndex + 1, pointIndex));
                isSetPixelWandH = true;
                if(typeIndex == -1 && fpsIndex == -1){
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_w);
                    return false;
                }
                if(typeIndex == -1) {
                    yuv_fps = Integer.parseInt(input_url.substring(fpsIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_w+" fps:"+yuv_fps);
                    return false;
                }
                if(fpsIndex == -1){
                    yuv_pixel_type = Integer.parseInt(input_url.substring(typeIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_w+" type:"+yuv_pixel_type);
                    return false;
                }
                if(typeIndex < fpsIndex && fpsIndex < _Index){
                    yuv_pixel_type = Integer.parseInt(input_url.substring(typeIndex + 1, fpsIndex));
                    yuv_fps = Integer.parseInt(input_url.substring(fpsIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_w+" type:"+yuv_pixel_type+" fps:"+yuv_fps);
                    return true;
                }
            } catch (Exception e) {
                Log.i(TAG, "输入YUV格式信息解析错误");
                return false;
            }
        }
        Log.i(TAG, "输入YUV格式信息解析错误");
        return false;
    }


    public static void setProgressRate(int frameConut){
//        Log.i(TAG, "--------正在播放第"+frameConut+"帧-------");
        progressRateHandler.sendEmptyMessage(frameConut);
    }

    public static void setProgressRateFull(){
        Log.i(TAG, "##########播放完成##########");
        progressRateHandler.sendEmptyMessage(-1);
    }

    public static void setProgressDuration(long duration){
//        Log.i(TAG, "###########"+duration);
        Message msg = Message.obtain();
        msg.what = -2;
        Bundle bundle = new Bundle();
        bundle.putLong("duration",duration);
        msg.setData(bundle);
        progressRateHandler.sendMessage(msg);
    }

    public static void setProgressDTS(long dts){
        Message msg = Message.obtain();
        msg.what = -3;
        Bundle bundle = new Bundle();
        bundle.putLong("dts",dts);
        msg.setData(bundle);
        progressRateHandler.sendMessage(msg);
    }

    public static void showIFrameDTS(long I_Frame_dts,int forwardOffset){
        if(forwardOffset > 0){//前进偏移
            nativeForwardSDLThread(5+forward_offset_step * forwardOffset);//前进5秒
        }
        else if(forwardOffset == -1){//前进偏移终止（文件结尾）
            Message msg = Message.obtain();
            msg.what = -5;
            Bundle bundle = new Bundle();
            bundle.putLong("I_Frame_dts",I_Frame_dts);
            msg.setData(bundle);
            progressRateHandler.sendMessage(msg);
        }
        else{
            Message msg = Message.obtain();
            msg.what = -4;
            Bundle bundle = new Bundle();
            bundle.putLong("I_Frame_dts",I_Frame_dts);
            msg.setData(bundle);
            progressRateHandler.sendMessage(msg);
        }
    }

    public static void initOrientation(){
        Log.i(TAG, "##########initOrientation##########");
        showUIHandler.sendEmptyMessage(2);
    }

    public static void hideLoading(){
        if(isStreamMedia) {
            Log.i(TAG, "##########hideLoading##########");
            showUIHandler.sendEmptyMessage(3);
        }
    }

    public static void changeCodec(String codec_name){
        Log.i(TAG, "##########changeCodec##########");
        Message msg = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putString("codec_name",codec_name);
        msg.what = 4;
        msg.setData(bundle);
        showUIHandler.sendMessage(msg);
    }

    static class ProgressRateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (SDLActivity.mSingleton != null) {
                if(msg.what == -5){//封装格式视频dts定位终止（文件结尾）专用消息
                    long I_Frame_dts = msg.getData().getLong("I_Frame_dts");
                    int frameCount = msg.getData().getInt("frameCount");
                    int time = (int) ((double) I_Frame_dts * time_base);
                    int hour = time / 3600;
                    int minute = time / 60 % 60;
                    int second = time % 60;

                    mSingleton.progressRate.setProgress((int) I_Frame_dts);
                    mSingleton.timeStart.setText(String.format("%02d:%02d:%02d", hour, minute, second));
//                    mSingleton.showToast("临近文件末尾，视频前进无效~~",Toast.LENGTH_SHORT);
                }
                else if(msg.what == -4){//封装格式视频dts定位专用消息
                    long I_Frame_dts = msg.getData().getLong("I_Frame_dts");
                    int frameCount = msg.getData().getInt("frameCount");
                    int time = (int) ((double) I_Frame_dts * time_base);
                    int hour = time / 3600;
                    int minute = time / 60 % 60;
                    int second = time % 60;

                    mSingleton.progressRate.setProgress((int) I_Frame_dts);
                    mSingleton.timeStart.setText(String.format("%02d:%02d:%02d", hour, minute, second));
                }
                else if(msg.what == -3){//封装格式视频进度更新专用消息
                    long dts = msg.getData().getLong("dts");
//                    Log.i(TAG, "=================="+dts);
                    int time = (int)((double)dts * time_base);
                    int hour = time/3600;
                    int minute = time/60 % 60;
                    int second = time % 60;

                    if(dts>mSingleton.progressRate.getProgress()) {//消除dts乱序影响
                        mSingleton.progressRate.setProgress((int)dts);
                        mSingleton.timeStart.setText(String.format("%02d:%02d:%02d",hour,minute,second));
                    }
                }
                else if(msg.what == -2){//封装格式视频总时长设置专用消息
                    long duration = 0;
                    if(!isStreamMedia){
                        duration = msg.getData().getLong("duration");
                    }
                    if(duration == 0){
                        mSingleton.btnBackward.setEnabled(false);
                        mSingleton.btnForward.setEnabled(false);
                        mSingleton.progressRate.setOnSeekBarChangeListener(null);
                        mSingleton.btnBackward.setAlpha(0.5f);
                        mSingleton.btnForward.setAlpha(0.5f);
                    }
                    int maxTime = (int)((double)duration * time_base);
                    int hour = maxTime/3600;
                    int minute = maxTime/60 % 60;
                    int second = maxTime % 60;

                    mSingleton.timeStart.setText("00:00:00");
                    mSingleton.timeEnd.setText(String.format("%02d:%02d:%02d",hour,minute,second));
                    mSingleton.progressRate.setProgress(0);
                    mSingleton.progressRate.setMax((int)duration);
                }
                else if(msg.what == -1) {//播放完成消息
                    mSingleton.showToast("播放完成~~",Toast.LENGTH_LONG);
                }
                else {//YUV进度更新专用消息
                    mSingleton.timeStart.setText(msg.what + "");
                    mSingleton.progressRate.setProgress(msg.what);
                }
            }
        }
    }

    static class ShowUIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (SDLActivity.mSingleton != null) {
                if(msg.what == 0){//statusTimer
                    mSingleton.textStatus.setVisibility(View.GONE);
                }else if(msg.what == 1) {//UITimer
                    mSingleton.root.setVisibility(View.GONE);
                }else if(msg.what == 2) {//initOrientation
                    mSingleton.btnRotate.performClick();
                }else if(msg.what == 3) {//hideLoading
                    mSingleton.loadingAnimation.stop();
                    mSingleton.loading.setVisibility(View.GONE);
                    if(isStreamMedia){
                        mSingleton.btnPause.setAlpha(1f);
                        mSingleton.btnZoom.setAlpha(1f);
                        mSingleton.btnRotate.setAlpha(1f);
                        mSingleton.progressRate.setAlpha(1f);
                        mSingleton.btnPause.setEnabled(true);
                        mSingleton.btnZoom.setEnabled(true);
                        mSingleton.btnRotate.setEnabled(true);
                        mSingleton.progressRate.setEnabled(true);
                    }
                }else if(msg.what == 4) {//changeCodec
                    mSingleton.showToast("该视频的编码格式为 “"+ msg.getData().get("codec_name") + "”，硬件解码器暂不支持，尝试切换至软件解码器。", Toast.LENGTH_LONG);
                    mSingleton.codec_type = 0;
                    mSingleton.btnCodecType.setText("软解");
                }else{//error toast
                    switch (msg.what) {
                        case -1:
                            mSingleton.showToast( "SDL初始化失败！", Toast.LENGTH_SHORT);
                            break;
                        case -2:
                            mSingleton.showToast( "SDL窗口创建失败！", Toast.LENGTH_SHORT);
                            break;
                        case -3:
                            mSingleton.showToast( "SDL渲染器创建失败！", Toast.LENGTH_SHORT);
                            break;
                        case -4:
                            mSingleton.showToast( "无法打开视频文件，请检查是否拥有读写权限！", Toast.LENGTH_SHORT);
                            break;
                        case -5:
                            mSingleton.showToast( "无法打开视频文件，请检查是否拥有读写权限！", Toast.LENGTH_SHORT);
                            break;
                        case -6:
                            mSingleton.showToast( "无法从该文件获取流信息，请确认是否打开视频文件！", Toast.LENGTH_SHORT);
                            break;
                        case -7:
                            mSingleton.showToast( "无法从该文件获取视频流信息，该封装格式无视频流！", Toast.LENGTH_SHORT);
                            break;
                        case -8:
                            mSingleton.showToast( "无法获取正确的解码器，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                        case -9:
                            mSingleton.showToast( "无法打开解码器，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                        case -10:
                            mSingleton.showToast( "视频帧解码失败，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                    }
                }
            }
        }
    }

    /**
     * 显示Toast，解决重复弹出问题
     */
    public void showToast(String text , int time) {
        if(mToast == null) {
            mToast = Toast.makeText(mSingleton, "", time);
            mToast.setText(text);
        } else {
            mToast.setText(text);
            mToast.setDuration(time);
        }
        mToast.show();
    }

    /**
     * 隐藏Toast
     */
    public void cancelToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    // Events
    @Override
    protected void onPause() {
        Log.v("SDL", "onPause()");
        super.onPause();
        if(!isPause) {
            nativePauseSDLThread();
        }
        handlePause();
    }

    @Override
    protected void onResume() {
        Log.v("SDL", "onResume()");
        super.onResume();
        if(!isPause) {
            nativePlaySDLThread();
        }
        handleResume();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.v("SDL", "onWindowFocusChanged(): " + hasFocus);

        SDLActivity.mHasFocus = hasFocus;
        if (hasFocus) {
            SDLActivity.handleResume();
        }
    }

    @Override
    public void onLowMemory() {
        Log.v("SDL", "onLowMemory()");
        super.onLowMemory();
        SDLActivity.nativeLowMemory();
    }

    @Override
    protected void onDestroy() {
        Log.v("SDL", "onDestroy()");
        // Send a quit message to the application
        SDLActivity.mExitCalledFromJava = true;
        SDLActivity.nativeQuit();

        // Now wait for the SDL thread to quit
        if (SDLActivity.mSDLThread != null) {
            try {
                SDLActivity.mSDLThread.join();
            } catch(Exception e) {
                Log.v("SDL", "Problem stopping thread: " + e);
            }
            SDLActivity.mSDLThread = null;

            //Log.v("SDL", "Finished waiting for SDL thread");
        }

        if(loadingAnimation != null){
            if(loadingAnimation.isRunning()){
                loadingAnimation.stop();
                loading.setVisibility(View.GONE);
            }
        }

        if(updateShowUITimer != null){
            updateShowUITimer.cancel();
            updateShowUITimer = null;
        }
        if(updateStatusTimer != null){
            updateStatusTimer.cancel();
            updateStatusTimer = null;
        }
            
        super.onDestroy();
        // Reset everything in case the user re opens the app
        SDLActivity.initialize();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // Ignore certain special keys so they're handled by Android
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_CAMERA ||
            keyCode == 168 || /* API 11: KeyEvent.KEYCODE_ZOOM_IN */
            keyCode == 169 /* API 11: KeyEvent.KEYCODE_ZOOM_OUT */
            ) {
            return false;
        }
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP){
            Log.i(TAG, "点击back键");
            btnBack.performClick();
        }
        return super.dispatchKeyEvent(event);
    }

    /** Called by onPause or surfaceDestroyed. Even if surfaceDestroyed
     *  is the first to be called, mIsSurfaceReady should still be set
     *  to 'true' during the call to onPause (in a usual scenario).
     */
    public static void handlePause() {
        if (!SDLActivity.mIsPaused && SDLActivity.mIsSurfaceReady) {
            SDLActivity.mIsPaused = true;
            SDLActivity.nativePause();
            mSurface.enableSensor(Sensor.TYPE_ACCELEROMETER, false);
        }
    }

    /** Called by onResume or surfaceCreated. An actual resume should be done only when the surface is ready.
     * Note: Some Android variants may send multiple surfaceChanged events, so we don't need to resume
     * every time we get one of those events, only if it comes after surfaceDestroyed
     */
    public static void handleResume() {
        if (SDLActivity.mIsPaused && SDLActivity.mIsSurfaceReady && SDLActivity.mHasFocus) {
            SDLActivity.mIsPaused = false;
            SDLActivity.nativeResume();
            mSurface.enableSensor(Sensor.TYPE_ACCELEROMETER, true);
        }
    }
        
    /* The native thread has finished */
    public static void handleNativeExit() {
        SDLActivity.mSDLThread = null;
        if(mSingleton != null)mSingleton.finish();
    }


    // Messages from the SDLMain thread
    static final int COMMAND_CHANGE_TITLE = 1;
    static final int COMMAND_UNUSED = 2;
    static final int COMMAND_TEXTEDIT_HIDE = 3;
    static final int COMMAND_SET_KEEP_SCREEN_ON = 5;

    public static final int COMMAND_USER = 0x8000;


    /**
     * This method is called by SDL if SDL did not handle a message itself.
     * This happens if a received message contains an unsupported command.
     * Method can be overwritten to handle Messages in a different class.
     * @param command the command of the message.
     * @param param the parameter of the message. May be null.
     * @return if the message was handled in overridden method.
     */
    protected boolean onUnhandledMessage(int command, Object param) {
        return false;
    }

    /**
     * A Handler class for Messages from native SDL applications.
     * It uses current Activities as target (e.g. for the title).
     * static to prevent implicit references to enclosing object.
     */
    protected static class SDLCommandHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Context context = getContext();
            if (context == null) {
                Log.e(TAG, "error handling message, getContext() returned null");
                return;
            }
            switch (msg.arg1) {
            case COMMAND_CHANGE_TITLE:
                if (context instanceof Activity) {
                    ((Activity) context).setTitle((String)msg.obj);
                } else {
                    Log.e(TAG, "error handling message, getContext() returned no Activity");
                }
                break;
            case COMMAND_TEXTEDIT_HIDE:
                if (mTextEdit != null) {
                    mTextEdit.setVisibility(View.GONE);

                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);
                }
                break;

            default:
                if ((context instanceof SDLActivity) && !((SDLActivity) context).onUnhandledMessage(msg.arg1, msg.obj)) {
                    Log.e(TAG, "error handling message, command is " + msg.arg1);
                }
            }
        }
    }

    // Handler for the messages
    Handler commandHandler = new SDLCommandHandler();

    // Send a message from the SDLMain thread
    boolean sendCommand(int command, Object data) {
        Message msg = commandHandler.obtainMessage();
        msg.arg1 = command;
        msg.obj = data;
        return commandHandler.sendMessage(msg);
    }

    //CUSTOM JNI
    public static native void nativeCodecType(int codec_type);
    public static native void nativePauseSDLThread();
    public static native void nativePlaySDLThread();
    public static native void nativeBackSDLThread();
    public static native void nativeZoomSDLThread(int zoom);
    public static native void nativeBackwardSDLThread(long skipFrame);
    public static native void nativeForwardSDLThread(long skipFrame);
    public static native void nativeSeekSDLThread(long seekFrame);
    public static native void nativeUpdateSdlRect(int wdith,int height);
    // C functions we call
    public static native int nativeInitYUV(String inputurl,int pixel_w,int pixel_h,int pixel_type, int fps);
    public static native int nativeInit(String inputurl);
    public static native void nativeLowMemory();
    public static native void nativeQuit();
    public static native void nativePause();
    public static native void nativeResume();
    public static native void onNativeResize(int x, int y, int format, float rate);
    public static native int onNativePadDown(int device_id, int keycode);
    public static native int onNativePadUp(int device_id, int keycode);
    public static native void onNativeJoy(int device_id, int axis,
                                          float value);
    public static native void onNativeHat(int device_id, int hat_id,
                                          int x, int y);
    public static native void onNativeKeyDown(int keycode);
    public static native void onNativeKeyUp(int keycode);
    public static native void onNativeKeyboardFocusLost();
    public static native void onNativeTouch(int touchDevId, int pointerFingerId,
                                            int action, float x, 
                                            float y, float p);
    public static native void onNativeAccel(float x, float y, float z);
    public static native void onNativeSurfaceChanged();
    public static native void onNativeSurfaceDestroyed();
    public static native void nativeFlipBuffers();
    public static native int nativeAddJoystick(int device_id, String name, 
                                               int is_accelerometer, int nbuttons, 
                                               int naxes, int nhats, int nballs);
    public static native int nativeRemoveJoystick(int device_id);
    public static native String nativeGetHint(String name);

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean setActivityTitle(String title) {
        // Called from SDLMain() thread and can't directly affect the view
        return mSingleton.sendCommand(COMMAND_CHANGE_TITLE, title);
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean sendMessage(int command, int param) {
        return mSingleton.sendCommand(command, Integer.valueOf(param));
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static Context getContext() {
        return mSingleton;
    }

    /**
     * This method is called by SDL using JNI.
     * @return result of getSystemService(name) but executed on UI thread.
     */
    public Object getSystemServiceFromUiThread(final String name) {
        final Object lock = new Object();
        final Object[] results = new Object[2]; // array for writable variables
        synchronized (lock) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        results[0] = getSystemService(name);
                        results[1] = Boolean.TRUE;
                        lock.notify();
                    }
                }
            });
            if (results[1] == null) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return results[0];
    }

    static class ShowTextInputTask implements Runnable {
        /*
         * This is used to regulate the pan&scan method to have some offset from
         * the bottom edge of the input region and the top edge of an input
         * method (soft keyboard)
         */
        static final int HEIGHT_PADDING = 15;

        public int x, y, w, h;

        public ShowTextInputTask(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public void run() {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(w, h + HEIGHT_PADDING);
            params.leftMargin = x;
            params.topMargin = y;

            if (mTextEdit == null) {
                mTextEdit = new DummyEdit(getContext());

                mLayout.addView(mTextEdit, params);
            } else {
                mTextEdit.setLayoutParams(params);
            }

            mTextEdit.setVisibility(View.VISIBLE);
            mTextEdit.requestFocus();

            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mTextEdit, 0);
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static boolean showTextInput(int x, int y, int w, int h) {
        // Transfer the task to the main thread as a Runnable
        return mSingleton.commandHandler.post(new ShowTextInputTask(x, y, w, h));
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static Surface getNativeSurface() {
        return SDLActivity.mSurface.getNativeSurface();
    }

    // Audio

    /**
     * This method is called by SDL using JNI.
     */
    public static int audioOpen(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        int channelConfig = isStereo ? AudioFormat.CHANNEL_CONFIGURATION_STEREO : AudioFormat.CHANNEL_CONFIGURATION_MONO;
        int audioFormat = is16Bit ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        int frameSize = (isStereo ? 2 : 1) * (is16Bit ? 2 : 1);

        Log.v(TAG, "SDL audio: wanted " + (isStereo ? "stereo" : "mono") + " " + (is16Bit ? "16-bit" : "8-bit") + " " + (sampleRate / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        // Let the user pick a larger buffer if they really want -- but ye
        // gods they probably shouldn't, the minimums are horrifyingly high
        // latency already
        desiredFrames = Math.max(desiredFrames, (AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize);

        if (mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    channelConfig, audioFormat, desiredFrames * frameSize, AudioTrack.MODE_STREAM);

            // Instantiating AudioTrack can "succeed" without an exception and the track may still be invalid
            // Ref: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/media/java/android/media/AudioTrack.java
            // Ref: http://developer.android.com/reference/android/media/AudioTrack.html#getState()

            if (mAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed during initialization of Audio Track");
                mAudioTrack = null;
                return -1;
            }

            mAudioTrack.play();
        }

        Log.v(TAG, "SDL audio: got " + ((mAudioTrack.getChannelCount() >= 2) ? "stereo" : "mono") + " " + ((mAudioTrack.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) ? "16-bit" : "8-bit") + " " + (mAudioTrack.getSampleRate() / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        return 0;
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void audioWriteShortBuffer(short[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                Log.w(TAG, "SDL audio: error return from write(short)");
                return;
            }
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void audioWriteByteBuffer(byte[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                Log.w(TAG, "SDL audio: error return from write(byte)");
                return;
            }
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static int captureOpen(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        int channelConfig = isStereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = is16Bit ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        int frameSize = (isStereo ? 2 : 1) * (is16Bit ? 2 : 1);

        Log.v(TAG, "SDL capture: wanted " + (isStereo ? "stereo" : "mono") + " " + (is16Bit ? "16-bit" : "8-bit") + " " + (sampleRate / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        // Let the user pick a larger buffer if they really want -- but ye
        // gods they probably shouldn't, the minimums are horrifyingly high
        // latency already
        desiredFrames = Math.max(desiredFrames, (AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize);

        if (mAudioRecord == null) {
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate,
                    channelConfig, audioFormat, desiredFrames * frameSize);

            // see notes about AudioTrack state in audioOpen(), above. Probably also applies here.
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed during initialization of AudioRecord");
                mAudioRecord.release();
                mAudioRecord = null;
                return -1;
            }

            mAudioRecord.startRecording();
        }

        Log.v(TAG, "SDL capture: got " + ((mAudioRecord.getChannelCount() >= 2) ? "stereo" : "mono") + " " + ((mAudioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) ? "16-bit" : "8-bit") + " " + (mAudioRecord.getSampleRate() / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        return 0;
    }

    /** This method is called by SDL using JNI. */
    public static int captureReadShortBuffer(short[] buffer, boolean blocking) {
        // !!! FIXME: this is available in API Level 23. Until then, we always block.  :(
        //return mAudioRecord.read(buffer, 0, buffer.length, blocking ? AudioRecord.READ_BLOCKING : AudioRecord.READ_NON_BLOCKING);
        return mAudioRecord.read(buffer, 0, buffer.length);
    }

    /** This method is called by SDL using JNI. */
    public static int captureReadByteBuffer(byte[] buffer, boolean blocking) {
        // !!! FIXME: this is available in API Level 23. Until then, we always block.  :(
        //return mAudioRecord.read(buffer, 0, buffer.length, blocking ? AudioRecord.READ_BLOCKING : AudioRecord.READ_NON_BLOCKING);
        return mAudioRecord.read(buffer, 0, buffer.length);
    }


    /** This method is called by SDL using JNI. */
    public static void audioClose() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    /** This method is called by SDL using JNI. */
    public static void captureClose() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    // Input

    /**
     * This method is called by SDL using JNI.
     * @return an array which may be empty but is never null.
     */
    public static int[] inputGetInputDeviceIds(int sources) {
        int[] ids = InputDevice.getDeviceIds();
        int[] filtered = new int[ids.length];
        int used = 0;
        for (int i = 0; i < ids.length; ++i) {
            InputDevice device = InputDevice.getDevice(ids[i]);
            if ((device != null) && ((device.getSources() & sources) != 0)) {
                filtered[used++] = device.getId();
            }
        }
        return Arrays.copyOf(filtered, used);
    }

    // Joystick glue code, just a series of stubs that redirect to the SDLJoystickHandler instance
    public static boolean handleJoystickMotionEvent(MotionEvent event) {
        return mJoystickHandler.handleMotionEvent(event);
    }

    /**
     * This method is called by SDL using JNI.
     */
    public static void pollInputDevices() {
        if (SDLActivity.mSDLThread != null) {
            mJoystickHandler.pollInputDevices();
        }
    }

    // Check if a given device is considered a possible SDL joystick
    public static boolean isDeviceSDLJoystick(int deviceId) {
        InputDevice device = InputDevice.getDevice(deviceId);
        // We cannot use InputDevice.isVirtual before API 16, so let's accept
        // only nonnegative device ids (VIRTUAL_KEYBOARD equals -1)
        if ((device == null) || (deviceId < 0)) {
            return false;
        }
        int sources = device.getSources();
        return (((sources & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK) ||
                ((sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) ||
                ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
        );
    }

    // APK expansion files support

    /** com.android.vending.expansion.zipfile.ZipResourceFile object or null. */
    private Object expansionFile;

    /** com.android.vending.expansion.zipfile.ZipResourceFile's getInputStream() or null. */
    private Method expansionFileMethod;

    /**
     * This method is called by SDL using JNI.
     * @return an InputStream on success or null if no expansion file was used.
     * @throws IOException on errors. Message is set for the SDL error message.
     */
    public InputStream openAPKExpansionInputStream(String fileName) throws IOException {
        // Get a ZipResourceFile representing a merger of both the main and patch files
        if (expansionFile == null) {
            String mainHint = nativeGetHint("SDL_ANDROID_APK_EXPANSION_MAIN_FILE_VERSION");
            if (mainHint == null) {
                return null; // no expansion use if no main version was set
            }
            String patchHint = nativeGetHint("SDL_ANDROID_APK_EXPANSION_PATCH_FILE_VERSION");
            if (patchHint == null) {
                return null; // no expansion use if no patch version was set
            }

            Integer mainVersion;
            Integer patchVersion;
            try {
                mainVersion = Integer.valueOf(mainHint);
                patchVersion = Integer.valueOf(patchHint);
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
                throw new IOException("No valid file versions set for APK expansion files", ex);
            }

            try {
                // To avoid direct dependency on Google APK expansion library that is
                // not a part of Android SDK we access it using reflection
                expansionFile = Class.forName("com.android.vending.expansion.zipfile.APKExpansionSupport")
                        .getMethod("getAPKExpansionZipFile", Context.class, int.class, int.class)
                        .invoke(null, this, mainVersion, patchVersion);

                expansionFileMethod = expansionFile.getClass()
                        .getMethod("getInputStream", String.class);
            } catch (Exception ex) {
                ex.printStackTrace();
                expansionFile = null;
                expansionFileMethod = null;
                throw new IOException("Could not access APK expansion support library", ex);
            }
        }

        // Get an input stream for a known file inside the expansion file ZIPs
        InputStream fileStream;
        try {
            fileStream = (InputStream)expansionFileMethod.invoke(expansionFile, fileName);
        } catch (Exception ex) {
            // calling "getInputStream" failed
            ex.printStackTrace();
            throw new IOException("Could not open stream from APK expansion file", ex);
        }

        if (fileStream == null) {
            // calling "getInputStream" was successful but null was returned
            throw new IOException("Could not find path in APK expansion file");
        }

        return fileStream;
    }

    // Messagebox

    /** Result of current messagebox. Also used for blocking the calling thread. */
    public final int[] messageboxSelection = new int[1];

    /** Id of current dialog. */
    public int dialogs = 0;

    /**
     * This method is called by SDL using JNI.
     * Shows the messagebox from UI thread and block calling thread.
     * buttonFlags, buttonIds and buttonTexts must have same length.
     * @param buttonFlags array containing flags for every button.
     * @param buttonIds array containing id for every button.
     * @param buttonTexts array containing text for every button.
     * @param colors null for default or array of length 5 containing colors.
     * @return button id or -1.
     */
    public int messageboxShowMessageBox(
            final int flags,
            final String title,
            final String message,
            final int[] buttonFlags,
            final int[] buttonIds,
            final String[] buttonTexts,
            final int[] colors) {

        messageboxSelection[0] = -1;

        // sanity checks

        if ((buttonFlags.length != buttonIds.length) && (buttonIds.length != buttonTexts.length)) {
            return -1; // implementation broken
        }

        // collect arguments for Dialog

        final Bundle args = new Bundle();
        args.putInt("flags", flags);
        args.putString("title", title);
        args.putString("message", message);
        args.putIntArray("buttonFlags", buttonFlags);
        args.putIntArray("buttonIds", buttonIds);
        args.putStringArray("buttonTexts", buttonTexts);
        args.putIntArray("colors", colors);

        // trigger Dialog creation on UI thread

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showDialog(dialogs++, args);
            }
        });

        // block the calling thread

        synchronized (messageboxSelection) {
            try {
                messageboxSelection.wait();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                return -1;
            }
        }

        // return selected value

        return messageboxSelection[0];
    }

    @Override
    public Dialog onCreateDialog(int ignore, Bundle args) {

        // TODO set values from "flags" to messagebox dialog

        // get colors

        int[] colors = args.getIntArray("colors");
        int backgroundColor;
        int textColor;
        int buttonBorderColor;
        int buttonBackgroundColor;
        int buttonSelectedColor;
        if (colors != null) {
            int i = -1;
            backgroundColor = colors[++i];
            textColor = colors[++i];
            buttonBorderColor = colors[++i];
            buttonBackgroundColor = colors[++i];
            buttonSelectedColor = colors[++i];
        } else {
            backgroundColor = Color.TRANSPARENT;
            textColor = Color.TRANSPARENT;
            buttonBorderColor = Color.TRANSPARENT;
            buttonBackgroundColor = Color.TRANSPARENT;
            buttonSelectedColor = Color.TRANSPARENT;
        }

        // create dialog with title and a listener to wake up calling thread

        final Dialog dialog = new Dialog(this);
        dialog.setTitle(args.getString("title"));
        dialog.setCancelable(false);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface unused) {
                synchronized (messageboxSelection) {
                    messageboxSelection.notify();
                }
            }
        });

        // create text

        TextView message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setText(args.getString("message"));
        if (textColor != Color.TRANSPARENT) {
            message.setTextColor(textColor);
        }

        // create buttons

        int[] buttonFlags = args.getIntArray("buttonFlags");
        int[] buttonIds = args.getIntArray("buttonIds");
        String[] buttonTexts = args.getStringArray("buttonTexts");

        final SparseArray<Button> mapping = new SparseArray<Button>();

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        for (int i = 0; i < buttonTexts.length; ++i) {
            Button button = new Button(this);
            final int id = buttonIds[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    messageboxSelection[0] = id;
                    dialog.dismiss();
                }
            });
            if (buttonFlags[i] != 0) {
                // see SDL_messagebox.h
                if ((buttonFlags[i] & 0x00000001) != 0) {
                    mapping.put(KeyEvent.KEYCODE_ENTER, button);
                }
                if ((buttonFlags[i] & 0x00000002) != 0) {
                    mapping.put(111, button); /* API 11: KeyEvent.KEYCODE_ESCAPE */
                }
            }
            button.setText(buttonTexts[i]);
            if (textColor != Color.TRANSPARENT) {
                button.setTextColor(textColor);
            }
            if (buttonBorderColor != Color.TRANSPARENT) {
                // TODO set color for border of messagebox button
            }
            if (buttonBackgroundColor != Color.TRANSPARENT) {
                Drawable drawable = button.getBackground();
                if (drawable == null) {
                    // setting the color this way removes the style
                    button.setBackgroundColor(buttonBackgroundColor);
                } else {
                    // setting the color this way keeps the style (gradient, padding, etc.)
                    drawable.setColorFilter(buttonBackgroundColor, PorterDuff.Mode.MULTIPLY);
                }
            }
            if (buttonSelectedColor != Color.TRANSPARENT) {
                // TODO set color for selected messagebox button
            }
            buttons.addView(button);
        }

        // create content

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(message);
        content.addView(buttons);
        if (backgroundColor != Color.TRANSPARENT) {
            content.setBackgroundColor(backgroundColor);
        }

        // add content to dialog and return

        dialog.setContentView(content);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                Button button = mapping.get(keyCode);
                if (button != null) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        button.performClick();
                    }
                    return true; // also for ignored actions
                }
                return false;
            }
        });

        return dialog;
    }
}

/**
    Simple nativeInit() runnable
*/
class SDLMain implements Runnable {
    SDLActivity mSingleton;

    public SDLMain(SDLActivity sdlActivity){
        mSingleton = sdlActivity;
    }

    @Override
    public void run() {
        // Runs SDL_main()
        int stutas;
        if(SDLActivity.isYUV){
            stutas = SDLActivity.nativeInitYUV(SDLActivity.input_url,SDLActivity.yuv_pixel_w,SDLActivity.yuv_pixel_h,SDLActivity.yuv_pixel_type,SDLActivity.yuv_fps);
        }
        else {
            SDLActivity.nativeCodecType(SDLActivity.codec_type);
            stutas = SDLActivity.nativeInit(SDLActivity.input_url);
        }
        if(stutas != 0) {
            mSingleton.showUIHandler.sendEmptyMessage(stutas);
        }

        //Log.v("SDL", "SDL thread terminated");
    }
}


/**
    SDLSurface. This is what we draw on, so we need to know when it's created
    in order to do anything useful. 

    Because of this, that's where we set up the SDL thread
*/
class SDLSurface extends SurfaceView implements SurfaceHolder.Callback, 
    View.OnKeyListener, View.OnTouchListener, SensorEventListener  {

    // Sensors
    protected static SensorManager mSensorManager;
    protected static Display mDisplay;

    // Keep track of the surface size to normalize touch events
    protected static float mWidth, mHeight;
    protected static int sdlFormat;
    private boolean isFirst = true;

    // Startup    
    public SDLSurface(Context context ,AttributeSet attrs ) {
        super(context,attrs);
        getHolder().addCallback(this); 
    
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this); 
        setOnTouchListener(this);   

        mDisplay = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        
        if(Build.VERSION.SDK_INT >= 12) {
            setOnGenericMotionListener(new SDLGenericMotionListener_API12());
        }

        // Some arbitrary defaults to avoid a potential division by zero
        mWidth = 1.0f;
        mHeight = 1.0f;
    }
    
    public Surface getNativeSurface() {
        return getHolder().getSurface();
    }

    // Called when we have a valid drawing surface
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v("SDL", "surfaceCreated()");
        holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
    }

    // Called when we lose the surface
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v("SDL", "surfaceDestroyed()");
        // Call this *before* setting mIsSurfaceReady to 'false'
        SDLActivity.handlePause();
        SDLActivity.mIsSurfaceReady = false;
        SDLActivity.onNativeSurfaceDestroyed();
    }

    // Called when the surface is resized
    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {
        Log.v("SDL", "surfaceChanged()");

        sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565 by default
        switch (format) {
        case PixelFormat.A_8:
            Log.v("SDL", "pixel format A_8");
            break;
        case PixelFormat.LA_88:
            Log.v("SDL", "pixel format LA_88");
            break;
        case PixelFormat.L_8:
            Log.v("SDL", "pixel format L_8");
            break;
        case PixelFormat.RGBA_4444:
            Log.v("SDL", "pixel format RGBA_4444");
            sdlFormat = 0x15421002; // SDL_PIXELFORMAT_RGBA4444
            break;
        case PixelFormat.RGBA_5551:
            Log.v("SDL", "pixel format RGBA_5551");
            sdlFormat = 0x15441002; // SDL_PIXELFORMAT_RGBA5551
            break;
        case PixelFormat.RGBA_8888:
            Log.v("SDL", "pixel format RGBA_8888");
            sdlFormat = 0x16462004; // SDL_PIXELFORMAT_RGBA8888
            break;
        case PixelFormat.RGBX_8888:
            Log.v("SDL", "pixel format RGBX_8888");
            sdlFormat = 0x16261804; // SDL_PIXELFORMAT_RGBX8888
            break;
        case PixelFormat.RGB_332:
            Log.v("SDL", "pixel format RGB_332");
            sdlFormat = 0x14110801; // SDL_PIXELFORMAT_RGB332
            break;
        case PixelFormat.RGB_565:
            Log.v("SDL", "pixel format RGB_565");
            sdlFormat = 0x15151002; // SDL_PIXELFORMAT_RGB565
            break;
        case PixelFormat.RGB_888:
            Log.v("SDL", "pixel format RGB_888");
            // Not sure this is right, maybe SDL_PIXELFORMAT_RGB24 instead?
            sdlFormat = 0x16161804; // SDL_PIXELFORMAT_RGB888
            break;
        default:
            Log.v("SDL", "pixel format unknown " + format);
            break;
        }

        mWidth = width;
        mHeight = height;
        SDLActivity.onNativeResize(width, height, sdlFormat,mDisplay.getRefreshRate());
        SDLActivity.nativeUpdateSdlRect(width, height);
        Log.i("SDL", "Window size:" + width + "x"+height);

        // Set mIsSurfaceReady to 'true' *before* making a call to handleResume
        SDLActivity.mIsSurfaceReady = true;
        SDLActivity.onNativeSurfaceChanged();

        startSDLThread();
    }

    public void startSDLThread() {
        if (isFirst && SDLActivity.mSDLThread == null && SDLActivity.isSetPixel) {
            isFirst = false;
            Log.i("SDL", "SDLThread start");
            SDLActivity.mSDLThread = new Thread(new SDLMain(SDLActivity.mSingleton), "SDLThread");
            enableSensor(Sensor.TYPE_ACCELEROMETER, true);
            SDLActivity.mSDLThread.start();

            // Set up a listener thread to catch when the native thread ends
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SDLActivity.mSDLThread.join();
                    } catch (Exception e) {
                    } finally {
                        // Native thread has finished
                        if (!SDLActivity.mExitCalledFromJava) {
                            SDLActivity.handleNativeExit();
                        }
                    }
                }
            }).start();
        }
    }

    // unused
    @Override
    public void onDraw(Canvas canvas) {}


    // Key events
    @Override
    public boolean onKey(View  v, int keyCode, KeyEvent event) {
        // Dispatch the different events depending on where they come from
        // Some SOURCE_DPAD or SOURCE_GAMEPAD are also SOURCE_KEYBOARD
        // So, we try to process them as DPAD or GAMEPAD events first, if that fails we try them as KEYBOARD
        
        if ( (event.getSource() & 0x00000401) != 0 || /* API 12: SOURCE_GAMEPAD */
                   (event.getSource() & InputDevice.SOURCE_DPAD) != 0 ) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (SDLActivity.onNativePadDown(event.getDeviceId(), keyCode) == 0) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (SDLActivity.onNativePadUp(event.getDeviceId(), keyCode) == 0) {
                    return true;
                }
            }
        }
        
        if( (event.getSource() & InputDevice.SOURCE_KEYBOARD) != 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                //Log.v("SDL", "key down: " + keyCode);
                SDLActivity.onNativeKeyDown(keyCode);
                return true;
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                //Log.v("SDL", "key up: " + keyCode);
                SDLActivity.onNativeKeyUp(keyCode);
                return true;
            }
        }
        
        return false;
    }

    // Touch events
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        /* Ref: http://developer.android.com/training/gestures/multi.html */
        final int touchDevId = event.getDeviceId();
        final int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();
        int pointerFingerId;
        int i = -1;
        float x,y,p;
        
        switch(action) {
            case MotionEvent.ACTION_MOVE:
                for (i = 0; i < pointerCount; i++) {
                    pointerFingerId = event.getPointerId(i);
                    x = event.getX(i) / mWidth;
                    y = event.getY(i) / mHeight;
                    p = event.getPressure(i);
                    SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                }
                break;
            
            case MotionEvent.ACTION_UP:
                Log.i("SDLSurface", "onTouch:");
                if(SDLActivity.mSingleton.root.getVisibility() == GONE)
                    SDLActivity.mSingleton.updateShowUI();
                else
                    SDLActivity.mSingleton.root.setVisibility(GONE);
                break;
            case MotionEvent.ACTION_DOWN:
                // Primary pointer up/down, the index is always zero
                i = 0;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                // Non primary pointer up/down
                if (i == -1) {
                    i = event.getActionIndex();
                }
                
                pointerFingerId = event.getPointerId(i);
                x = event.getX(i) / mWidth;
                y = event.getY(i) / mHeight;
                p = event.getPressure(i);
                SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                break;
            
            default:
                break;
        }

        return true;
   } 

    // Sensor events
    public void enableSensor(int sensortype, boolean enabled) {
        // TODO: This uses getDefaultSensor - what if we have >1 accels?
        if (enabled) {
            mSensorManager.registerListener(this, 
                            mSensorManager.getDefaultSensor(sensortype), 
                            SensorManager.SENSOR_DELAY_GAME, null);
        } else {
            mSensorManager.unregisterListener(this, 
                            mSensorManager.getDefaultSensor(sensortype));
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x, y;
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_90:
                    x = -event.values[1];
                    y = event.values[0];
                    break;
                case Surface.ROTATION_270:
                    x = event.values[1];
                    y = -event.values[0];
                    break;
                case Surface.ROTATION_180:
                    x = -event.values[1];
                    y = -event.values[0];
                    break;
                default:
                    x = event.values[0];
                    y = event.values[1];
                    break;
            }
            SDLActivity.onNativeAccel(-x / SensorManager.GRAVITY_EARTH,
                                      y / SensorManager.GRAVITY_EARTH,
                                      event.values[2] / SensorManager.GRAVITY_EARTH - 1);
        }
    }    
}

/* This is a fake invisible editor view that receives the input and defines the
 * pan&scan region
 */
class DummyEdit extends View implements View.OnKeyListener {
    InputConnection ic;

    public DummyEdit(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        setFocusable(true);
        setOnKeyListener(this);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // This handles the hardware keyboard input
        if (event.isPrintingKey()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                ic.commitText(String.valueOf((char) event.getUnicodeChar()), 1);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            SDLActivity.onNativeKeyDown(keyCode);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            SDLActivity.onNativeKeyUp(keyCode);
            return true;
        }

        return false;
    }
        
    //
    @Override
    public boolean onKeyPreIme (int keyCode, KeyEvent event) {
        // As seen on StackOverflow: http://stackoverflow.com/questions/7634346/keyboard-hide-event
        // FIXME: Discussion at http://bugzilla.libsdl.org/show_bug.cgi?id=1639
        // FIXME: This is not a 100% effective solution to the problem of detecting if the keyboard is showing or not
        // FIXME: A more effective solution would be to change our Layout from AbsoluteLayout to Relative or Linear
        // FIXME: And determine the keyboard presence doing this: http://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        // FIXME: An even more effective way would be if Android provided this out of the box, but where would the fun be in that :)
        if (event.getAction()==KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            if (SDLActivity.mTextEdit != null && SDLActivity.mTextEdit.getVisibility() == View.VISIBLE) {
                SDLActivity.onNativeKeyboardFocusLost();
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        ic = new SDLInputConnection(this, true);

        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | 33554432 /* API 11: EditorInfo.IME_FLAG_NO_FULLSCREEN */;

        return ic;
    }
}

class SDLInputConnection extends BaseInputConnection {

    public SDLInputConnection(View targetView, boolean fullEditor) {
        super(targetView, fullEditor);

    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {

        /*
         * This handles the keycodes from soft keyboard (and IME-translated
         * input from hardkeyboard)
         */
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.isPrintingKey()) {
                commitText(String.valueOf((char) event.getUnicodeChar()), 1);
            }
            SDLActivity.onNativeKeyDown(keyCode);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {

            SDLActivity.onNativeKeyUp(keyCode);
            return true;
        }
        return super.sendKeyEvent(event);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {

        nativeCommitText(text.toString(), newCursorPosition);

        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {

        nativeSetComposingText(text.toString(), newCursorPosition);

        return super.setComposingText(text, newCursorPosition);
    }

    public native void nativeCommitText(String text, int newCursorPosition);

    public native void nativeSetComposingText(String text, int newCursorPosition);

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {       
        // Workaround to capture backspace key. Ref: http://stackoverflow.com/questions/14560344/android-backspace-in-webview-baseinputconnection
        if (beforeLength == 1 && afterLength == 0) {
            // backspace
            return super.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                && super.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }

        return super.deleteSurroundingText(beforeLength, afterLength);
    }
}

/* A null joystick handler for API level < 12 devices (the accelerometer is handled separately) */
class SDLJoystickHandler {
    
    public boolean handleMotionEvent(MotionEvent event) {
        return false;
    }
    
    public void pollInputDevices() {
    }
}

/* Actual joystick functionality available for API >= 12 devices */
class  SDLJoystickHandler_API12 extends SDLJoystickHandler {
  
    class SDLJoystick {
        public int device_id;
        public String name;
        public ArrayList<InputDevice.MotionRange> axes;
        public ArrayList<InputDevice.MotionRange> hats;
    }
    class RangeComparator implements Comparator<InputDevice.MotionRange>
    {
        @Override
        public int compare(InputDevice.MotionRange arg0, InputDevice.MotionRange arg1) {
            return arg0.getAxis() - arg1.getAxis();
        }
    }
    
    private ArrayList<SDLJoystick> mJoysticks;
    
    public SDLJoystickHandler_API12() {
       
        mJoysticks = new ArrayList<SDLJoystick>();
    }

    @Override
    public void pollInputDevices() {
        int[] deviceIds = InputDevice.getDeviceIds();
        // It helps processing the device ids in reverse order
        // For example, in the case of the XBox 360 wireless dongle,
        // so the first controller seen by SDL matches what the receiver
        // considers to be the first controller
        
        for(int i=deviceIds.length-1; i>-1; i--) {
            SDLJoystick joystick = getJoystick(deviceIds[i]);
            if (joystick == null) {
                joystick = new SDLJoystick();
                InputDevice joystickDevice = InputDevice.getDevice(deviceIds[i]);
                if( (joystickDevice.getSources() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
                    joystick.device_id = deviceIds[i];
                    joystick.name = joystickDevice.getName();
                    joystick.axes = new ArrayList<InputDevice.MotionRange>();
                    joystick.hats = new ArrayList<InputDevice.MotionRange>();
                    
                    List<InputDevice.MotionRange> ranges = joystickDevice.getMotionRanges();
                    Collections.sort(ranges, new RangeComparator());
                    for (InputDevice.MotionRange range : ranges ) {
                        if ((range.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ) {
                            if (range.getAxis() == MotionEvent.AXIS_HAT_X ||
                                range.getAxis() == MotionEvent.AXIS_HAT_Y) {
                                joystick.hats.add(range);
                            }
                            else {
                                joystick.axes.add(range);
                            }
                        }
                    }
                    
                    mJoysticks.add(joystick);
                    SDLActivity.nativeAddJoystick(joystick.device_id, joystick.name, 0, -1, 
                                                  joystick.axes.size(), joystick.hats.size()/2, 0);
                }
            }
        }
        
        /* Check removed devices */
        ArrayList<Integer> removedDevices = new ArrayList<Integer>();
        for(int i=0; i < mJoysticks.size(); i++) {
            int device_id = mJoysticks.get(i).device_id;
            int j;
            for (j=0; j < deviceIds.length; j++) {
                if (device_id == deviceIds[j]) break;
            }
            if (j == deviceIds.length) {
                removedDevices.add(device_id);
            }
        }
            
        for(int i=0; i < removedDevices.size(); i++) {
            int device_id = removedDevices.get(i);
            SDLActivity.nativeRemoveJoystick(device_id);
            for (int j=0; j < mJoysticks.size(); j++) {
                if (mJoysticks.get(j).device_id == device_id) {
                    mJoysticks.remove(j);
                    break;
                }
            }
        }        
    }
    
    protected SDLJoystick getJoystick(int device_id) {
        for(int i=0; i < mJoysticks.size(); i++) {
            if (mJoysticks.get(i).device_id == device_id) {
                return mJoysticks.get(i);
            }
        }
        return null;
    }   
    
    @Override        
    public boolean handleMotionEvent(MotionEvent event) {
        if ( (event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0) {
            int actionPointerIndex = event.getActionIndex();
            int action = event.getActionMasked();
            switch(action) {
                case MotionEvent.ACTION_MOVE:
                    SDLJoystick joystick = getJoystick(event.getDeviceId());
                    if ( joystick != null ) {
                        for (int i = 0; i < joystick.axes.size(); i++) {
                            InputDevice.MotionRange range = joystick.axes.get(i);
                            /* Normalize the value to -1...1 */
                            float value = ( event.getAxisValue( range.getAxis(), actionPointerIndex) - range.getMin() ) / range.getRange() * 2.0f - 1.0f;
                            SDLActivity.onNativeJoy(joystick.device_id, i, value );
                        }          
                        for (int i = 0; i < joystick.hats.size(); i+=2) {
                            int hatX = Math.round(event.getAxisValue( joystick.hats.get(i).getAxis(), actionPointerIndex ) );
                            int hatY = Math.round(event.getAxisValue( joystick.hats.get(i+1).getAxis(), actionPointerIndex ) );
                            SDLActivity.onNativeHat(joystick.device_id, i/2, hatX, hatY );
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }            
}

class SDLGenericMotionListener_API12 implements View.OnGenericMotionListener {
    // Generic Motion (mouse hover, joystick...) events go here
    // We only have joysticks yet
    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        return SDLActivity.handleJoystickMotionEvent(event);
    }
}
