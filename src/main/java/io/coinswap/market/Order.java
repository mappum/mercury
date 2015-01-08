package io.coinswap.market;

import io.coinswap.client.Currency;
import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Order {
    // TODO: make ids strings?
    public int id;
    public Coin amount, price;
    public boolean bid;
    public String[] currencies;

    public Order(boolean bid, Coin amount, Coin price, String[] currencies) {
        this.bid = bid;
        this.amount = checkNotNull(amount);
        this.price = checkNotNull(price);
        this.currencies = checkNotNull(currencies);
        checkArgument(currencies.length == 2);
    }

    public Order(boolean bid, long amount, long price, String[] currencies) {
        new Order(bid, Coin.valueOf(amount), Coin.valueOf(price), currencies);
    }

    public Order clone() {
        Order output = new Order(bid, amount.longValue(), price.longValue(), currencies);
        output.id = id;
        return output;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> data = new HashMap<String, Object>(3);
        data.put("id", id);
        data.put("bid", bid);
        data.put("amount", amount.toPlainString());
        data.put("price", price.toPlainString());
        data.put("currencies", currencies);
        return data;
    }

    public static Order fromJson(Map<String, Object> data) {
        checkNotNull(data);
        Order output = new Order(
                (boolean) checkNotNull(data.get("bid")),
                Coin.parseCoin((String) checkNotNull(data.get("amount"))),
                Coin.parseCoin((String) checkNotNull(data.get("price"))),
                ((List<String>) checkNotNull(data.get("currencies"))).toArray(new String[0])
        );
        output.id = (int) checkNotNull(data.get("id"));
        return output;
    }
}