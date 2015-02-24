package io.coinswap.client;

import io.mappum.altcoinj.kits.WalletAppKit;
import io.mappum.altcoinj.core.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contains the settings and state for one currency. Includes an AltcoinJ wallet,
 * and a JS-side Currency model for interfacing with UI.
 */
public class Currency {
    private static final Logger log = LoggerFactory.getLogger(Currency.class);
    private static final File checkpointDir = new File("./checkpoints");

    protected NetworkParameters params;
    protected File directory;
    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected String[] pairs;
    protected int index;
    protected boolean hashlock; // whether or not this coin can be used on the Alice-side of a swap
    protected int confirmationDepth;
    protected List<InetSocketAddress> connectPeers;

    private boolean setup;
    private SettableFuture<Object> setupFuture;

    public Currency(NetworkParameters params, File directory,
                String name, String id, String symbol, String[] pairs,
                int index, boolean hashlock, int confirmationDepth) {

        this.params = params;
        this.directory = directory;
        this.name = name;
        this.id = id;
        this.symbol = symbol;
        this.pairs = pairs;
        this.index = index;
        this.hashlock = hashlock;
        this.confirmationDepth = confirmationDepth;
        this.connectPeers = new ArrayList<InetSocketAddress>(0);

        setupFuture = SettableFuture.create();

        // create the AltcoinJ wallet to interface with the currency
        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
                makeBackup();

                peerGroup().setMaxConnections(8);
                peerGroup().setFastCatchupTimeSecs(wallet.wallet().getEarliestKeyCreationTime());

                for(InetSocketAddress peer : connectPeers) {
                    peerGroup().connectTo(peer);
                }

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

    public void broadcastTransaction(Transaction tx) {
        wallet.peerGroup().broadcastTransaction(tx);
        wallet.wallet().receivePending(tx, null);
    }

    public void start() {
        wallet.startAsync();
        wallet.awaitRunning();
    }

    public void stop() {
        wallet.stopAsync();
        wallet.awaitTerminated();
    }

    public void addConnectPeers(List<InetSocketAddress> peers) {
        checkNotNull(peers);
        this.connectPeers.addAll(peers);
    }

    private void makeBackup() {
        File backupDir = new File(directory, "backup");
        if(!backupDir.exists()) {
            boolean success = backupDir.mkdir();
            if(!success) throw new RuntimeException();
        }

        File backup = new File(backupDir, name.toLowerCase()+".wallet");
        if(!backup.exists()) {
            try {
                wallet.wallet().saveToFile(backup);
            } catch(IOException e) {
                throw new RuntimeException();
            }
        }
    }

    public String getId() { return id; }

    public int getIndex() { return index; }

    public boolean supportsHashlock() { return hashlock; }

    public int getConfirmationDepth() { return confirmationDepth; }

    public String[] getPairs() { return pairs; }

    public boolean isSetup() { return setup; }

    public WalletAppKit getWallet() { return wallet; }

    public NetworkParameters getParams() { return params; }

    public ListenableFuture<Object> getSetupFuture() { return setupFuture; }
}
