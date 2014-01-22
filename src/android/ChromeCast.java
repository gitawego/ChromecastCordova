/**
 *  PhoneStateChangeListener cordova plugin (Android)
 *
 *    @author Hongbo LU
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
    public static final boolean ENABLE_LOGV = true;
    private static final String TAG = "ChromCastPlugin";

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
    private CallbackContext statusCallback;
    private HashMap<String, CallbackContext> channelCallback;
    private HashMap<String, Messenger> channels;
    private RouteInfo currentRoute;

    private enum Actions {
        setAppId, startReceiverListener, onMessage, sendMessage, setReceiver,loadMedia,
        pauseMedia, playMedia, seekBy, setVolume, setMuted, getMediaStatus, stopCast, startStatusListener
    }

    /*public ChromecastPlugin() {
        
    }*/

    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        //Logger.setDebugEnabledByDefault(true);
        channelCallback = new HashMap<String, CallbackContext>();
        channels = new HashMap<String, Messenger>();
        receiverCallback = null;
        statusCallback = null;

    }

    private void initRouters() {
        logVIfEnabled(TAG, "init routers...");
        castContext = new CastContext(cordova.getActivity().getApplicationContext());
        MediaRouteHelper.registerMinimalMediaRouteProvider(castContext, this);

        mediaRouter = MediaRouter.getInstance(cordova.getActivity().getApplicationContext());
        mediaRouteSelector = MediaRouteHelper.
                buildMediaRouteSelector(MediaRouteHelper.CATEGORY_CAST, APP_ID, null);
        mediaRouterCallback = new MediaRouterCallback();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

        Runnable runnable = new StatusRunner();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        final CallbackContext cb = callbackContext;
        logVIfEnabled(TAG, "APP ID: " + APP_ID);

        if (action.equals("setAppId")) {
            APP_ID = args.getString(0);
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    initRouters();
                    cb.success();
                }
            });
            return true;
        }
        if (APP_ID == null) {
            callbackContext.error("APP_ID NOT FOUND");
            return false;
        }
        switch (Actions.valueOf(action)) {
            case startReceiverListener:
                startReceiverListenerAction(callbackContext);
                break;
            case onMessage:
                onMessage(args.getString(0), callbackContext);
                break;
            case sendMessage:
                String channelName = args.getString(0);
                JSONObject msg = args.getJSONObject(1);
                try {
                    sendMessage(channelName, msg, callbackContext);
                } catch (IOException e) {
                    callbackContext.error("SEND_MESSAGE_FAILED");
                }
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
                    seekBy(args.getInt(0));
                    cb.success();
                } catch (IOException e) {
                    cb.error(e.getMessage());
                }
                break;
            case setVolume:
                final double vol = args.getLong(0);
                System.out.println("setVolume");
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        setVolume(vol, cb);
                    }
                });
                break;
            case setMuted:
                final boolean muted = args.getBoolean(0);
                System.out.println("setMuted");
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        setMuted(muted, cb);
                    }
                });
                break;
            case getMediaStatus:
                System.out.println("getMediaStatus");
                callbackContext.sendPluginResult(getStatus(""));
                break;
            case stopCast:
                try {
                    System.out.println("stopCast");
                    stopCast();
                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error("stop cast failed :" + e.getMessage());
                    return false;
                }
                break;
            case startStatusListener:
                statusCallback = callbackContext;
                callbackContext.sendPluginResult(getStatus(null));
                break;
            default:
                callbackContext.error("Invalid action");

        }

        return true;
    }

    private void startReceiverListenerAction(final CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                receiverCallback = callbackContext;
                JSONArray routeList = getRoutes();
                logVIfEnabled(TAG, "getting routers");
                PluginResult result = new PluginResult(PluginResult.Status.OK, routeList);
                result.setKeepCallback(true);
                receiverCallback.sendPluginResult(result);
            }
        });
    }

    private void setReceiverAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        int index = args.getInt(0);
        try {
            final RouteInfo route = mediaRouter.getRoutes().get(index);
            System.out.println("route :" + index + " " + route.getId() + " selected");
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    mediaRouter.selectRoute(route);
                    callbackContext.success();
                }
            });

        } catch (IndexOutOfBoundsException e) {
            callbackContext.error("Receiver not found");
        }
    }

    private void loadMediaAction(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final JSONObject opt = args.getJSONObject(0);
        try {
            System.out.println("casting...");
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
        System.out.println("play :" + position);
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
            logVIfEnabled(TAG, "failed to get routes list");
        }
        return routeList;
    }

    public void onDeviceAvailable(CastDevice castDevice, String deviceName,
                                  MediaRouteStateChangeListener listener) {
        selectedDevice = castDevice;
        routeStateListener = listener;
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

        logVIfEnabled(TAG, "Beginning session with context: " + castContext);
        logVIfEnabled(TAG, "The session to begin: " + session);
        session.setListener(new ApplicationSession.Listener() {

            @Override
            public void onSessionStarted(ApplicationMetadata appMetadata) {
                logVIfEnabled(TAG, "Getting channel after session start");
                ApplicationChannel channel = session.getChannel();
                if (channel == null) {
                    Log.e(TAG, "channel = null");
                    return;
                }
                logVIfEnabled(TAG, "Creating and attaching Message Stream");
                messageStream = new MediaProtocolMessageStream();
                channel.attachMessageStream(messageStream);
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
            logVIfEnabled(TAG, "Starting session with app name " + APP_ID);

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

    private void onMessage(String channelName, CallbackContext callbackContext) {
        final String cName = channelName;
        if (session == null) {
            callbackContext.error("SESSION_NOT_FOUND");
            return;
        }
        if (channels.get(channelName) == null) {
            ApplicationChannel channel = session.getChannel();
            Messenger msg = new Messenger(channelName) {
                @Override
                public void onMessageReceived(JSONObject obj) {
                    final CallbackContext storedCallback = channelCallback.get(cName);
                    if (storedCallback != null) {
                        PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                        result.setKeepCallback(true);
                        storedCallback.sendPluginResult(result);
                    }
                }
            };
            channel.attachMessageStream(msg);
            channels.put(cName, msg);
            channelCallback.put(cName, callbackContext);
        }

    }

    private void sendMessage(String channelName, JSONObject msg, CallbackContext callbackContext) throws IOException {
        Messenger channel = channels.get(channelName);
        if (channel == null) {
            callbackContext.error("CHANNEL_NOT_FOUND");
            return;
        }
        channel.send(msg);
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

        if (session != null && messageStream.getPlayerState() != PlayerState.PLAYING) {
            try {
                logVIfEnabled(TAG, "Session loadMedia :" + url);
                MediaProtocolCommand cmd = messageStream.loadMedia(url, context, autostart);
                cmd.setListener(new MediaProtocolCommand.Listener() {
                    public void onCompleted(MediaProtocolCommand cmd) {
                        System.out.println("load complete :" + url);
                        if (statusCallback != null) {
                            statusCallback.sendPluginResult(getStatus("load complete :" + url));
                        }
                    }

                    public void onCancelled(MediaProtocolCommand cmd) {
                        System.out.println("load cancelled :" + url);
                        if (statusCallback != null) {
                            statusCallback.sendPluginResult(getStatus("load cancelled :" + url));
                        }
                    }
                });
                callbackContext.success();
            } catch (IOException e) {
                System.out.println("load exception :" + e.getMessage());
                e.printStackTrace();
                if (statusCallback != null) {
                    statusCallback.sendPluginResult(getStatus("load exception :" + e.getMessage()));
                }
                callbackContext.error("FAILED_TO_CAST");
            }
        } else {
            System.out.println("player state :" + messageStream.getPlayerState());
            callbackContext.error("SESSION_NOT_FOUND");
        }
    }


    private void play(int position) throws IOException {
        System.out.println("Player State :" + messageStream.getPlayerState());
        if (messageStream.getPlayerState() == PlayerState.STOPPED) {
            messageStream.resume();
        } else {
            messageStream.playFrom((double) position);
        }
    }

    private void seekBy(final int value) throws IOException {
        if (messageStream != null) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    double pos = messageStream.getStreamPosition();
                    pos = pos + value;
                    try {
                        play((int) pos);
                    } catch (IOException e) {
                        logVIfEnabled(TAG, "seekBy Error " + e.getMessage());
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

    private PluginResult getStatus(String statusMessage) {
        JSONObject status = new JSONObject();
        try {
            if (messageStream != null) {
                status.put("state", messageStream.getPlayerState().toString());
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
        logVIfEnabled(TAG, "on pause");
        super.onPause(multitasking);
    }

    @Override
    public void onReset() {
        super.onReset();
        logVIfEnabled(TAG, "on reset -- end session");
        endSession();
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        logVIfEnabled(TAG, "resuming a session");
        if (session != null && session.isResumable()) {
            try {
                session.resumeSession();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Messenger extends MessageStream {
        public Messenger(String channelName) {
            super(channelName);
        }

        @Override
        public void onMessageReceived(JSONObject obj) {

        }

        public final void send(JSONObject msg) throws IOException {
            sendMessage(msg);
        }
    }

    private class MediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            super.onRouteAdded(router, route);
            logVIfEnabled(TAG, router.toString());
            System.out.println("route added :" + route.getId() + ":" + route.getName() + ":" + route.getDescription());
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
            System.out.println("route removed :" + route.getId() + ":" + route.getName() + ":" + route.getDescription());
            if (receiverCallback != null) {
                JSONArray jsonRoute = getRoutes();
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
                result.setKeepCallback(true);
                receiverCallback.sendPluginResult(result);
            }
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo route) {
            System.out.println("route selected :" + route.getName());
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
                    if (statusCallback != null) {
                        statusCallback.sendPluginResult(getStatus(null));
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Logs in verbose mode with the given tag and message, if the LOCAL_LOGV tag is set.
     */
    private void logVIfEnabled(String tag, String message) {
        if (ENABLE_LOGV) {
            Log.v(tag, message);
        }
    }
}