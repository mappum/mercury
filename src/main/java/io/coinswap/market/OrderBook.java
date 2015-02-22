package io.coinswap.market;

import net.minidev.json.JSONObject;
import org.bitcoinj.core.Coin;

import java.util.*;

public class OrderBook {
    protected List<Order> bids, asks;

    public OrderBook() {
        bids = new LinkedList<Order>();
        asks = new LinkedList<Order>();
    }

    public void add(Order order) {
        ListIterator<Order> i = getIteratorAt(order.bid, order.price);
        if(!i.hasNext()) {
            i.add(order);
        } else {
            Order cursor = i.next();
            if(cursor.price.equals(order.price)) {
                cursor.amount = cursor.amount.add(order.amount);
            } else {
                i.add(order);
            }
        }
    }

    public Order remove(Order order) {
        ListIterator<Order> i = getIteratorAt(order.bid, order.price);
        if(!i.hasNext()) {
            return null;
        } else {
            Order cursor = i.next();
            if(cursor.price.equals(order.price)) {
                cursor.amount = cursor.amount.subtract(order.amount);
                if(!cursor.amount.isGreaterThan(Coin.ZERO)) {
                    i.remove();
                    return cursor;
                }
            }
            return null;
        }
    }

    public Map<String, Object> toJson(int limit) {
        Map<String, Object> json = new JSONObject();
        List<Object> bidsJson = new ArrayList<Object>(limit);
        for(Order order : bids.subList(0, Math.min(bids.size(), limit))) bidsJson.add(order.toJson(true));
        json.put("bids", bidsJson);
        List<Object> asksJson = new ArrayList<Object>(limit);
        for(Order order : asks.subList(0, Math.min(asks.size(), limit))) asksJson.add(order.toJson(true));
        json.put("asks", asksJson);
        return json;
    }

    private ListIterator<Order> getIteratorAt(boolean bid, Coin price) {
        // TODO: do a binary search instead of iterating all the way through

        List<Order> orders = bid ? bids : asks;
        int comparator = bid ? -1 : 1;
        ListIterator<Order> i = orders.listIterator();
        while(i.hasNext()) {
            Order order = i.next();
            int cmp = price.compareTo(order.price);
            if(cmp == 0 || cmp == comparator) {
                i.previous();
                break;
            }
        }
        return i;
    }
}
