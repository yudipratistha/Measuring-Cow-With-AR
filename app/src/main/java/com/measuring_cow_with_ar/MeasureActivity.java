package com.measuring_cow_with_ar;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arcore.DisplayRotationHelper;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

public class MeasureActivity extends AppCompatActivity {
    private Session session;
    private GestureDetector gestureDetector;

    private DisplayRotationHelper displayRotationHelper = null;

    private final int QUEUED_SIZE = 16;
    private final ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(QUEUED_SIZE);
    private final ArrayBlockingQueue<Float> queuedScrollDx = new ArrayBlockingQueue<>(QUEUED_SIZE);
    private final ArrayBlockingQueue<Float> queuedScrollDy = new ArrayBlockingQueue<>(QUEUED_SIZE);
    private final GestureDetector.SimpleOnGestureListener gestureDetectorListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Queue tap if there is space. Tap is lost if queue is full.
            queuedSingleTaps.offer(e);
//            log(TAG, "onSingleTapUp, e=" + e.getRawX() + ", " + e.getRawY());
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            queuedScrollDx.offer(distanceX);
            queuedScrollDy.offer(distanceY);
            return true;
        }
    };

    private int currentSelected;
    private GlSurfaceRenderer glSerfaceRenderer;

    private final ArrayList<Anchor> anchors = new ArrayList<>();
    private GlSurfaceRenderer.RenderListener renderListener = new GlSurfaceRenderer.RenderListener() {

        public final void drawCube(int index, MotionEvent lastTap, GlSurfaceRenderer renderer) {
            renderer.drawCube((Anchor)MeasureActivity.this.anchors.get(index));
            if (lastTap != null) {
                if (renderer.isHitObject(lastTap)) {
                    currentSelected = index;
                    queuedSingleTaps.poll();
                }
            }

        }

        @Override
        public void onFrame(GlSurfaceRenderer renderer, Frame frame, Camera camera, int viewWidth, int viewHeight) {
            if (MeasureActivity.this.anchors.size() < 1) {
                MeasureActivity.this.showResult("");
            } else {
                Object var10001 = MeasureActivity.this.anchors.get(MeasureActivity.this.currentSelected);
                renderer.drawSelectedCube((Anchor)var10001);
                StringBuilder sb = new StringBuilder();
                double total = 0.0D;
                Pose point1;
                Object var10000 = MeasureActivity.this.anchors.get(0);
                Pose point0 = ((Anchor)MeasureActivity.this.anchors.get(0)).getPose();
                MotionEvent lastTap = (MotionEvent)MeasureActivity.this.queuedSingleTaps.peek();
                this.drawCube(0, lastTap, renderer);
                int i = 1;

                for(int var13 = MeasureActivity.this.anchors.size(); i < var13; ++i) {
                    point1 = ((Anchor)MeasureActivity.this.anchors.get(i)).getPose();
//                    Logger.INSTANCE.log("onDrawFrame()", "before drawObj()");
                    this.drawCube(i, lastTap, renderer);
//                    Logger.INSTANCE.log("onDrawFrame()", "before drawLine()");
                    renderer.drawLine(point0, point1);
                    float distanceCm = (float)((int)(MeasureActivity.this.getDistance(point0, point1) * (double)1000)) / 10.0F;
                    total += (double)distanceCm;
                    sb.append(" + ").append(distanceCm);
                    point0 = point1;
                }

                showResult(
                        sb.toString().replaceFirst(
                                "[+]",
                                ""
                        ) + " = " + (float)((int)(total * (double)10.0F)) / 10f + "cm"
                );
            }

            // check if there is any touch event
            MotionEvent var25 = MeasureActivity.this.queuedSingleTaps.poll();
            if (var25 != null) {
                MotionEvent var15 = var25;
                boolean var8 = false;
                if (camera.getTrackingState() == TrackingState.TRACKING) {
                    Iterator var16 = frame.hitTest(var15).iterator();

                    HitResult hit;
                    Trackable trackable;
                    do {
                        if (!var16.hasNext()) {
                            return;
                        }

                        hit = (HitResult)var16.next();
                        trackable = hit.getTrackable();
                    } while((!(trackable instanceof Plane) || !((Plane)trackable).isPoseInPolygon(hit.getHitPose())) && (!(trackable instanceof Point) || ((Point)trackable).getOrientationMode() != Point.OrientationMode.ESTIMATED_SURFACE_NORMAL));

                    if (MeasureActivity.this.anchors.size() >= 16) {
                        ((Anchor)MeasureActivity.this.anchors.get(0)).detach();
                        MeasureActivity.this.anchors.remove(0);
                    }

                    MeasureActivity.this.anchors.add(hit.createAnchor());
                }
            }
        }
    };

    private final double getDistance(Pose pose0, Pose pose1) {
        float dx = pose0.tx() - pose1.tx();
        float dy = pose0.ty() - pose1.ty();
        float dz = pose0.tz() - pose1.tz();
        double var6 = (double)(dx * dx + dz * dz + dy * dy);
        return Math.sqrt(var6);
    }

    private final void showResult(final String result) {
        this.runOnUiThread((Runnable)(new Runnable() {
            public final void run() {
                TextView var10000 = (TextView)MeasureActivity.this.findViewById(R.id.tv_result);
                var10000.setText((CharSequence)result);
            }
        }));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        Logger.logStatus("onResume()");
        try {
            initiate();
        } catch (UnavailableDeviceNotCompatibleException e) {
            e.printStackTrace();
        } catch (UnavailableSdkTooOldException e) {
            e.printStackTrace();
        } catch (UnavailableArcoreNotInstalledException e) {
            e.printStackTrace();
        } catch (UnavailableApkTooOldException e) {
            e.printStackTrace();
        }
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        GLSurfaceView surfaceView = this.findViewById(R.id.surfaceView);
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        Logger.logStatus("onPause()");
        session.pause();
        GLSurfaceView surfaceView = this.findViewById(R.id.surfaceView);
        surfaceView.onResume();
        displayRotationHelper.onPause();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
//        Logger.INSTANCE.logStatus("onWindowFocusChanged()");
        if (hasFocus) {
            this.getWindow().getDecorView().setSystemUiVisibility(5894);
            this.getWindow().addFlags(128);
        }

    }

    private final void initiate() throws UnavailableDeviceNotCompatibleException, UnavailableSdkTooOldException, UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        DisplayRotationHelper rotationHelper = new DisplayRotationHelper(this);
