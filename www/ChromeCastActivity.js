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


        onMediaStatus: function (fnc) {
            return this.caster.on('mediaStatus', fnc);
        },

        stop: function () {

        }
    };
    module.exports = ChromeCastActivity;
})();