package com.blockhero.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import android.widget.TextView;

/** TV-remote / gamepad / keyboard controlled 3D platformer. */
public class MainActivity extends Activity {

    private GLSurfaceView glView;
    private Game game;
    private Sfx sfx;
    private TextView topText, centerText;
    private ImageView titleImg;
    private boolean onTitle, imageSet;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String lastTop = "", lastCenter = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sfx = new Sfx();
        game = new Game(sfx, new Game.Hud() {
            @Override
            public void set(final String top, final String center, final boolean title) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!top.equals(lastTop)) { lastTop = top; topText.setText(top); }
                        if (!center.equals(lastCenter)) { lastCenter = center; centerText.setText(center); }
                        onTitle = title;
                        updateTitleImage();
                    }
                });
            }
        });

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        glView.setRenderer(game);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Render at 720p at most and let the TV scale it: low GPU load + small buffers on 4K sets.
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        int longSide = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(720, shortSide);
        int w = Math.round(h * (float) longSide / shortSide);
        glView.getHolder().setFixedSize(w, h);

        FrameLayout root = new FrameLayout(this);
        root.addView(glView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        titleImg = new ImageView(this);
        titleImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        titleImg.setVisibility(View.GONE);
        root.addView(titleImg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topText = makeText(20, Gravity.START | Gravity.TOP);
        centerText = makeText(34, Gravity.CENTER);
        root.addView(topText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        root.addView(centerText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setContentView(root);

        // Use cached downloads right away, then fetch whatever is still missing (needs internet).
        new Thread(new Runnable() {
            @Override
            public void run() {
                File d = Assets.dir(MainActivity.this);
                applyAssets(d);
                Assets.fetchMissing(d);
                applyAssets(d);
            }
        }).start();
    }

    private void applyAssets(File d) {
        sfx.loadRemote(d);
        if (imageSet) return;
        File f = new File(d, Assets.TITLE_IMAGE);
        if (!f.exists()) return;
        try {
            // decode at roughly 1280 px wide to keep RAM low
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1280) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            final Bitmap bm = BitmapFactory.decodeFile(f.getPath(), o);
            if (bm == null) return;
            ui.post(new Runnable() {
                @Override
                public void run() {
                    titleImg.setImageBitmap(bm);
                    imageSet = true;
                    updateTitleImage();
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void updateTitleImage() {
        titleImg.setVisibility(onTitle && imageSet ? View.VISIBLE : View.GONE);
    }

    private TextView makeText(int sp, int gravity) {
        TextView t = new TextView(this);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(6f, 2f, 2f, 0xFF000000);
        t.setGravity(gravity);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        t.setPadding(pad, pad / 2, pad, pad / 2);
        return t;
    }

    // ----------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }

    @Override
    protected void onPause() {
        game.autoPause = true;
        sfx.music(false);
        glView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sfx.release();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    // --------------------------------------------------------------------- input

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                game.kUp = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                game.kDown = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                game.kLeft = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                game.kRight = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_1:
                if (e.getRepeatCount() == 0) game.actPress = true;
                game.kJump = true;
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_MENU:
                if (e.getRepeatCount() == 0) game.backPress = true;
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (e.getRepeatCount() == 0 && game.backExit()) finish();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                game.kUp = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                game.kDown = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                game.kLeft = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                game.kRight = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_1:
                game.kJump = false;
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return super.onKeyUp(code, e);
        }
    }

    /** Gamepad stick and hat (many pads report their D-pad as a hat axis, not as keys). */
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != 0 && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_X);
            float y = e.getAxisValue(MotionEvent.AXIS_Y);
            if (Math.abs(x) < 0.2f) x = 0f;
            if (Math.abs(y) < 0.2f) y = 0f;
            game.axX = x != 0f ? x : e.getAxisValue(MotionEvent.AXIS_HAT_X);
            game.axY = y != 0f ? y : e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    /** Touch (for testing on a phone): tap = jump / OK. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            game.actPress = true;
            game.kJump = true;
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            game.kJump = false;
        }
        return true;
    }
}
