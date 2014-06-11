package io.coinswap.swap;

import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.KeyChain;
import io.coinswap.net.Connection;
import net.jcip.annotations.GuardedBy;
import net.minidev.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/*
 * A client for performing cross-chain atomic transfers.
 * Loosely based on the Atomic Cross Chain Transfers BIP draft by Noel Tiernan.
 * (https://github.com/TierNolan/bips/blob/bip4x/bip-atom.mediawiki)
 */
public class AtomicSwapClient implements Connection.ReceiveListener {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwapClient.class);
    public static final int VERSION = 0;

    private List<ECKey> myKeys;

    // alice = trader on blockchain that accepts hashlocked transactions
    // bob = other trader, generates x for hashlock
    private final boolean alice;

    private final WalletAppKit[] wallets;
    private final Connection connection;
    private final AtomicSwap state;

    public AtomicSwapClient(AtomicSwap state, boolean alice, WalletAppKit[] wallets, Connection connection) {
        this.state = checkNotNull(state);
        this.alice = alice;

        this.wallets = checkNotNull(wallets);
        checkState(wallets.length == 2);
        checkNotNull(wallets[0]);
        checkNotNull(wallets[1]);

        this.connection = checkNotNull(connection);
        connection.addListener(state.trade.id, this);
    }

    public void start() {
        JSONObject message = new JSONObject();
        message.put("channel", state.trade.id);
        message.put("method", AtomicSwapMethod.VERSION);
        message.put("version", VERSION);
        connection.write(message);
    }

    private void sendKeys() {
        JSONObject message = new JSONObject();
        message.put("channel", state.trade.id);
        message.put("method", AtomicSwapMethod.KEYS_REQUEST);

        int a = alice ? 0 : 1;
        myKeys = (List<ECKey>)(List<?>) wallets[a].wallet().freshKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, 3);
        state.setKeys(alice, myKeys);
        List<String> keyStrings = new ArrayList<String>(3);
        for(ECKey key : myKeys)
            keyStrings.add(Base58.encode(key.getPubKey()));
        message.put("keys", keyStrings);

        if(!alice) {
            state.setXKey(wallets[a].wallet().freshReceiveKey());
            message.put("x", Base58.encode(state.getXHash()));
        }

        connection.write(message);
    }

    @Override
    public void onReceive(Map data) {
        try {
            state.onReceive(alice, data);

            String method = (String) data.get("method");

            if (method.equals(AtomicSwapMethod.VERSION)) {
                state.setStep(AtomicSwap.Step.EXCHANGING_KEYS);
                sendKeys();

            } else if (method.equals(AtomicSwapMethod.KEYS_REQUEST)) {
                state.setStep(AtomicSwap.Step.EXCHANGING_BAILIN_HASHES);
            }
        } catch(Exception ex) {
            log.error(ex.getMessage());
        }
    }
}
