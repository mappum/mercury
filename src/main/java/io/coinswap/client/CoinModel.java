package io.coinswap.client;


import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;
import com.google.common.base.Joiner;
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
    private Currency currency;

    public CoinModel(Controller controller, Currency currency) {
        // build JSON string with arguments,
        // eval to create object via JS constructor,
        // pass to Model() to initialize Java-side handling of object
        super((JSObject) controller.context.eval(
            "new this.Coin({ " +
                "name: '" + currency.name + "'," +
                "id: '" + currency.id + "'," +
                "index: " + currency.index + ", " +
                "symbol: '" + currency.symbol + "'," +
                "pairs: ['" + Joiner.on("','").join(currency.pairs) + "']," +
                "address: \"" + currency.getWallet().wallet().currentReceiveAddress().toString() + "\"" +
            "})"));

        this.controller = controller;
        this.currency = currency;

        addDownloadListener();
        addTransactionListener();
        handleRequests();

        object.setMember("controller", this);
    }

    public boolean isAddressValid(String address) {
        try {
            new Address(currency.params, address);
            return true;
        } catch(AddressFormatException ex) {
            return false;
        }
    }

    public void send(String addressString, String amountString) {
        try {
            Address address = new Address(currency.params, addressString);
            org.bitcoinj.core.Coin amount = org.bitcoinj.core.Coin.parseCoin(amountString);
            Wallet.SendRequest req = Wallet.SendRequest.to(address, amount);
            req.feePerKb = currency.params.getMinFee();
            currency.wallet.wallet().sendCoins(currency.wallet.peerGroup(), req);
            // TODO(altcoinj): set default feePerKb in altcoinj SendRequest
        } catch(Exception ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException();
        }
    }

    private void handleRequests() {
        on("address:new", new EventEmitter.Callback() {
            @Override
            public void f(Object a) {
                String address = currency.getWallet().wallet().freshReceiveAddress().toString();
                trigger("address", "\"" + address + "\"");
            }
        });
    }

    private void addDownloadListener() {
        UIDownloadListener udl = new UIDownloadListener();
        currency.wallet.peerGroup().addEventListener(udl, controller.e);
    }

    private void addTransactionListener() {
        Wallet w = currency.wallet.wallet();
        UITransactionListener utl = new UITransactionListener();
        w.addEventListener(utl, controller.e);

        // trigger the UI transaction listener for old transactions at startup
        List<Transaction> txs = w.getRecentTransactions(100, false);
        for(Transaction tx : txs) {
            utl.onTransaction(tx);
        }
    }

    class UIDownloadListener extends DownloadListener {
        private boolean done = false,
                started = false,
                connected = false;

        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            trigger("peers:connected", "{ \"peers\": " + peerCount +
                    ", \"maxPeers\": " + currency.wallet.peerGroup().getMaxConnections() + " }");

            // if we are now connected to the network and already synced, tell the JS model we are done downloading
            if(!connected && peerCount == 2) {
                connected = true;
                try {
                    long limit = new Date().getTime() / 1000 - 60 * 15;
                    if (currency.wallet.store().getChainHead().getHeader().getTimeSeconds() > limit)
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
                int height = currency.wallet.store().getChainHead().getHeight();
                trigger("sync:start", height + blocks + "");
            } catch(BlockStoreException ex) {
                log.error(ex.getMessage());
            }
        }

        @Override
        protected void doneDownload() {
            if(done) return;
            done = true;
            log.info("Done syncing " + currency.name);
            trigger("sync:done");
        }
    }

    class UITransactionListener extends AbstractWalletEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, org.bitcoinj.core.Coin prevBalance, org.bitcoinj.core.Coin newBalance) {
            onTransaction(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, org.bitcoinj.core.Coin prevBalance, org.bitcoinj.core.Coin newBalance) {
            onTransaction(tx);
        }

        @Override
        public void onWalletChanged(Wallet wallet) {
            List<Transaction> txs = wallet.getRecentTransactions(100, true);
            for(Transaction tx : txs) {
                if(tx.getConfidence().getDepthInBlocks() > 6) break;
                onTransaction(tx);
            }
        }

        public void onTransaction(Transaction tx) {
            Wallet w = currency.wallet.wallet();
            JSONObject obj = new JSONObject();
            obj.put("type", "payment");
            obj.put("coin", currency.id);
            obj.put("id", Utils.HEX.encode(tx.getHash().getBytes()));
            obj.put("date", tx.getUpdateTime().getTime());
            obj.put("depth", tx.getConfidence().getDepthInBlocks());
            obj.put("dead", tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.DEAD);

            org.bitcoinj.core.Coin value = tx.getValue(w);
            obj.put("value", Double.parseDouble(value.toPlainString()));

            String address = null;
            boolean received = value.compareTo(org.bitcoinj.core.Coin.ZERO) == 1;
            for(TransactionOutput out : tx.getOutputs()) {
                try {
                    if (received == w.isPubKeyHashMine(out.getScriptPubKey().getPubKeyHash())) {
                        address = out.getScriptPubKey().getToAddress(currency.params).toString();
                        break;
                    }
                } catch(Exception e) {}
            }

            if(address != null) {
                org.bitcoinj.core.Coin sent = org.bitcoinj.core.Coin.ZERO;
                for(TransactionOutput out : tx.getOutputs()) {
                    try {
                        if (!w.isPubKeyHashMine(out.getScriptPubKey().getPubKeyHash()))
                            sent = sent.add(out.getValue());
                    } catch(Exception e){}
                }
                obj.put("sent", sent.toFriendlyString());
            } else {
                address = "self";
            }

            obj.put("address", address);


            trigger("transaction", obj.toJSONString(JSONStyle.LT_COMPRESS));
        }
    }
}
