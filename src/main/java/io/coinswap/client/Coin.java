package io.coinswap.client;

import com.google.bitcoin.core.*;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.store.BlockStoreException;
import com.google.common.base.Joiner;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

/**
 * Contains the settings and state for one currency. Includes an AltcoinJ wallet,
 * and a JS-side Coin model for interfacing with UI.
 */
public class Coin {
    private static final Logger log = LoggerFactory.getLogger(Console.class);
    private static final File checkpointDir = new File("./checkpoints");

    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected String[] pairs;
    protected CoinModel model;

    public Coin(Controller controller, NetworkParameters params, File directory,
                String name, String id, String symbol, String[] pairs) {

        this.name = name;
        this.id = id;
        this.symbol = symbol;
        this.pairs = pairs;

        final Controller c = controller;

        // create the JS object so we can interface with the UI
        model = new CoinModel(c, name, id, symbol, pairs);

        // create the AltcoinJ wallet to interface with the currency
        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
              peerGroup().addEventListener(new UIDownloadListener(), c.e);
              peerGroup().setMaxConnections(6);
              peerGroup().setFastCatchupTimeSecs(wallet.wallet().getEarliestKeyCreationTime());
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

    class UIDownloadListener extends DownloadListener {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            model.trigger("peers:connected", "{ \"peers\": " + peerCount +
                    ", \"maxPeers\": " + wallet.peerGroup().getMaxConnections() + " }");
        }

        @Override
        protected void progress(double pct, int blocks, Date date) {
            model.trigger("sync:progress", "{ \"blocks\": " + blocks +
                    ", \"date\": " + date.getTime() + " }");
        }

        @Override
        protected void startDownload(int blocks) {
            try {
                model.trigger("sync:start", wallet.store().getChainHead().getHeight() + blocks + "");
            } catch(BlockStoreException ex) {
                log.error(ex.getMessage());
            }
        }

        @Override
        protected void doneDownload() {
            model.trigger("sync:done");
        }
    }

    public class CoinModel extends Model {
        public CoinModel(Controller c, String name, String id, String symbol, String[] pairs) {
            // build JSON string with arguments,
            // eval to create object via JS constructor,
            // pass to Model() to initialize Java-side handling of object
            super((JSObject) c.context.eval(
                "new this.Coin({ " +
                    "name: '" + name + "'," +
                    "id: '" + id + "'," +
                    "symbol: '" + symbol + "'," +
                    "pairs: ['" + Joiner.on("','").join(pairs) + "']" +
                "})"));
        }
    }
}
