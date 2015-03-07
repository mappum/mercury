package io.coinswap.market;

import io.mappum.altcoinj.core.Coin;

import java.math.BigInteger;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Order {
    // TODO: make ids strings?
    public int id;
    public Coin amount, price;
    public boolean bid;
    public String[] currencies;

    private static String[] emptyCurrencyArray = new String[]{"",""};

    public Order(boolean bid, Coin amount, Coin price, String[] currencies) {
        this.bid = bid;
        this.amount = checkNotNull(amount);
        this.price = checkNotNull(price);
        this.currencies = checkNotNull(currencies);
        checkArgument(currencies.length == 2);
    }

    public Order(boolean bid, long amount, long price, String[] currencies) {
        this(bid, Coin.valueOf(amount), Coin.valueOf(price), currencies);
    }

    public Order clone() {
        Order output = new Order(bid, amount.longValue(), price.longValue(), currencies);
        output.id = id;
        return output;
    }

    public Object toJson() {
        return toJson(false);
    }

    public Object toJson(boolean compact) {
        if(compact) {
            List<Object> data = new ArrayList<Object>(3);
            data.add(bid);
            data.add(amount.toPlainString());
            data.add(price.toPlainString());
            return data;

        } else {
            Map<String, Object> data = new HashMap<String, Object>(5);
            data.put("id", id);
            data.put("bid", bid);
            data.put("amount", amount.toPlainString());
            data.put("price", price.toPlainString());
            data.put("currencies", currencies);
            return data;
        }
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

    public static Order fromJson(List<Object> data) {
        checkNotNull(data);
        Order output = new Order(
                (boolean) checkNotNull(data.get(0)),
                Coin.parseCoin((String) checkNotNull(data.get(1))),
                Coin.parseCoin((String) checkNotNull(data.get(2))),
                emptyCurrencyArray
        );
        return output;
    }

    public static Coin[] getTotals(List<? extends Order> orders) {
        Coin[] totals = new Coin[]{ Coin.ZERO, Coin.ZERO };
        for(Order order : orders) {
            totals[0] = totals[0].add(order.amount);
            totals[1] = totals[1].add(Coin.valueOf(
                    BigInteger.valueOf(order.amount.value)
                            .multiply(BigInteger.valueOf(order.price.value))
                            .divide(BigInteger.valueOf(Coin.COIN.value))
                            .longValue()));
        }
        return totals;
    }

    public static List<Order> reduce(List<? extends Order> orders) {
        List<Order> output = new ArrayList<Order>(orders.size());
        Order last = null;
        for(Order order : orders) {
            if(!output.isEmpty() && last.price.equals(order.price)) {
                last.amount = last.amount.add(order.amount);
            } else {
                last = order.clone();
                output.add(last);
            }
        }
        return output;
    }
}