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
            if(msg === null || msg === undefined){
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
        playMedia: function (position, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
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
        setMediaVolume: function (opt, callback) {
            opt = opt || {};
            var mtd, param;
            if ('muted' in opt) {
                mtd = 'setMuted';
                param = opt.muted;
            } else {
                mtd = 'setVolume';
                param = opt.volume;
                if (param < 0) {
                    param = 0;
                } else if (param > 1) {
                    param = 1;
                }
                //param *= 100;
                console.log('setMediaVolume', param);
            }

            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolume error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                mtd,
                [param]
            );
        },
        setMediaVolumeBy: function (value, callback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolumeBy error: ', err);
                    callback && callback(err);
                },
                "ChromeCast",
                "setVolumeBy",
                [value]
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