package com.red5pro.reactnative.view;

import android.app.Activity;
import android.util.Log;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.annotations.ReactProp;

import com.red5pro.streaming.R5Stream;
import com.red5pro.streaming.R5StreamProtocol;
import com.red5pro.streaming.config.R5Configuration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Created by kylekellogg on 9/11/17.
 */

public class R5VideoViewManager extends SimpleViewManager<R5VideoViewLayout> {

    private static final String REACT_CLASS = "R5VideoView";

    private static final String PROP_HOST = "host";
    private static final String PROP_PORT = "port";
    private static final String PROP_CONTEXT_NAME = "contextName";
    private static final String PROP_STREAM_NAME = "streamName";
    private static final String PROP_BUFFER_TIME = "bufferTime";
    private static final String PROP_LICENSE_KEY = "licenseKey";
    private static final String PROP_BUNDLE_ID = "bundleID";
    private static final String PROP_PARAMETERS = "parameters";
    private static final String PROP_STREAM_BUFFER_TIME = "streamBufferTime";

    private static final int COMMAND_SUBSCRIBE = 1;
    private static final int COMMAND_PUBLISH = 2;
    private static final int COMMAND_UNSUBSCRIBE = 3;
    private static final int COMMAND_UNPUBLISH = 4;
    private static final int COMMAND_SWAP_CAMERA = 5;
    private static final int COMMAND_UPDATE_SCALE_MODE = 6;

    private int logLevel = R5Stream.LOG_LEVEL_ERROR;
    private boolean showDebug = false;

    private AtomicBoolean isConfigured = new AtomicBoolean(false);
    private AtomicBoolean isAttached = new AtomicBoolean(false);

    private R5VideoViewLayout mView;

    private ThemedReactContext mContext;

    public R5VideoViewManager() {
        super();
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected R5VideoViewLayout createViewInstance(ThemedReactContext reactContext) {

        mContext = reactContext;
        mView = new R5VideoViewLayout(reactContext);
        return mView;

    }

    @Override
    public void receiveCommand(final R5VideoViewLayout root, int commandId, @Nullable ReadableArray args) {
        if (args != null) {
            Log.d("R5VideoViewManager", "Args are " + args.toString());
        }

        switch (commandId) {
            case COMMAND_SUBSCRIBE:

                int w = mView.getWidth();
                int h = mView.getHeight();

                final String streamName = args.getString(0);
                mView.subscribe(streamName);

                break;
            case COMMAND_PUBLISH:

                final int type = args.getInt(1);
                final String name = args.getString(0);
                R5Stream.RecordType recordType = R5Stream.RecordType.Live;
                if (type == 1) {
                    recordType = R5Stream.RecordType.Record;
                }
                else if (type == 2) {
                    recordType = R5Stream.RecordType.Append;
                }
                mView.publish(name, recordType);

                break;
            case COMMAND_UNSUBSCRIBE:

                mView.unsubscribe();

                break;
            case COMMAND_UNPUBLISH:

                mView.unpublish();

                break;
            case COMMAND_SWAP_CAMERA:

                mView.swapCamera();

                break;
            case COMMAND_UPDATE_SCALE_MODE:

                final int mode = args.getInt(0);
                mView.updateScaleMode(mode);

                break;
            default:
                super.receiveCommand(root, commandId, args);
                break;
        }
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        MapBuilder.Builder<String, Integer> builder = MapBuilder.builder();
        for (R5VideoViewLayout.Commands command : R5VideoViewLayout.Commands.values()) {
            builder.put(command.toString(), command.getValue());
        }
        return builder.build();
    }

    @Override
    @Nullable
    public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
        for (R5VideoViewLayout.Events event : R5VideoViewLayout.Events.values()) {
            builder.put(event.toString(), MapBuilder.of("registrationName", event.toString()));
        }
        return builder.build();
    }

