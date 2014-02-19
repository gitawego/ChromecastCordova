/*global require,define,console,module,CustomEvent*/
/*jslint plusplus: true */
/*jslint expr:true */
(function () {
    "use strict";

    var exec = require('cordova/exec'),
        globalConf = {
            receiverListener: false,
            receiverList: [],
            isAvailable: false,
            channels: {}
        }, evt = new CustomEvent();

    function ChromeCast(opt) {
        var self = this;
        this.config = {
            appId: opt.appId,
            defaultRouteRegxp: /DEFAULT_ROUTE/
        };
        exec(
            function () {
                self.initialized = true;
                console.log('chromecast initialized');
                self.startOnEndedListener();
                self.emit('initialized');
            },
            function (err) {
                console.error('chromecast init error', err);
            },
            "ChromeCast",
            "setAppId",
            [opt.appId]
        );
    }

    ChromeCast.prototype = {
        init: function () {
            var self = this;
            if (this.initialized) {
                this.startReceiverListener(null, function (err) {
                    if (err) {
                        throw new Error('cast initialization failed : ' + err);
                    } else {
                        globalConf.isAvailable = true;
                    }
                });

                this.attachCoreEvent();
                this.startCommonListeners();

            } else {
                this.once('initialized', function () {
                    return self.init();
                });
            }
            return this;
        },
        attachCoreEvent: function () {
            var self = this;
            this.onMessage('media', function (msg) {
                if (typeof(msg) === 'string') {
                    msg = JSON.parse(msg);
                }
                self.emit(msg.name, msg.message);
            });
            this.on('timeupdate', function (evt) {
                self.config.currentTime = evt.currentTime;
            });
        },
        startListener: function (type, callback, fallback) {
            exec(
                function (response) {
                    callback && callback(response);
                },
                function (err) {
                    console.error(type, err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "startListener",
                [type]
            );
        },
        startCommonListeners: function (callback, errback) {
            var self = this;
            if (globalConf.statusListener) {
                return;
            }
            globalConf.statusListener = true;
            this.startListener('status', function (status) {
                    if (self.config.session) {
                        self.config.session.media[0] = status;
                    }
                    evt.emit('mediaStatus', status);
                    !globalConf.statusListener && callback && callback(status);
                },
                function (err) {
                    errback && errback(err);
                });

            this.startListener('session', function (session) {
                    if (typeof(session.media) === 'string') {
                        session.media = JSON.parse(session.media);
                    }
                    evt.emit("requestSessionSuccess", session);
                    self.config.session = session;
                    if (!session.wasLaunched) {
                        self.emit('activeSession');
                    }
                },
                function (err) {
                    evt.emit("requestSessionError", new Error(err));
                });
        },
        startReceiverListener: function (callback, errback) {
            var self = this;
            if (globalConf.receiverListener) {
                return;
            }
            globalConf.receiverListener = true;
            this.startListener('receivers', function (receivers) {
                    console.log("receivers", receivers);
                    globalConf.receiverList = receivers.filter(function (rec) {
                        return !self.config.defaultRouteRegxp.exec(rec.id);
                    });

                    if (globalConf.receiverList.length) {
                        self.config.receiverAvailable = true;
                        evt.emit('receiverAvailable');
                    } else {
                        delete self.config.receiverAvailable;
                    }
                    evt.emit('receivers', globalConf.receiverList);
                    !globalConf.receiverListener && callback && callback(receivers);
                },
                function (err) {
                    delete self.config.receiverAvailable;
                    errback && errback(err);
                });
        },
        startOnEndedListener: function () {
            var self = this;
            this.startListener('sessionEnded', function () {
                    console.log("session ended");
                    self.emit('closed');
                    self.emit('sessionUpdated', false);
                },
                function (err) {

                });
        },
        getSelectedReceiver: function () {
            var i = 0, l = globalConf.receiverList.length, receiver;
            for (; i < l; i++) {
                receiver = globalConf.receiverList[i];
                if (receiver.isSelected) {
                    return receiver;
                }
            }
        },
        getReceiver: function (id) {
            if (typeof(id) === 'undefined') {
                return globalConf.receiverList;
            }
            var i = 0, l = globalConf.receiverList.length, rec;
            for (; i < l; i++) {
                rec = globalConf.receiverList[i];
                if (rec.id === id) {
                    return rec;
                }
            }
        },
        on: function (evtName, fnc) {
            return evt.on(evtName, fnc);
        },
        once: function (evtName, fnc) {
            return evt.once(evtName, fnc);
        },
        emit: function () {
            return evt.emit.apply(evt, arguments);
        },

        launch: function (receiverInfo) {
            if (!this.config.receiverAvailable) {
                console.warn('no receiver is available');
                return;
            }
            var promise = {};
            exec(
                function () {
                    setTimeout(function () {
                        promise.callback && promise.callback();
                    }, 0);
                },
                function (err) {
                    setTimeout(function () {
                        promise.fallback && promise.fallback(err);
                    }, 0);

                },
                "ChromeCast",
                "setReceiver",
                [receiverInfo.index]
            );
            return {
                then: function (cb, fb) {
                    promise.callback = cb;
                    promise.fallback = fb;
                }
            };
        },
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
         * @param {Object|String|Number|Boolean} msg
         * @param {Function} callback
         */
        sendMessage: function (channelName, msg, callback) {
            if (msg === null || msg === undefined) {
                msg = {};
            }
            if (typeof(msg) !== 'string') {
                msg = JSON.stringify(msg);
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
        loadMedia: function (opt, callback, fallback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error("loadMedia failed", err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "loadMedia",
                [opt]
            );
        },
        playMedia: function (callback) {
            var self = this;
            exec(
                function (e) {
                    callback && callback(null, e);
                    self.emit('play', e);
                },
                function (err) {
                    console.error("playMedia failed", err);
                    callback && callback(err);
                },
                "ChromeCast",
                "playMedia",
                []
            );
        },
        pauseMedia: function (callback) {
            var self = this;
            exec(
                function (e) {
                    callback && callback(null, e);
                    self.emit('pause');
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "pauseMedia",
                []
            );
        },
        stopMedia: function (callback) {
            var self = this;
            exec(
                function (e) {
                    callback && callback(null, e);
                    self.emit('stop');
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "stopMedia",
                []
            );
        },
        seekMedia: function (position, callback) {
            var self = this;
            exec(
                function (e) {
                    callback && callback(null, e);
                    self.emit('seek');
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
        setReceiverVolume: function (vol, callback, fallback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolume error: ', err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "setDeviceVolume",
                [vol]
            );
        },
        setReceiverVolumeBy: function (vol, callback, fallback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setMediaVolume error: ', err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "setDeviceVolumeBy",
                [vol]
            );
        },
        toggleReceiverMute: function (callback, fallback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('toggleReceiverMute error: ', err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "toggleMuted",
                []
            );
        },
        setReceiverMuted: function (muted, callback, fallback) {
            exec(
                function (e) {
                    callback && callback(null, e);
                },
                function (err) {
                    console.error('setReceiverMuted error: ', err);
                    fallback && fallback(err);
                },
                "ChromeCast",
                "setMuted",
                [muted]
            );
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
        }

    };
    module.exports = ChromeCast;
})();