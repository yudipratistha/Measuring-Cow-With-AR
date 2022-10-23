package com.measuring_cow_with_ar;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import com.arcore.DisplayRotationHelper;
import com.arcore.rendering.BackgroundRenderer;
import com.arcore.rendering.ObjectRenderer;
import com.arcore.rendering.PlaneRenderer;
import com.arcore.rendering.PointCloudRenderer;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.measuring_cow_with_ar.renderer.RectanglePolygonRenderer;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlSurfaceRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private final Session session;
    private final DisplayRotationHelper displayRotationHelper;
    private final RenderListener listener;

    private final BackgroundRenderer backgroundRenderer;
    private final PlaneRenderer planeRenderer;
    private final PointCloudRenderer pointCloud;

    private final ObjectRenderer cube;
    private final ObjectRenderer cubeSelected;
    private RectanglePolygonRenderer rectRenderer;

    private int viewWidth = 0;
    private int viewHeight = 0;
    // according to cube.obj, cube diameter = 0.02f
    private final float cubeHitAreaRadius;
    private final float[] centerVertexOfCube;
    private final float[] vertexResult;

    private float[] tempTranslation = new float[3];
    private float[] tempRotation = new float[4];
    private float[] projmtx = new float[16];
    private float[] viewmtx = new float[16];

//    public void GLSurfaceRenderer(Context context){
//        this.context = context;
//    }

    public GlSurfaceRenderer(Context context, Session session, DisplayRotationHelper displayRotationHelper, GlSurfaceRenderer.RenderListener listener) {
        this.context = context;
        this.session = session;
        this.displayRotationHelper = displayRotationHelper;
        this.listener = listener;
        this.backgroundRenderer = new BackgroundRenderer();
        this.planeRenderer = new PlaneRenderer();
        this.pointCloud = new PointCloudRenderer();
        this.cube = new ObjectRenderer();
        this.cubeSelected = new ObjectRenderer();
        this.cubeHitAreaRadius = 0.08F;
        this.centerVertexOfCube = new float[]{0.0F, 0.0F, 0.0F, 1.0F};
        this.vertexResult = new float[4];
        this.projmtx = new float[16];
        this.viewmtx = new float[16];
        this.anchorMatrix = new float[16];
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//        logStatus("onSurfaceCreated()");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        this.backgroundRenderer.createOnGlThread(context);
        if (session != null) {
            session.setCameraTextureName(this.backgroundRenderer.getTextureId());
        }

        // Prepare the other rendering objects.
        try {
            rectRenderer = new RectanglePolygonRenderer();
            cube.createOnGlThread(context, "cube.obj", "cube_green.png");
            cube.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
            cubeSelected.createOnGlThread(context,"cube.obj", "cube_cyan.png");
            cubeSelected.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
        } catch (IOException e) {
//            log(TAG, "Failed to read obj file");
        }
        try {
            planeRenderer.createOnGlThread(context, "trigrid.png");
        } catch (IOException e) {
//            log(TAG, "Failed to read plane texture");
        }
        pointCloud.createOnGlThread(context);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if(width <= 0 || height <= 0){
//            logStatus("onSurfaceChanged(), <= 0");
            return;
        }
//        logStatus("onSurfaceChanged()");

        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
//            log(TAG, "onDrawFrame(), mTouches.size=" + mTouches.size());
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (viewWidth == 0 || viewWidth == 0) {
            return;
        }
        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(this.backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();
            // Draw background.
            this.backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

            // Visualize tracked points.
            PointCloud pointCloud = frame.acquirePointCloud();
            this.pointCloud.update(pointCloud);
            this.pointCloud.draw(viewmtx, projmtx);

            // Application is responsible for releasing the point cloud resources after
            // using it.
            pointCloud.release();

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            listener.onFrame(this, frame, camera, viewWidth, viewHeight);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private final Float lightIntensity = 0f;

    private final float[] anchorMatrix;

    private void drawObject(Anchor anchor, ObjectRenderer objectRenderer) {
        anchor.getPose().toMatrix(anchorMatrix, 0);
        objectRenderer.updateModelMatrix(anchorMatrix, 1f);
        objectRenderer.draw(viewmtx, projmtx, lightIntensity);
    }

    public final void drawCube(Anchor anchor){
        drawObject(anchor, cube);
    };


    public final void drawSelectedCube(Anchor anchor){
        drawObject(anchor, cubeSelected);
    }

    public final void drawLine(Pose pose0, Pose pose1) {
        float lineWidth = 0.002F;
        float lineWidthH = lineWidth / (float)this.viewHeight * (float)this.viewWidth;
//        RectanglePolygonRenderer var10000 = this.rectRenderer;
        if (rectRenderer != null) {
            rectRenderer.setVerts(
                    pose0.tx() - lineWidth, pose0.ty() + lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() + lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() + lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() + lineWidthH, pose1.tz() - lineWidth,
                    pose0.tx() - lineWidth, pose0.ty() - lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() - lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() - lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() - lineWidthH, pose1.tz() - lineWidth
            );
        }

//        var10000 = this.rectRenderer;
        if (rectRenderer != null) {
            rectRenderer.draw(this.viewmtx, this.projmtx);
        }
    }

    public final boolean isHitObject(MotionEvent motionEvent) {
        return isMVPMatrixHitMotionEvent(cubeSelected.getModelViewProjectionMatrix(), motionEvent)
                || isMVPMatrixHitMotionEvent(cube.getModelViewProjectionMatrix(), motionEvent);
    }

    private final boolean isMVPMatrixHitMotionEvent(float[] ModelViewProjectionMatrix, MotionEvent event) {
        if (event == null) {
            return false;
        } else {
            Matrix.multiplyMV(this.vertexResult, 0, ModelViewProjectionMatrix, 0, this.centerVertexOfCube, 0);
            /**
             * vertexResult = [x, y, z, w]
             *
             * coordinates in View
             * ┌─────────────────────────────────────────┐╮
             * │[0, 0]                     [viewWidth, 0]│
             * │       [viewWidth/2, viewHeight/2]       │view height
             * │[0, viewHeight]   [viewWidth, viewHeight]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             *
             * coordinates in GLSurfaceView frame
             * ┌─────────────────────────────────────────┐╮
             * │[-1.0,  1.0]                  [1.0,  1.0]│
             * │                 [0, 0]                  │view height
             * │[-1.0, -1.0]                  [1.0, -1.0]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             */
            // circle hit test
            float radius = (float)(this.viewWidth / 2) * (this.cubeHitAreaRadius / this.vertexResult[3]);
            float dx = event.getX() - (float)(this.viewWidth / 2) * ((float)1 + this.vertexResult[0] / this.vertexResult[3]);
            float dy = event.getY() - (float)(this.viewHeight / 2) * ((float)1 - this.vertexResult[1] / this.vertexResult[3]);
            double distance = Math.sqrt((double)(dx * dx + dy * dy));
            //            // for debug
//            overlayViewForTest.setPoint("cubeCenter", screenX, screenY);
//            overlayViewForTest.postInvalidate();
            return distance < (double)radius;
        }
    }

    interface RenderListener {
        void onFrame(GlSurfaceRenderer renderer, Frame frame, Camera camera, int viewWidth, int viewHeight);
    }
}
