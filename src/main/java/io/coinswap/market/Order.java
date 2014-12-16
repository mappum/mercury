package io.coinswap.market;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Order {
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

    public Object toJson() {
        List<Object> data = new ArrayList<Object>(3);
        data.add(id);
        data.add(amount.longValue());
        data.add(price.longValue());
        return data;
    }

    public static Order fromJson(List<Object> data) {
        checkNotNull(data);
        checkState(data.size() == 3);
        Order output = new Order(
                Coin.valueOf((long) checkNotNull(data.get(1))),
                Coin.valueOf((long) checkNotNull(data.get(2)))
        );
        output.id = (int) checkNotNull(data.get(0));
        return output;
    }
}