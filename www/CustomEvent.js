/*global require,define,console,module*/
/*jslint plusplus: true */
/*jslint expr:true */
(function(){
    'use strict';
    var slice = Array.prototype.slice;

    function CustomEvent(args) {
        this._listeners = {};
        this.channelSeparator = '/';
        if (args) {
            if ('channelSeparator' in args) {
                this.channelSeparator = args.channelSeparator;
            }
        }
    }

    CustomEvent.prototype = {
        /**
         * @method buildListener
         * @private
         * @param {Function|Object} listener
         * @return {{id:String,content:Function}}
         */
        buildListener: function (listener) {
            var _id = '#Listener:' + (new Date().getTime() + Math.random());
            if (typeof listener === 'function') {
                listener = {
                    id: _id,
                    content: listener
                };
            } else {
                if (!listener.id) {
                    listener.id = _id;
                }
            }
            return listener;
        },
        /**
         * @method on
         * @param {String} eventName
         * @param {Function|Object} listener
         * @param {String} listener.id if listener is an object, define listener id
         * @param {Function} listener.content if listener is an object, define function
         * @return {{id:Number,remove:Function}}
         */
        on: function (eventName, listener) {
            var self = this;
            if (typeof this._listeners[eventName] === "undefined") {
                this._listeners[eventName] = [];
            }
            listener = this.buildListener(listener);
            if (this.hasListener(eventName, listener.id)) {
                throw new Error('listener id ' + listener.id + ' duplicated');
            }
            this._listeners[eventName].push(listener);
            return {
                remove: function () {
                    return self.off(eventName, listener.id);
                },
                id: listener.id
            };
        },
        /**
         * listener is triggered only once
         * @method once
         * @param {String} eventName
         * @param {Function|Object} listener
         * @param {Function} listener.content
         * @param {String} listener.id
         * @return {{id:Number,remove:Function}}
         */
        once: function (eventName, listener) {
            var self = this;
            listener = this.buildListener(listener);
            var origContent = listener.content;
            listener.content = function () {
                self.off(eventName, listener.id);
                origContent.apply(self, arguments);
            };
            return this.on(eventName, listener);
        },
        /**
         * listener is removed when the callback return true
         *
         *      this.when('click',function(node){
             *          return node.id === 'container';
             *      });
         *
         * @method when
         * @param {String} event
         * @param {Function} callback
         * @returns {*}
         */
        when: function (event, callback) {
            var self = this;

            function check() {
                if (callback.apply(this, arguments)) {
                    self.off(event, check);
                }
            }

            check.listener = callback;
            self.on(event, check);
            return this;
        },
        /**
         * broadcast message to events, support wildcard (* or ?)
         *
         *      this.broadcast('channel1/*',message);
         *      this.broadcast('channel1/????/event1',message);
         *      this.broadcast('channel1/event1',msg1,msg2);
         *
         * @method broadcast
         * @param {String} wildcard
         */
        broadcast: function (wildcard) {
            var params = slice.call(arguments, 1), evtNames = this.getEventNamesByWildcard(wildcard),
                i = 0, l = evtNames.length, res = [];
            for (; i < l; i++) {
                res.push(this.emit.apply(this, [evtNames[i]].concat(params)));
            }
            return res;
        },
        /**
         * @method getEventNamesByWildcard
         * @param {String} wildcard
         * @return {Array.<String>}
         */
        getEventNamesByWildcard: function (wildcard) {
            var evts = [],
                self = this,
                reg = wildcard.replace(/(\?)/g,function (str, m) {
                    return '[\\w\'-]{1}';
                }).replace(/\*/g, function (key, idx, str) {
                    return str.charAt(idx + 1) !== '' ? '([\\S\\s][^' + self.channelSeparator + ']*)' : '.*?';
                });
            JSON.stringify(Object.keys(this._listeners)).replace(new RegExp('"(' + reg + ')"', 'g'), function (ignore, eventName) {
                evts.push(eventName);
            });
            return evts;
        },
        /**
         * example:
         *
         *      this.emit('evtName');
         *      this.emit('evtName',params);
         *      this.emit('evtName',param1,param2);
         *
         * @method emit
         * @param {String} eventType
         * @return {Array}
         */
        emit: function (eventType) {
            var params, res = [], listeners, len;
            if (!eventType) {  //falsy
                throw new Error("Event object missing 'eventName' property.");
            }
            params = slice.call(arguments, 1);
            if (this._listeners[eventType] instanceof Array) {
                listeners = this._listeners[eventType];
                len = listeners.length;
                //decrease the length (instead of increasing from 0)
                // in case listener is removed while emit method is running
                while (len--) {
                    if (listeners[len]) {
                        res.push({
                            id: listeners[len].id,
                            result: listeners[len].content.apply(this, params)
                        });
                    }
                }
            }
            return res;
        },
        /**
         * emit to a particular listener
         *
         *      this.emitTo('eventName','listenerId',param1,param2);
         *
         * @method emitTo
         * @param eventType
         * @param listenerId
         * @returns {Array}
         */
        emitTo: function (eventType, listenerId) {
            if (!eventType) {  //falsy
                throw new Error("Event object missing 'eventName' property.");
            }
            var params = slice.call(arguments, 2), res = [], listeners, l;
            if (this._listeners[eventType] instanceof Array) {
                listeners = this._listeners[eventType];
                l = listeners.length;
                while (l--) {
                    if (listeners[l] && listeners[l].id === listenerId) {
                        res.push({
                            id: listenerId,
                            result: listeners[l].content.apply(this, params)
                        });
                        break;
                    }
                }
                if (listenerId && !res.length) {
                    throw new Error('listener ' + listenerId + ' is not found in event ' + eventType);
                }
            }
            return res;
        },
        /**
         * remove a listener
         * @method off
         * @param {String} eventName
         * @param {String|Function} listener a listener id or listener handler
         */
        off: function (eventName, listener) {
            var res;
            if (res = this.hasListener(eventName, listener)) {
                this._listeners[eventName].splice(res.index, 1);
            }
        },
        hasEvent: function (eventName) {
            if (!eventName) {
                return Object.keys(this._listeners).length > 0;
            }
            return eventName in this._listeners;
        },
        eventList: function () {
            return Object.keys(this._listeners);
        },
        /**
         * @method hasListener
         * @param {String} eventName
         * @param {String|Function} listener
         * @return {Object}
         */
        hasListener: function (eventName, listener) {
            var listenerType = typeof(listener) === 'string' ? 'id' : 'content', listeners, i = 0, len;
            if ((listeners = this._listeners[eventName]) && (len = listeners.length) > 0) {
                for (; i < len; i++) {
                    if (listeners[i][listenerType] === listener) {
                        return {
                            index: i
                        };
                    }
                }
            }
            return null;
        },
        /**
         * @method hasListeners
         * @param {String} eventName event name
         * @return {Boolean}
         */
        hasListeners: function (eventName) {
            if (this._listeners[eventName] instanceof Array) {
                return this._listeners[eventName].length > 0;
            }
            return false;
        },
        /**
         * @method getListeners
         * @param {string} eventName event name
         * @return {Array.<Object>}
         */
        getListeners: function (eventName) {
            if (this._listeners[eventName] instanceof Array) {
                return this._listeners[eventName];
            }
        },
        /**
         * @method purgeListeners
         * @param {String} [evtName] if evtName is undefined, remove all the events
         */
        purgeListeners: function (evtName) {
            if (evtName) {
                delete this._listeners[evtName];
            } else {
                delete this._listeners;
                this._listeners = {};
            }
        }
    };
    module.exports = CustomEvent;
})();