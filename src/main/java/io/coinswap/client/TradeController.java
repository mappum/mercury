package io.coinswap.client;

import com.google.common.util.concurrent.SettableFuture;
import io.coinswap.market.Order;
import io.coinswap.market.Ticker;
import io.coinswap.market.TradeClient;
import io.coinswap.swap.AtomicSwapTrade;
import netscape.javascript.JSObject;
import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Map;

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

    public void submit(boolean buy, String currency1, String currency2, String quantity, String total, JSObject cb) {
        String[] pair = getPair(currency1, currency2);
        Coin[] quantities = new Coin[]{
                Coin.parseCoin(quantity),
                Coin.parseCoin(total)
        };
        AtomicSwapTrade trade = new AtomicSwapTrade(buy, pair, quantities, AtomicSwapTrade.FEE);
        SettableFuture<Map> future = client.trade(trade);

        future.addListener(new Runnable() {
            @Override
            public void run() {
                // TODO: send some response data
                controller.callFunction(cb, new Object[]{null});
            }
        }, controller.e);
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

        controller.callFunction(cb, values);
    }

    public JSObject orders() {
        List<Order> orders = client.getOrders();
        JSObject ordersJs = controller.eval("[]");
        for(int i = 0; i < orders.size(); i++) {
            Map<String, Object> orderJson = orders.get(i).toJson();
            orderJson.put("currencies", new String[]{
                    currencies.get(orders.get(i).currencies[0]).getId(),
                    currencies.get(orders.get(i).currencies[1]).getId(),
            });
            ordersJs.setSlot(i, toJSObject(orderJson));
        }
        return ordersJs;
    }

    public void cancel(int id) {
        client.cancel(id);
    }

    public void on(String event, JSObject listener) {
        client.on(event, new EventEmitter.Callback(controller.e) {
            @Override
            public void f(Object a) {
                // TODO: convert arguments for JS
                controller.callFunction(listener, new Object[]{a});
            }
        });
    }

    private JSObject toJSObject(Map<String, Object> obj) {
        JSObject output = controller.eval("new Object()");
        for(String key : obj.keySet()) {
            output.setMember(key, obj.get(key));
        }
        return output;
    }
}
