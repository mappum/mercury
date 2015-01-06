package io.coinswap.client;

import io.coinswap.market.TradeClient;
import io.coinswap.swap.AtomicSwapTrade;
import org.bitcoinj.core.Coin;

import java.util.Map;

public class TradeController {
    private TradeClient client;
    private Map<String, Currency> currencies;

    public TradeController(TradeClient client) {
        this.client = client;
        this.currencies = client.getCurrencies();
    }

    public void submit(boolean buy, String currency1, String currency2, String quantity, String total) {
        currency1 = currency1.toLowerCase();
        currency2 = currency2.toLowerCase();
        String[] pair = new String[]{ currency1, currency2 };

        // if the pair is in the wrong order, flip it
        if(currencies.get(currency1).getIndex() < currencies.get(currency2).getIndex()) {
            pair[0] = currency2;
            pair[1] = currency1;
        }

        Coin[] quantities = new Coin[]{
                Coin.parseCoin(quantity),
                Coin.parseCoin(total)
        };
        AtomicSwapTrade trade = new AtomicSwapTrade(buy, pair, quantities, AtomicSwapTrade.FEE);
        client.trade(trade);
    }
}