//
        // session
        Session arcoreSession = new Session(this);
        Config config = new Config(arcoreSession);
        arcoreSession.configure(config);
        this.session = arcoreSession;

        // renderer & surfaceview
        if (gestureDetector == null) {

            glSerfaceRenderer = new GlSurfaceRenderer(this, arcoreSession, rotationHelper, this.renderListener);
            gestureDetector = new GestureDetector(this, this.gestureDetectorListener);
            GLSurfaceView var10000 = this.findViewById(R.id.surfaceView);
            if (var10000 != null) {
                Log.e("TAG", "azzz");
                var10000.setOnTouchListener(new MeasureActivity$initiate$$inlined$apply$lambda$1(this));
                var10000.setPreserveEGLContextOnPause(true);
                var10000.setEGLContextClientVersion(2);
                var10000.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
                var10000.setRenderer((GLSurfaceView.Renderer)this.glSerfaceRenderer);
                var10000.setRenderMode(1);
            }
        }

        this.displayRotationHelper = rotationHelper;
    }

    // $FF: synthetic method
    public static final GestureDetector access$getGestureDetector$p(MeasureActivity $this) {
        return $this.gestureDetector;
    }

    static class MeasureActivity$initiate$$inlined$apply$lambda$1 implements View.OnTouchListener {
        // $FF: synthetic field
        final MeasureActivity this$0;

        MeasureActivity$initiate$$inlined$apply$lambda$1(MeasureActivity var1) {
            this.this$0 = var1;
        }

        public final boolean onTouch(View v, MotionEvent event) {
            GestureDetector var10000 = MeasureActivity.access$getGestureDetector$p(this.this$0);
            return var10000 != null && var10000.onTouchEvent(event);
        }
    }
}

