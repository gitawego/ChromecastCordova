/*global require,define,console,module,cast,CustomEvent*/
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
    module.exports = {
        init: function () {
            var self = this;
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
        emit: function () {
            return evt.apply(evt, arguments);
        },
        launch: function (receiverInfo) {
            var self = this, callback, fallback;
            exec(
                function () {
                    callback && callback(new cast.Activity(receiverInfo, self));
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
})();