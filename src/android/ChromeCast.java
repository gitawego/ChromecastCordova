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
import java.util.List;
import java.util.HashMap;


import android.support.v7.app.MediaRouteActionProvider;
import com.google.android.gms.cast.*;
import com.google.android.gms.common.images.WebImage;
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


public class ChromeCast extends CordovaPlugin {
    //private final String APP_ID = "31a76198-2182-481a-a4a6-27d351872026";
    private String APP_ID = null;
    private static Boolean DEBUG_MODE = true;
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
    private HashMap<String, CallbackContext> mChannelsQueue = new HashMap<String, CallbackContext>();
    private CallbackContext receiverCallback;
    private CallbackContext mediaStatusCallback;
    private CallbackContext setReceiverCallback;
    private CallbackContext onEndedCallback;
    private CallbackContext onSessionCreatedCallback;


    private enum Actions {
        startReceiverListener, onMessage, sendMessage, setReceiver, loadMedia, stopMedia,
        pauseMedia, playMedia, seekMedia, seekMediaBy, setDeviceVolume, setDeviceVolumeBy, setMuted, toggleMuted,
        getMediaStatus, stopApplication, startStatusListener, startOnEndedListener,
        startSessionListener
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
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
            throws JSONException {
        Log.d(TAG, "APP ID: " + APP_ID);
        Log.d(TAG, "execute action : " + action);

        if (action.equals("setAppId")) {
            APP_ID = args.getString(0);
            if (args.length() > 1) {
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
            Log.w(TAG, "app id not found");
            callbackContext.error("APP_ID_NOT_FOUND");
        } else {
            if (action.equals("startListener")) {
                this.startListener(args.getString(0), callbackContext);
            } else if (action.equals("onMessage")) {
                this.onCastMessage(args.getString(0), callbackContext);
            } else if (action.equals("setReceiver")) {
                this.setReceiverAction(args, callbackContext);
            } else {
                executeActions(action, args, callbackContext);
            }

        }
        return true;
    }

    private void startListener(String type, CallbackContext callbackContext) {
        Log.d(TAG, "startListener for " + type);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        if (type.equals("session")) {
            onSessionCreatedCallback = callbackContext;
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        } else if (type.equals("receivers")) {
            receiverCallback = callbackContext;
            onReceiverListChanged();
        } else if (type.equals("sessionEnded")) {
            onEndedCallback = callbackContext;
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        } else if (type.equals("status")) {
            mediaStatusCallback = callbackContext;
            mediaStatusCallback.sendPluginResult(getMediaStatus());
        } else {
            callbackContext.error("LISTENER_TYPE_NOT_FOUND");
        }


    }

    private boolean executeActions(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        switch (Actions.valueOf(action)) {
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
            case stopMedia:
                stopMediaAction(callbackContext, args);
                break;
            case playMedia:
                playMediaAction(callbackContext);
                break;
            case seekMediaBy:
                try {
                    seekMediaByAction(args.getLong(0));
                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error(e.getMessage());
                }
                break;
            case seekMedia:
                try {
                    seekMediaAction(args.getLong(0));
                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error(e.getMessage());
                }
                break;
            case setDeviceVolume:
                final double vol = args.getDouble(0);
                Log.d(TAG, "setVolume " + vol);
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        setVolumeAction(vol, callbackContext);
                    }
                });
                break;
            case setDeviceVolumeBy:
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            setVolumeByAction(args.getDouble(0), callbackContext);
                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });
                break;
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
        return true;
    }


    private void setReceiverAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {

        final int index = args.getInt(0);
        setReceiverCallback = callbackContext;

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                final RouteInfo route = mMediaRouter.getRoutes().get(index);

                Log.d(TAG, "route :" + index + " " + route.getId() +
                        " selected " + route.isSelected() + " playback type: " + route.getPlaybackType());

                try {
                    if (route.isSelected()) {
                        Log.d(TAG, "found selected route");
                        //mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
                        //mMediaRouter.selectRoute(route);

                        mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
                        onReceiverListChanged();
                        launchReceiver();

                    } else {
                        mMediaRouter.selectRoute(route);
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });

    }

    private void loadMediaAction(final JSONObject opt, final CallbackContext callbackContext) throws JSONException {
        try {
            Log.d(TAG, "load Media...");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        mMediaPlayer.loadMedia(opt, callbackContext);
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

    private void stopMediaAction(final CallbackContext callbackContext, final JSONArray args) {

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                JSONObject customData = null;
                try {
                    if (args.length() > 0) {
                        customData = args.getJSONObject(0);
                    }
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                }
                mMediaPlayer.stopMedia(callbackContext, customData);
            }
        });
    }

    private void playMediaAction(final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "play ");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mMediaPlayer.playMedia(callbackContext);
            }
        });
    }

    private void seekMediaByAction(final long value) throws IOException {
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

    private void seekMediaAction(final long value) throws IOException {
        Log.d(TAG, "seekmedia " + value);
        if (ensurePlayer()) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        mMediaPlayer.seekMedia(value);
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
            mChannelsQueue.put(channelName, callbackContext);
            Log.w(TAG, "add cast message failed: application is not yet ready, add to queue");
        }
    }

    private void clearChannelsQueue() {
        Log.d(TAG, "clear channels queue");
        if (mChannelsQueue.size() > 0) {
            for (Map.Entry<String, CallbackContext> entry : mChannelsQueue.entrySet()) {
                String key = entry.getKey();
                CallbackContext value = entry.getValue();

                this.onCastMessage(key, value);
            }
            mChannelsQueue.clear();
        }

    }

    private void addChannels() {
        if (hasValidConnection()) {

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
    }

    private void removeChannels() {
        if (hasValidConnection()) {
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

    /**
     * when webview page reloaded
     */
    @Override
    public void onReset() {
        Log.d(TAG, "onreset");
        teardown();
        super.onReset();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "on destroy");
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
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "onReceiverListChanged " + (receiverCallback != null));
                if (receiverCallback != null) {
                    JSONArray jsonRoute = getRoutes();
                    PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
                    result.setKeepCallback(true);
                    Log.d(TAG, "onReceiverListChanged " + jsonRoute.toString());
                    receiverCallback.sendPluginResult(result);
                }
            }
        });

    }

    private void onSessionStarted(ApplicationConnectionResult result) {
        if (setReceiverCallback != null) {
            setReceiverCallback.success();
            setReceiverCallback = null;
        }
        if (onSessionCreatedCallback != null) {
            try {
                JSONObject info = new JSONObject();
                info.put("appId", APP_ID);
                info.put("sessionId", result.getSessionId());
                info.put("displayName", result.getApplicationMetadata().getName());
                info.put("statusCode", result.getStatus().getStatusCode());
                info.put("wasLaunched", result.getWasLaunched());

                ArrayList<JSONObject> media = new ArrayList<JSONObject>();
                if (mMediaPlayer != null) {
                    JSONObject mediaStatus = mMediaPlayer.getMediaStatus();
                    if (mediaStatus.has("contentId")) {
                        media.add(mediaStatus);
                    }
                }
                info.put("media", media);
                PluginResult res = new PluginResult(PluginResult.Status.OK, info);
                res.setKeepCallback(true);
                onSessionCreatedCallback.sendPluginResult(res);
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private void onSessionEnded(JSONObject info) {
        if (info == null) {
            info = new JSONObject();
        }
        if (onEndedCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
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
                CastDevice dInfo = getDevice(rInfo);
                JSONObject jsonRoute = new JSONObject();
                jsonRoute.put("id", rInfo.getId());
                jsonRoute.put("name", rInfo.getName());
                jsonRoute.put("description", rInfo.getDescription());
                jsonRoute.put("isSelected", rInfo.isSelected());
                jsonRoute.put("index", i);
                jsonRoute.put("volume", rInfo.getVolume());
                jsonRoute.put("ipAddress", dInfo.getIpAddress());
                List<WebImage> icons = dInfo.getIcons();
                if ((icons != null) && !icons.isEmpty()) {
                    WebImage icon = icons.get(0);
                    jsonRoute.put("icon", icon.getUrl());
                }
                if (rInfo.isSelected()) {
                    jsonRoute.put("isConnected", mSelectedDevice != null);
                }
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
            Log.d(TAG, "on route removed");
            onReceiverListChanged();
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteSelected");
            // Handle the user route selection.
            mSelectedDevice = getDevice(info);
            onReceiverListChanged();
            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteUnselected: info=" + info);
            onReceiverListChanged();
            teardown();
            mSelectedDevice = null;
        }

    }

    private CastDevice getDevice(RouteInfo info) {
        return CastDevice.getFromBundle(info.getExtras());
    }

    private boolean hasValidConnection() {
        return mApiClient != null && mApiClient.isConnected();
    }

    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {
                @Override
                public void onApplicationStatusChanged() {
                    if (hasValidConnection()) {
                        Log.d(TAG, "onApplicationStatusChanged: "
                                + Cast.CastApi.getApplicationStatus(mApiClient));
                    }
                }

                @Override
                public void onVolumeChanged() {
                    if (hasValidConnection()) {
                        Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
                    }
                }

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
            if (DEBUG_MODE) {
                apiOptionsBuilder.setDebuggingEnabled();
            }
            mApiClient = new GoogleApiClient.Builder(cordova.getActivity().getApplicationContext())
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();

        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    private void launchApplication() {
        if (mApiClient == null) {
            return;
        }
        Log.d(TAG, "launch application");
        Cast.CastApi
                .launchApplication(mApiClient,
                        APP_ID, false)
                .setResultCallback(new ApplicationConnectionResultCallback("LaunchApp"));
    }

    private void joinApplication() {

        if (mApiClient == null) {
            return;
        }
        Log.d(TAG, "join application");
        Cast.CastApi.joinApplication(mApiClient, APP_ID).setResultCallback(
                new ApplicationConnectionResultCallback("JoinApplication"));
    }

    private void leaveApplication() {
        if (hasValidConnection()) {
            return;
        }
        Log.d(TAG, "leave application");
        Cast.CastApi.leaveApplication(mApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                if (result.isSuccess()) {
                    mMediaPlayer.detachMediaPlayer();
                    onSessionEnded(null);
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
        Log.d(TAG, "stop application");
        Cast.CastApi.stopApplication(mApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                if (result.isSuccess()) {
                    mMediaPlayer.detachMediaPlayer();
                    onSessionEnded(null);
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
                        joinApplication();
                        if (mMediaPlayer != null) {
                            mMediaPlayer.reattachMediaPlayer();
                        }
                    }
                } else {
                    // Launch the receiver app
                    //if (mMediaRouter.getDefaultRoute() != mMediaRouter.getSelectedRoute()) {
                    launchApplication();
                    //}

                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
            if (mMediaPlayer != null) {
                mMediaPlayer.detachMediaPlayer();
            }
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
        onSessionEnded(null);

        if (mMediaPlayer != null) {
            mMediaPlayer.detachMediaPlayer();
            mMediaPlayer = null;
        }
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
        if (hasValidConnection() && mChannels.containsKey(channelName)) {
            try {
                Cast.CastApi.sendMessage(mApiClient, mChannels.get(channelName).getNamespace(), message)
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
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
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

        public void destroy() {
            callbackContext = null;
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, this.getNamespace());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
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
                Log.d(mClassTag, "application name: "
                        + applicationMetadata
                        .getName()
                        + ", status: "
                        + applicationStatus
                        + ", sessionId: "
                        + sessionId
                        + ", wasLaunched: "
                        + wasLaunched);
                mApplicationStarted = true;

                mMediaPlayer = new com.sesamtv.cordova.chromecast.MediaPlayer(mApiClient, ChromeCast.this);
                mMediaPlayer.attachMediaPlayer();

                onSessionStarted(result);
                clearChannelsQueue();
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