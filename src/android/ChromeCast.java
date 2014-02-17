/**
 *  ChromeCast cordova plugin (Android)
 *
 *  @author Hongbo LU
 *  @see http://sesamtv.com
 *  @license MIT <http://szanata.com/MIT.txt>
 *  @license GNU <http://szanata.com/GNU.txt>
 *
 *  chromecast plugin for cordova
 *
 */
package com.sesamtv.cordova.chromecast;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.HashMap;


import android.support.v7.app.MediaRouteActionProvider;
import android.text.TextUtils;
import com.google.android.gms.cast.*;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.net.Uri;
import android.widget.Toast;


import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class ChromeCast extends CordovaPlugin {
    //private final String APP_ID = "31a76198-2182-481a-a4a6-27d351872026";
    private String APP_ID = null;
    private static final String MSG_PREFIX = "urn:x-cast:";
    private String PACKAGE_NAME = "com.sesamtv.chromecast";
    private static final String TAG = "ChromeCastPlugin";


    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private Cast.Listener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private boolean mApplicationStarted;
    private boolean mWaitingForReconnect;
    private com.sesamtv.cordova.chromecast.MediaPlayer mMediaPlayer;
    private HashMap<String, CustomChannel> mChannels = new HashMap<String, CustomChannel>();
    private CallbackContext receiverCallback;
    private CallbackContext mediaStatusCallback;
    private CallbackContext setReceiverCallback;
    private CallbackContext onEndedCallback;


    private enum Actions {
        startReceiverListener, onMessage, sendMessage, setReceiver, loadMedia,
        pauseMedia, playMedia, seekBy, setVolume, setVolumeBy, setMuted, toggleMuted,
        getMediaStatus, stopApplication, startStatusListener, startOnEndedListener
    }

    public String getRawNSName(String ns) {
        return MSG_PREFIX + PACKAGE_NAME + "." + ns;
    }

    public String getNSName(String rawNs) {
        return rawNs.replace(MSG_PREFIX + PACKAGE_NAME + ".", "");
    }
    /*public ChromecastPlugin() {

    }*/

    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // Configure Cast device discovery


    }

    private void initRouters(CallbackContext callbackContext) {
        Log.d(TAG, "init routers...");

        mMediaRouter = MediaRouter.getInstance(cordova.getActivity().getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
                .build();
        mMediaRouterCallback = new MyMediaRouterCallback();

        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

        if (callbackContext != null) {
            callbackContext.success();
        }
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "APP ID: " + APP_ID);
        Log.d(TAG, "execute action : " + action);

        if (action.equals("setAppId")) {
            APP_ID = args.getString(0);
            if (args.getString(1) != null) {
                PACKAGE_NAME = args.getString(1);
            }
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        initRouters(callbackContext);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } else if (APP_ID == null) {
            callbackContext.error("APP_ID NOT FOUND");
        } else {
            /*if (castContext == null) {
                Log.d(TAG, "castContext is null, reinit routers...");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            initRouters(null);
                            execute(action, args, callbackContext);
                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
                return true;
            }*/
            switch (Actions.valueOf(action)) {
                case startOnEndedListener:
                    onEndedCallback = callbackContext;
                    break;
                case startReceiverListener:
                    startReceiverListenerAction(callbackContext);
                    break;
                case startStatusListener:
                    mediaStatusCallback = callbackContext;
                    callbackContext.sendPluginResult(getMediaStatus());
                    break;
                case setReceiver:
                    setReceiverAction(args, callbackContext);
                    break;
                case onMessage:
                    onCastMessage(args.getString(0), callbackContext);
                    break;
                case sendMessage:
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            try {
                                String channelName = args.getString(0);
                                String msg = args.getString(1);
                                sendMessage(channelName, msg, callbackContext);
                            } catch (Exception e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                    break;

                case loadMedia:
                    loadMediaAction(args.getJSONObject(0), callbackContext);
                    break;
                case pauseMedia:
                    pauseMediaAction(callbackContext);
                    break;
                case playMedia:
                    playMediaAction(callbackContext);
                    break;
                case seekBy:
                    try {
                        seekByAction(args.getLong(0));
                        callbackContext.success();
                    } catch (IOException e) {
                        callbackContext.error(e.getMessage());
                    }
                    break;
                case setVolume:
                    final double vol = args.getDouble(0);
                    Log.d(TAG, "setVolume " + vol);
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            setVolumeAction(vol, callbackContext);
                        }
                    });
                    break;
                case setVolumeBy:
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            try {
                                setVolumeByAction(args.getDouble(0), callbackContext);
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                case setMuted:
                    final boolean muted = args.getBoolean(0);
                    Log.d(TAG, "setMuted " + muted);
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            setDeviceMutedAction(muted, callbackContext);
                        }
                    });
                    break;
                case toggleMuted:
                    Log.d(TAG, "toggleMuted ");
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            toggleDeviceMutedAction(callbackContext);
                        }
                    });
                    break;
                case getMediaStatus:
                    Log.d(TAG, "getMediaStatus");
                    callbackContext.sendPluginResult(getMediaStatus());
                    break;
                case stopApplication:
                    try {
                        Log.d(TAG, "stopCast");
                        stopApplication();
                        callbackContext.success();
                    } catch (Exception e) {
                        callbackContext.error("stop cast failed :" + e.getMessage());
                        return false;
                    }
                    break;

                default:
                    callbackContext.error("Invalid action: " + action);

            }
        }

        return true;
    }

    private void startReceiverListenerAction(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                receiverCallback = callbackContext;
                JSONArray routeList = getRoutes();
                Log.d(TAG, "getting routers");
                PluginResult result = new PluginResult(PluginResult.Status.OK, routeList);
                result.setKeepCallback(true);
                receiverCallback.sendPluginResult(result);
            }
        });
    }

    private void setReceiverAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final int index = args.getInt(0);
        setReceiverCallback = callbackContext;
        try {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    final RouteInfo route = mMediaRouter.getRoutes().get(index);
                    Log.d(TAG, "route :" + index + " " + route.getId() +
                            " selected " + route.isSelected() + " playback type: " + route.getPlaybackType());

                    try {
                        if (route.isSelected()) {
                            mMediaRouter.updateSelectedRoute(mMediaRouteSelector);
                            mMediaRouter.selectRoute(route);

                        } else {
                            mMediaRouter.selectRoute(route);
                        }
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } catch (IndexOutOfBoundsException e) {
            callbackContext.error("Receiver not found");
        }
    }

    private void loadMediaAction(final JSONObject opt, final CallbackContext callbackContext) throws JSONException {
        try {
            Log.d(TAG, "load Media...");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        mMediaPlayer.playMedia(opt, callbackContext);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callbackContext.error("cast failed :" + e.getMessage());
        }
    }

    private void pauseMediaAction(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mMediaPlayer.pauseMedia(callbackContext);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void playMediaAction(final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "play ");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {

                mMediaPlayer.playMedia(callbackContext);
                callbackContext.success();

            }
        });
    }

    private void seekByAction(final long value) throws IOException {
        Log.d(TAG, "seekBy " + value);
        if (ensurePlayer()) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        mMediaPlayer.seekMediaBy(value);
                    } catch (Exception e) {
                        Log.d(TAG, "seekBy Error " + e.getMessage());
                    }
                }
            });
        }
    }

    private void setVolumeAction(double vol, CallbackContext callbackContext) {
        if (ensurePlayer(callbackContext)) {
            mMediaPlayer.setDeciveVolume(vol);
            callbackContext.success();
        }
    }

    private void setVolumeByAction(double value, CallbackContext callbackContext) {
        if (ensurePlayer(callbackContext)) {
            mMediaPlayer.setDeviceVolumeBy(value);
            callbackContext.success();
        }
    }

    private void setDeviceMutedAction(boolean muted, CallbackContext callbackContext) {
        if (ensurePlayer(callbackContext)) {
            mMediaPlayer.setDeviceMuted(muted);
            callbackContext.success();
        }
    }

    private void toggleDeviceMutedAction(CallbackContext callbackContext) {
        if (ensurePlayer(callbackContext)) {
            mMediaPlayer.toggleDeviceMuted();
            callbackContext.success();
        }
    }


    private boolean ensurePlayer(CallbackContext... params) {
        int l = params.length;
        CallbackContext callbackContext = null;
        if (l == 1) {
            callbackContext = params[0];
        }
        boolean flag = true;
        String reason = null;
        if (mMediaPlayer == null) {
            reason = "MEDIAPLAYER_NOT_FOUND";
            flag = false;
        }
        if (!flag && callbackContext != null) {
            callbackContext.error(reason);
        }
        return flag;
    }


    private void onCastMessage(final String channelName, final CallbackContext callbackContext) {
        if (mApplicationStarted) {
            mChannels.put(channelName, new CustomChannel(channelName, callbackContext));
        } else {
            Log.w(TAG, "add cast message failed: application is not yet ready");
        }
    }

    private void addChannels() {
        for (Map.Entry<String, CustomChannel> entry : mChannels.entrySet()) {
            //String key = entry.getKey();
            CustomChannel value = entry.getValue();
            try {
                Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                        value.getNamespace(),
                        value);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private void removeChannels() {
        for (Map.Entry<String, CustomChannel> entry : mChannels.entrySet()) {
            CustomChannel value = entry.getValue();
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient,
                        value.getNamespace());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        mChannels.clear();
    }


    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.d(TAG, "resuming a session");
        // Start media router discovery
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);


    }


    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "on pause");
        if (cordova.getActivity().isFinishing()) {
            // End media router discovery
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }

        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    public void onMediaStatusCallback(JSONObject status) {
        if (mediaStatusCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
            result.setKeepCallback(true);
            mediaStatusCallback.sendPluginResult(result);
        }
    }

    private void onReceiverListChanged() {
        if (receiverCallback != null) {
            JSONArray jsonRoute = getRoutes();
            PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
            result.setKeepCallback(true);
            receiverCallback.sendPluginResult(result);
        }
    }

    private void onSessionStarted() {
        if (setReceiverCallback != null) {
            setReceiverCallback.success();
            setReceiverCallback = null;
        }
    }

    private void onSessionEnded() {
        if (onEndedCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, new JSONObject());
            result.setKeepCallback(true);
            onEndedCallback.sendPluginResult(result);
        }
    }

    private JSONArray getRoutes() {
        JSONArray routeList = new JSONArray();
        List<RouteInfo> routesInMedia = mMediaRouter.getRoutes();
        final int l = routesInMedia.size();
        if (l == 0) {
            return routeList;
        }
        try {
            for (int i = 0; i < l; i++) {
                RouteInfo rInfo = routesInMedia.get(i);
                JSONObject jsonRoute = new JSONObject();
                jsonRoute.put("id", rInfo.getId());
                jsonRoute.put("name", rInfo.getName());
                jsonRoute.put("description", rInfo.getDescription());
                jsonRoute.put("isSelected", rInfo.isSelected());
                jsonRoute.put("index", i);
                /*if (mSelectedDevice != null) {
                    jsonRoute.put("ipAddress", mSelectedDevice.getIpAddress());
                }*/
                routeList.put(jsonRoute);
            }
        } catch (JSONException e) {
            Log.d(TAG, "failed to get routes list");
        }
        return routeList;
    }

    public PluginResult getMediaStatus() {
        JSONObject status = new JSONObject();
        try {
            if (mMediaPlayer != null) {
                status = mMediaPlayer.getMediaStatus();
            } else {
                status.put("state", "");
                status.put("position", 0.0);
                status.put("duration", 0.0);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
        result.setKeepCallback(true);
        return result;
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
                .getActionProvider(mediaRouteMenuItem);
        // Set the MediaRouteActionProvider selector for device discovery.
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }*/


    /**
     * Callback for MediaRouter events
     */
    private class MyMediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            super.onRouteAdded(router, route);
            Log.d(TAG, "on route added");
            Log.d(TAG, "route added :" + route.getId() + ":" + route.getName() + ":" + route.getDescription());
            onReceiverListChanged();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            super.onRouteRemoved(router, route);
            onReceiverListChanged();
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteSelected");
            // Handle the user route selection.
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteUnselected: info=" + info);
            teardown();
            mSelectedDevice = null;
        }
    }

    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                    teardown();
                }

            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mSelectedDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(cordova.getActivity().getApplicationContext())
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
            mMediaPlayer = new com.sesamtv.cordova.chromecast.MediaPlayer(mApiClient, this);
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    private void launchApplication() {
        if (mApiClient == null) {
            return;
        }
        Cast.CastApi
                .launchApplication(mApiClient,
                        APP_ID, false)
                .setResultCallback(new ApplicationConnectionResultCallback("LaunchApp"));
    }

    private void joinApplication() {
        if (mApiClient == null) {
            return;
        }

        Cast.CastApi.joinApplication(mApiClient, APP_ID).setResultCallback(
                new ApplicationConnectionResultCallback("JoinApplication"));
    }

    private void leaveApplication() {
        if (mApiClient == null) {
            return;
        }

        Cast.CastApi.leaveApplication(mApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                if (result.isSuccess()) {
                    mMediaPlayer.detachMediaPlayer();
                    onSessionEnded();
                } else {
                    Log.w(TAG, "leave application failed");
                }
            }
        });
    }

    private void stopApplication() {
        if (mApiClient == null) {
            return;
        }

        Cast.CastApi.stopApplication(mApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                if (result.isSuccess()) {
                    mMediaPlayer.detachMediaPlayer();
                    onSessionEnded();
                } else {
                    Log.w(TAG, "stop application failed");
                }
            }
        });
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");

            if (mApiClient == null) {
                // We got disconnected while this runnable was pending
                // execution.
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if ((connectionHint != null)
                            && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        Log.d(TAG, "App  is no longer running");
                        teardown();
                    } else {
                        // Re-create the custom message channel
                        addChannels();
                    }
                } else {
                    // Launch the receiver app
                    launchApplication();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

            teardown();
        }
    }


    /**
     * Tear down the connection to the receiver
     */
    private void teardown() {
        onSessionEnded();
        if (mApiClient != null) {
            if (mApplicationStarted) {
                Cast.CastApi.stopApplication(mApiClient);
                removeChannels();
                mApplicationStarted = false;
            }
            if (mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
    }


    /**
     * Send a message to the receiver
     */
    private void sendMessage(String channelName, String message, final CallbackContext callbackContext) {
        if (mApiClient != null && mChannels.containsKey(channelName)) {
            try {
                Cast.CastApi.sendMessage(mApiClient,
                        mChannels.get(channelName).getNamespace(), message)
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status result) {
                                if (!result.isSuccess()) {
                                    Log.e(TAG, "Sending message failed");
                                    callbackContext.error("Sending message failed");
                                } else {
                                    callbackContext.success();
                                }
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
                callbackContext.error("Exception while sending message");
            }
        } else {
            Toast.makeText(cordova.getActivity(), message, Toast.LENGTH_SHORT)
                    .show();
        }
    }


    /**
     * Custom message channel
     */
    class CustomChannel implements MessageReceivedCallback {
        private String CHANNEL_NAME;
        private CallbackContext callbackContext;

        public CustomChannel(String ns, CallbackContext callback) {
            CHANNEL_NAME = ns;
            callbackContext = callback;
            try {
                Cast.CastApi.setMessageReceivedCallbacks(
                        mApiClient,
                        this.getNamespace(),
                        this);
            } catch (IOException e) {
                Log.e(TAG,
                        "Exception while creating channel",
                        e);
            }
        }

        /**
         * @return custom namespace
         */
        public String getNamespace() {
            return getRawNSName(CHANNEL_NAME);
        }

        /*
         * Receive message from the receiver app
         */
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            Log.d(TAG, "onMessageReceived: " + message);

            PluginResult result = new PluginResult(PluginResult.Status.OK, message);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        }

    }

    private final class ApplicationConnectionResultCallback implements
            ResultCallback<Cast.ApplicationConnectionResult> {
        private final String mClassTag;

        public ApplicationConnectionResultCallback(String suffix) {
            mClassTag = TAG + "_" + suffix;
        }

        @Override
        public void onResult(
                ApplicationConnectionResult result) {
            Status status = result.getStatus();
            Log.d(mClassTag,
                    "ApplicationConnectionResultCallback.onResult: statusCode"
                            + status.getStatusCode());
            if (status.isSuccess()) {
                ApplicationMetadata applicationMetadata = result
                        .getApplicationMetadata();
                String sessionId = result
                        .getSessionId();
                String applicationStatus = result
                        .getApplicationStatus();
                boolean wasLaunched = result
                        .getWasLaunched();
                Log.d(mClassTag,
                        "application name: "
                                + applicationMetadata
                                .getName()
                                + ", status: "
                                + applicationStatus
                                + ", sessionId: "
                                + sessionId
                                + ", wasLaunched: "
                                + wasLaunched);
                mApplicationStarted = true;

                onSessionStarted();
                // set the initial instructions
                // on the receiver
                //sendMessage(getString(R.string.instructions));
            } else {
                Log.e(mClassTag,
                        "application could not launch");
                teardown();
            }
        }
    }

}