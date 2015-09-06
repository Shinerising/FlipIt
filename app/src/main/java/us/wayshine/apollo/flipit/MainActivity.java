/*
* Title: Flip It!
* Author: Apollo Wayne
* Description: A game that count the flip times when you toss your phone
* Keywords: Game, Casual, Motion
* Create Date: 8.11.15
* Last Modified Date: 9.6.15
 */

package us.wayshine.apollo.flipit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;

import com.google.android.gms.common.ConnectionResult;
import com.google.example.games.basegameutils.BaseGameUtils;

/*
* App main activity
* Use SensorEventListener for motion detection
* Use GoogleApiClient for Google Play Games Service
 */
public class MainActivity extends Activity implements
        SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    //Game Status
    public static final short GAME_LOADING = 0;
    public static final short GAME_MENU = 1;
    public static final short GAME_WAITING = 2;
    public static final short GAME_SPINNING = 3;
    public static final short GAME_RESULT = 4;
    public static final short GAME_GOMENU = 5;
    public static final short GAME_GOGAME = 6;

    //Sound ID
    public static final short SOUND_BLOP = 0;
    public static final short SOUND_CLICK = 1;
    public static final short SOUND_CLOSE = 2;
    public static final short SOUND_OPEN = 3;
    public static final short SOUND_RISE = 4;
    public static final short SOUND_TADA = 5;

    private GoogleApiClient mGoogleApiClient;

    //3 text labels
    private TextView label01;
    private TextView label02;
    private TextView label03;

    private SoundPool mSound;
    private int[] mSoundID = new int[6];

    //Motion sensor parameters
    private SensorManager sensorMan;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private float mAccel;
    private float mGyroX;
    private float mGyroY;
    private float mGyroX0;
    private float mGyroY0;
    private float mGyro;
    private float mRotateX;
    private float mRotateY;
    private float mRotate;

    private int videoId = 0;
    private boolean showRank = false;
    private boolean showAchieve = false;

    private int flipTimes = 0;
    private int flipduration = 0;

    public static final short GAME_FPS = 100;
    private int framems = 1000 / GAME_FPS;
    private int timer = 0;
    private short gameStatus;

    private int mHighScore = 0, mHighDura = 0;

    private final Handler runningHandler = new Handler();

    //Game main function
    //Gather the motion sensor values in each call
    private Runnable running = new Runnable(){
        public void run(){
            gameRun();
            runningHandler.postDelayed(running, framems);
        }
    };

    //Initiate the game value
    private Runnable init = new Runnable(){
        public void run(){
            init();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Use Fullscreen feature
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if(android.os.Build.VERSION.SDK_INT > 18) {
            uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_main);

        //Loading Scene with Studio Title
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int width = displaymetrics.widthPixels;
        int height = displaymetrics.heightPixels;
        try {
            ImageView myTitleImage = (ImageView) findViewById(R.id.imageView);
            myTitleImage.setImageBitmap(decodeSampledBitmapFromResource(getResources(), R.drawable.mytitle, width, height));
        }
        catch(Throwable e) {
            Log.e("MainActivity", e.toString());
        }

        //Use the motion sensor service
        sensorMan = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroscope = sensorMan.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        label01 = (TextView)findViewById(R.id.ftimes);  //Flip Times
        label02 = (TextView)findViewById(R.id.fdura);   //Flipping Duration
        label03 = (TextView)findViewById(R.id.fhigh);   //High Score

        //Add button onclick listener
        Button buttonRestart = (Button)findViewById(R.id.restart);
        buttonRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(gameStatus == GAME_RESULT && v.getAlpha() == 1) {
                    restart();
                    mSound.play(mSoundID[SOUND_CLICK], 1, 1, 1, 0, 1);
                }
            }
        });

        Button buttonBack = (Button)findViewById(R.id.back);
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(gameStatus == GAME_RESULT && v.getAlpha() == 1) backToMenu();
            }
        });

        Button buttonStart = (Button)findViewById(R.id.start);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(v.getAlpha() == 1)enterGame();
            }
        });

        Button buttonRank = (Button)findViewById(R.id.rank);
        buttonRank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(v.getAlpha() == 1 && isGoogleApiConnected())showRank();
            }
        });

        Button buttonAchieve = (Button)findViewById(R.id.achieve);
        buttonAchieve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(v.getAlpha() == 1 && isGoogleApiConnected())showAchieve();
            }
        });

        Button buttonShare = (Button)findViewById(R.id.share);
        buttonShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(v.getAlpha() == 1)showShare();
            }
        });

        //The animation video in main menu
        VideoView videoView = (VideoView)findViewById(R.id.videoView);
        setVideo(0);

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                setVideo(videoId);
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.d("video", "setOnErrorListener");
                return true;
            }
        });


        //Set the Soundpool
        if(android.os.Build.VERSION.SDK_INT > 20) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mSound = new SoundPool.Builder()
                    .setAudioAttributes(attributes)
                    .setMaxStreams(4)
                    .build();
        }
        else {
            mSound = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        }
        mSoundID[SOUND_BLOP] = mSound.load(this, R.raw.sound_blop, 1);
        mSoundID[SOUND_CLICK] = mSound.load(this, R.raw.sound_click, 1);
        mSoundID[SOUND_CLOSE] = mSound.load(this, R.raw.sound_close, 1);
        mSoundID[SOUND_OPEN] = mSound.load(this, R.raw.sound_open, 1);
        mSoundID[SOUND_RISE] = mSound.load(this, R.raw.sound_slide, 1);
        mSoundID[SOUND_TADA] = mSound.load(this, R.raw.sound_tada, 1);

        //Load the user data
        readData();

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API, Games.GamesOptions.builder().setShowConnectingPopup(true, Gravity.CENTER).build())
                .addScope(Games.SCOPE_GAMES)
                        // add other APIs and scopes here as needed
                .build();

        gameStatus = GAME_LOADING;
    }

    /*
    * The animation in main menu contains 6 videos
    * If a video finished, the next one will be played
    * This function is used for changing the resource of the videoView
     */
    private void setVideo(int id) {
        VideoView videoView = (VideoView)findViewById(R.id.videoView);
        Uri uri = Uri.EMPTY;
        videoId = id;
        if (videoId > 5) {
            unlockAchievement(R.string.achievement_thats_funny);
            videoId = 0;
        }
        if (videoId == 0) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation00);
        }
        else if (videoId == 1) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation01);
        }
        else if (videoId == 2) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation02);
        }
        else if (videoId == 3) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation03);
        }
        else if (videoId == 4) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation04);
        }
        else if (videoId == 5) {
            uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.flipitanimation05);
        }
        if (videoId == -1) {
            videoView.stopPlayback();
        }
        else {
            videoId++;

            try {
                videoView.setVideoURI(uri);
                videoView.setVisibility(View.VISIBLE);
                videoView.start();
            }
            catch(Exception e) {
                videoView.setVisibility(View.INVISIBLE);
                videoView.setOnCompletionListener(null);
            }
        }

    }

    // This function detects phone motion status and use it to process the game.
    // Caution: the motion sensor in smartphone has low accuracy when flipping at a fast speed
    // So the detection process used in this function works not very well.
    // however, it could work as a game.
    private void gameRun() {
        timer += framems;

        // When the acceleration > 9.5, we thought the phone has been tossed and is flying in the air.
        if(gameStatus == GAME_WAITING && mAccel > 9.5f) {
            gameStatus = GAME_SPINNING;
            flipduration = 0;
            mRotate = 0;
            mGyroX0 = 0;
            mGyroY0 = 0;
            label01.setText("0");
            label02.setText("00:00");

            // Hide the buttons and tips
            showView(R.id.ani_do, 1000, 5);
            showView(R.id.ani_notdo, 1000, 5);

            showView(R.id.text_do, 1000, 5);
            showView(R.id.text_notdo, 1000, 5);

            mSound.play(mSoundID[SOUND_RISE], 1, 1, 1, 0, 1);
        }
        if(gameStatus == GAME_SPINNING && mAccel > 9.5f) {
            mRotate += mGyro * framems / 1000;

            mGyroX0 = mGyroX;
            mGyroY0 = mGyroY;

            while(mRotate >= Math.PI) {
                // Complete one flip
                flipTimes++;
                label01.setText(Integer.toString(flipTimes));

                if(flipTimes > mHighScore) {
                    label03.setText(getString(R.string.highscore) + " " + Integer.toString(flipTimes));
                }

                mRotate -= Math.PI;
            }

            flipduration += framems;
            String timeText = Integer.toString(flipduration / 1000) + ":" + String.format("%02d", (flipduration % 1000) / 10);
            if(flipduration > 99999) timeText = "$%^&**@^#^$*@*@^^%("; //We don't think someone could keep the phone flying with 99 seconds
            label02.setText(timeText);
        }
        // When the acceleration < 8.0, we thought the phone has stopped flipping and dropped on the ground(sadly).
        else if(gameStatus == GAME_SPINNING && mAccel <= 8.0f) {
            mSound.pause(mSoundID[SOUND_RISE]);

            gameStatus = GAME_RESULT;
            if (flipTimes > mHighScore) {
                mHighScore = flipTimes;
                mSound.play(mSoundID[SOUND_TADA], 1, 1, 1, 0, 1);
                updateHighScore(flipTimes);
                saveData();
            }
            if(flipduration > mHighDura) {
                mHighDura = flipduration;
                saveData();
            }

            if(isGoogleApiConnected()) {
                // Unlock the achievement
                unlockAchievement(R.string.achievement_first_toss);
                if (flipTimes >= 5) unlockAchievement(R.string.achievement_flip_master);
                if (flipTimes >= 10) unlockAchievement(R.string.achievement_flip_monster);
                if (flipTimes >= 20) unlockAchievement(R.string.achievement_flip_god);
                if (flipTimes > 24) unlockAchievement(R.string.achievement_you_must_be_cheating);

                if (flipduration >= 1000) unlockAchievement(R.string.achievement_highly_toss);
                if (flipduration >= 2000) unlockAchievement(R.string.achievement_im_michael_jordan);
                if (flipduration >= 5000) unlockAchievement(R.string.achievement_i_can_fly);
                if (flipduration >= 100000) unlockAchievement(R.string.achievement_homerun);
            }
            //Enable the flipTimes label visual effect
            ((TextView)findViewById(R.id.ftimesback)).setText(Integer.toString(flipTimes));
            findViewById(R.id.ftimesback).setVisibility(View.VISIBLE);

            // Show the buttons
            showView(R.id.back, 1200, 0);
            showView(R.id.restart, 1000, 0);
            showView(R.id.share, 1400, 0);
        }
    }

    // Reset the parameters when game is restarting
    private void restart() {
        gameStatus = GAME_WAITING;
        flipTimes = 0;
        label01.setText("0");
        label02.setText("00:00");

        showView(R.id.ani_do, 600, 4);
        showView(R.id.ani_notdo, 600, 4);

        showView(R.id.text_do, 1000, 4);
        showView(R.id.text_notdo, 1000, 4);

        showView(R.id.back, 0, 6);
        showView(R.id.restart, 0, 6);
        showView(R.id.share, 0, 6);

        findViewById(R.id.ftimesback).setVisibility(View.INVISIBLE);
    }

    // Layout showing effect(using CircularReveal)
    private void showLayout(int viewId, int viewId2) {
        final View myView = findViewById(viewId);
        View myView2 = findViewById(viewId2);

        if(android.os.Build.VERSION.SDK_INT > 20) {

            int finalRadius = Math.max(myView.getWidth(), myView.getHeight());

            int cx = (myView2.getLeft() + myView2.getRight()) / 2;
            int cy = (myView2.getTop() + myView2.getBottom()) / 2;

            Animator anim =
                    ViewAnimationUtils.createCircularReveal(myView, cx, cy, 0, finalRadius);
            anim.setDuration(500);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (gameStatus == GAME_GOGAME) {
                        gameStatus = GAME_WAITING;
                    }
                }
            });

            myView.setVisibility(View.VISIBLE);
            myView.setAlpha(1);
            anim.start();
        }
        else {
            myView.setVisibility(View.VISIBLE);
            if (gameStatus == GAME_GOGAME) {
                gameStatus = GAME_WAITING;
            }
        }
    }

    // Layout hiding effect(using CircularReveal)
    private void hideLayout(int viewId, int viewId2) {

        final View myView = findViewById(viewId);
        View myView2 = findViewById(viewId2);

        if(android.os.Build.VERSION.SDK_INT > 20) {

            int initialRadius = myView.getWidth();

            int cx = (myView2.getLeft() + myView2.getRight()) / 2;
            int cy = (myView2.getTop() + myView2.getBottom()) / 2;

            Animator anim =
                    ViewAnimationUtils.createCircularReveal(myView, cx, cy, initialRadius, 0);
            //anim.setStartDelay(200);
            anim.setDuration(500);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    myView.setVisibility(View.INVISIBLE);
                    if (gameStatus == GAME_LOADING) showMenu();
                    else if (gameStatus == GAME_GOMENU) {
                        hideView(R.id.back);
                        hideView(R.id.restart);
                        hideView(R.id.share);
                        gameStatus = GAME_MENU;
                    }
                }
            });

            anim.start();
        }
        else {
            myView.setVisibility(View.INVISIBLE);
            if (gameStatus == GAME_LOADING) showMenu();
            else if (gameStatus == GAME_GOMENU) {
                hideView(R.id.back);
                hideView(R.id.restart);
                hideView(R.id.share);
                gameStatus = GAME_MENU;
            }
        }
    }

    //Instantly hide view
    private void hideView(int viewId) {
        View myView = findViewById(viewId);
        myView.setAlpha(0);
    }

    //Show(or hide) one view with some effects
    private void showView(int viewId, int delay, int b) {
        View myView = findViewById(viewId);
        final int behavior = b;

        if(android.os.Build.VERSION.SDK_INT >= 21) {

            float x = myView.getX();
            float y = myView.getY();

            Path path1 = new Path();
            Path path2 = new Path();

            //Show with bounce effect
            if(behavior == 0) {
                path1.moveTo(x, y + 100);
                path1.rLineTo(0, -100);
                path2.moveTo(0, 0);
                path2.lineTo(1, 1);
            }
            //Show with bounce effect(and drop from top)
            else if(behavior == 1) {
                path1.moveTo(x, y - 300);
                path1.rLineTo(0, 300);
                path2.moveTo(1, 1);
                path2.lineTo(1, 1);
            }
            //Show with inflate effect
            else if(behavior == 2) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(1, 1);
                path2.lineTo(10, 10);
            }
            //Hide with deflate effect(from large shape)
            else if(behavior == 3) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(10, 10);
                path2.lineTo(1, 1);
            }
            //Fade in and float
            else if(behavior == 4) {
                path1.moveTo(x, y + 50);
                path1.rLineTo(0, -50);
                path2.moveTo(1, 1);
                path2.lineTo(1, 1);
            }
            //Fade out
            else if(behavior == 5) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(1, 1);
                path2.lineTo(1, 1);
            }
            //Hide with deflate effect
            else if(behavior == 6) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(1, 1);
                path2.lineTo(0, 0);
            }
            //Show with inflate effect
            else if(behavior == 7) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(1, 1);
                path2.lineTo(20, 20);
            }
            //Hide with deflate effect(from large shape)
            else if(behavior == 8) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(20, 20);
                path2.lineTo(1, 1);
            }
            //This effect only used for the flipTimes label
            else if(behavior == 9) {
                path1.moveTo(x, y);
                path1.rLineTo(0, 0);
                path2.moveTo(1, 1);
                path2.lineTo(1, 1);
                path2.lineTo(2, 2);
            }

            ObjectAnimator mAnimator1, mAnimator2, mAnimator3;
            mAnimator1 = ObjectAnimator.ofFloat(myView, View.X, View.Y, path1);
            mAnimator2 = ObjectAnimator.ofFloat(myView, View.SCALE_X, View.SCALE_Y, path2);
            mAnimator1.setDuration(300);
            mAnimator2.setDuration(300);
            mAnimator1.setStartDelay(delay);
            mAnimator2.setStartDelay(delay);

            if(behavior == 0 || behavior == 1) {
                mAnimator1.setInterpolator(new BounceInterpolator());
                mAnimator2.setInterpolator(new BounceInterpolator());
                mAnimator3 = ObjectAnimator.ofFloat(myView, "alpha", myView.getAlpha(), 1);
                mAnimator3.setDuration(10);
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
            }
            else if(behavior == 2 || behavior == 7) {
                myView.bringToFront();
                FrameLayout menuLayout = (FrameLayout)findViewById(R.id.menu);
                menuLayout.requestLayout();
                menuLayout.invalidate();

                mAnimator3 = ObjectAnimator.ofArgb(myView, "textColor", ((Button)myView).getCurrentTextColor(), Color.TRANSPARENT);
                mAnimator3.setDuration(100);
                mAnimator1.setInterpolator(new AccelerateInterpolator());
                mAnimator2.setInterpolator(new AccelerateInterpolator());
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
                mSound.play(mSoundID[SOUND_OPEN], 1, 1, 1, 0, 1);
            }
            else if(behavior == 3 || behavior == 8) {
                mAnimator3 = ObjectAnimator.ofArgb(myView, "textColor", ((Button)myView).getCurrentTextColor(), Color.WHITE);
                mAnimator3.setDuration(100);
                mAnimator1.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setInterpolator(new DecelerateInterpolator());
                mAnimator3.setStartDelay(delay + 200);
                mAnimator3.start();
                mSound.play(mSoundID[SOUND_CLOSE], 1, 1, 1, 0, 1);
            }
            else if(behavior == 4) {
                mAnimator3 = ObjectAnimator.ofFloat(myView, "alpha", myView.getAlpha(), 1);
                mAnimator3.setDuration(300);
                mAnimator1.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setInterpolator(new DecelerateInterpolator());
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
            }
            else if(behavior == 5) {
                mAnimator3 = ObjectAnimator.ofFloat(myView, "alpha", myView.getAlpha(), 0);
                mAnimator3.setDuration(300);
                mAnimator1.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setInterpolator(new DecelerateInterpolator());
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
            }
            else if(behavior == 6) {
                mAnimator3 = ObjectAnimator.ofFloat(myView, "alpha", myView.getAlpha(), 0);
                mAnimator3.setDuration(300);
                mAnimator1.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setInterpolator(new DecelerateInterpolator());
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
            }
            else if(behavior == 9) {
                mAnimator3 = ObjectAnimator.ofFloat(myView, "alpha", 1, 0);
                mAnimator2.setDuration(1000);
                mAnimator3.setDuration(1000);
                mAnimator1.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setInterpolator(new DecelerateInterpolator());
                mAnimator2.setRepeatCount(Animation.INFINITE);
                mAnimator3.setRepeatCount(Animation.INFINITE);
                mAnimator3.setStartDelay(delay);
                mAnimator3.start();
            }
            if (behavior == 0 || behavior == 1 || behavior == 4) {
                mAnimator1.start();
            }
            mAnimator2.start();

            mAnimator1.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (behavior == 0) {
                        mSound.play(mSoundID[SOUND_BLOP], 1, 1, 1, 0, 1);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
        //If the device OS is below Lollipop, the animation feature will be lost
        else {
            if(behavior == 5 || behavior == 6) {
                myView.setAlpha(0);
            }
            else {
                myView.setAlpha(1);
            }

            if(behavior == 2) {
                myView.setScaleX(10);
                myView.setScaleY(10);
                ((Button)myView).setTextColor(Color.TRANSPARENT);
            }
            else if(behavior == 3) {
                myView.setScaleX(1);
                myView.setScaleY(1);
                ((Button)myView).setTextColor(Color.WHITE);
            }

            if (behavior == 0) {
                mSound.play(mSoundID[SOUND_BLOP], 1, 1, 1, 0, 1);
            } else if (behavior == 2 || behavior == 7) {
                mSound.play(mSoundID[SOUND_OPEN], 1, 1, 1, 0, 1);
            }
            else if(behavior == 3 || behavior == 8) {
                mSound.play(mSoundID[SOUND_CLOSE], 1, 1, 1, 0, 1);
            }
            else if(behavior == 6) {
                mSound.play(mSoundID[SOUND_CLICK], 1, 1, 1, 0, 1);
            }
        }
    }

    // Initiate the game(in fact this function only does two things)
    private void init() {
        hideLayout(R.id.title, R.id.title);

        showView(R.id.ftimesback, 800, 9);
    }

    // Show the main menu
    private void showMenu() {

        showView(R.id.name, 100, 1);
        showView(R.id.start, 500, 0);

        gameStatus = GAME_MENU;
    }

    // Open leaderboard with Google Play Games Service
    private void showRank() {
        if(showRank) {
            showView(R.id.rank, 0, 8);
            showRank = false;
        }
        else {
            showView(R.id.rank, 0, 7);
            showRank = true;
            showLeaderBoard();
        }
    }

    // Open achievements with Google Play Games Service
    private void showAchieve() {
        if(showAchieve) {
            showView(R.id.achieve, 0, 8);
            showAchieve = false;
        }
        else {
            showView(R.id.achieve, 0, 7);
            showAchieve = true;
            showAchievements();
        }
    }

    // Open share window
    private void showShare() {

        Intent sendIntent = new Intent();
        String shareText =
                String.format(getString(R.string.string_sharescore) , flipTimes) +
                        " " + getString(R.string.string_appurl);
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    // Start the game
    private void enterGame() {

        if(gameStatus == GAME_MENU) {
            gameStatus = GAME_GOGAME;
            showView(R.id.start, 0, 2);
            showLayout(R.id.main, R.id.start);

            //Show Tips animation
            AnimationDrawable animationDo;
            ImageView doImage = (ImageView) findViewById(R.id.ani_do);
            doImage.setBackgroundResource(R.drawable.animation_do);
            animationDo = (AnimationDrawable) doImage.getBackground();
            animationDo.start();

            AnimationDrawable animationNotDo;
            ImageView ndoImage = (ImageView) findViewById(R.id.ani_notdo);
            ndoImage.setBackgroundResource(R.drawable.animation_notdo);
            animationNotDo = (AnimationDrawable) ndoImage.getBackground();
            animationNotDo.start();

            showView(R.id.ani_do, 600, 4);
            showView(R.id.ani_notdo, 600, 4);

            showView(R.id.text_do, 1000, 4);
            showView(R.id.text_notdo, 1000, 4);

            label01.setText("0");
            label02.setText("00:00");
            label03.setText(getString(R.string.highscore) + " " + Integer.toString(mHighScore));

            VideoView videoView = (VideoView)findViewById(R.id.videoView);
            videoView.pause();
        }
    }

    // Go back to main menu from game scene
    private void backToMenu() {
        if (gameStatus != GAME_GOMENU) {
            gameStatus = GAME_GOMENU;

            // Layout change
            showView(R.id.start, 0, 3);
            hideLayout(R.id.main, R.id.start);

            showView(R.id.ani_do, 0, 5);
            showView(R.id.ani_notdo, 0, 5);

            showView(R.id.text_do, 0, 5);
            showView(R.id.text_notdo, 0, 5);

            // Show menu buttons
            showView(R.id.back, 0, 6);
            showView(R.id.restart, 0, 6);
            showView(R.id.share, 0, 6);

            setVideo(0);

            findViewById(R.id.ftimesback).setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        runningHandler.postDelayed(running, framems);
        runningHandler.postDelayed(init, 1000);
    }

    @Override
    public void onBackPressed() {
        if(gameStatus == GAME_MENU) finish();
        else if (gameStatus == GAME_WAITING || gameStatus == GAME_RESULT) backToMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set motion sensor listener
        sensorMan.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorMan.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        setVideo(0);
        if(showRank)showRank();
        if(showAchieve)showAchieve();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorMan.unregisterListener(this);
        //Stop animation video
        setVideo(-1);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            // Get the absolute acceleration
            mAccel = (float)Math.sqrt(x * x + y * y + z * z);
        }
        else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            mGyroX = x;
            mGyroY = y;
            // Get the gyroscope
            mGyro = (float)Math.sqrt(x * x + y * y);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // required method
    }

    public boolean isGoogleApiConnected() {
        if(mGoogleApiClient == null) return false;
        else return mGoogleApiClient.isConnected();
    }

    public void updateHighScore(int score) {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Games.Leaderboards.submitScore(mGoogleApiClient, getString(R.string.leaderboard_flip_times_ranking), score);
        }
    }

    public void unlockAchievement(int achi) {
        String achievement = getString(achi);
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Games.Achievements.unlock(mGoogleApiClient, achievement);
        }
    }

    public void showLeaderBoard() {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(mGoogleApiClient,
                    getString(R.string.leaderboard_flip_times_ranking)), 2);
        }
        else if(mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    public void showAchievements() {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            startActivityForResult(Games.Achievements.getAchievementsIntent(mGoogleApiClient), 2);
        }
        else if(mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // The player is signed in. Hide the sign-in button and allow the
        // player to proceed.

        // Show achievement and leaderboard buttons when Game Service is connected
        showView(R.id.achieve, 1500, 0);
        showView(R.id.rank, 1700, 0);

    }

    /*
    * The code below is from Android Developer
     */
    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (mResolvingConnectionFailure) {
            // already resolving
            return;
        }

        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (mSignInClicked || mAutoStartSignInFlow) {
            mAutoStartSignInFlow = false;
            mSignInClicked = false;
            mResolvingConnectionFailure = true;

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."

            if (!BaseGameUtils.resolveConnectionFailure(this,
                    mGoogleApiClient, connectionResult,
                    RC_SIGN_IN, "error")) {
                mResolvingConnectionFailure = false;
            }

        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        // Attempt to reconnect
        mGoogleApiClient.connect();
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // Load images in sample size
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    private boolean saveData() {
        String filename = "userdata";

        FileOutputStream outputStream;
        try {
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(intToByteArray(mHighScore));
            outputStream.write(intToByteArray(mHighDura));
            outputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean readData() {
        String filename = "userdata";
        byte[] data = new byte[4];
        FileInputStream inputStream;
        try {
            inputStream = openFileInput(filename);
            if(inputStream.read(data) != -1) mHighScore = byteArrayToInt(data);
            if(inputStream.read(data) != -1) mHighDura = byteArrayToInt(data);
            inputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    protected int byteArrayToInt(byte[] b) {
        return   b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    protected byte[] intToByteArray(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

}
