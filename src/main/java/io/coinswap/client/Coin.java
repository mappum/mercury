package io.coinswap.client;

import com.google.bitcoin.core.DownloadListener;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.kits.WalletAppKit;

import java.io.File;
import java.util.Date;

public class Coin {
    protected WalletAppKit wallet;
    protected String name, id, symbol;
    protected Controller controller;
    protected Controller.Emitter emitter;

    public Coin(Controller controller, NetworkParameters params, File directory, String name, String id, String symbol) {
        this.controller = controller;
        this.name = name;
        this.id = id;
        this.symbol = symbol;
        emitter = controller.createEmitter(id);

        wallet = new WalletAppKit(params, directory, name.toLowerCase()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().addEventListener(new UIDownloadListener(), controller.e);
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
            emitter.emit("sync:progress", "{ progress: " + pct +
                    ", blocks: " + blocksSoFar +
                    ", date: " + date.getTime() + " }");
            super.progress(pct, blocksSoFar, date);
        }
    }
}
