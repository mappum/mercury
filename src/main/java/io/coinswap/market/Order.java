package io.coinswap.market;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Order {
    // TODO: make ids strings?
    public int id;
    public Coin amount, price;

    public Order(Coin amount, Coin price) {
        this.amount = amount;
        this.price = price;
    }

    public Order(long amount, long price) {
        this.amount = Coin.valueOf(amount);
        this.price = Coin.valueOf(price);
    }

    public Order clone() {
        Order output = new Order(amount.longValue(), price.longValue());
        output.id = id;
        return output;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> data = new HashMap<String, Object>(3);
        data.put("id", id);
        data.put("amount", amount.toPlainString());
        data.put("price", price.toPlainString());
        return data;
    }

    public static Order fromJson(Map<String, Object> data) {
        checkNotNull(data);
        Order output = new Order(
                Coin.parseCoin((String) checkNotNull(data.get("amount"))),
                Coin.parseCoin((String) checkNotNull(data.get("price")))
        );
        output.id = (int) checkNotNull(data.get("id"));
        return output;
    }
}