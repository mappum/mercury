package io.coinswap.net;

import com.google.common.util.concurrent.SettableFuture;
import io.mappum.altcoinj.utils.Threading;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

public class Connection extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Connection.class);

    public static final int PORT = 16800;

    public static final int SO_TIMEOUT = 10 * 1000; // 10 seconds
    public static final int KEEPALIVE_INTERVAL = SO_TIMEOUT / 2;

    private SSLSocket socket;
    private Map<String, List<ReceiveListener>> listeners;
    private BufferedWriter out;
    private BufferedReader in;
    private boolean keepalive;

    private ScheduledThreadPoolExecutor executor;
    private SettableFuture disconnectFuture;

    private int id = 0;

    private final ReentrantLock lock = Threading.lock("io.coinswap.net.Connection");

    // TODO: add asynchronous writing (push messages into a queue)

    public Connection(SSLSocket socket, boolean keepalive) {
        this.socket = socket;
        this.listeners = new HashMap<String, List<ReceiveListener>>();
        this.disconnectFuture = SettableFuture.create();
        this.keepalive = keepalive;

        try {
            socket.setSoTimeout(SO_TIMEOUT);
        } catch (SocketException ex) {
            log.error(ex.getMessage());
        }

        try {
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch(IOException ex) {
            log.error(ex.getMessage());
        }
    }

    public Connection(SSLSocket socket) {
        this(socket, true);
    }

    public Map waitForMessage(final String channel) {
        final SettableFuture<Map> future = SettableFuture.create();
        onMessage(channel, new ReceiveListener() {
            @Override
            public void onReceive(Map data) {
                future.set(data);
                removeMessageListener(channel, this);
            }
        });
        try {
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void onMessage(String channel, ReceiveListener listener) {
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

    public void onDisconnect(Runnable listener) {
        disconnectFuture.addListener(listener, Threading.SAME_THREAD);
    }

    public void removeMessageListener(String channel, ReceiveListener listener) {
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

    public void removeMessageListeners(String channel) {
        lock.lock();
        try {
            if(!listeners.containsKey(channel)) return;

            listeners.remove(channel);
        } finally {
            lock.unlock();
        }
    }

    public void write(Map obj) {
        String data = ((JSONObject) obj).toJSONString(JSONStyle.LT_COMPRESS);
        log.info(">> " + data);

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

    public Map request(Map req) {
        final SettableFuture<Map> responseFuture = SettableFuture.create();
        String channel = (String) checkNotNull(req.get("channel"));
        final String requestId = id++ + "";
        req.put("request", requestId);

        ReceiveListener onRes = new ReceiveListener() {
            @Override
            public void onReceive(Map data) {
                String responseId = (String) data.get("response");
                if(responseId != null && responseId.equals(requestId))
                    responseFuture.set(data);
            }
        };

        onMessage(channel, onRes);
        write(req);

        Map res = null;
        try {
            res = responseFuture.get();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            removeMessageListener(channel, onRes);
        }
        return res;
    }

    public void run() {
        if(keepalive) startKeepalive();

        try {
            String data;
            while (socket.isConnected() && (data = in.readLine()) != null) {
                if(data.length() == 0) continue;

                log.info("<< " + data);
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
                        // TODO: run listeners on different threads?
                        listener.onReceive(obj);
                    }
                }
            }
        } catch (Exception ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        } finally {
            close();
        }
    }

    public void close() {
        if(keepalive) executor.shutdownNow();

        try {
            socket.close();
            in.close();
            out.close();
        } catch(IOException ex) {
            log.error(ex.getMessage());
        } finally {
            // TODO: maybe indicate how the connection closed?
            disconnectFuture.set(null);
        }
    }

    private void startKeepalive() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    out.write("\r\n");
                    out.flush();
                } catch (IOException ex) {
                    log.error(ex.getMessage());
                } finally {
                    lock.unlock();
                }
            }
        }, KEEPALIVE_INTERVAL, KEEPALIVE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public boolean isConnected() {
        return isAlive();
    }

    public SSLSocket getSocket() { return socket; }

    public interface ReceiveListener {
        public void onReceive(Map data);
    }
}
