package io.coinswap.client;

import com.google.bitcoin.core.*;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.store.BlockStoreException;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.List;

public class Coin {
    private static final Logger log = LoggerFactory.getLogger(Console.class);

    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected Controller controller;
    protected CoinModel model;

    public Coin(Controller controller, NetworkParameters params, File directory, String name, String id, String symbol) {
        final Controller c = this.controller = controller;
        this.name = name;
        this.id = id;
        this.symbol = symbol;

        model = new CoinModel(name, id, symbol);

        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
              peerGroup().addEventListener(new UIDownloadListener(), c.e);
              peerGroup().setMaxConnections(7);
            }
        };
        wallet.setUserAgent(Main.APP_NAME, Main.APP_VERSION);
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
        public CoinModel(String name, String id, String symbol) {
            super((JSObject) controller.context.eval("new this.Coin({ " +
                    "name: '" + name +
                    "', id: '" + id +
                    "', symbol: '" + symbol +
                    "' })"));
        }
    }
}
