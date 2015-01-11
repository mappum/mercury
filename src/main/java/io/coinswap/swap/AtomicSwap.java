package io.coinswap.swap;

import net.minidev.json.JSONObject;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;
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
    private static final int REFUND_PERIOD = 4 * 60; // in minutes

    private List<ECKey>[] keys;

    private byte[] x;
    private byte[] xHash;

    private Sha256Hash[] bailinHashes;
    private Transaction[] bailinTxs;
    private ECKey.ECDSASignature[] payoutSigs;
    private ECKey.ECDSASignature[] refundSigs;

    private final long time;

    public final String id;
    public final AtomicSwapTrade trade;

    public enum Step {
        STARTING,
        EXCHANGING_KEYS,
        EXCHANGING_BAILIN_HASHES,
        EXCHANGING_SIGNATURES,
        EXCHANGING_BAILINS,
        WAITING_FOR_BAILIN,
        WAITING_FOR_PAYOUT, // alice-only
        COMPLETE,
        WAITING_FOR_REFUND,
        CANCELED
    }
    private Step step = Step.STARTING;

    public AtomicSwap(String id, AtomicSwapTrade trade, long time) {
        this.id = checkNotNull(id);
        this.trade = checkNotNull(trade);
        this.time = time;
        keys = new ArrayList[2];
        bailinHashes = new Sha256Hash[2];
        bailinTxs = new Transaction[2];
        payoutSigs = new ECKey.ECDSASignature[2];
        refundSigs = new ECKey.ECDSASignature[2];
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

    public long getTime() {
        lock.lock();
        try {
            return time;
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
        checkState(keys.size() >= 3);
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
        setX(x);
    }

    public void setX(byte[] x) {
        checkNotNull(x);

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

    public byte[] getX() {
        lock.lock();
        try {
            checkNotNull(x);
            return x;
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

    public Transaction getBailinTx(boolean alice) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            return checkNotNull(bailinTxs[a]);
        } finally {
            lock.unlock();
        }
    }

    public void setPayoutSig(boolean alice, ECKey.ECDSASignature sig) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(payoutSigs[a] == null);
            payoutSigs[a] = sig;
        } finally {
            lock.unlock();
        }
    }

    public ECKey.ECDSASignature getPayoutSig(boolean alice) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            return payoutSigs[a];
        } finally {
            lock.unlock();
        }
    }

    public void setRefundSig(boolean alice, ECKey.ECDSASignature sig) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(refundSigs[a] == null);
            refundSigs[a] = sig;
        } finally {
            lock.unlock();
        }
    }

    public ECKey.ECDSASignature getRefundSig(boolean alice) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            return refundSigs[a];
        } finally {
            lock.unlock();
        }
    }

    public long getLocktime(boolean alice) {
        int period = REFUND_PERIOD * (alice ? 1 : 2) * 60;
        return getTime() + period;
    }

    public Map toJson() {
        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("trade", trade.toJson());
        data.put("time", time + "");
        return data;
    }

    public static AtomicSwap fromJson(Map data) {
        String id = (String) checkNotNull(data.get("id"));
        AtomicSwapTrade trade = AtomicSwapTrade.fromJson((Map) checkNotNull(data.get("trade")));
        long time = Long.valueOf((String) checkNotNull(data.get("time")));
        return new AtomicSwap(id, trade, time);
    }

    public String getChannelId(boolean alice) {
        return "swap:" + id + ":" + (alice ? "0" : "1");
    }
}
