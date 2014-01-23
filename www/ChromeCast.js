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
        this.appId = opt.appId;
        exec(
            function () {
                self.initialized = true;
                console.log('chromecast initialized');
                self.emit('initialized');
            },
            function (err) {
                console.log('chromecast init error',err);
            },
            "ChromeCast",
            "setAppId",
            [this.appId]
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
            } else {
                this.once('initialized', function () {
                    return self.init();
                });
            }
            return this;
        },
        startReceiverListener: function (callback) {
            if (globalConf.receiverListener) {
                return;
            }
            globalConf.receiverListener = true;
            exec(
                function (receivers) {
                    evt.emit('receiver', receivers);
                    globalConf.receiverList = receivers;
                    !globalConf.receiverListener && callback && callback(receivers);
                },
                function (err) {
                    callback && callback(err);
                },
                "ChromeCast",
                "startReceiverListener",
                []
            );
        },
        getReceiver: function (id) {
            if(typeof(id) === 'undefined'){
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
            var self = this, callback, fallback;
            exec(
                function () {
                    callback && callback(new ChromeCast.Activity(receiverInfo, self));
                },
                function (err) {
                    fallback && fallback(err);
                },
                "ChromeCast",
                "setReceiver",
                [receiverInfo.index]
            );
            return {
                then: function (cb, fb) {
                    callback = cb;
                    fallback = fb;
                }
            };
        }
    };
    module.exports = ChromeCast;
})();