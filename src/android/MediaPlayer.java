package com.sesamtv.cordova.chromecast;

import android.net.Uri;
import android.util.Log;
import com.google.android.gms.cast.*;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.WebImage;

import com.google.android.gms.drive.Metadata;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;


public class MediaPlayer {
    private RemoteMediaPlayer mMediaPlayer;
    private static final String TAG = "ChromeCastPluginMediaPlayer";
    private GoogleApiClient mApiClient;
    private com.sesamtv.cordova.chromecast.ChromeCast parentContext;
    private Boolean mSeeking = false;

    public MediaPlayer(GoogleApiClient client, com.sesamtv.cordova.chromecast.ChromeCast scope) {
        mApiClient = client;
        parentContext = scope;
    }

    private void clearMediaState() {
        setCurrentMediaMetadata(null, null, null);
        refreshPlaybackPosition(0, 0);
    }

    /**
     * Updates the currently-playing-item metadata display. If the image URL is non-null and is
     * different from the one that is currently displayed, an asynchronous request will be started
     * to fetch the image at that URL.
     */
    protected final void setCurrentMediaMetadata(String title, String subtitle, Uri imageUrl) {
        Log.d(TAG, "setCurrentMediaMetadata: " + title + "," + subtitle + "," + imageUrl);


    }

    /**
     * @param position The stream position, or 0 if no media is currently loaded, or -1 to leave
     *                 the value unchanged.
     * @param duration The stream duration, or 0 if no media is currently loaded, or -1 to leave
     *                 the value unchanged.
     */
    protected final void refreshPlaybackPosition(long position, long duration) {

    }

    private void updatePlaybackPosition() {
        if (mMediaPlayer == null) {
            return;
        }
        refreshPlaybackPosition(mMediaPlayer.getApproximateStreamPosition(),
                mMediaPlayer.getStreamDuration());
    }

