package io.coinswap.swap;

import io.coinswap.client.Currency;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOP;

public abstract class AtomicSwapController {
    public static final int VERSION = 0;

    protected final AtomicSwap swap;

    protected final ReentrantLock lock = Threading.lock(AtomicSwapController.class.getName());

    protected AtomicSwapController(AtomicSwap swap) {
        this.swap = checkNotNull(swap);
    }

    public void onMessage(boolean fromAlice, Map data) throws Exception {
        lock.lock();
        try {
            String method = (String) checkNotNull(data.get("method"));

            if (method.equals(AtomicSwapMethod.VERSION)) {
                checkState(swap.getStep() == AtomicSwap.Step.STARTING);
                checkState((Integer) data.get("version") == VERSION);

            } else if (method.equals(AtomicSwapMethod.KEYS_REQUEST)) {
                checkState(swap.getStep() == AtomicSwap.Step.EXCHANGING_KEYS);
                checkState(swap.getKeys(fromAlice) == null);

                List<String> keyStrings = (ArrayList<String>) data.get("keys");
                checkState(keyStrings.size() == 3);

                List<ECKey> keys = new ArrayList<ECKey>(3);
                for(String s : keyStrings)
                    keys.add(ECKey.fromPublicOnly(Base58.decode(checkNotNull(s))));
                swap.setKeys(fromAlice, keys);

                if (!fromAlice) {
                    byte[] xHash = Base58.decode((String) checkNotNull(data.get("x")));
                    swap.setXHash(xHash);
                }

            } else if (method.equals(AtomicSwapMethod.BAILIN_HASH_REQUEST)) {
                checkState(swap.getStep() == AtomicSwap.Step.EXCHANGING_BAILIN_HASHES);
                checkState(swap.getBailinHash(fromAlice) == null);

                byte[] hashBytes = Base58.decode((String) checkNotNull(data.get("hash")));
                Sha256Hash hash = new Sha256Hash(hashBytes);
                swap.setBailinHash(fromAlice, hash);

            } else if (method.equals(AtomicSwapMethod.EXCHANGE_SIGNATURES)) {
                checkState(swap.getStep() == AtomicSwap.Step.EXCHANGING_SIGNATURES);
            }
        } finally {
            lock.unlock();
        }
    }
}
