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


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.net.Uri;

import com.google.cast.ApplicationChannel;
import com.google.cast.ApplicationMetadata;
import com.google.cast.ApplicationSession;
import com.google.cast.CastContext;
import com.google.cast.CastDevice;
import com.google.cast.ContentMetadata;
import com.google.cast.MediaProtocolCommand;
import com.google.cast.MessageStream;
import com.google.cast.MediaProtocolMessageStream;
import com.google.cast.MediaProtocolMessageStream.PlayerState;
import com.google.cast.MediaRouteAdapter;
import com.google.cast.MediaRouteHelper;
import com.google.cast.MediaRouteStateChangeListener;
import com.google.cast.SessionError;

public class ChromeCast extends CordovaPlugin implements MediaRouteAdapter {
    //private final String APP_ID = "31a76198-2182-481a-a4a6-27d351872026";
    private String APP_ID = null;
    private static final String TAG = "ChromeCastPlugin";

    private CastContext castContext = null;
    private CastDevice selectedDevice;
    private ApplicationSession session;
    private MediaProtocolMessageStream messageStream;
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private MediaRouter.Callback mediaRouterCallback;
    private MediaRouteStateChangeListener routeStateListener;
    private MediaProtocolCommand status;
    private CallbackContext receiverCallback;
    private CallbackContext mediaStatusCallback;
    private CallbackContext setReceiverCallback;
    private HashMap<String, CallbackContext> channelCallback;
    private HashMap<String, Messenger> channels;
    private RouteInfo currentRoute;

    private enum Actions {
        startReceiverListener, onMessage, sendMessage, setReceiver, loadMedia,
        pauseMedia, playMedia, seekBy, setVolume, setVolumeBy, setMuted,
        getMediaStatus, stopCast, startStatusListener
    }

