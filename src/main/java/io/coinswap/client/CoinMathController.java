package io.coinswap.client;

import io.mappum.altcoinj.core.Coin;

import java.math.BigInteger;

public class CoinMathController {
    private static final BigInteger COIN = BigInteger.valueOf(Coin.COIN.value);

    private BigInteger parse(String value) {
        try {
            return BigInteger.valueOf(Coin.parseCoin(value).value);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    public String add(String aString, String bString) {
        return Coin.parseCoin(aString).add(Coin.parseCoin(bString)).toPlainString();
    }

    public String subtract(String aString, String bString) {
        return Coin.parseCoin(aString).subtract(Coin.parseCoin(bString)).toPlainString();
    }

    public String multiply(String aString, String bString) {
        BigInteger a = parse(aString),
                   b = parse(bString);
        return Coin.valueOf(a.multiply(b).divide(COIN).longValue())
                .toPlainString();
    }

    public String divide(String aString, String bString) {
        BigInteger a = parse(aString),
                   b = parse(bString);
        return Coin.valueOf(a.multiply(COIN).divide(b).longValue())
                .toPlainString();
    }

    public int compare(String aString, String bString) {
        BigInteger a = parse(aString),
                b = parse(bString);
        return a.compareTo(b);
    }

    public String truncate(String value) {
        return Coin.valueOf(parse(value).longValue()).toPlainString();
    }

    public String format(String value) {
        return Coin.parseCoin(value).toFriendlyString();
    }
}
