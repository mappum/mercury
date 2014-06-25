package io.coinswap.swap;

import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.utils.Threading;
import io.coinswap.net.Connection;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class AtomicSwapController implements Connection.ReceiveListener {
    public static final int VERSION = 0;

    protected final AtomicSwap state;

    protected final ReentrantLock lock = Threading.lock(AtomicSwapController.class.getName());

    protected AtomicSwapController(AtomicSwap state) {
        this.state = checkNotNull(state);
    }

    public void onMessage(boolean fromAlice, Map data) throws Exception {
        lock.lock();
        try {
            String method = (String) checkNotNull(data.get("method"));

            if (method.equals(AtomicSwapMethod.VERSION)) {
                checkState(state.getStep() == AtomicSwap.Step.STARTING);
                checkState((Integer) data.get("version") == VERSION);

            } else if (method.equals(AtomicSwapMethod.KEYS_REQUEST)) {
                checkState(state.getStep() == AtomicSwap.Step.EXCHANGING_KEYS);
                checkState(state.getKeys(fromAlice) == null);

                List<String> keyStrings = (ArrayList<String>) data.get("keys");
                checkState(keyStrings.size() == 3);

                List<ECKey> keys = new ArrayList<ECKey>(3);
                for(String s : keyStrings)
                    keys.add(ECKey.fromPublicOnly(Base58.decode(checkNotNull(s))));
                state.setKeys(fromAlice, keys);

                if (!fromAlice) {
                    byte[] xHash = Base58.decode((String) checkNotNull(data.get("x")));
                    state.setXHash(xHash);
                }

            } else if (method.equals(AtomicSwapMethod.BAILIN_HASH_REQUEST)) {
                checkState(state.getStep() == AtomicSwap.Step.EXCHANGING_BAILIN_HASHES);
                checkState(state.getBailinHash(fromAlice) == null);

                byte[] hashBytes = Base58.decode(checkNotNull((String) data.get("hash")));
                Sha256Hash hash = new Sha256Hash(hashBytes);
                state.setBailinHash(fromAlice, hash);
            }
        } finally {
            lock.unlock();
        }
    }
}
