package io.coinswap.market;

import io.mappum.altcoinj.core.Coin;

import java.math.BigInteger;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Ticker {
    public Coin last;
    public Coin bestBid, bestAsk;
    public List<TickerHistory> history;

    public Ticker(Coin last, Coin bestBid, Coin bestAsk, List<TickerHistory> history) {
        this.last = checkNotNull(last);
        this.bestBid = checkNotNull(bestBid);
        this.bestAsk = checkNotNull(bestAsk);
        this.history = history;
    }

    public Coin change() {
        BigInteger start = BigInteger.valueOf(history.get(0).start.value),
                end = BigInteger.valueOf(history.get(history.size()-1).end.value);
        if(start.equals(BigInteger.ZERO)) return Coin.ZERO;
        return Coin.valueOf(end.subtract(start)
                .multiply(BigInteger.valueOf(Coin.COIN.value))
                .multiply(BigInteger.valueOf(100))
                .divide(start).longValueExact());
    }

    public Object toJson() {
        Map<String, Object> data = new HashMap<String, Object>(2);
        data.put("last", last.toPlainString());
        data.put("bestBid", bestBid.toPlainString());
        data.put("bestAsk", bestAsk.toPlainString());

        List<Object> historyJson = new ArrayList<Object>(history.size());
        for(TickerHistory period : history)
            historyJson.add(period.toJson());
        data.put("history", historyJson);

        return data;
    }

    public static Ticker fromJson(Map data) {
        checkNotNull(data);

        Coin last = Coin.parseCoin((String) checkNotNull(data.get("last")));

        // history is an optional field
        List<TickerHistory> history = null;
        if(data.get("history") != null) {
            List<List> historyJson = (List<List>) data.get("history");
            history = new ArrayList<TickerHistory>(historyJson.size());
            for (List period : historyJson)
                history.add(TickerHistory.fromJson(period));
        } else {
            history = new ArrayList<TickerHistory>(1);
            TickerHistory period = new TickerHistory();
            period.start = period.end = last;
            history.add(period);
        }

        return new Ticker(
                last,
                Coin.parseCoin((String) checkNotNull(data.get("bestBid"))),
                Coin.parseCoin((String) checkNotNull(data.get("bestAsk"))),
                history
        );
    }
}
