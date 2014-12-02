package io.coinswap;

import org.bitcoinj.params.*;
import io.coinswap.client.Coin;

import java.io.File;
import java.util.ArrayList;

public class Coins {
    public static ArrayList<Coin> get(File dataDir) {
        // TODO: load pairs from exchange server
        ArrayList<Coin> coins = new ArrayList<Coin>();

        coins.add(new Coin(MainNetParams.get(), dataDir,
                "Bitcoin", "BTC", "<i class=\"fa fa-bitcoin\"></i>",
                new String[] { "LTC", "DOGE", "BTCt", "LTCt" }, coins.size(), false));
        coins.add(new Coin(LitecoinMainNetParams.get(), dataDir,
                "Litecoin", "LTC", "&#321;",
                new String[] { "BTC", "DOGE", "LTCt" }, coins.size(), false));
        coins.add(new Coin(DogecoinMainNetParams.get(), dataDir,
                "Dogecoin", "DOGE", "&#272;",
                new String[] { "BTC", "LTC" }, coins.size(), false));
        coins.add(new Coin(TestNet3Params.get(), dataDir,
                "Bitcoin Testnet", "BTCt", "<i class=\"fa fa-bitcoin\"></i>",
                new String[] { "BTC", "LTCt" }, coins.size(), true));
        coins.add(new Coin(LitecoinTestNetParams.get(), dataDir,
                "Litecoin Testnet", "LTCt", "&#321;",
                new String[] { "BTC", "LTC", "BTCt" }, coins.size(), true));

        // TODO: sort coins by volume

        return coins;
    }
}
