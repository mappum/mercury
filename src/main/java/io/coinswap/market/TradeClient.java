package io.coinswap.market;

import com.google.common.util.concurrent.SettableFuture;
import io.coinswap.client.Currency;
import io.coinswap.client.EventEmitter;
import io.coinswap.net.Connection;
import io.coinswap.swap.AtomicSwap;
import io.coinswap.swap.AtomicSwapClient;
import io.coinswap.swap.AtomicSwapTrade;
import net.minidev.json.JSONObject;
import org.bitcoinj.core.Coin;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class TradeClient extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TradeClient.class);

    public static final String HOST = "localhost";
    public static final int PORT = Connection.PORT;
    public static final org.bitcoinj.core.Coin FEE = Coin.ZERO;

    public static final long TIME_EPSILON = 60;

    private Map<String, Currency> currencies;
    private Connection connection;

    private Queue<AtomicSwapTrade> tradeRequests;
    private Map<AtomicSwapTrade, SettableFuture<Map>> requestFutures;
    private Map<Integer, Order> orders;
    private Queue<Order> cancelRequests;

    private EventEmitter emitter;

    public TradeClient(List<Currency> currencies) {
        checkNotNull(currencies);
        this.currencies = new HashMap<String, Currency>();
        for(Currency c : currencies) {
            this.currencies.put(c.getId().toLowerCase(), c);
        }

        tradeRequests = new ConcurrentLinkedQueue<>();
        requestFutures = new HashMap<>();
        orders = new HashMap<Integer, Order>();
        cancelRequests = new ConcurrentLinkedQueue<>();

        emitter = new EventEmitter();
    }

    @Override
    public void run() {
        connect();

        final TradeClient parent = this;
        connection.onMessage("trade", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map res) {
                String method = (String) checkNotNull(res.get("method"));

                if(method.equals("fill")) {
                    parent.onFill(res);
                }
            }
        });

        while(true) {
            // TODO: should we change to submit trade requests without waiting for responses?
            while (!tradeRequests.isEmpty()) {
                submitTrade(tradeRequests.remove());
            }
            while (!cancelRequests.isEmpty()) {
                submitCancel(cancelRequests.remove());
            }

            // TODO: use better signaling instead of polling
            try {
                Thread.sleep(250);
            } catch(Exception e) {}
        }
    }

    private void connect() {
        try {
            FileInputStream storeFile = new FileInputStream("coinswap.jks");
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(storeFile, "password".toCharArray());

            TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory factory = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);

            connection = new Connection(socket);
            connection.start();

        } catch(Exception e) {
            log.error(e.getMessage());
        }
    }

    public SettableFuture trade(AtomicSwapTrade trade) {
        tradeRequests.add(trade);

        SettableFuture<Map> future = SettableFuture.create();
        requestFutures.put(trade, future);
        return future;
    }

    public void cancel(int id) {
        Order order = orders.remove(id);
        if(order != null) cancelRequests.add(order);
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
            }

            // verify the total amount isn't bigger than we requested
            checkState(!totalAmount.isGreaterThan(trade.getAmount()));

            log.debug("Swaps are valid, starting AtomicSwapClients");
            for(AtomicSwap swap : swaps) {
                Currency[] swapCurrencies = new Currency[]{
                   currencies.get(trade.coins[0].toLowerCase()),
                   currencies.get(trade.coins[1].toLowerCase())
                };

                // we are alice if we are selling and the second currency supports hashlock TXs,
                // or if we are buying and the second currency doesn't support them.
                // otherwise, we are bob
                boolean alice = trade.buy ^ !swapCurrencies[1].supportsHashlock();

                AtomicSwapClient client =
                        new AtomicSwapClient(swap, connection, alice, swapCurrencies);
                client.start();
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
    }

    private void onFill(Map message) {
        AtomicSwap swap = AtomicSwap.fromJson((Map) message.get("swap"));

        // make sure time value is correct (otherwise server could get us to use unreasonable locktimes)
        checkState(Math.abs(System.currentTimeMillis() / 1000 - swap.getTime()) < TIME_EPSILON);

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

        emitter.emit("fill", orderIds);

        Currency[] swapCurrencies = new Currency[]{
            currencies.get(swap.trade.coins[0].toLowerCase()),
            currencies.get(swap.trade.coins[1].toLowerCase())
        };

        boolean alice = swap.trade.buy ^ !swapCurrencies[1].supportsHashlock();

        AtomicSwapClient client =
                new AtomicSwapClient(swap, connection, alice, swapCurrencies);
        client.start();
    }

    public Ticker getTicker(String pairId) {
        Map req = new JSONObject();
        req.put("channel", "trade");
        req.put("method", "ticker");
        req.put("pair", pairId);

        Map res = connection.request(req);
        Map ticker = (Map) res.get("data");
        return Ticker.fromJson(ticker);
    }

    public void on(String event, EventEmitter.Callback cb) {
        emitter.on(event, cb);
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public List<Order> getOrders() {
        return new ArrayList<>(orders.values());
    }
}
