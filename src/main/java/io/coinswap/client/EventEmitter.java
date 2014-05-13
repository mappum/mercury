package io.coinswap.client;

import com.sun.javafx.scene.control.skin.VirtualFlow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class EventEmitter {
    protected Map<String, List<Callback>> listeners;

    public EventEmitter() {
        listeners = new HashMap<String, List<Callback>>();
    }

    public void on(String event, Callback cb) {
        if(!listeners.containsKey(event))
            listeners.put(event, new LinkedList<Callback>());
        List<Callback> l = listeners.get(event);
        l.add(cb);
    }

    public void emit(String event, Object arg) {
        List<Callback> l = listeners.get(event);
        if(l == null) return;

        for(Callback cb : l)
            cb.f(arg);
    }

    public interface Callback {
        void f(Object a);
    }
}
