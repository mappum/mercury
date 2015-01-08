package io.coinswap.market;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Ticker {
    public Coin bestBid, bestAsk;

    // TODO: last, average, volume, etc.

    public Ticker(Coin bestBid, Coin bestAsk) {
        this.bestBid = checkNotNull(bestBid);
        this.bestAsk = checkNotNull(bestAsk);
    }

    public Object toJson() {
        Map<String, Object> data = new HashMap<String, Object>(2);
        data.put("bestBid", bestBid.toPlainString());
        data.put("bestAsk", bestAsk.toPlainString());
        return data;
    }

    public static Ticker fromJson(Map data) {
        checkNotNull(data);
        return new Ticker(
                Coin.parseCoin((String) checkNotNull(data.get("bestBid"))),
                Coin.parseCoin((String) checkNotNull(data.get("bestAsk")))
        );
    }
}