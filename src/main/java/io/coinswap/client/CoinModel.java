package io.coinswap.client;


import com.google.bitcoin.core.*;
import com.google.bitcoin.store.BlockStoreException;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.MoreExecutors;
import com.subgraph.orchid.encoders.Hex;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

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
        addTransactionListener();
        handleRequests();
    }

    private void handleRequests() {
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

    private void addTransactionListener() {
        Wallet w = coin.wallet.wallet();
        UITransactionListener utl = new UITransactionListener();
        w.addEventListener(utl, controller.e);

        // trigger the UI transaction listener for old transactions at startup
        List<Transaction> txs = w.getRecentTransactions(100, false);
        for(Transaction tx : txs) {
            if(tx.getValueSentToMe(w).compareTo(com.google.bitcoin.core.Coin.ZERO) == 1)
                utl.onCoinsReceived(w, tx, null, null);
            else
                utl.onCoinsSent(w, tx, null, null);
        }
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

    class UITransactionListener extends AbstractWalletEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, com.google.bitcoin.core.Coin prevBalance, com.google.bitcoin.core.Coin newBalance) {
            onTransaction(tx, true);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, com.google.bitcoin.core.Coin prevBalance, com.google.bitcoin.core.Coin newBalance) {
            onTransaction(tx, false);
        }

        public void onTransaction(Transaction tx, boolean receive) {
            Wallet w = coin.wallet.wallet();
            JSONObject obj = new JSONObject();
            obj.put("type", receive ? "receive" : "send");
            obj.put("coin", coin.id);
            obj.put("id", Utils.HEX.encode(tx.getHash().getBytes()));
            obj.put("date", tx.getUpdateTime().getTime());
            obj.put("depth", tx.getConfidence().getDepthInBlocks());
            obj.put("value", Double.parseDouble(tx.getValue(w).toFriendlyString()));

            String address = null;
            if(receive) {
                for(TransactionOutput out : tx.getOutputs()) {
                    if(w.isPubKeyHashMine(out.getScriptPubKey().getPubKeyHash())) {
                        address = out.getScriptPubKey().getToAddress(coin.params).toString();
                        break;
                    }
                }
            } else {
                address = tx.getOutput(0).getScriptPubKey().getToAddress(coin.params).toString();
            }
            obj.put("address", address);

            trigger("transaction", obj.toJSONString(JSONStyle.LT_COMPRESS));
        }
    }
}
