package io.coinswap.market;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import io.coinswap.client.Currency;
import io.coinswap.client.EventEmitter;
import io.coinswap.client.SwapCollection;
import io.coinswap.net.Connection;
import io.coinswap.swap.AtomicSwap;
import io.coinswap.swap.AtomicSwapClient;
import io.coinswap.swap.AtomicSwapTrade;
import net.minidev.json.JSONObject;
import io.mappum.altcoinj.core.Coin;
import io.mappum.altcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Connects to an order-matching service to find counterparties to trade with. When market orders
 * or trade requests get filled by the service, TradeClient will create `market.AtomicSwapClient`s
 * to execute the trade with the atomic swap protocol.
 *
 * Note that even though the order-matching service is centralized, no trust is being placed in it
 * (it never holds the funds involved in the trades), so this exchange scheme is fully trustless.
 */
public class TradeClient extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TradeClient.class);

    public static final String HOST = "trade.mercuryex.com";
    public static final int PORT = Connection.PORT;
    public static final io.mappum.altcoinj.core.Coin FEE = Coin.ZERO;

    public static final long TIME_EPSILON = 60 * 60 * 4; // 4 hours

    private Map<String, Currency> currencies;
    private Map<String, Ticker> tickers;
    private Connection connection;

    private Queue<AtomicSwapTrade> tradeRequests;
    private Map<AtomicSwapTrade, SettableFuture<Map>> requestFutures;
    private Map<Integer, Order> orders;
    private Queue<Order> cancelRequests;

    private Map<String, OrderBook> depth;

    private ReentrantLock lock = Threading.lock(getClass().getCanonicalName());
    private Semaphore requestSignal;

    private SwapCollection swapCollection;

    private EventEmitter emitter;

    public TradeClient(List<Currency> currencies, SwapCollection swapCollection) {
        checkNotNull(currencies);
        this.currencies = new HashMap<String, Currency>();
        for(Currency c : currencies) {
            this.currencies.put(c.getId().toLowerCase(), c);
        }

        initDepth();

        tickers = new ConcurrentHashMap<String, Ticker>();

        tradeRequests = new ConcurrentLinkedQueue<>();
        requestSignal = new Semaphore(0, true);
        requestFutures = new HashMap<>();
        orders = new HashMap<Integer, Order>();
        cancelRequests = new ConcurrentLinkedQueue<>();
        emitter = new EventEmitter();

        this.swapCollection = swapCollection;
        swapCollection.addEventListener(new SwapCollection.StateListener() {
            @Override
            public void onStateChange(AtomicSwap swap) {
                emitter.emit("swap", swap);
            }
        }, Threading.SAME_THREAD);
    }

    private void initDepth() {
        depth = new HashMap<String, OrderBook>();
        for(Currency a : this.currencies.values()) {
            for(String id : a.getPairs()) {
                Currency b = this.currencies.get(id.toLowerCase());
                if(a.getIndex() > b.getIndex()) {
                    String pairId = a.getId().toLowerCase() + "/" + b.getId().toLowerCase();
                    depth.put(pairId, new OrderBook());
                }
            }
        }
    }

    @Override
    public void run() {
        connect();
        resumeSwaps();

        while(true) {
            try {
                requestSignal.acquire();
                lock.lock();
                try {
                    // TODO: should we change to submit trade requests without waiting for responses?
                    while (isConnected() && !tradeRequests.isEmpty()) {
                        submitTrade(tradeRequests.remove());
                    }
                    while (isConnected() && !cancelRequests.isEmpty()) {
                        submitCancel(cancelRequests.remove());
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void connect() {
        try {
            InputStream ksStream = getClass().getResourceAsStream("/keys/coinswap.jks");
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(ksStream, "password".toCharArray());

            TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory factory = context.getSocketFactory();

            while(true) try {
                log.info("Connecting to trade server (" + HOST + ":" + PORT + ")");
                SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);
                connection = new Connection(socket);
                initListeners();

                // wait until we get a message on the trade channel before we try to send stuff
                connection.onMessage("trade", new Connection.ReceiveListener() {
                    @Override
                    public void onReceive(Map data) {
                        connection.removeMessageListener("trade", this);
                        requestSignal.release();
                        emitter.emit("connect", null);
                    }
                });

                connection.start();
                log.info("Connection started");

                break;

            } catch (Exception e) {
                log.info("Could not connect to trade server");
                Thread.sleep(5000);
            }

        } catch(Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void initListeners() {
        final TradeClient parent = this;
        connection.onMessage("trade", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map message) {
                String method = (String) checkNotNull(message.get("method"));

                if(method.equals("fill")) {
                    parent.onFill(message);
                }
            }
        });

        connection.onMessage("ticker", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map message) {
                parent.onTicker(message);
            }
        });

        connection.onMessage("feed", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map message) {
                parent.onFeed(message);
            }
        });

        connection.onMessage("info", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map message) {
                int version = (int) message.get("version");
                emitter.emit("version", version);
            }
        });

        connection.onMessage("depth", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map message) {
                boolean add = ((String) checkNotNull(message.get("type"))).equals("add");
                String pairId = (String) checkNotNull(message.get("pair"));
                List<List<Object>> ordersJson = (List<List<Object>>) checkNotNull(message.get("orders"));
                for(List<Object> obj : ordersJson) {
                    Order order = Order.fromJson(obj);
                    if(add) depth.get(pairId).add(order);
                    else depth.get(pairId).remove(order);
                }
                emitter.emit("depth", pairId);
                emitter.emit("depth:"+pairId, null);
            }
        });

        connection.onDisconnect(new Runnable() {
            @Override
            public void run() {
                connection = null;
                log.info("Disconnected from trade server");

                // remove orders since server removes them on disconnect, then reinsert trades into queue
                for(Map.Entry<Integer, Order> entry : orders.entrySet()) {
                    orders.remove(entry.getKey());
                    Order order = entry.getValue();
                    Coin[] quantities = Order.getTotals(ImmutableList.of(order));
                    AtomicSwapTrade trade =
                            new AtomicSwapTrade(order.bid, order.currencies, quantities, FEE);
                    queueTrade(trade);
                }

                // clear orderbooks
                initDepth();

                emitter.emit("disconnect", null);
                connect();
            }
        });
    }

    private void resumeSwaps() {
        List<AtomicSwap> swaps = swapCollection.getPending();
        if(swaps.size() == 0) return;
        log.info("Resuming " + swaps.size() + " pending swaps");
        for(AtomicSwap swap : swaps) {
            startSwap(swap, getPair(swap.trade.coins));
        }
    }

    private SettableFuture queueTrade(AtomicSwapTrade trade) {
        lock.lock();
        try {
            tradeRequests.add(trade);
            SettableFuture<Map> future = SettableFuture.create();
            requestFutures.put(trade, future);
            return future;
        } finally {
            lock.unlock();
        }
    }

    public SettableFuture trade(AtomicSwapTrade trade) {
        SettableFuture future = queueTrade(trade);
        requestSignal.release();
        return future;
    }

    public void cancel(int id) {
        lock.lock();
        try {
            Order order = orders.remove(id);
            if (order != null) cancelRequests.add(order);
            requestSignal.release();
        } finally {
            lock.unlock();
        }
    }

    private void submitTrade(AtomicSwapTrade trade) {
        JSONObject req = new JSONObject();
        req.put("channel", "trade");
        req.put("method", "trade");
        req.put("trade", trade.toJson());
        Map res = connection.request(req);

        if(res.containsKey("order")) {
            Order order = Order.fromJson((Map) res.get("order"));

            // make sure the server didn't open the order at the wrong price/amount
            checkState(!order.amount.isGreaterThan(trade.getAmount()));
            checkState(order.price.equals(trade.getPrice()));
            checkState(order.bid == trade.buy);

            orders.put(order.id, order);
            log.info("Opened order: " + order.toJson().toString());
            emitter.emit("orders:change", null);
            emitter.emit("orders:add", order.toJson());
        }

        if(res.containsKey("swaps")) {
            List<Map> swapsJson = (List<Map>) res.get("swaps");
            List<AtomicSwap> swaps = new ArrayList<>(swapsJson.size());
            Coin totalAmount = Coin.ZERO;

            // deserialize list of AtomicSwaps
            for(Map swapJson : swapsJson) {
                AtomicSwap swap = AtomicSwap.fromJson(swapJson);
                swaps.add(swap);

                // verify that the coin IDs and fee are what we expected
                checkState(swap.trade.coins[0].equals(trade.coins[0]));
                checkState(swap.trade.coins[1].equals(trade.coins[1]));
                checkState(swap.trade.fee.equals(trade.fee));
                checkState(swap.trade.buy == trade.buy);

                // verify price is at or better than what we expected
                int comp = swap.trade.getPrice().compareTo(trade.getPrice());
                checkState(comp == 0 || comp == (trade.buy ? -1 : 1));

                // make sure time value is correct (otherwise server could get us to use unreasonable locktimes)
                checkState(Math.abs(System.currentTimeMillis() / 1000 - swap.getTime()) < TIME_EPSILON);

                // increment total
                totalAmount = totalAmount.add(swap.trade.getAmount());

                emitter.emit("swap", swap);
            }

            // verify the total amount isn't bigger than we requested
            checkState(!totalAmount.isGreaterThan(trade.getAmount()));

            log.debug("Swaps are valid, starting AtomicSwapClients");
            for(AtomicSwap swap : swaps) {
                startSwap(swap, getPair(trade.coins));
                swapCollection.add(swap);
            }
        }

        requestFutures.remove(trade).set(res);
    }

    private void submitCancel(Order order) {
        JSONObject req = new JSONObject();
        req.put("channel", "trade");
        req.put("method", "cancel");
        req.put("order", order.toJson());
        connection.write(req);
        // TODO: get response
        emitter.emit("orders:change", null);
        emitter.emit("orders:cancel", order.toJson());
    }

    private void onFill(Map message) {
        AtomicSwap swap = AtomicSwap.fromJson((Map) message.get("swap"));

        // make sure time value is correct (otherwise server could get us to use unreasonable locktimes)
        // (we use blockchain timestamp instead of system time, in case system time is set weirdly)
        long time = currencies.get("btc").getWallet().chain().getChainHead().getHeader().getTime().getTime();
        checkState(Math.abs(time / 1000 - swap.getTime()) < TIME_EPSILON);

        Coin totalVolume = Coin.ZERO,
             totalPrice = Coin.ZERO,
             remaining = swap.trade.getAmount();

        List<Integer> orderIds = (List<Integer>) message.get("orders");
        boolean partial = false;
        for(int id : orderIds) {
            Order order = checkNotNull(orders.get(id));
            checkState(swap.trade.buy == order.bid);
            checkState(order.currencies[0].equals(swap.trade.coins[0]));
            checkState(order.currencies[1].equals(swap.trade.coins[1]));

            remaining = swap.trade.getAmount().subtract(totalVolume);
            checkState(remaining.isGreaterThan(Coin.ZERO));

            Coin toAdd = order.amount;
            if(order.amount.isGreaterThan(remaining)) {
                toAdd = remaining;
                partial = true;
            }

            totalVolume = totalVolume.add(toAdd);
            Coin price = AtomicSwapTrade.getTotal(order.price, toAdd);
            totalPrice = totalPrice.add(price);
        }

        checkState(totalPrice.equals(swap.trade.quantities[1]));

        for(int i = 0; i < orderIds.size() - 1; i++) {
            orders.remove(orderIds.get(i));
        }

        // If the last order is being partially filled, update its amount.
        // Otherwise, just remove it.
        int lastOrder = orderIds.get(orderIds.size() - 1);
        if(partial) {
            orders.get(lastOrder).amount = orders.get(lastOrder).amount.subtract(remaining);
        } else {
            orders.remove(lastOrder);
        }

        emitter.emit("orders:change", null);
        emitter.emit("orders:fill", orderIds);

        startSwap(swap, getPair(swap.trade.coins));
        swapCollection.add(swap);
    }

    private void startSwap(AtomicSwap swap, Currency[] pair) {
        AtomicSwapClient client =
                new AtomicSwapClient(swap, connection, pair);
        client.start();
    }

    private void onFeed(Map message) {
        emitter.emit("feed", message);
    }

    private void onTicker(Map message) {
        String pair = (String) checkNotNull(message.get("pair"));
        Ticker ticker = Ticker.fromJson((Map) checkNotNull(message.get("data")));
        Ticker previousTicker = tickers.get(pair);
        if(previousTicker != null && message.get("history") == null) {
            // if server didn't send history data, get it from the previous ticker
            // (we sometimes don't send it as an optimization since it takes up a lot of bandwidth)
            ticker.history = previousTicker.history;
            // update last period based on current data
            TickerHistory lastPeriod = ticker.history.get(ticker.history.size()-1);
            if(lastPeriod != null) {
                lastPeriod.end = ticker.last;
                // TODO: fill trade count and volume
            }
        }
        tickers.put(pair, ticker);
        emitter.emit("ticker", pair);
        emitter.emit("ticker:" + pair, pair);
    }

    public Ticker getTicker(String pair) {
        return tickers.get(pair);
    }

    public List<AtomicSwap> getPendingSwaps() {
        return swapCollection.getPending();
    }

    public List<AtomicSwap> getSwaps() {
        return swapCollection.getAll();
    }

    public void on(String event, EventEmitter.Callback cb) {
        emitter.on(event, cb);
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public Currency[] getPair(String[] ids) {
        return new Currency[]{
                currencies.get(ids[0].toLowerCase()),
                currencies.get(ids[1].toLowerCase())
        };
    }

    public List<Order> getOrders() {
        return new ArrayList<>(orders.values());
    }

    public Map<String, Object> getDepth(String pairId, int n) {
        return depth.get(pairId).toJson(n);
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }
}
