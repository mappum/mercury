package io.coinswap.swap;

import com.google.bitcoin.core.*;

public class AtomicSwapTrade {
    public final String id;

    public boolean buy;

    public final Coin buyQuantity;
    public final Coin sellQuantity;

    // exchange fee, in satoshis per 10 microcoins
    public final Coin fee;

    public final String buyCoin;
    public final String sellCoin;

    public AtomicSwapTrade(String id, boolean buy, String buyCoin, Coin buyQuantity, String sellCoin, Coin sellQuantity, Coin fee) {
        this.id = id;
        this.buy = buy;

        this.buyQuantity = buyQuantity;
        this.buyCoin = buyCoin;

        this.sellQuantity = sellQuantity;
        this.sellCoin = sellCoin;

        this.fee = fee;
    }

    public Coin getFeeAmount() {
        Coin[] divided = (buy ? buyQuantity : sellQuantity).divideAndRemainder(1000);
        long tensOfMicrocoins = divided[0].longValue();
        // ceil the number of 10*microcoins
        if(divided[1].longValue() > 0)
            tensOfMicrocoins++;
        return fee.multiply(tensOfMicrocoins);
    }
}
