package io.coinswap.market;

import com.google.common.util.concurrent.SettableFuture;
import io.coinswap.client.Currency;
import io.coinswap.net.Connection;
import io.coinswap.swap.AtomicSwap;
import io.coinswap.swap.AtomicSwapClient;
import io.coinswap.swap.AtomicSwapTrade;
import net.minidev.json.JSONObject;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

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

    private Queue<AtomicSwapTrade> requests;
    private Map<Integer, Order> orders;
    private Set<Integer> bids;

    public TradeClient(List<Currency> currencies) {
        checkNotNull(currencies);
        this.currencies = new HashMap<String, Currency>();
        for(Currency c : currencies) {
            this.currencies.put(c.getId().toLowerCase(), c);
        }

        requests = new ConcurrentLinkedQueue<>();
        orders = new HashMap<Integer, Order>();
        bids = new HashSet<Integer>();
    }

    @Override
    public void run() {
        connect();

        final TradeClient parent = this;
        connection.onMessage("trade", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map res) {
                String method = (String) res.get("method");

                if(method.equals("fill")) {
                    parent.onFill(res);
                }
            }
        });

        while(true) {
            while (!requests.isEmpty()) {
                submit(requests.remove());
            }
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

    public void trade(AtomicSwapTrade trade) {
        requests.add(trade);
    }

    private void submit(AtomicSwapTrade trade) {
        JSONObject req = new JSONObject();
        req.put("channel", "trade");
        req.put("method", "trade");
        req.put("trade", trade.toJson());
        Map res = connection.request(req);

        if(res.containsKey("order")) {
            Order order = Order.fromJson((List<Object>) res.get("order"));

            // make sure the server didn't open the order at the wrong price/amount
            checkState(!order.amount.isGreaterThan(trade.getAmount()));
            checkState(order.price.equals(trade.getPrice()));

            orders.put(order.id, order);
            if(trade.buy) bids.add(order.id);
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

                // verify that the coin IDs, price, and fee are what we expected
                checkState(swap.trade.coins[0].equals(trade.coins[0]));
                checkState(swap.trade.coins[1].equals(trade.coins[1]));
                checkState(swap.trade.getPrice().equals(trade.getPrice()));
                checkState(swap.trade.fee.equals(trade.fee));
                checkState(swap.trade.buy == trade.buy);

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

    }

    private void onFill(Map message) {
        AtomicSwap swap = AtomicSwap.fromJson((Map) message.get("swap"));

        // make sure time value is correct (otherwise server could get us to use unreasonable locktimes)
        checkState(Math.abs(System.currentTimeMillis() / 1000 - swap.getTime()) < TIME_EPSILON);

        Coin totalVolume = Coin.ZERO,
             totalPrice = Coin.ZERO;

        for(int id : (List<Integer>) message.get("orders")) {
            Order order = orders.get(id);
            boolean bid = bids.contains(id);
            checkState(swap.trade.buy == bid);

            Coin remaining = swap.trade.getAmount().subtract(totalVolume);
            checkState(remaining.isGreaterThan(Coin.ZERO));

            Coin toAdd = order.amount;
            if(order.amount.isGreaterThan(remaining)) {
                toAdd = remaining;
            }

            totalVolume = totalVolume.add(toAdd);
            long price = toAdd.multiply(order.price.longValue()).divide(Coin.COIN);
            totalPrice = totalPrice.add(Coin.valueOf(price));
        }

        checkState(totalPrice.equals(swap.trade.quantities[1]));

        for(int id : (List<Integer>) message.get("orders")) {
            orders.remove(id);
            bids.remove(id);
        }

        Currency[] swapCurrencies = new Currency[]{
            currencies.get(swap.trade.coins[0].toLowerCase()),
            currencies.get(swap.trade.coins[1].toLowerCase())
        };

        boolean alice = swap.trade.buy ^ !swapCurrencies[1].supportsHashlock();

        AtomicSwapClient client =
                new AtomicSwapClient(swap, connection, alice, swapCurrencies);
        client.start();
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }
}
