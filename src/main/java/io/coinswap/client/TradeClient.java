package io.coinswap.client;

import io.coinswap.net.Connection;
import net.minidev.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class TradeClient extends Thread {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TradeClient.class);

    public static final String HOST = "localhost";
    public static final int PORT = Connection.PORT;

    private List<Coin> coins;
    private Connection connection;

    public TradeClient(List<Coin> coins) {
        this.coins = checkNotNull(coins);
    }

    @Override
    public void run() {
        connect();
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
}