    /*public ChromecastPlugin() {
        
    }*/

    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        //Logger.setDebugEnabledByDefault(true);
        channelCallback = new HashMap<String, CallbackContext>();
        channels = new HashMap<String, Messenger>();
        receiverCallback = null;
        mediaStatusCallback = null;

    }

    private void initRouters(CallbackContext callbackContext) {
        Log.d(TAG, "init routers...");

        castContext = new CastContext(cordova.getActivity().getApplicationContext());
        MediaRouteHelper.registerMinimalMediaRouteProvider(castContext, this);

        mediaRouter = MediaRouter.getInstance(cordova.getActivity().getApplicationContext());

        mediaRouteSelector = MediaRouteHelper.
                buildMediaRouteSelector(MediaRouteHelper.CATEGORY_CAST, APP_ID, null);

        mediaRouterCallback = new MediaRouterCallback();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        callbackContext.success();

        /*Runnable runnable = new StatusRunner();
        Thread thread = new Thread(runnable);
        thread.start();*/
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "APP ID: " + APP_ID);
        Log.d(TAG, "execute action : " + action);

        if (action.equals("setAppId")) {
            APP_ID = args.getString(0);
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
            switch (Actions.valueOf(action)) {
                case startReceiverListener:
                    startReceiverListenerAction(callbackContext);
                    break;
                case onMessage:
                    onMessage(args.getString(0), callbackContext);
                    break;
                case sendMessage:
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            try {
                                String channelName = args.getString(0);
                                JSONObject msg = args.getJSONObject(1);
                                sendMessage(channelName, msg, callbackContext);
                            } catch (Exception e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });

                    break;
                case setReceiver:
                    setReceiverAction(args, callbackContext);
                    break;
                case loadMedia:
                    loadMediaAction(args, callbackContext);
                    break;
                case pauseMedia:
                    pauseMediaAction(callbackContext);
                    break;
                case playMedia:
                    playMediaAction(args, callbackContext);
                    break;
                case seekBy:
                    try {
                        seekBy(args.getDouble(0));
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
                            setVolume(vol, callbackContext);
                        }
                    });
                    break;
                case setVolumeBy:
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            try {
                                setVolumeBy(args.getDouble(0), callbackContext);
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
                            setMuted(muted, callbackContext);
                        }
                    });
                    break;
                case getMediaStatus:
                    Log.d(TAG, "getMediaStatus");
                    callbackContext.sendPluginResult(getMediaStatus(""));
                    break;
                case stopCast:
                    try {
                        Log.d(TAG, "stopCast");
                        stopCast();
                        callbackContext.success();
                    } catch (IOException e) {
                        callbackContext.error("stop cast failed :" + e.getMessage());
                        return false;
                    }
                    break;
                case startStatusListener:
                    mediaStatusCallback = callbackContext;
                    callbackContext.sendPluginResult(getMediaStatus(null));
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
                    final RouteInfo route = mediaRouter.getRoutes().get(index);
                    Log.d(TAG, "route :" + index + " " + route.getId() + " selected");
                    mediaRouter.selectRoute(route);
                }
            });

        } catch (IndexOutOfBoundsException e) {
            callbackContext.error("Receiver not found");
        }
    }

    private void loadMediaAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final JSONObject opt = args.getJSONObject(0);
        try {
            Log.d(TAG, "casting...");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        startCast(opt, callbackContext);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callbackContext.error("cast failed :" + e.getMessage());
        }
    }

    private void playMediaAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final int position = args.getInt(0);
        Log.d(TAG, "play :" + position);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    play(position);
                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void pauseMediaAction(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    pause();
                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private JSONArray getRoutes() {
        JSONArray routeList = new JSONArray();
        List<RouteInfo> routesInMedia = mediaRouter.getRoutes();
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
                routeList.put(jsonRoute);
            }
        } catch (JSONException e) {
            Log.d(TAG, "failed to get routes list");
        }
        return routeList;
    }

    public void onDeviceAvailable(CastDevice castDevice, String deviceName,
                                  MediaRouteStateChangeListener listener) {
        selectedDevice = castDevice;
        routeStateListener = listener;
        Log.d(TAG, "device available");
        openSession();
    }

    public void onSetVolume(double volume) {
    }

    public void onUpdateVolume(double volume) {
    }

    /**
     * Starts a new video playback session with the current CastContext and selected device.
     */
    private void openSession() {
        Log.d(TAG, "OPEN SESSION");
        if (selectedDevice == null) {
            return;
        }
        session = new ApplicationSession(castContext, selectedDevice);

        // TODO: The below lines allow you to specify either that your application uses the default
        // implementations of the Notification and Lock Screens, or that you will be using your own.
        int flags = 0;

        // Comment out the below line if you are not writing your own Notification Screen.
        flags |= ApplicationSession.FLAG_DISABLE_NOTIFICATION;

        // Comment out the below line if you are not writing your own Lock Screen.
        flags |= ApplicationSession.FLAG_DISABLE_LOCK_SCREEN_REMOTE_CONTROL;

        session.setApplicationOptions(flags);

        Log.d(TAG, "Beginning session with context: " + castContext);
        Log.d(TAG, "The session to begin: " + session);
        session.setListener(new ApplicationSession.Listener() {

            @Override
            public void onSessionStarted(ApplicationMetadata appMetadata) {
                Log.d(TAG, "Getting channel after session start");
                ApplicationChannel channel = session.getChannel();
                if (channel == null) {
                    Log.e(TAG, "channel = null");
                    return;
                }
                Log.d(TAG, "Creating and attaching Message Stream");
                messageStream = new MediaProtocolMessageStream() {
                    @Override
                    public void onStatusUpdated() {
                        super.onStatusUpdated();
                        if (mediaStatusCallback != null) {
                            mediaStatusCallback.sendPluginResult(getMediaStatus(null));
                        }
                    }
                };
                channel.attachMessageStream(messageStream);
                if (setReceiverCallback != null) {
                    setReceiverCallback.success();
                    setReceiverCallback = null;
                }
            }

            @Override
            public void onSessionStartFailed(SessionError error) {
                Log.e(TAG, "onStartFailed " + error);
                messageStream = null;
            }

            @Override
            public void onSessionEnded(SessionError error) {
                Log.i(TAG, "onEnded " + error);
                messageStream = null;

            }
        });

        try {
            Log.d(TAG, "Starting session with app name " + APP_ID);

            // TODO: To run your own copy of the receiver, you will need to set app_name in
            // /res/strings.xml to your own appID, and then upload the provided receiver
            // to the url that you whitelisted for your app.
            // The current value of app_name is "YOUR_APP_ID_HERE".
            session.startSession(APP_ID);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open session", e);
        }
    }

    private void endSession() {
        if ((session != null) && (session.hasStarted())) {
            try {
                if (session.hasChannel()) {
                    //todo leave channel
                }
                session.endSession();
            } catch (IOException e) {
                Log.e(TAG, "Failed to end the session.", e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to end session.", e);
            } finally {
                session = null;
            }
        }
    }

    private void onMessage(final String channelName, final CallbackContext callbackContext) {
        if (session == null) {
            if (callbackContext != null) {
                callbackContext.error("SESSION_NOT_FOUND");
            }
            return;
        }
        if (channels.get(channelName) == null) {

            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    ApplicationChannel channel = session.getChannel();
                    if (channel == null) {
                        Log.w(TAG, "channel is null");
                        callbackContext.error("CHANNEL_NOT_FOUND");
                        return;
                    }
                    Messenger msg = new Messenger(channelName);
                    Log.d(TAG, "adding msg stream " + channelName);
                    channel.attachMessageStream(msg);
                    channels.put(channelName, msg);
                    channelCallback.put(channelName, callbackContext);
                }
            });

        }

    }

    private void sendMessage(String channelName, JSONObject msg, CallbackContext callbackContext) throws IOException {
        Log.d(TAG, "sendMessage to channel " + channelName);
        Messenger msgerInChannel = channels.get(channelName);
        if (msgerInChannel == null) {
            onMessage(channelName, null);
            msgerInChannel = channels.get(channelName);
        }
        if (msgerInChannel == null) {
            callbackContext.error("channel " + channelName + " is not found");
            return;
        }
        msgerInChannel.send(msg);
    }


    private void startCast(JSONObject opt, CallbackContext callbackContext) throws IOException, JSONException, URISyntaxException {

        final String url = opt.getString("src");
        final Boolean autostart = !opt.has("autostart") || opt.getBoolean("autostart");
        final String title = opt.has("title") ? opt.getString("title") : null;
        final JSONObject info = opt.has("contentInfo") ? opt.getJSONObject("contentInfo") : null;
        Uri imageUrl = null;
        if (opt.has("imageUrl")) {
            imageUrl = Uri.parse(opt.getString("imageUrl"));
        }
        ContentMetadata context = new ContentMetadata();
        if (title != null) {
            context.setTitle(title);
        }
        if (imageUrl != null) {
            context.setImageUrl(imageUrl);
        }
        if (info != null) {
            context.setContentInfo(info);
        }

        if (session == null) {
            callbackContext.error("SESSION_NOT_FOUND");
        } else if (messageStream == null) {
            callbackContext.error("REMOTEMEDIA_NOT_FOUND");
        } else {
            String currentContentId = messageStream.getContentId();
            //not content id defined, or current content id is different, or player is not playing
            if (currentContentId == null || !currentContentId.equals(url) ||
                    (currentContentId.equals(url) || messageStream.getPlayerState() != PlayerState.PLAYING)) {
                try {
                    Log.d(TAG, "Session loadMedia :" + url);
                    MediaProtocolCommand cmd = messageStream.loadMedia(url, context, autostart);
                    cmd.setListener(new MediaProtocolCommand.Listener() {
                        public void onCompleted(MediaProtocolCommand cmd) {
                            Log.d(TAG, "load complete :" + url);
                            if (mediaStatusCallback != null) {
                                mediaStatusCallback.sendPluginResult(getMediaStatus("load complete :" + url));
                            }
                        }

                        public void onCancelled(MediaProtocolCommand cmd) {
                            Log.d(TAG, "load cancelled :" + url);
                            if (mediaStatusCallback != null) {
                                mediaStatusCallback.sendPluginResult(getMediaStatus("load cancelled :" + url));
                            }
                        }
                    });
                    callbackContext.success();
                } catch (IOException e) {
                    Log.d(TAG, "load exception :" + e.getMessage());
                    e.printStackTrace();
                    if (mediaStatusCallback != null) {
                        mediaStatusCallback.sendPluginResult(getMediaStatus("load exception :" + e.getMessage()));
                    }
                    callbackContext.error("FAILED_TO_CAST");
                }
            }
        }

    }


    private void play(double position) throws IOException {
        Log.d(TAG, "Player State :" + messageStream.getPlayerState());
        if (messageStream.getPlayerState() == PlayerState.STOPPED) {
            messageStream.resume();
        } else {
            messageStream.playFrom(position);
        }
    }

    private void seekBy(final double value) throws IOException {
        Log.d(TAG, "seekBy " + value);
        if (messageStream != null) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    double pos = messageStream.getStreamPosition();
                    pos = pos + value;
                    try {
                        play(pos);
                    } catch (IOException e) {
                        Log.d(TAG, "seekBy Error " + e.getMessage());
                    }
                }
            });
        }
    }

    private void pause() throws IOException {
        if (messageStream.getPlayerState() == PlayerState.PLAYING) {
            messageStream.stop();
        }
    }

    private void setVolume(double vol, CallbackContext callbackContext) {
        if (messageStream != null) {
            try {
                messageStream.setVolume(vol);
                callbackContext.success();
            } catch (IOException e) {
                callbackContext.error(e.getMessage());
            }
        }
    }

    private void setVolumeBy(double value, CallbackContext callbackContext) {
        if (messageStream == null) {
            callbackContext.error("RemoteMedia not found");
            return;
        }
        double currentVol = messageStream.getVolume();
        double vol = currentVol + value;
        if (vol < 0) {
            vol = 0;
        } else if (vol > 1) {
            vol = 1;
        }
        setVolume(vol, callbackContext);
    }

    private void setMuted(boolean muted, CallbackContext callbackContext) {
        if (messageStream != null) {
            try {
                messageStream.setMuted(muted);
                callbackContext.success();
            } catch (IOException e) {
                callbackContext.error(e.getMessage());
            }
        }
    }

    private void stopCast() throws IOException {
        if (messageStream != null) {
            messageStream.stop();
            /*session.endSession();
            messageStream = null;
            session = null;*/
        }
    }

    private PluginResult getMediaStatus(String statusMessage) {
        JSONObject status = new JSONObject();
        try {
            if (messageStream != null) {
                status.put("state", messageStream.getPlayerState());
                status.put("position", messageStream.getStreamPosition());
                status.put("duration", messageStream.getStreamDuration());
                status.put("imageUrl", messageStream.getImageUrl());
                status.put("title", messageStream.getTitle());
                status.put("contentId", messageStream.getContentId());
                status.put("contentInfo", messageStream.getContentInfo());
                status.put("volume", messageStream.getVolume());
                status.put("muted", messageStream.isMuted());
                status.put("processing", messageStream.isStreamProgressing());
            } else {
                status.put("state", "");
                status.put("position", 0.0);
                status.put("duration", 0.0);
            }
            if (statusMessage != null) {
                status.put("statusMessage", statusMessage);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
        result.setKeepCallback(true);
        return result;
    }


    public void onDestroy() {
        super.onDestroy();
        endSession();
        mediaRouter.removeCallback(mediaRouterCallback);
        castContext.dispose();
        castContext = null;
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "on pause");
        super.onPause(multitasking);
    }

    @Override
    public void onReset() {
        super.onReset();
        Log.d(TAG, "on reset -- end session");
        endSession();
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.d(TAG, "resuming a session");
        if (session != null && session.isResumable()) {
            try {
                session.resumeSession();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Messenger extends MessageStream {
        protected Messenger(String channelName) {
            super(channelName);
        }

        @Override
        public void onMessageReceived(JSONObject obj) {
            final CallbackContext storedCallback = channelCallback.get(getNamespace());
            if (storedCallback != null) {
                //Log.i(TAG, "onMessageReceived > send message to callback");
                PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                result.setKeepCallback(true);
                storedCallback.sendPluginResult(result);
            }
        }

        public final void send(JSONObject msg) throws IOException {
            sendMessage(msg);
        }
    }

    private class MediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            super.onRouteAdded(router, route);
            Log.d(TAG, "on route added");
            Log.d(TAG, "route added :" + route.getId() + ":" + route.getName() + ":" + route.getDescription());
            if (receiverCallback != null) {
                JSONArray jsonRoute = getRoutes();
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
                result.setKeepCallback(true);
                receiverCallback.sendPluginResult(result);
            }
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            super.onRouteRemoved(router, route);
            Log.d(TAG, "route removed :" + route.getId() + ":" + route.getName() + ":" + route.getDescription());
            if (receiverCallback != null) {
                JSONArray jsonRoute = getRoutes();
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
                result.setKeepCallback(true);
                receiverCallback.sendPluginResult(result);
            }
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "route selected :" + route.getName());
            MediaRouteHelper.requestCastDeviceForRoute(route);
            currentRoute = route;
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo route) {
            routeStateListener = null;
        }
    }

    private class StatusRunner implements Runnable {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (mediaStatusCallback != null) {
                        mediaStatusCallback.sendPluginResult(getMediaStatus(null));
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }


}