/**
 * Copyright (C) 2019-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.biometrics.BiometricSourceType;
import android.net.Uri;
import android.hardware.biometrics.BiometricSourceType;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.net.Uri;
import android.util.Spline;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.utils.ThreadUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class FODCircleView extends ImageView implements ConfigurationListener {
    private static final int FADE_ANIM_DURATION = 125;
    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";
    private static final String SCREEN_BRIGHTNESS = Settings.System.SCREEN_BRIGHTNESS;
    private final int mPositionX;
    private final int mPositionY;
    private final int mSize;
    private final int mDreamingMaxOffset;
    private final int mNavigationBarSize;
    private final boolean mHideFodCircleGoingToSleep;
    private final boolean mShouldBoostBrightness;
    private final boolean mTargetUsesInKernelDimming;
    private final Paint mPaintFingerprintBackground = new Paint();
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams mPressedParams = new WindowManager.LayoutParams();
    private final WindowManager mWindowManager;

    private IFingerprintInscreen mFingerprintInscreenDaemon;
    private Context mContext;

    private int mCurrentBrightness;
    private int mColorBackground;
    private int mDreamingOffsetY;

    private boolean mFading;
    private boolean mIsBouncer;
    private boolean mIsBiometricRunning;
    private boolean mIsCircleShowing;
    private boolean mIsDreaming;
    private boolean mIsKeyguard;
    private boolean mTouchedOutside;
    private boolean mIsAnimating = false;

    private boolean mDozeEnabled;
    private boolean mFodGestureEnable;
    private boolean mPressPending;
    private boolean mScreenTurnedOn;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private Handler mHandler;

    private final ImageView mPressedView;

    private LockPatternUtils mLockPatternUtils;

    private Timer mBurnInProtectionTimer;

    private FODAnimation mFODAnimation;
    private boolean mIsRecognizingAnimEnabled;

    private int mSelectedIcon;
    private final int[] ICON_STYLES = {
        R.drawable.fod_icon_default,
        R.drawable.fod_icon_default_0,
        R.drawable.fod_icon_default_1,
        R.drawable.fod_icon_default_2,
        R.drawable.fod_icon_default_3,
        R.drawable.fod_icon_default_4,
        R.drawable.fod_icon_default_5,
        R.drawable.fod_icon_arc_reactor,
        R.drawable.fod_icon_cpt_america_flat,
        R.drawable.fod_icon_cpt_america_flat_gray,
        R.drawable.fod_icon_dragon_black_flat,
        R.drawable.fod_icon_hycon,
        R.drawable.fod_icon_glow_circle,
        R.drawable.fod_icon_neon_arc,
        R.drawable.fod_icon_neon_arc_gray,
        R.drawable.fod_icon_neon_circle_pink,
        R.drawable.fod_icon_neon_triangle,
        R.drawable.fod_icon_paint_splash_circle,
        R.drawable.fod_icon_rainbow_horn,
        R.drawable.fod_icon_shooky,
        R.drawable.fod_icon_spiral_blue,
        R.drawable.fod_icon_sun_metro,
        R.drawable.fod_icon_scratch_pink_blue,
        R.drawable.fod_icon_scratch_red_blue,
        R.drawable.fod_icon_fire_ice_ouroboros,
        R.drawable.fod_icon_transparent,
        R.drawable.fod_icon_heisinbug,
        R.drawable.fod_icon_iron_man,
        R.drawable.fod_icon_spiderman,
        R.drawable.fod_icon_thanos,
        R.drawable.fod_icon_captain,
        R.drawable.fod_icon_batman,
        R.drawable.fod_icon_thor
    };

    private int mDefaultPressedColor;
    private int mPressedColor;
    private final int[] PRESSED_COLOR = {
        R.drawable.fod_icon_pressed,
        R.drawable.fod_icon_pressed_cyan,
        R.drawable.fod_icon_pressed_green,
        R.drawable.fod_icon_pressed_yellow
    };

    private Spline mFODiconBrightnessToDimAmountSpline;

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
            new IFingerprintInscreenCallback.Stub() {
        @Override
        public void onFingerDown() {
            if (mUpdateMonitor.userNeedsStrongAuth()) {
                // Keyguard requires strong authentication (not biometrics)
                return;
            }

            if (mFodGestureEnable && !mScreenTurnedOn) {
                if (mDozeEnabled) {
                    mHandler.post(() -> mContext.sendBroadcast(new Intent(DOZE_INTENT)));
                } else {
                    mWakeLock.acquire(3000);
                    mHandler.post(() -> mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                        PowerManager.WAKE_REASON_GESTURE, FODCircleView.class.getSimpleName()));
                }
                mPressPending = true;
            } else if (mScreenTurnedOn) {
                mHandler.post(() -> showCircle());
            }
        }

        @Override
        public void onFingerUp() {
            mHandler.post(() -> hideCircle());
            mPressPending = false;
        }
    };

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onBiometricAuthenticated(int userId, BiometricSourceType biometricSourceType,
                boolean isStrongBiometric) {
            // We assume that if biometricSourceType matches Fingerprint it will be
            // handled here, so we hide only when other biometric types authenticate
            if (biometricSourceType != BiometricSourceType.FINGERPRINT) {
                hide();
            }
        }

        @Override
        public void onBiometricRunningStateChanged(boolean running,
                BiometricSourceType biometricSourceType) {
            if (biometricSourceType == BiometricSourceType.FINGERPRINT) {
                mIsBiometricRunning = running;
            }
        }

        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            updateIconDim(false);

            if (mIsKeyguard && mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
                updateIconDim(false);
            } else {
                hide();
            }

            if (dreaming) {
                mBurnInProtectionTimer = new Timer();
                mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
            } else if (mBurnInProtectionTimer != null) {
                mBurnInProtectionTimer.cancel();
                mBurnInProtectionTimer = null;
                updatePosition();
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            mIsKeyguard = showing;
            updateStyle();
            if (mFODAnimation != null) {
                mFODAnimation.setAnimationKeyguard(mIsKeyguard);
            }
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;
            if (mUpdateMonitor.isFingerprintDetectionRunning() && !mUpdateMonitor.userNeedsStrongAuth()) {
            updateStyle();
                if (isPinOrPattern(mUpdateMonitor.getCurrentUser()) || !isBouncer) {
                    show();
                } else {
                    hide();
                }
            } else {
                hide();
            }
            if (mFODAnimation != null) {
                mFODAnimation.setAnimationKeyguard(mIsBouncer);
            }
        }

        @Override
        public void onStartedGoingToSleep(int why) {
            if (mHideFodCircleGoingToSleep) {
                hide();
            }
        }

        @Override
        public void onScreenTurnedOff() {
            mScreenTurnedOn = false;
            if (mFodGestureEnable){
                hideCircle();
            }else if (!mHideFodCircleGoingToSleep) {
                hide();
            }
        }

        @Override
        public void onScreenTurnedOn() {
            if (!mFodGestureEnable && mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
            if (mFodGestureEnable && mPressPending) {
                mHandler.post(() -> showCircle());
                mPressPending = false;
            }
            mScreenTurnedOn = true;
        }

        @Override
        public void onBiometricHelp(int msgId, String helpString,
                BiometricSourceType biometricSourceType) {
            if (msgId == -1){ // Auth error
                mHandler.post(() -> mFODAnimation.hideFODanimation());
            }
        }
    };

    private class FodGestureSettingsObserver extends ContentObserver {
        FodGestureSettingsObserver(Context context, Handler handler) {
            super(handler);
        }

        void registerListener() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(
                    Settings.Secure.DOZE_ENABLED),
                    false, this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(
                    Settings.System.FOD_GESTURE),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateSettings();
        }

        public void updateSettings() {
            mDozeEnabled = Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.DOZE_ENABLED, 1,
                    UserHandle.USER_CURRENT) == 1;
            mFodGestureEnable = Settings.System.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.System.FOD_GESTURE, 1,
                    UserHandle.USER_CURRENT) == 1;
        }
    }

    private boolean mCutoutMasked;
    private int mStatusbarHeight;
    private FodGestureSettingsObserver mFodGestureSettingsObserver;
    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    SCREEN_BRIGHTNESS), false, this, UserHandle.USER_ALL);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // if (uri.equals(Settings.System.getUriFor(SCREEN_BRIGHTNESS))) {
            update();
            // }
        }

        void update() {
            int brightness = Settings.System.getInt(
                    mContext.getContentResolver(), SCREEN_BRIGHTNESS, 100);
            if (mCurrentBrightness != brightness) {
                mCurrentBrightness = brightness;
                updateIconDim(false);
            }
        }
    }

    private CustomSettingsObserver mCustomSettingsObserver;

    public FODCircleView(Context context) {
        super(context);
        mContext = context;

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon == null) {
            throw new RuntimeException("Unable to get IFingerprintInscreen");
        }

        try {
            mShouldBoostBrightness = daemon.shouldBoostBrightness();
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mSize = daemon.getSize();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to retrieve FOD circle position or size");
        }

        Resources res = context.getResources();

        mColorBackground = res.getColor(R.color.config_fodColorBackground);
        mDefaultPressedColor = res.getInteger(com.android.internal.R.
             integer.config_fod_pressed_color);
        mPaintFingerprintBackground.setColor(mColorBackground);
        mPaintFingerprintBackground.setAntiAlias(true);

        mPowerManager = context.getSystemService(PowerManager.class);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                 FODCircleView.class.getSimpleName());

        float[] icon_dim_amount =
                getFloatArray(res.obtainTypedArray(R.array.config_FODiconDimAmount));
        float[] display_brightness =
                getFloatArray(res.obtainTypedArray(R.array.config_FODiconDisplayBrightness));
        mFODiconBrightnessToDimAmountSpline =
                Spline.createSpline(display_brightness, icon_dim_amount);

        mTargetUsesInKernelDimming = res.getBoolean(com.android.internal.R.bool.config_targetUsesInKernelDimming);

        mHideFodCircleGoingToSleep = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_hideFodCircleGoingToSleep);

        mWindowManager = context.getSystemService(WindowManager.class);

        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        mDreamingMaxOffset = (int) (mSize * 0.1f);

        mHandler = new Handler(Looper.getMainLooper());

        mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
        mCustomSettingsObserver.update();

        mParams.height = mSize;
        mParams.width = mSize;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mPressedParams.copyFrom(mParams);
        mPressedParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        mParams.setTitle("Fingerprint on display");
        mPressedParams.setTitle("Fingerprint on display.touched");

        mPressedView = new ImageView(context)  {
            @Override
            protected void onDraw(Canvas canvas) {
                if (mIsCircleShowing) {
                    setImageResource(PRESSED_COLOR[mPressedColor]);
                }
                super.onDraw(canvas);
            }
        };

        mWindowManager.addView(this, mParams);

        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();
        updatePosition();
        hide();

        mLockPatternUtils = new LockPatternUtils(mContext);

        mUpdateMonitor = Dependency.get(KeyguardUpdateMonitor.class);
        mUpdateMonitor.registerCallback(mMonitorCallback);

        mFODAnimation = new FODAnimation(context, mPositionX, mPositionY);

        if (context.getResources().getBoolean(
            com.android.internal.R.bool.config_supportsScreenOffInDisplayFingerprint)){
            mFodGestureSettingsObserver = new FodGestureSettingsObserver(context, mHandler);
            mFodGestureSettingsObserver.registerListener();
        }

        updateCutoutFlags();
        Dependency.get(ConfigurationController.class).addCallback(this);
    }


    private int getDimAlpha() {
        return Math.round(mFODiconBrightnessToDimAmountSpline.interpolate(mCurrentBrightness));
    }

    public void updateIconDim(boolean animate) {
        if (!mIsCircleShowing && mTargetUsesInKernelDimming) {
            if (animate && !mIsAnimating) {
                ValueAnimator anim = new ValueAnimator();
                anim.setIntValues(0, getDimAlpha());
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        int progress = (Integer) valueAnimator.getAnimatedValue();
                        setColorFilter(Color.argb(progress, 0, 0, 0),
                                PorterDuff.Mode.SRC_ATOP);
                    }
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIsAnimating = false;
                    }
                });
                anim.setDuration(500);
                mIsAnimating = true;
                mHandler.post(() -> anim.start());
            } else if (!mIsAnimating) {
                mHandler.post(() ->
                        setColorFilter(Color.argb(getDimAlpha(), 0, 0, 0),
                        PorterDuff.Mode.SRC_ATOP));
            }
        } else {
            mHandler.post(() -> setColorFilter(Color.argb(0, 0, 0, 0),
                    PorterDuff.Mode.SRC_ATOP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsCircleShowing) {
            canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprintBackground);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newIsInside = (x > 0 && x < mSize) && (y > 0 && y < mSize);
        mTouchedOutside = false;

        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mTouchedOutside = true;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN && newIsInside) {
            showCircle();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hideCircle();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        }

        mHandler.post(() -> mFODAnimation.hideFODanimation());
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateStyle();
        updatePosition();
    }

    public IFingerprintInscreen getFingerprintInScreenDaemon() {
        if (mFingerprintInscreenDaemon == null) {
            try {
                mFingerprintInscreenDaemon = IFingerprintInscreen.getService();
                if (mFingerprintInscreenDaemon != null) {
                    mFingerprintInscreenDaemon.setCallback(mFingerprintInscreenCallback);
                    mFingerprintInscreenDaemon.asBinder().linkToDeath((cookie) -> {
                        mFingerprintInscreenDaemon = null;
                    }, 0);
                }
            } catch (NoSuchElementException | RemoteException e) {
                // do nothing
            }
        }
        return mFingerprintInscreenDaemon;
    }

    public void dispatchPress() {
        if (mFading) return;
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onPress();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchRelease() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onRelease();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchShow() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onShowFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchHide() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onHideFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void showCircle() {
        if (mFading || mTouchedOutside) return;
        mIsCircleShowing = true;

        setKeepScreenOn(true);

        setDim(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchPress();
        });

        mHandler.post(() -> mFODAnimation.showFODanimation());

        setImageDrawable(null);
        updateIconDim(false);
        updatePosition();
        invalidate();
    }

    public void hideCircle() {
        mIsCircleShowing = false;

        setImageResource(ICON_STYLES[mSelectedIcon]);
        invalidate();

        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchRelease();
        });
        setDim(false);

        mHandler.post(() -> mFODAnimation.hideFODanimation());

        setKeepScreenOn(false);
    }

    public void show() {
        if (mUpdateMonitor.userNeedsStrongAuth()) {
            // Keyguard requires strong authentication (not biometrics)
            return;
        }

        if (!mFodGestureEnable && !mUpdateMonitor.isScreenOn()) {
            // Keyguard is shown just after screen turning off
            return;
        }

        if (mIsBouncer && !isPinOrPattern(mUpdateMonitor.getCurrentUser())) {
            // Ignore show calls when Keyguard password screen is being shown
            return;
        }

        if (mIsKeyguard && mUpdateMonitor.getUserCanSkipBouncer(mUpdateMonitor.getCurrentUser())) {
            // Ignore show calls if user can skip bouncer
            return;
        }

        if (mIsKeyguard && !mIsBiometricRunning) {
            return;
        }

        updatePosition();
        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();

        setVisibility(View.VISIBLE);
        animate().withStartAction(() -> mFading = true)
                .alpha(mIsDreaming ? 0.5f : 1.0f)
                .setDuration(FADE_ANIM_DURATION)
                .withEndAction(() -> mFading = false)
                .start();
        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchShow();
        });
    }

    public void hide() {
        animate().withStartAction(() -> mFading = true)
                .alpha(0)
                .setDuration(FADE_ANIM_DURATION)
                .withEndAction(() -> {
                    setVisibility(View.GONE);
                    mFading = false;
                })
                .start();
        mCustomSettingsObserver.unobserve();
        hideCircle();
        ThreadUtils.postOnBackgroundThread(() -> {
            dispatchHide();
        });
    }

    private void updateStyle() {
        mIsRecognizingAnimEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_RECOGNIZING_ANIMATION, 0) != 0;
        mSelectedIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON, 0);
        mPressedColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_COLOR, mDefaultPressedColor);

        if (mFODAnimation != null) {
            mFODAnimation.update(mIsRecognizingAnimEnabled);
        }
    }

    public int getHeight(boolean includeDecor) {
        DisplayMetrics dm = new DisplayMetrics();
        if (includeDecor) {
            mWindowManager.getDefaultDisplay().getMetrics(dm);
        } else {
            mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        }
        return dm.heightPixels - mPositionY + mSize / 2;
    }

    private void updatePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        int cutoutMaskedExtra = mCutoutMasked ? mStatusbarHeight : 0;
        int x, y;
        switch (rotation) {
            case Surface.ROTATION_0:
                x = mPositionX;
                y = mPositionY - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_90:
                x = mPositionY;
                y = mPositionX - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_180:
                x = mPositionX;
                y = size.y - mPositionY - mSize - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_270:
                x = size.x - mPositionY - mSize - mNavigationBarSize - cutoutMaskedExtra;
                y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        mPressedParams.x = mParams.x = x;
        mPressedParams.y = mParams.y = y;

        if (mIsDreaming && !mIsCircleShowing) {
            mParams.y += mDreamingOffsetY;
            mFODAnimation.updateParams(mParams.y);
        }

        mWindowManager.updateViewLayout(this, mParams);

        if (mPressedView.getParent() != null) {
            mWindowManager.updateViewLayout(mPressedView, mPressedParams);
        }
    }

    private void setDim(boolean dim) {
        if (dim) {
            int curBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            int dimAmount = 0;

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            try {
                dimAmount = daemon.getDimAmount(curBrightness);
            } catch (RemoteException e) {
                // do nothing
            }

            if (mShouldBoostBrightness) {
                mPressedParams.screenBrightness = 1.0f;
            }

            mPressedParams.dimAmount = dimAmount / 255.0f;
            if (mPressedView.getParent() == null) {
                mWindowManager.addView(mPressedView, mPressedParams);
            } else {
                mWindowManager.updateViewLayout(mPressedView, mPressedParams);
            }
        } else {
            if (mShouldBoostBrightness) {
                mPressedParams.screenBrightness = 0.0f;
            }
            mPressedParams.dimAmount = 0.0f;
            if (mPressedView.getParent() != null) {
                mWindowManager.removeView(mPressedView);
            }
            updateIconDim(true);
        }
    }

    private boolean isPinOrPattern(int userId) {
        int passwordQuality = mLockPatternUtils.getActivePasswordQuality(userId);
        switch (passwordQuality) {
            // PIN
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            // Pattern
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return true;
        }

        return false;
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000 / 60;

            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int) ((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            mDreamingOffsetY -= mDreamingMaxOffset;

            mHandler.post(() -> updatePosition());
        }
    };

    @Override
    public void onOverlayChanged() {
        updateCutoutFlags();
    }

    private void updateCutoutFlags() {
        mStatusbarHeight = getContext().getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height_portrait);
        boolean cutoutMasked = getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_maskMainBuiltInDisplayCutout);
        if (mCutoutMasked != cutoutMasked){
            mCutoutMasked = cutoutMasked;
            updatePosition();
        }
    }

    private static float[] getFloatArray(TypedArray array) {
        int length = array.length();
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = array.getFloat(i, Float.NaN);
        }
        array.recycle();
        return floatArray;
    }
}
