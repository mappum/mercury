package io.coinswap.swap;

import net.minidev.json.JSONObject;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AtomicSwap implements Serializable {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwap.class);
    protected static final ReentrantLock LOCK = Threading.lock(AtomicSwap.class.getName());
    protected ReentrantLock lock = LOCK;

    public static final int VERSION = 0;
    private static final int REFUND_PERIOD = 4 * 60; // in minutes
    private static final int SERIALIZATION_VERSION = 0;

    private Map<StateListener, Executor> listeners;

    private List<ECKey>[] keys;

    private byte[] x;
    private byte[] xHash;

    private Sha256Hash[] bailinHashes;
    private Sha256Hash[] payoutHashes;
    private Sha256Hash[] refundHashes;

    private Transaction[] bailinTxs;
    private byte[][] payoutSigs;
    private byte[][] refundSigs;

    private long time;

    public String id;
    public AtomicSwapTrade trade;
    public boolean switched;

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
        payoutHashes = new Sha256Hash[2];
        refundHashes = new Sha256Hash[2];
        bailinTxs = new Transaction[2];
        payoutSigs = new byte[2][];
        refundSigs = new byte[2][];
        listeners = new HashMap<StateListener, Executor>();
    }

    public boolean isAlice() {
        return !trade.buy ^ switched;
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
        Step previous;

        lock.lock();
        try {
            previous = this.step;
            this.step = step;
        } finally {
            lock.unlock();
        }

        // trigger event
        if(!step.equals(previous)) {
            final AtomicSwap parent = this;
            for(StateListener listener : listeners.keySet()) {
                listeners.get(listener).execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStepChange(step, parent);
                    }
                });
            }
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

    public Sha256Hash getPayoutHash(boolean alice) {
        lock.lock();
        try {
            return payoutHashes[alice ? 0 : 1];
        } finally {
            lock.unlock();
        }
    }

    public void setPayoutHash(boolean alice, Sha256Hash hash) {
        checkNotNull(hash);
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(payoutHashes[a] == null);
            payoutHashes[a] = hash;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash getRefundHash(boolean alice) {
        lock.lock();
        try {
            return refundHashes[alice ? 0 : 1];
        } finally {
            lock.unlock();
        }
    }

    public void setRefundHash(boolean alice, Sha256Hash hash) {
        checkNotNull(hash);
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(refundHashes[a] == null);
            refundHashes[a] = hash;
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
            payoutSigs[a] = sig.encodeToDER();
        } finally {
            lock.unlock();
        }
    }

    public ECKey.ECDSASignature getPayoutSig(boolean alice) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            return ECKey.ECDSASignature.decodeFromDER(payoutSigs[a]);
        } finally {
            lock.unlock();
        }
    }

    public void setRefundSig(boolean alice, ECKey.ECDSASignature sig) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            checkState(refundSigs[a] == null);
            refundSigs[a] = sig.encodeToDER();
        } finally {
            lock.unlock();
        }
    }

    public ECKey.ECDSASignature getRefundSig(boolean alice) {
        int a = alice ? 0 : 1;

        lock.lock();
        try {
            return ECKey.ECDSASignature.decodeFromDER(refundSigs[a]);
        } finally {
            lock.unlock();
        }
    }

    public long getLocktime(boolean alice) {
        int period = REFUND_PERIOD * (alice ? 1 : 2) * 60;
        return getTime() + period;
    }

    public String getChannelId(boolean alice) {
        return "swap:" + id + ":" + (alice ? "0" : "1");
    }

    public void addEventListener(StateListener listener) {
        addEventListener(listener, Threading.SAME_THREAD);
    }

    public void addEventListener(StateListener listener, Executor executor) {
        lock.lock();
        try {
            this.listeners.put(listener, executor);
        } finally {
            lock.unlock();
        }
    }

    public void removeEventListener(StateListener listener) {
        lock.lock();
        try {
            this.listeners.remove(listener);
        } finally {
            lock.unlock();
        }
    }

    public Map toJson(boolean compact) {
        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("trade", trade.toJson());
        data.put("time", time + "");
        if(!compact) {
            data.put("step", step);
            data.put("trade", trade.toJson());
        }
        return data;
    }

    public static AtomicSwap fromJson(Map data) {
        String id = (String) checkNotNull(data.get("id"));
        AtomicSwapTrade trade = AtomicSwapTrade.fromJson((Map) checkNotNull(data.get("trade")));
        long time = Long.valueOf((String) checkNotNull(data.get("time")));
        return new AtomicSwap(id, trade, time);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        lock.lock();
        try {
            out.writeObject(SERIALIZATION_VERSION);
            out.writeObject(x);
            out.writeObject(xHash);
            out.writeObject(bailinHashes);
            out.writeObject(payoutHashes);
            out.writeObject(refundHashes);
            out.writeObject(bailinTxs);
            out.writeObject(payoutSigs);
            out.writeObject(refundSigs);
            out.writeObject(time);
            out.writeObject(id);
            out.writeObject(trade);
            out.writeObject(step);
            out.writeObject(switched);

            List<byte[]>[] keys = new List[2];
            keys[0] = new ArrayList<byte[]>(3);
            if(this.keys[0] != null)
                for(ECKey key : this.keys[0]) keys[0].add(key.getPubKey());
            keys[1] = new ArrayList<byte[]>(3);
            if(this.keys[1] != null)
                for(ECKey key : this.keys[1]) keys[1].add(key.getPubKey());
            out.writeObject(keys);

        } finally {
            lock.unlock();
        }
    }

    private void readObject(ObjectInputStream in) throws IOException {
        this.lock = LOCK;
        lock.lock();
        try {
            int version = (int) in.readObject();
            checkState(version == SERIALIZATION_VERSION);

            x = (byte[]) in.readObject();
            xHash = (byte[]) in.readObject();
            bailinHashes = (Sha256Hash[]) in.readObject();
            payoutHashes = (Sha256Hash[]) in.readObject();
            refundHashes = (Sha256Hash[]) in.readObject();
            bailinTxs = (Transaction[]) in.readObject();
            payoutSigs = (byte[][]) in.readObject();
            refundSigs = (byte[][]) in.readObject();
            time = (long) in.readObject();
            id = (String) in.readObject();
            trade = (AtomicSwapTrade) in.readObject();
            step = (AtomicSwap.Step) in.readObject();
            switched = (boolean) in.readObject();

            List<byte[]>[] keys = (List<byte[]>[]) in.readObject();
            this.keys = new List[2];
            this.keys[0] = new ArrayList<ECKey>(keys[0].size());
            for(byte[] key : keys[0]) this.keys[0].add(ECKey.fromPublicOnly(key));
            this.keys[1] = new ArrayList<ECKey>(keys[1].size());
            for(byte[] key : keys[1]) this.keys[1].add(ECKey.fromPublicOnly(key));

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public interface StateListener {
        public void onStepChange(AtomicSwap.Step step, AtomicSwap swap);
    }
}

