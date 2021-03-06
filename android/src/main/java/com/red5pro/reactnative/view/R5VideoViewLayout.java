package com.red5pro.reactnative.view;

import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.red5pro.streaming.R5Connection;
import com.red5pro.streaming.R5Stream;
import com.red5pro.streaming.config.R5Configuration;
import com.red5pro.streaming.event.R5ConnectionEvent;
import com.red5pro.streaming.event.R5ConnectionListener;
import com.red5pro.streaming.media.R5AudioController;
import com.red5pro.streaming.source.R5AdaptiveBitrateController;
import com.red5pro.streaming.source.R5Camera;
import com.red5pro.streaming.source.R5Microphone;
import com.red5pro.streaming.view.R5VideoView;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by kylekellogg on 9/11/17.
 */

public class R5VideoViewLayout extends R5VideoView implements R5ConnectionListener, LifecycleEventListener {

    public int logLevel;
    public int scaleMode;
    public boolean showDebug;

    protected String mStreamName;
    protected boolean mIsPublisher;
    protected boolean mIsStreaming;
    protected R5VideoView mVideoView;

    protected ThemedReactContext mContext;
    protected RCTEventEmitter mEventEmitter;
    protected R5Connection mConnection;
    protected R5Stream mStream;
    protected R5Camera mCamera;

    protected boolean mUseVideo = true;
    protected boolean mUseAudio = true;
    protected int mCameraWidth = 640;
    protected int mCameraHeight = 360;
    protected int mBitrate = 750;
    protected int mFramerate = 15;
    protected int mAudioMode = 0;
    protected int mAudioBitrate = 32;
    protected int mAudioSampleRate = 44100;
    protected boolean mUseAdaptiveBitrateController = false;
    protected boolean mUseBackfacingCamera = false;

    protected int mCameraOrientation;
    protected int mDisplayOrientation;
    protected boolean mOrientationDirty;
    protected int mOrigCamOrientation = 0;
    protected View.OnLayoutChangeListener mLayoutListener;

    public enum Events {

