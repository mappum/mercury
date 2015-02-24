package io.coinswap.client;


import io.coinswap.swap.AtomicSwap;
import io.mappum.altcoinj.core.*;
import io.mappum.altcoinj.store.BlockStoreException;
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
    private SwapCollection swaps;

    public CoinModel(Controller controller, Currency currency, SwapCollection swaps) {
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
        this.swaps = swaps;

        object.setMember("controller", this);

        addDownloadListener();
        addTransactionListener();

        trigger("initialized");
    }

    public boolean isAddressValid(String address) {
        try {
            new Address(currency.params, address);
            return true;
        } catch(AddressFormatException ex) {
            return false;
        }
    }

    public String newAddress() {
        return currency.getWallet().wallet().freshReceiveAddress().toString();
    }

    // TODO: accept callback, return error, tx info
    public void send(String addressString, String amountString) {
        try {
            Address address = new Address(currency.params, addressString);
            io.mappum.altcoinj.core.Coin amount = io.mappum.altcoinj.core.Coin.parseCoin(amountString);
            Wallet.SendRequest req = Wallet.SendRequest.to(address, amount);
            currency.wallet.wallet().sendCoins(currency.wallet.peerGroup(), req);
        } catch(Exception ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException();
        }
    }

    public String balance() {
        return currency.getWallet().wallet().getBalance()
                .toPlainString();
    }

    public String pendingBalance() {
        Wallet w = currency.getWallet().wallet();
        return w.getBalance(Wallet.BalanceType.ESTIMATED)
                .subtract(w.getBalance())
                .toPlainString();
    }

    private void addDownloadListener() {
        UIDownloadListener udl = new UIDownloadListener();
        currency.wallet.peerGroup().addEventListener(udl, controller.e);

        // trigger events for any peers we are already connected to
        // (probably only happens for nodes running on localhost)
        int i = 0;
        for(Peer peer : currency.getWallet().peerGroup().getConnectedPeers()) {
            udl.onPeerConnected(peer, ++i);
        }
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
                connected = false;

        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            trigger("peers:connected", "{ \"peers\": " + peerCount +
                    ", \"maxPeers\": " + 1 + " }");

            // if we are now connected to the network and already synced, tell the JS model we are done downloading
            if(!connected) {
                connected = true;

                try {
                    // if this peer has the same height as we do, tell the client we're done syncing
                    if (currency.wallet.store().getChainHead().getHeight() == peer.getBestHeight())
                        doneDownload();
                } catch(BlockStoreException ex) {
                    log.error(ex.getMessage());
                }
            }
        }

        @Override
        protected void progress(double pct, int blocks, Date date) {
            trigger("sync:progress", "{ \"blocks\": " + blocks +
                    ", \"percent\": " + pct +
                    ", \"date\": " + date.getTime() + " }");
        }

        @Override
        protected void startDownload(int blocks) {
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
        public void onCoinsReceived(Wallet wallet, Transaction tx, io.mappum.altcoinj.core.Coin prevBalance, io.mappum.altcoinj.core.Coin newBalance) {
            onTransaction(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, io.mappum.altcoinj.core.Coin prevBalance, io.mappum.altcoinj.core.Coin newBalance) {
            onTransaction(tx);
        }

        @Override
        public void onWalletChanged(Wallet wallet) {
            trigger("changed");

            List<Transaction> txs = wallet.getRecentTransactions(100, true);
            for(Transaction tx : txs) {
                onTransaction(tx);
            }
        }

        public void onTransaction(Transaction tx) {
            Wallet w = currency.wallet.wallet();
            JSONObject obj = new JSONObject();
            Sha256Hash txid = tx.getHash();

            AtomicSwap swap = swaps.get(txid);
            if(swap == null) {
                obj.put("type", "payment");
                obj.put("value", tx.getValue(w).toPlainString());

            } else {
                obj.put("type", "trade");
                // TODO: support other party's trade transactions too?
                // (we're assuming the bailin was sent from us, payout is paying to us)
                boolean alice = !swap.trade.buy ^ swap.switched;
                if (txid.equals(swap.getBailinHash(alice))) {
                    obj.put("which", "bailin");
                    Coin paid = tx.getOutput(0).getValue()
                            .add(tx.getOutput(1).getValue());
                    obj.put("value", paid.negate().toPlainString());
                } else if (txid.equals(swap.getPayoutHash(alice))) {
                    obj.put("which", "payout");
                    obj.put("value", tx.getOutput(0).getValue().toPlainString());
                } else if(txid.equals(swap.getRefundHash(alice))) {
                    obj.put("which", "refund");
                    obj.put("value", tx.getOutput(0).getValue().toPlainString());
                } else {
                    return;
                }

                obj.put("trade", swap.trade.toJson());
            }
            obj.put("coin", currency.id);
            obj.put("id", Utils.HEX.encode(tx.getHash().getBytes()));
            obj.put("date", tx.getUpdateTime().getTime());
            obj.put("depth", tx.getConfidence().getDepthInBlocks());
            obj.put("dead", tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.DEAD);

            String address = null;
            boolean received = tx.getValue(w).compareTo(io.mappum.altcoinj.core.Coin.ZERO) == 1;
            for(TransactionOutput out : tx.getOutputs()) {
                try {
                    if (received == w.isPubKeyHashMine(out.getScriptPubKey().getPubKeyHash())) {
                        address = out.getScriptPubKey().getToAddress(currency.params).toString();
                        break;
                    }
                } catch(Exception e) {}
            }

            if(address != null) {
                io.mappum.altcoinj.core.Coin sent = io.mappum.altcoinj.core.Coin.ZERO;
                for(TransactionOutput out : tx.getOutputs()) {
                    try {
                        if (!w.isPubKeyHashMine(out.getScriptPubKey().getPubKeyHash()))
                            sent = sent.add(out.getValue());
                    } catch(Exception e){}
                }
                obj.put("sent", sent.toPlainString());
            } else {
                address = "self";
            }

            obj.put("address", address);

            trigger("transaction", obj.toJSONString(JSONStyle.LT_COMPRESS));
        }
    }
}
