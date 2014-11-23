package io.coinswap;

import io.coinswap.client.Currency;
import org.bitcoinj.params.*;

import java.io.File;
import java.util.ArrayList;

public class Coins {
    public static ArrayList<Currency> get(File dataDir) {
        // TODO: load pairs from exchange server
        ArrayList<Currency> currencies = new ArrayList<Currency>();

        coins.add(new Currency(MainNetParams.get(), dataDir,
                "Bitcoin", "BTC", "<i class=\"fa fa-bitcoin\"></i>",
                new String[] { "LTC", "DOGE", "BTCt", "LTCt" }, coins.size(), false));
        coins.add(new Currency(LitecoinMainNetParams.get(), dataDir,
                "Litecoin", "LTC", "&#321;",
                new String[] { "BTC", "DOGE", "LTCt" }, coins.size(), false));
        coins.add(new Currency(DogecoinMainNetParams.get(), dataDir,
                "Dogecoin", "DOGE", "&#272;",
                new String[] { "BTC", "LTC" }, coins.size(), false));
        coins.add(new Currency(TestNet3Params.get(), dataDir,
                "Bitcoin Testnet", "BTCt", "<i class=\"fa fa-bitcoin\"></i>",
                new String[] { "BTC", "LTCt" }, coins.size(), true));
        coins.add(new Currency(LitecoinTestNetParams.get(), dataDir,
                "Litecoin Testnet", "LTCt", "&#321;",
                new String[] { "BTC", "LTC", "BTCt" }, coins.size(), true));

        // TODO: sort coins by volume

        return currencies;
    }
}
