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
                    function (e) {
                        evt.emit(channelName, e);
                    },
                    function (err) {
                        console.error('onMessage Error : ' + err + ' for channel ' + channelName);
                    },
                    "ChromeCast",
                    "onMessage",
                    [channelName]
                );
            }
            return evt.on(channelName, fnc);
        },
        /**
         * @method sendMessage
         * @param {String} channelName
         * @param {Object} msg
         * @param {Function} callback
         */
        sendMessage: function (channelName, msg, callback) {
            if (msg === null || msg === undefined) {
                msg = {};
            }
            if (typeof(msg) !== 'object') {
                throw new TypeError('message must be an object');
            }
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('sendMessage Error', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "sendMessage",
                [channelName, msg]
            );
        },
        loadMedia: function (opt, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "loadMedia",
                [opt]
            );
        },
        playMedia: function (callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "playMedia",
                []
            );
        },
        pauseMedia: function (callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "pauseMedia",
                []
            );
        },
        seekMedia: function (position, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "seekMedia",
                [position]
            );
        },
        seekMediaBy: function (position, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "seekMediaBy",
                [position]
            );
        },
        setReceiverVolume: function (vol, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolume error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "setDeviceVolume",
                [vol]
            );
        },
        setReceiverVolumeBy: function (vol, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolume error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "setDeviceVolumeBy",
                [vol]
            );
        },
        toggleReceiverMute: function (callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('toggleReceiverMute error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "toggleMuted",
                []
            );
        },
        setReceiverMuted: function (muted, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setReceiverMuted error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "setMuted",
                [muted]
            );
        },
        onMediaStatus: function (fnc) {
            return this.caster.on('mediaStatus', fnc);
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
        stop: function () {

        }
    };
    module.exports = ChromeCastActivity;
})();