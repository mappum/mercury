package io.coinswap.swap;

import com.google.bitcoin.core.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class AtomicSwapTrade {
    public final String id;

    public boolean buy;

    // exchange fee, in satoshis per 10 microcoins
    public final Coin fee;

    public final String[] coins;
    public final Coin[] quantities;

    // coins: 0 = chain A (A->B), 1 = chain B (B->A)
    // quantities: 0 = amount traded from A->B, 1 = B->A
    public AtomicSwapTrade(String id, String[] coins, Coin[] quantities, Coin fee) {
        this.id = checkNotNull(id);
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
}
