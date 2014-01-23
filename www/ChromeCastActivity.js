/*global require,define,console,module, CustomEvent*/
/*jslint plusplus: true */
/*jslint expr:true */
(function () {
    'use strict';
    var exec = require('cordova/exec'),
        globalConf = {
            channels: {}
        },
        evt = new CustomEvent();

    function ChromeCastActivity(receiverInfo, caster) {
        this.receiverInfo = receiverInfo;
        this.caster = caster;
    }

    ChromeCastActivity.prototype = {
        onMessage: function (channelName, fnc) {
            if (!globalConf.channels[channelName]) {
                exec(
                    function (evt) {
                        evt.emit(channelName, evt);
                    },
                    function (err) {
                        console.error('err', err);
                    },
                    "ChromeCast",
                    "onMessage",
                    []
                );
            }
            return evt.on(channelName, fnc);
        },
        sendMessage: function (channelName, msg, callback) {
            exec(
                function (evt) {
                    callback && callback(null, evt);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "sendMessage",
                [channelName, msg]
            );
        },
        loadMedia: function (opt, callback) {
            exec(
                function (evt) {
                    callback && callback(null, evt);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "loadMedia",
                [opt]
            );
        },
        playMedia: function (position, callback) {
            exec(
                function (evt) {
                    callback && callback(null, evt);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "playMedia",
                [position]
            );
        },
        pauseMedia: function (callback) {
            exec(
                function (evt) {
                    callback && callback(null, evt);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "pauseMedia",
                []
            );
        },
        setMediaVolume: function (opt, callback) {
            opt = opt || {};
            exec(
                function (evt) {
                    callback && callback(null, evt);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                ('muted' in opt) ? "setMuted" : "setVolume",
                []
            );
        },
        onMediaStatus:function(fnc){
            return this.caster.on('mediaStatus',fnc);
        },
        getMediaStatus: function (callback) {
            exec(
                function (status) {
                    callback && callback({
                        status: status
                    });
                },
                function (err) {
                    callback && callback({
                        status: null,
                        error: new Error(err)
                    });
                },
                "ChromeCast",
                'getMediaStatus',
                []
            );
        },
        stop:function(){

        }
    };
    module.exports = ChromeCastActivity;
})();