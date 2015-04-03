package io.coinswap;

import io.coinswap.client.Currency;
import io.mappum.altcoinj.params.*;

import java.io.File;
import java.util.ArrayList;

public class Coins {
    public static ArrayList<Currency> get(File dataDir) {
        // TODO: load pairs from exchange server
        ArrayList<Currency> currencies = new ArrayList<Currency>();

        currencies.add(new Currency(MainNetParams.get(), dataDir,
                "Bitcoin", "BTC", "<i class=\"fa fa-bitcoin\"></i>",
                new String[]{"LTC","DOGE","DRK"}, currencies.size(), true, 1));

        currencies.add(new Currency(LitecoinMainNetParams.get(), dataDir,
                "Litecoin", "LTC", "&#321;",
                new String[]{"BTC"}, currencies.size(), false, 5));

        currencies.add(new Currency(DarkcoinMainNetParams.get(), dataDir,
                "Darkcoin", "DRK", "D",
                new String[] {"BTC"}, currencies.size(), false, 5));

        currencies.add(new Currency(DogecoinMainNetParams.get(), dataDir,
                "Dogecoin", "DOGE", "&#272;",
                new String[] {"BTC"}, currencies.size(), false, 5));

        /*currencies.add(new Currency(TestNet3Params.get(), dataDir,
                "Bitcoin Testnet", "BTCt", "<i class=\"fa fa-bitcoin\"></i>",
                new String[]{"BTC","LTC","DOGE"}, currencies.size(), true, 1));*/

        /*currencies.add(new Currency(LitecoinTestNetParams.get(), dataDir,
                "Litecoin Testnet", "LTCt", "&#321;",
                new String[] {"BTC", "LTC", "BTCt"}, currencies.size(), true, 3));*/

        return currencies;
    }
}
