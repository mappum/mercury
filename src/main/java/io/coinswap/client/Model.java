package io.coinswap.client;

import netscape.javascript.JSObject;

/**
 * Provides a wrapper to interface with a Backbone Model object
 */
public class Model {
    protected static Object[] empty = new Object[]{};

    protected JSObject object;
    protected int index = 0;
    protected EventEmitter emitter;

    public Model(JSObject object) {
        this.object = object;

        emitter = new EventEmitter();
        object.setMember("__emitter__", emitter);
        // TODO: support n arguments
        object.eval("this.on('all', function(e, a) {" +
                        "this.__emitter__.emit(e, a);" +
                    "})");
    }

    public void emit(String event, Object arg) {
        emitter.emit(event, arg);
    }

    public void on(String event, EventEmitter.Callback cb) {
        emitter.on(event, cb);
    }

    public void trigger(String event, String arg) {
        object.eval("this.trigger('" + event + "', JSON.parse('" + arg + "'))");
    }

    public void set(String key, Object value) {
        Object[] arguments = { key, value };
        call("set", arguments);
    }

    public Object get(String key) {
        String[] arguments = { key };
        return call("get", arguments);
    }

    public Object call(String method) {
        return call(method, empty);
    }

    public Object call(String method, Object[] arguments) {
        return object.call(method, arguments);
    }

    public JSObject getObject() { return object; }
}