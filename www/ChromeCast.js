/*global require,define,console,module,CustomEvent*/
/*jslint plusplus: true */
/*jslint expr:true */
(function () {
    "use strict";

    var exec = require('cordova/exec'),
        globalConf = {
            receiverListener: false,
            receiverList: [],
            isAvailable: false
        }, evt = new CustomEvent();

    function ChromeCast(opt) {
        var self = this;
        this.config = {
            appId: opt.appId
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
                this.startReceiverListener(function (err) {
                    if (err) {
                        throw new Error('cast initialization failed : ' + err);
                    } else {
                        globalConf.isAvailable = true;
                        window.postMessage && window.postMessage({
                            source: 'CastApi',
                            event: 'Hello'
                        }, window.location.href);
                    }
                });
                this.startStatusListener();
            } else {
                this.once('initialized', function () {
                    return self.init();
                });
            }
            return this;
        },
        startStatusListener: function (callback, errback) {
            var self = this;
            if (globalConf.statusListener) {
                return;
            }
            globalConf.statusListener = true;
            exec(
                function (status) {
                    if (self.config.session) {
                        self.config.session.media[0] = status;
                    }
                    evt.emit('mediaStatus', status);
                    !globalConf.statusListener && callback && callback(status);
                },
                function (err) {
                    errback && errback(err);
                },
                "ChromeCast",
                "startStatusListener",
                []
            );
            exec(
                function (session) {
                    evt.emit("requestSessionSuccess", session);
                    self.config.session = session;
                },
                function (err) {
                    evt.emit("requestSessionError", new Error(err));
                },
                "ChromeCast",
                "startSessionListener",
                []
            );
        },
        startReceiverListener: function (callback, errback) {
            if (globalConf.receiverListener) {
                return;
            }
            globalConf.receiverListener = true;
            exec(
                function (receivers) {
                    receivers.length && evt.emit('receiverAvailable');
                    evt.emit('receiver', receivers);
                    globalConf.receiverList = receivers;
                    !globalConf.receiverListener && callback && callback(receivers);
                },
                function (err) {
                    errback && errback(err);
                },
                "ChromeCast",
                "startReceiverListener",
                []
            );
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
        startOnEndedListener: function () {
            var self = this;
            exec(
                function () {
                    console.log("session ended");
                    self.emit('closed');
                },
                function (err) {

                },
                "ChromeCast",
                "startOnEndedListener",
                []
            );
        },
        launch: function (receiverInfo) {
            var self = this, promise = {};
            setTimeout(function () {
                exec(
                    function () {
                        self.config.activity = new ChromeCast.Activity(receiverInfo, self);
                        promise.callback && promise.callback(self.config.activity);

                    },
                    function (err) {
                        promise.fallback && promise.fallback(err);
                    },
                    "ChromeCast",
                    "setReceiver",
                    [receiverInfo.index]
                );
            }, 0);
            return {
                then: function (cb, fb) {
                    promise.callback = cb;
                    promise.fallback = fb;
                }
            };
        },
        loadMedia: function (opt, callback) {
            return this.config.activity.loadMedia(opt, callback);
        },
        onMessage: function (channelName, fnc) {
            return this.config.activity.onMessage(channelName, fnc);
        },
        sendMessage: function (channelName, msg, callback) {
            return this.config.activity.sendMessage(channelName, msg, callback);
        },
        playMedia:function(){
            return this.config.activity.playMedia();
        },
        pauseMedia:function(){
            return this.config.activity.pauseMedia();
        },
        seekMedia:function(position,callback){
            return this.config.activity.seekMedia(position,callback);
        },
        seekMediaBy:function(position,callback){
            return this.config.activity.seekMediaBy(position,callback);
        },
        setReceiverVolume:function(vol,callback){
            return this.config.activity.setReceiverVolume(vol,callback);
        },
        toggleReceiverMute:function(){
            return this.config.activity.toggleReceiverMute();
        },
        setReceiverMuted:function(muted){
            return this.config.activity.setReceiverMuted(muted);
        }
    };
    module.exports = ChromeCast;
})();