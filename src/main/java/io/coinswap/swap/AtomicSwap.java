package io.coinswap.swap;

import com.google.bitcoin.core.*;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.utils.Threading;
import io.coinswap.net.Connection;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AtomicSwap {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwap.class);
    protected final ReentrantLock lock = Threading.lock(AtomicSwap.class.getName());
    public static final int VERSION = 0;

    private List<ECKey>[] keys;

    private byte[] x;
    private byte[] xHash;

    private Sha256Hash[] bailinHashes;
    private Transaction[] bailinTxs;

    public final AtomicSwapTrade trade;

    public enum Step {
        STARTING,
        EXCHANGING_KEYS,
        EXCHANGING_BAILIN_HASHES,
        EXCHANGING_SIGNATURES,
        BROADCASTING_BAILIN,
        BROADCASTING_PAYOUT,
        BROADCASTING_REFUND,
        COMPLETE
    }
    private Step step = Step.STARTING;

    public AtomicSwap(AtomicSwapTrade trade) {
        this.trade = checkNotNull(trade);
        keys = new ArrayList[2];
        bailinHashes = new Sha256Hash[2];
        bailinTxs = new Transaction[2];
    }

    public void cancel() {
        lock.lock();
        try {
            step = Step.BROADCASTING_REFUND;
        } finally {
            lock.unlock();
        }
    }

    public Step getStep() {
        lock.lock();
        try {
            return step;
        } finally {
            lock.unlock();
        }
    }

    public void setStep(Step step) {
        lock.lock();
        try {
            this.step = step;
        } finally {
            lock.unlock();
        }
    }

    public List<ECKey> getKeys(boolean alice) {
        lock.lock();
        try {
            return keys[alice ? 0 : 1];
        } finally {
            lock.unlock();
        }
    }

    public void setKeys(boolean alice, List<ECKey> keys) {
        checkState(keys.size() == 3);
        checkNotNull(keys.get(0));
        checkNotNull(keys.get(1));
        checkNotNull(keys.get(2));
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            this.keys[a] = keys;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getXHash() {
        lock.lock();
        try {
            return xHash;
        } finally {
            lock.unlock();
        }
    }

    public void setXKey(ECKey key) {
        checkNotNull(key);

        byte[] x = ScriptBuilder.createOutputScript(key).getProgram();
        byte[] hash = Utils.Hash160(x);

        lock.lock();
        try {
            this.x = x;
            if(xHash == null) xHash = hash;
            else checkState(Arrays.equals(xHash, hash));
        } finally {
            lock.unlock();
        }
    }

    public void setXHash(byte[] hash) {
        checkNotNull(hash);

        lock.lock();
        try {
            checkState(x == null);
            xHash = hash;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash getBailinHash(boolean alice) {
        lock.lock();
        try {
            return bailinHashes[alice ? 0 : 1];
        } finally {
            lock.unlock();
        }
    }

    public void setBailinHash(boolean alice, Sha256Hash hash) {
        checkNotNull(hash);
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(bailinHashes[a] == null);
            bailinHashes[a] = hash;
        } finally {
            lock.unlock();
        }
    }

    public void setBailinTx(boolean alice, Transaction tx) {
        checkNotNull(tx);
        Sha256Hash hash = tx.getHash();
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(bailinTxs[a] == null);
            bailinTxs[a] = tx;

            if(bailinHashes[a] == null) bailinHashes[a] = hash;
            else checkState(hash.compareTo(bailinHashes[a]) == 0);
        } finally {
            lock.unlock();
        }
    }
}
