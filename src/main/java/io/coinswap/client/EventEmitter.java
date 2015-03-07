package io.coinswap.client;

import io.mappum.altcoinj.utils.Threading;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class EventEmitter {
    protected Map<String, List<Callback>> listeners;

    public EventEmitter() {
        listeners = new HashMap<String, List<Callback>>();
    }

    public void on(String event, Callback cb) {
        if (!listeners.containsKey(event))
            listeners.put(event, new LinkedList<Callback>());
        List<Callback> l = listeners.get(event);
        l.add(cb);
    }

    public void emit(String event, final Object arg) {
        List<Callback> l = listeners.get(event);
        if (l == null) return;

        for (final Callback cb : l) {
            cb.executor.execute(new Runnable() {
                @Override
                public void run() {
                    cb.f(arg);
                }
            });
        }
    }

    public static abstract class Callback {
        private Executor executor;

        public Callback() {
            this.executor = Threading.SAME_THREAD;
        }

        public Callback(Executor executor) {
            this.executor = executor;
        }

        public abstract void f(Object a);
    }
}

