package io.coinswap.client;

import com.google.bitcoin.core.DownloadListener;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.kits.WalletAppKit;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;

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
            }
        };
    }

    public void start() {
        wallet.startAsync();
    }

    public void stop() {
        wallet.stopAsync();
    }

    class UIDownloadListener extends DownloadListener {
        @Override
        protected void progress(double pct, int blocksSoFar, Date date) {
            model.trigger("sync:progress", "{ \"progress\": " + pct +
                    ", \"blocks\": " + blocksSoFar +
                    ", \"date\": " + date.getTime() + " }");
            super.progress(pct, blocksSoFar, date);
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
