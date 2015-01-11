package io.coinswap.client;

import org.bitcoinj.core.Coin;

import java.math.BigInteger;

public class CoinMathController {
    private static final BigInteger COIN = BigInteger.valueOf(Coin.COIN.value);

    private BigInteger parse(String value) {
        return BigInteger.valueOf(Coin.parseCoin(value).value);
    }

    public String multiply(String aString, String bString) {
        BigInteger a = parse(aString),
                   b = parse(bString);
        return Coin.valueOf(a.multiply(b).divide(COIN).longValueExact())
                .toPlainString();
    }

    public String divide(String aString, String bString) {
        BigInteger a = parse(aString),
                   b = parse(bString);
        return Coin.valueOf(a.divide(b).longValueExact())
                .toPlainString();
    }

    public String truncate(String value) {
        return Coin.valueOf(parse(value).longValue()).toPlainString();
    }
}
