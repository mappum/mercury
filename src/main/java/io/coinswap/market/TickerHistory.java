package io.coinswap.market;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TickerHistory {
    public Coin start, end;
    public Coin[] volume;
    public int trades;

    public TickerHistory() {
        start = end = Coin.ZERO;
        volume = new Coin[]{ Coin.ZERO, Coin.ZERO };
        trades = 0;
    }

    public Object toJson() {
        List<Object> output = new ArrayList<>(5);
        output.add(start.toPlainString());
        output.add(end.toPlainString());
        output.add(volume[0].toPlainString());
        output.add(volume[1].toPlainString());
        output.add(trades);
        return output;
    }

    public static TickerHistory fromJson(List data) {
        TickerHistory output = new TickerHistory();
        output.start = Coin.parseCoin((String) data.get(0));
        output.end = Coin.parseCoin((String) data.get(1));
        output.volume = new Coin[]{
                Coin.parseCoin((String) data.get(2)),
                Coin.parseCoin((String) data.get(3)),
        };
        output.trades = (int) data.get(4);
        return output;
    }
}