        CONFIGURED("onConfigured"),
        METADATA("onMetaDataEvent"),
        PUBLISHER_STATUS("onPublisherStreamStatus"),
        SUBSCRIBER_STATUS("onSubscriberStreamStatus"),
        UNPUBLISH_NOTIFICATION("onUnpublishNotification"),
        UNSUBSCRIBE_NOTIFICATION("onUnsubscribeNotification");

        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }

    }

    public enum Commands {

        SUBSCRIBE("subscribe", 1),
        PUBLISH("publish", 2),
        UNSUBSCRIBE("unsubscribe", 3),
        UNPUBLISH("unpublish", 4),
        SWAP_CAMERA("swapCamera", 5),
        UPDATE_SCALE_MODE("updateScaleMode", 6);

        private final String mName;
        private final int mValue;

        Commands(final  String name, final int value) {
            mName = name;
            mValue = value;
        }

        public final int getValue() {
            return mValue;
        }

        @Override
        public String toString() {
            return mName;
        }

    }

    R5VideoViewLayout(ThemedReactContext context) {

        super(context);

        mContext = context;
        mEventEmitter = mContext.getJSModule(RCTEventEmitter.class);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mContext.addLifecycleEventListener(this);
        mVideoView = this;

    }

    public void loadConfiguration(R5Configuration configuration, String forKey) {

        R5AudioController.mode = mAudioMode == 1
                ? R5AudioController.PlaybackMode.STANDARD
                : R5AudioController.PlaybackMode.AEC;

        mConnection = new R5Connection(configuration);
        mStream = new R5Stream(mConnection);

        mStream.setListener(this);
        mStream.client = this;

        mStream.setLogLevel(logLevel);
        mStream.setScaleMode(scaleMode);

        onConfigured(forKey);

    }

    public void subscribe (String streamName) {

        mStreamName = streamName;

        mVideoView.attachStream(mStream);
        mVideoView.showDebugView(showDebug);
        mStream.play(streamName);

    }

    public void unsubscribe () {

        if (mStream != null && mIsStreaming) {
            mStream.stop();
        }
        else {
            WritableMap map = Arguments.createMap();
            mEventEmitter.receiveEvent(this.getId(), Events.UNPUBLISH_NOTIFICATION.toString(), map);
            cleanup();
        }

    }

    public void publish (String streamName, R5Stream.RecordType streamType) {

        mStreamName = streamName;
        mIsPublisher = true;
        if (mLayoutListener == null) {
            mLayoutListener = setUpOrientationListener();
        }

        R5Camera camera = null;
        // Establish Camera if requested.
        if (mUseVideo) {

            Camera device = mUseBackfacingCamera
                    ? openBackFacingCameraGingerbread()
                    : openFrontFacingCameraGingerbread();

            updateDeviceOrientationOnLayoutChange();
            int rotate = mUseBackfacingCamera ? 0 : 180;
            device.setDisplayOrientation((mCameraOrientation + rotate) % 360);

            SurfaceView v = new SurfaceView(mContext);
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mStream.setView(v);
            addView(v);

            camera = new R5Camera(device, mCameraWidth, mCameraHeight);
            camera.setBitrate(mBitrate);
            camera.setOrientation(mCameraOrientation);
            camera.setFramerate(mFramerate);

            mCamera = camera;

        }

        // Assign ABR Controller if requested.
        if (mUseAdaptiveBitrateController) {
            R5AdaptiveBitrateController adaptor = new R5AdaptiveBitrateController();
            adaptor.AttachStream(mStream);
        }
        // Establish Microphone if requested.
        if (mUseAudio) {

            R5Microphone mic = new R5Microphone();
            mStream.attachMic(mic);
            mic.setBitRate(mAudioBitrate);
            mStream.audioController.sampleRate = mAudioSampleRate;
            // e.g., ->
            // This is required to be 8000 in order for 2-Way to work.
//          mStream.audioController.sampleRate = 8000;

        }

        mVideoView.attachStream(mStream);

        if (mCamera != null && mUseVideo) {
            mStream.attachCamera(camera);
        }

        mVideoView.showDebugView(showDebug);
        mStream.publish(streamName, streamType);

        if (mCamera != null && mUseVideo) {
            mCamera.getCamera().startPreview();
        }

    }

    public void unpublish () {

        if (mStream != null && mIsStreaming) {
            if(mStream.getVideoSource() != null) {
                Camera c = mCamera.getCamera();
                c.stopPreview();
                c.release();
            }
            mStream.stop();
        }
        else {
            WritableMap map = Arguments.createMap();
            mEventEmitter.receiveEvent(this.getId(), Events.UNPUBLISH_NOTIFICATION.toString(), map);
            cleanup();
        }

    }

    public void swapCamera () {

        if (!mIsPublisher) {
            return;
        }

        Camera updatedCamera;

        // NOTE: Some devices will throw errors if you have a camera open when you attempt to open another
        mCamera.getCamera().stopPreview();
        mCamera.getCamera().release();

        // NOTE: The front facing camera needs to be 180 degrees further rotated than the back facing camera
        int rotate = 0;
        if (!mUseBackfacingCamera) {
            updatedCamera = openBackFacingCameraGingerbread();
            rotate = 0;
        }
        else {
            updatedCamera = openFrontFacingCameraGingerbread();
            rotate = 180;
        }

        if(updatedCamera != null) {

            updatedCamera.setDisplayOrientation((mCameraOrientation + rotate) % 360);
            mCamera.setCamera(updatedCamera);
            mCamera.setOrientation(mCameraOrientation);

            updatedCamera.startPreview();
            mUseBackfacingCamera = !mUseBackfacingCamera;
            mStream.updateStreamMeta();
        }

    }

    protected void cleanup() {

        if (mStream != null) {
            mStream.setListener(null);
            mStream = null;
        }

        if (mConnection != null) {
            mConnection.removeListener();
            mConnection = null;
        }
        mIsStreaming = false;

    }

    protected View.OnLayoutChangeListener setUpOrientationListener() {
        return new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                                       int oldTop, int oldRight, int oldBottom) {
                if (mOrientationDirty) {
                    reorient();
                }
            }
        };
    }

    protected void reorient() {

        if (mCamera != null) {
            int rotate = mUseBackfacingCamera ? 0 : 180;
            int displayOrientation = (mDisplayOrientation + rotate) % 360;
            mCamera.setOrientation(mCameraOrientation);
            mCamera.getCamera().setDisplayOrientation(displayOrientation);
            mStream.updateStreamMeta();
        }
        mOrientationDirty = false;

    }

    protected void updateDeviceOrientationOnLayoutChange() {

        int degrees = 0;
        int rotation = mContext.getCurrentActivity().getWindowManager().getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 270; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 90; break;
        }

        mDisplayOrientation = (mOrigCamOrientation + degrees) % 360;
        mCameraOrientation = rotation % 2 != 0 ? mDisplayOrientation - 180 : mDisplayOrientation;
        if (mUseBackfacingCamera && (rotation % 2 != 0)) {
            mCameraOrientation += 180;
        }
        mOrientationDirty = true;

    }

    protected void applyDeviceRotation () {

        int degrees = 0;
        int rotation = mContext.getCurrentActivity().getWindowManager().getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        mCameraOrientation += degrees;
        mCameraOrientation = mCameraOrientation % 360;
        mOrigCamOrientation = mCameraOrientation;

    }

    protected void applyInverseDeviceRotation(){

        int degrees = 0;
        int rotation = mContext.getCurrentActivity().getWindowManager().getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 270; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 90; break;
        }

        mCameraOrientation += degrees;
        mCameraOrientation = mCameraOrientation % 360;
        mOrigCamOrientation = mCameraOrientation;

    }

    protected Camera openFrontFacingCameraGingerbread() {

        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx);
                    mCameraOrientation = cameraInfo.orientation;
                    applyDeviceRotation();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        return cam;

    }

    protected Camera openBackFacingCameraGingerbread() {

        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    cam = Camera.open(camIdx);
                    mCameraOrientation = cameraInfo.orientation;
                    applyInverseDeviceRotation();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        return cam;

    }

    protected void onConfigured(String key) {
        WritableMap map = new WritableNativeMap();
        map.putString("key", key);
        mEventEmitter.receiveEvent(this.getId(), "onConfigured", map);
    }

    protected void updateOrientation(int value) {
        // subscriber only.
        value += 90;
        this.getVideoView().setStreamRotation(value);
    }

    public void onMetaData(String metadata) {

        String[] props = metadata.split(";");
        for (String s : props) {
            String[] kv = s.split("=");
            if (kv[0].equalsIgnoreCase("orientation")) {
                updateOrientation(Integer.parseInt(kv[1]));
            }
        }
        WritableMap map = new WritableNativeMap();
        map.putString("metadata", metadata);
        mEventEmitter.receiveEvent(this.getId(), Events.METADATA.toString(), map);

    }

    @Override
    public void onConnectionEvent(R5ConnectionEvent event) {

        Log.d("R5VideoViewLayout", ":onConnectionEvent " + event.name());
        WritableMap map = new WritableNativeMap();
        WritableMap statusMap = new WritableNativeMap();
        statusMap.putInt("code", event.value());
        statusMap.putString("message", event.message);
        statusMap.putString("name", event.name());
        statusMap.putString("streamName", mStreamName);
        map.putMap("status", statusMap);
        if (mIsPublisher) {
            mEventEmitter.receiveEvent(this.getId(), Events.PUBLISHER_STATUS.toString(), map);
        }
        else {
            mEventEmitter.receiveEvent(this.getId(), Events.SUBSCRIBER_STATUS.toString(), map);
        }

        if (event == R5ConnectionEvent.START_STREAMING) {
            mIsStreaming = true;
        }
        else if (event == R5ConnectionEvent.DISCONNECTED && mIsStreaming) {
            WritableMap evt = new WritableNativeMap();
            if (mIsPublisher) {
                mEventEmitter.receiveEvent(this.getId(), Events.UNPUBLISH_NOTIFICATION.toString(), evt);
            }
            else {
                mEventEmitter.receiveEvent(this.getId(), Events.UNSUBSCRIBE_NOTIFICATION.toString(), evt);
            }
            cleanup();
            mIsStreaming = false;
        }

    }

    @Override
    public void onHostResume() {
        Activity activity = mContext.getCurrentActivity();
        if (mLayoutListener == null) {
            mLayoutListener = setUpOrientationListener();
        }
        this.addOnLayoutChangeListener(mLayoutListener);
    }

    @Override
    public void onHostPause() {
        if (mLayoutListener != null) {
            this.removeOnLayoutChangeListener(mLayoutListener);
        }
    }

    @Override
    public void onHostDestroy() {
        //Log.d("R5VideoViewLayout", "onHostDestroy");
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        updateDeviceOrientationOnLayoutChange();
    }

    public void updateShowDebug(boolean show) {
        this.showDebug = show;
        if (this.getVideoView() != null) {
            this.getVideoView().showDebugView(this.showDebug);
        }
    }

    public void updateScaleMode(int mode) {
        this.scaleMode = mode;
        if (mStream != null) {
            mStream.setScaleMode(mode);
        }
    }

    public void updateLogLevel(int level) {
        this.logLevel = level;
        if (mStream != null) {
            mStream.setLogLevel(level);
        }
    }

    public void updatePublishVideo(boolean useVideo) {
        this.mUseVideo = useVideo;
    }

    public void updatePublishAudio(boolean useAudio) {
        this.mUseAudio = useAudio;
    }

    public void updateCameraWidth(int value) {
        this.mCameraWidth = value;
    }

    public void updateCameraHeight(int value) {
        this.mCameraHeight = value;
    }

    public void updatePublishBitrate(int value) {
        this.mBitrate = value;
    }

    public void updatePublishFramerate(int value) {
        this.mFramerate = value;
    }

    public void updateSubscriberAudioMode(int value) {
        this.mAudioMode = value;
    }

    public void updatePublishAudioBitrate(int value) {
        this.mAudioBitrate = value;
    }

    public void updatePublishAudioSampleRate(int value) {
        this.mAudioSampleRate = value;
    }

    public void updatePublisherUseAdaptiveBitrateController(boolean value) {
        this.mUseAdaptiveBitrateController = value;
    }

    public void updatePublisherUseBackfacingCamera(boolean value) {
        this.mUseBackfacingCamera = value;
    }

    public R5VideoView getVideoView() {
        return mVideoView;
    }

    /*
     * [Red5Pro]
     *
     * Start silly hack of enforcing layout of underlying GLSurface for view.
     */
    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
            if (mOrientationDirty) {
                reorient();
            }
        }
    };
    /*
     * [/Red5Pro]
     */

}
