package io.coinswap.client;

import io.coinswap.net.Connection;
import io.coinswap.swap.AtomicSwapTrade;
import net.minidev.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class TradeClient extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TradeClient.class);

    public static final String HOST = "localhost";
    public static final int PORT = Connection.PORT;
    public static final com.google.bitcoin.core.Coin FEE = com.google.bitcoin.core.Coin.valueOf(10);

    private List<Coin> coins;
    private Connection connection;

    public TradeClient(List<Coin> coins) {
        this.coins = checkNotNull(coins);
    }

    @Override
    public void run() {
        connect();

        final TradeClient parent = this;
        connection.addListener("trade", new Connection.ReceiveListener() {
            @Override
            public void onReceive(Map res) {
                if(checkNotNull(res.get("method")).equals("trade")) {

                }
            }
        });

        try {
            Thread.sleep(1000);
            trade(new AtomicSwapTrade(true, new String[]{"BTCt","BTC"},
                    new com.google.bitcoin.core.Coin[]{
                            com.google.bitcoin.core.Coin.valueOf(1,0),
                            com.google.bitcoin.core.Coin.valueOf(0,1)
                    }, FEE));
        } catch(Exception e) {
            log.error(e.getMessage());
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
        JSONObject req = new JSONObject();
        req.put("channel", "trade");
        req.put("method", "trade");
        req.put("trade", trade.toJson());
        connection.write(req);
    }
}
