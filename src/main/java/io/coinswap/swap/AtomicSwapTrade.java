package io.coinswap.swap;

import com.google.bitcoin.core.*;
import net.minidev.json.JSONObject;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class AtomicSwapTrade {
    public static final Coin FEE = Coin.valueOf(10);

    // exchange fee, in satoshis per 10 microcoins
    public final Coin fee;

    public final String[] coins;
    public final Coin[] quantities;
    public final boolean buy;

    // coins: 0 = chain A (A->B), 1 = chain B (B->A)
    // quantities: 0 = amount traded from A->B (quantity), 1 = B->A (total)
    public AtomicSwapTrade(boolean buy, String[] coins, Coin[] quantities, Coin fee) {
        this.buy = buy;
        this.coins = checkNotNull(coins);
        checkNotNull(coins[0]);
        checkNotNull(coins[1]);
        this.quantities = checkNotNull(quantities);
        checkNotNull(quantities[0]);
        checkNotNull(quantities[1]);
        this.fee = checkNotNull(fee);
    }

    public Coin getFeeAmount(boolean a) {
        Coin[] divided = quantities[a ? 0 : 1].divideAndRemainder(1000);
        long tensOfMicrocoins = divided[0].longValue();
        // ceil the number of 10*microcoins
        if(divided[1].longValue() > 0)
            tensOfMicrocoins++;
        return fee.multiply(tensOfMicrocoins);
    }

    public Map toJson() {
        Map data = new JSONObject();
        data.put("buy", this.buy);
        data.put("fee", this.fee);
        data.put("coins", this.coins);
        data.put("quantities", new long[]{ quantities[0].longValue(), quantities[1].longValue() });
        return data;
    }

    public static AtomicSwapTrade fromJson(Map data) {
        long[] longQuantities = (long[]) data.get("quantities");
        Coin[] quantities = new Coin[]{ Coin.valueOf(longQuantities[0]), Coin.valueOf(longQuantities[1]) };
        return new AtomicSwapTrade(
                (boolean) data.get("buy"),
                (String[]) data.get("coins"),
                quantities,
                Coin.valueOf((long) data.get("fee")));
    }
}
