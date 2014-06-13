package io.coinswap.client;

import com.google.bitcoin.core.*;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Contains the settings and state for one currency. Includes an AltcoinJ wallet,
 * and a JS-side Coin model for interfacing with UI.
 */
public class Coin {
    private static final Logger log = LoggerFactory.getLogger(Coin.class);
    private static final File checkpointDir = new File("./checkpoints");

    protected NetworkParameters params;
    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected String[] pairs;

    private boolean setup;
    private SettableFuture<Object> setupFuture;

    public Coin(NetworkParameters params, File directory,
                String name, String id, String symbol, String[] pairs) {

        this.params = params;
        this.name = name;
        this.id = id;
        this.symbol = symbol;
        this.pairs = pairs;

        setupFuture = SettableFuture.create();

        // create the AltcoinJ wallet to interface with the currency
        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setMaxConnections(6);
                peerGroup().setFastCatchupTimeSecs(wallet.wallet().getEarliestKeyCreationTime());
                setup = true;
                setupFuture.set(null);
            }
        };
        wallet.setUserAgent(Main.APP_NAME, Main.APP_VERSION);

        // load a checkpoint file (if it exists) to speed up initial blockchain sync
        try {
            FileInputStream checkpointStream = null;
            try {
                File checkpointFile = new File(checkpointDir.getCanonicalPath() + "/" + name + ".checkpoint");
                checkpointStream = new FileInputStream(checkpointFile);
                wallet.setCheckpoints(checkpointStream);
            } catch (FileNotFoundException ex) {
                log.info("No checkpoint file found for " + name);
            }
        } catch(IOException ex) {
            log.error(ex.getMessage());
        }
    }

    public void start() {
        wallet.startAsync();
    }

    public void stop() {
        wallet.stopAsync();
    }

    public boolean isSetup() {
        return setup;
    }

    public WalletAppKit getWallet() { return wallet; }

    public NetworkParameters getParams() { return params; }

    public ListenableFuture<Object> getSetupFuture() {
        return setupFuture;
    }
}