    private R5Configuration createConfigurationFromMap(ReadableMap configuration) {

        boolean hasHost = configuration.hasKey(PROP_HOST);
        boolean hasPort = configuration.hasKey(PROP_PORT);
        boolean hasContextName = configuration.hasKey(PROP_CONTEXT_NAME);
        boolean hasStreamName = configuration.hasKey(PROP_STREAM_NAME);
        boolean hasBufferTime = configuration.hasKey(PROP_BUFFER_TIME);
        boolean hasStreamBufferTime = configuration.hasKey(PROP_STREAM_BUFFER_TIME);
        boolean hasBundleID = configuration.hasKey(PROP_BUNDLE_ID);
        boolean hasLicenseKey = configuration.hasKey(PROP_LICENSE_KEY);
        boolean hasParameters = configuration.hasKey(PROP_PARAMETERS);

        boolean hasRequired = hasHost && hasPort && hasContextName;

        if (!hasRequired) {
            return null;
        }

        R5StreamProtocol protocol = R5StreamProtocol.RTSP;
        String host = configuration.getString(PROP_HOST);
        int port = configuration.getInt(PROP_PORT);
        String contextName = configuration.getString(PROP_CONTEXT_NAME);
        String streamName = hasStreamName ? configuration.getString(PROP_STREAM_NAME) : "mystream";
        String bundleID = hasBundleID ? configuration.getString(PROP_BUNDLE_ID) : "com.red5pro.android";
        String licenseKey = hasLicenseKey ? configuration.getString(PROP_LICENSE_KEY) : "";
        float bufferTime = hasBufferTime ? (float) configuration.getDouble(PROP_BUFFER_TIME) : 1.0f;
        float streamBufferTime = hasStreamBufferTime ? (float) configuration.getDouble(PROP_STREAM_BUFFER_TIME) : 2.0f;
        String parameters = hasParameters ? configuration.getString(PROP_PARAMETERS) : "";

        R5Configuration config = new R5Configuration(protocol, host, port, contextName, bufferTime, parameters);

        config.setStreamBufferTime(streamBufferTime);
        config.setBundleID(bundleID);
        config.setStreamName(streamName);
        config.setLicenseKey(licenseKey);

        return config;

    }

    @ReactProp(name = "configuration")
    public void setConfiguration(R5VideoViewLayout view, ReadableMap configuration) {
        view.loadConfiguration(createConfigurationFromMap(configuration), configuration.getString("key"));
        isConfigured.set(true);
    }

    @ReactProp(name = "showDebugView", defaultBoolean = false)
    public void setShowDebugView(R5VideoViewLayout view, boolean showDebug) {
        view.updateShowDebug(showDebug);
    }

    @ReactProp(name = "scaleMode", defaultInt = 0) // 0, 1, 2
    public void setScaleMode(R5VideoViewLayout view, int mode) {
        view.updateScaleMode(mode);
    }

    @ReactProp(name = "logLevel", defaultInt = 3) // LOG_LEVEL_ERROR
    public void setLogLevel(R5VideoViewLayout view, int logLevel) {
        view.updateLogLevel(logLevel);
    }

    @ReactProp(name = "publishVideo", defaultBoolean = true)
    public  void setPublishVideo(R5VideoViewLayout view, boolean useVideo) {
        view.updatePublishVideo(useVideo);
    }

    @ReactProp(name = "publishAudio", defaultBoolean = true)
    public  void setPublishAudio(R5VideoViewLayout view, boolean useAudio) {
        view.updatePublishAudio(useAudio);
    }

    @ReactProp(name = "cameraWidth", defaultInt = 640)
    public void setCameraWidth(R5VideoViewLayout view, int value) {
        view.updateCameraWidth(value);
    }

    @ReactProp(name = "cameraHeight", defaultInt = 360)
    public void setCameraHeight(R5VideoViewLayout view, int value) {
        view.updateCameraHeight(value);
    }

    @ReactProp(name = "bitrate", defaultInt = 750)
    public void setBitrate(R5VideoViewLayout view, int value) {
        view.updatePublishBitrate(value);
    }

    @ReactProp(name = "framerate", defaultInt = 15)
    public void setFramerate(R5VideoViewLayout view, int value) {
        view.updatePublishFramerate(value);
    }

    @ReactProp(name = "audioBitrate", defaultInt = 32)
    public void setAudioBitrate(R5VideoViewLayout view, int value) {
        view.updatePublishAudioBitrate(value);
    }

    @ReactProp(name = "audioSampleRate", defaultInt = 44100)
    public void setAudioSampleRate(R5VideoViewLayout view, int value) {
        view.updatePublishAudioSampleRate(value);
    }

    /*
     *
        public static enum PlaybackMode {
            AEC,
            STANDARD;

            private PlaybackMode() {}
        }
     *
     */
    @ReactProp(name = "audioMode", defaultInt = 0)
    public void setSubscriberAudioMode(R5VideoViewLayout view, int value) {
        view.updateSubscriberAudioMode(value);
    }

    @ReactProp(name = "useAdaptiveBitrateController", defaultBoolean = false)
    public void setUseAdaptiveBitrateController(R5VideoViewLayout view, boolean value) {
        view.updatePublisherUseAdaptiveBitrateController(value);
    }

    @ReactProp(name = "useBackfacingCamera", defaultBoolean = false)
    public void setUseBackfacingCamera(R5VideoViewLayout view, boolean value) {
        view.updatePublisherUseBackfacingCamera(value);
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        return super.getConstants();
    }

    @Override
    public boolean hasConstants() {
        return super.hasConstants();
    }

}
