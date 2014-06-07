package io.coinswap.net;

import com.google.bitcoin.utils.Threading;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Connection extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Connection.class);

    private SSLSocket socket;
    private Map<String, List<ReceiveListener>> listeners;
    private BufferedWriter out;
    private BufferedReader in;

    private final ReentrantLock lock = Threading.lock("io.coinswap.net.Connection");

    public Connection(SSLSocket socket) {
        this.socket = socket;
        this.listeners = new HashMap<String, List<ReceiveListener>>();

        try {
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch(IOException ex) {
            log.error(ex.getMessage());
        }
    }

    public void addListener(String channel, ReceiveListener listener) {
        List<ReceiveListener> list;

        lock.lock();
        try {
            if (!listeners.containsKey(channel)) {
                list = new LinkedList<ReceiveListener>();
                listeners.put(channel, list);
            } else {
                list = listeners.get(channel);
            }

            list.add(listener);
        } finally {
            lock.unlock();
        }
    }

    public void removeListener(String channel, ReceiveListener listener) {
        lock.lock();
        try {
            if(!listeners.containsKey(channel)) return;

            List<ReceiveListener> list = listeners.get(channel);
            list.remove(listener);
            if(list.isEmpty()) listeners.remove(channel);
        } finally {
            lock.unlock();
        }
    }

    public void write(Map obj) {
        String data = ((JSONObject) obj).toJSONString(JSONStyle.LT_COMPRESS);

        lock.lock();
        try {
            out.write(data);
            out.write("\r\n");
            out.flush();
        } catch (IOException ex) {
            log.error(ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void run() {
        try {
            String data;
            while ((data = in.readLine()) != null) {
                JSONObject obj = (JSONObject) JSONValue.parse(data);

                List<ReceiveListener> channelListeners;
                lock.lock();
                try {
                    channelListeners = listeners.get((String) obj.get("channel"));
                } finally {
                    lock.unlock();
                }

                if (channelListeners != null) {
                    for (ReceiveListener listener : channelListeners) {
                        // TODO: run listeners on different threads
                        listener.onReceive(obj);
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    public interface ReceiveListener {
        public void onReceive(Map data);
    }
}
