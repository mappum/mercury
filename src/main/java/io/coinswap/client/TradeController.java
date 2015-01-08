package io.coinswap.client;

import io.coinswap.market.Order;
import io.coinswap.market.Ticker;
import io.coinswap.market.TradeClient;
import io.coinswap.swap.AtomicSwapTrade;
import netscape.javascript.JSObject;
import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TradeController {
    private TradeClient client;
    private Map<String, Currency> currencies;
    private Controller controller;

    public TradeController(Controller controller, TradeClient client) {
        this.controller = controller;
        this.client = client;
        this.currencies = client.getCurrencies();
    }

    private String[] getPair(String currency1, String currency2) {
        currency1 = currency1.toLowerCase();
        currency2 = currency2.toLowerCase();
        String[] pair = new String[]{ currency1, currency2 };

        // if the pair is in the wrong order, flip it
        if(currencies.get(currency1).getIndex() < currencies.get(currency2).getIndex()) {
            pair[0] = currency2;
            pair[1] = currency1;
        }

        return pair;
    }

    public void submit(boolean buy, String currency1, String currency2, String quantity, String total) {
        String[] pair = getPair(currency1, currency2);
        Coin[] quantities = new Coin[]{
                Coin.parseCoin(quantity),
                Coin.parseCoin(total)
        };
        AtomicSwapTrade trade = new AtomicSwapTrade(buy, pair, quantities, AtomicSwapTrade.FEE);
        client.trade(trade);
    }

    public void ticker(String currency1, String currency2, JSObject cb) {
        String[] pair = getPair(currency1, currency2);
        Object[] values = new Object[2];
        try {
            Ticker ticker = client.getTicker(pair[0] + "/" + pair[1]);
            values[1] = toJSObject((Map<String, Object>) ticker.toJson());
        } catch(Exception e) {
            e.printStackTrace();
            values[0] = controller.eval("new Error('"+e.getMessage()+"')");
        }

        // hack to be able to call function:
        // create a wrapper object, set the function as a property, then use wrapper.call
        JSObject wrapper = controller.eval("new Object()");
        wrapper.setMember("f", cb);
        wrapper.call("f", values);
    }

    public JSObject orders() {
        List<Order> orders = client.getOrders();
        JSObject ordersJs = controller.eval("[]");
        for(int i = 0; i < orders.size(); i++) {
            ordersJs.setSlot(i, toJSObject(orders.get(i).toJson()));
        }
        return ordersJs;
    }

    private JSObject toJSObject(Map<String, Object> obj) {
        JSObject output = controller.eval("new Object()");
        for(String key : obj.keySet()) {
            output.setMember(key, obj.get(key));
        }
        return output;
    }
}
