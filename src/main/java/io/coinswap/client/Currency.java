package io.coinswap.client;

import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.core.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.wallet.KeyChainGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;

/**
 * Contains the settings and state for one currency. Includes an AltcoinJ wallet,
 * and a JS-side Currency model for interfacing with UI.
 */
public class Currency {
    private static final Logger log = LoggerFactory.getLogger(Currency.class);
    private static final File checkpointDir = new File("./checkpoints");

    protected NetworkParameters params;
    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected String[] pairs;
    protected int index;
    protected boolean hashlock; // whether or not this coin can be used on the Alice-side of a swap
    protected int confirmationDepth;

    private boolean setup;
    private SettableFuture<Object> setupFuture;

    public Currency(NetworkParameters params, File directory,
                String name, String id, String symbol, String[] pairs,
                int index, boolean hashlock, int confirmationDepth) {

        this.params = params;
        this.name = name;
        this.id = id;
        this.symbol = symbol;
        this.pairs = pairs;
        this.index = index;
        this.hashlock = hashlock;
        this.confirmationDepth = confirmationDepth;

        setupFuture = SettableFuture.create();

        // create the AltcoinJ wallet to interface with the currency
        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setMaxConnections(8);
                peerGroup().setFastCatchupTimeSecs(wallet.wallet().getEarliestKeyCreationTime());
                setup = true;
                setupFuture.set(null);
            }
        };
        wallet.setUserAgent(Main.APP_NAME, Main.APP_VERSION);
        wallet.setBlockingStartup(false);

        // load a checkpoint file (if it exists) to speed up initial blockchain sync
        InputStream checkpointStream = Main.class.getResourceAsStream("/checkpoints/"+name.toLowerCase()+".txt");
        if(checkpointStream != null) {
            wallet.setCheckpoints(checkpointStream);
        } else {
            log.info("No checkpoints found for " + name);
        }
    }

    public void start() {
        wallet.startAsync();
        wallet.awaitRunning();
    }

    public void stop() {
        wallet.stopAsync();
        wallet.awaitTerminated();
    }

    public String getId() { return id; }

    public int getIndex() { return index; }

    public boolean supportsHashlock() { return hashlock; }

    public int getConfirmationDepth() { return confirmationDepth; }

    public String[] getPairs() { return pairs; }

    public boolean isSetup() {
        return setup;
    }

    public WalletAppKit getWallet() { return wallet; }

    public NetworkParameters getParams() { return params; }

    public ListenableFuture<Object> getSetupFuture() {
        return setupFuture;
    }
}
