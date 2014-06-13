package io.coinswap.client;


import com.google.bitcoin.core.DownloadListener;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.store.BlockStoreException;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.MoreExecutors;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class CoinModel extends Model {
    private static final Logger log = LoggerFactory.getLogger(CoinModel.class);

    private Controller controller;
    private Coin coin;

    public CoinModel(Controller controller, Coin coin) {
        // build JSON string with arguments,
        // eval to create object via JS constructor,
        // pass to Model() to initialize Java-side handling of object
        super((JSObject) controller.context.eval(
            "new this.Coin({ " +
                "name: '" + coin.name + "'," +
                "id: '" + coin.id + "'," +
                "symbol: '" + coin.symbol + "'," +
                "pairs: ['" + Joiner.on("','").join(coin.pairs) + "']," +
                "address: \"" + coin.getWallet().wallet().currentReceiveAddress().toString() + "\"" +
            "})"));

        this.controller = controller;
        this.coin = coin;

        addDownloadListener();
        handleAddressRequests();
    }

    private void handleAddressRequests() {
        on("address:new", new EventEmitter.Callback() {
            @Override
            public void f(Object a) {
                String address = coin.getWallet().wallet().freshReceiveAddress().toString();
                trigger("address", "\"" + address + "\"");
            }
        });
    }

    private void addDownloadListener() {
        UIDownloadListener udl = new UIDownloadListener();
        coin.wallet.peerGroup().addEventListener(udl, controller.e);
    }

    class UIDownloadListener extends DownloadListener {
        private boolean done = false,
                started = false;

        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            trigger("peers:connected", "{ \"peers\": " + peerCount +
                    ", \"maxPeers\": " + coin.wallet.peerGroup().getMaxConnections() + " }");

            // if we are now connected to the network and already synced, tell the JS model we are done downloading
            if(peerCount == coin.wallet.peerGroup().getMaxConnections()) {
                try {
                    if (coin.wallet.store().getChainHead().getHeight() == coin.wallet.chain().getBestChainHeight())
                        doneDownload();
                } catch(BlockStoreException ex) {
                    log.error(ex.getMessage());
                }
            }
        }

        @Override
        protected void progress(double pct, int blocks, Date date) {
            trigger("sync:progress", "{ \"blocks\": " + blocks +
                    ", \"date\": " + date.getTime() + " }");
        }

        @Override
        protected void startDownload(int blocks) {
            if(started) return;
            started = true;
            try {
                int height = coin.wallet.store().getChainHead().getHeight();
                trigger("sync:start", height + blocks + "");
            } catch(BlockStoreException ex) {
                log.error(ex.getMessage());
            }
        }

        @Override
        protected void doneDownload() {
            if(done) return;
            done = true;
            log.info("Done syncing " + coin.name);
            trigger("sync:done");
        }
    }
}