    public void attachMediaPlayer() {
        if (mMediaPlayer != null) {
            return;
        }

        mMediaPlayer = new RemoteMediaPlayer();
        mMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                Log.d(TAG, "MediaControlChannel.onStatusUpdated");
                // If item has ended, clear metadata.
                MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
                if ((mediaStatus != null)
                        && (mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_IDLE)) {
                    clearMediaState();
                }
                parentContext.onMediaStatusCallback(getMediaStatus());
                //updatePlaybackPosition();
            }
        });

        mMediaPlayer.setOnMetadataUpdatedListener(
                new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                    @Override
                    public void onMetadataUpdated() {
                        Log.d(TAG, "MediaControlChannel.onMetadataUpdated");


                        //todo update metadata
                    }
                });

        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mMediaPlayer.getNamespace(),
                    mMediaPlayer);
        } catch (IOException e) {
            Log.w(TAG, "Exception while launching application", e);
        }
    }

    public void reattachMediaPlayer() {
        if ((mMediaPlayer != null) && (mApiClient != null)) {
            try {
                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mMediaPlayer.getNamespace(),
                        mMediaPlayer);
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
        }
    }

    public void detachMediaPlayer() {
        if ((mMediaPlayer != null) && (mApiClient != null)) {
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient,
                        mMediaPlayer.getNamespace());
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
        }
        mMediaPlayer = null;
    }

    public JSONObject getMediaStatus() {
        JSONObject status = new JSONObject();

        try {
            status.put("deviceVolume", Cast.CastApi.getVolume(mApiClient));
            status.put("deviceMuted", Cast.CastApi.isMute(mApiClient));
            if (mMediaPlayer != null) {
                MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
                MediaInfo mediaInfo = mediaStatus.getMediaInfo();
                MediaMetadata metadata = mediaInfo.getMetadata();
                status.put("state", mediaStatus.getPlayerState());
                status.put("position", mediaStatus.getStreamPosition());
                status.put("duration", mMediaPlayer.getStreamDuration());
                status.put("idleReason", mediaStatus.getIdleReason());
                //status.put("title", mediaStatus.getTitle());
                status.put("contentId", mediaStatus.getMediaInfo().getContentId());
                status.put("contentType", mediaStatus.getMediaInfo().getContentType());
                status.put("volume", mediaStatus.getStreamVolume());
                status.put("muted", mediaStatus.isMute());
                //status.put("processing", mediaStatus.isStreamProgressing());
                status.put("customData", mediaStatus.getCustomData());
                if (metadata != null) {
                    status.put("title", metadata.getString(MediaMetadata.KEY_TITLE));
                    String artist = metadata.getString(MediaMetadata.KEY_ARTIST);
                    if (artist != null) {
                        status.put("artist", artist);
                        status.put("studio", metadata.getString(MediaMetadata.KEY_STUDIO));
                    }
                    List<WebImage> images = metadata.getImages();
                    if ((images != null) && !images.isEmpty()) {
                        WebImage image = images.get(0);
                        status.put("imageUrl", image.getUrl());
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return status;
    }

    /*
     * Begins playback of the currently selected video.
     */
    public void playMedia(JSONObject media, final CallbackContext callbackContext) {
        Log.d(TAG, "playMedia: " + media);
        if (media == null) {
            return;
        }
        if (mMediaPlayer == null) {
            Log.e(TAG, "Trying to play a video with no active media session");
            return;
        }
        try {
            MediaInfo mediaInfo = buildMediaInfo(media);

            mMediaPlayer.load(mApiClient, mediaInfo, media.has("autoplay") && media.getBoolean("autoplay"))
                    .setResultCallback(
                            new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                @Override
                                public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                    if (!result.getStatus().isSuccess()) {
                                        Log.e(TAG, "Failed to load media.");
                                        callbackContext.error("Failed to load media.");
                                    } else {
                                        callbackContext.success();
                                    }
                                }
                            });
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

    }

    public void pauseMedia(CallbackContext callbackContext) {
        if (mMediaPlayer == null) {
            callbackContext.error("player not found");
            return;
        }
        try {
            mMediaPlayer.pause(mApiClient);
            callbackContext.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to pause", e);
            callbackContext.error("unable to pause");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public void playMedia(CallbackContext callbackContext) {
        if (mMediaPlayer == null) {
            callbackContext.error("player not found");
            return;
        }
        try {
            mMediaPlayer.play(mApiClient);
            callbackContext.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to play", e);
            callbackContext.error("unable to play");

        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public void seekMedia(Long position, String... behavior) {

        if (mMediaPlayer == null) {
            return;
        }
        String afterSeekMode = "DO_NOTHING";
        if (behavior.length != 0) {
            afterSeekMode = behavior[0];
        }

        int resumeState = RemoteMediaPlayer.RESUME_STATE_UNCHANGED;
        if (afterSeekMode.equals("PLAY")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_PLAY;
        } else if (afterSeekMode.equals("PAUSE")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_PAUSE;
        } else if (afterSeekMode.equals("DO_NOTHING")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_UNCHANGED;
        }
        mSeeking = true;
        mMediaPlayer.seek(mApiClient, position, resumeState).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        Status status = result.getStatus();
                        if (status.isSuccess()) {
                            mSeeking = false;
                        } else {
                            Log.w(TAG, "Unable to seek: " + status.getStatusCode());
                        }
                    }

                });
    }

    public void seekMediaBy(long value, String... behavior) {
        try {
            long pos = (long) (getMediaStatus().getDouble("position") + value);
            this.seekMedia(pos, behavior);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

    }

    public void setDeciveVolume(double volume) {
        if (mApiClient == null) {
            return;
        }
        try {
            Cast.CastApi.setVolume(mApiClient, volume);
        } catch (IOException e) {
            Log.e(TAG, "Unable to change volume");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void setDeviceVolumeBy(double value) {
        try {
            double vol = getMediaStatus().getDouble("deviceVolume") + value;
            if (vol < 0) {
                vol = 0;
            } else if (vol > 1) {
                vol = 1;
            }
            this.setDeciveVolume(vol);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void toggleDeviceMuted() {
        if (mApiClient == null) {
            return;
        }
        this.setDeviceMuted(!Cast.CastApi.isMute(mApiClient));
    }

    public void setDeviceMuted(boolean muted) {
        if (mApiClient == null) {
            return;
        }
        try {
            Cast.CastApi.setMute(mApiClient, muted);
        } catch (IOException e) {
            Log.w(TAG, "Unable to toggle mute");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }


    private MediaInfo buildMediaInfo(JSONObject opt) {
        try {
            MediaInfo.Builder info = new MediaInfo.Builder(opt.getString("src"));
            info.setContentType(opt.getString("contentType"));
            if (opt.has("customData")) {
                info.setCustomData(opt.getJSONObject("customData"));
            }


            String streamType = opt.has("streamType") ? opt.getString("streamType") : "BUFFERED";

            if (streamType.equals("BUFFERED")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED);
            } else if (streamType.equals("LIVE")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_LIVE);
            } else if (streamType.equals("NONE")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_NONE);
            }

            if (opt.has("duration")) {
                info.setStreamDuration(opt.getLong("duration"));
            }

            return info.build();

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }


    }

}
