package io.coinswap.swap;

import net.minidev.json.JSONObject;
import io.mappum.altcoinj.core.*;
import io.mappum.altcoinj.crypto.TransactionSignature;
import io.mappum.altcoinj.script.Script;
import io.mappum.altcoinj.script.ScriptBuilder;
import io.mappum.altcoinj.script.ScriptOpCodes;
import io.mappum.altcoinj.utils.Threading;
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

    private static final long serialVersionUID = 1;
    public static final int VERSION = 0;
    private static final int REFUND_PERIOD = 4 * 60; // in minutes
    private static final int REFUND_BROADCAST_DELAY = 10; // in seconds

    private Map<StateListener, Executor> listeners;

    private List<ECKey>[] keys;

    private byte[] x;
    private byte[] xHash;

    private Sha256Hash[] bailinHashes;
    private Sha256Hash[] payoutHashes;
    private Sha256Hash[] refundHashes;

    private Transaction[] bailinTxs;
    private byte[][][] payoutSigs;
    private byte[][][] refundSigs;

    private long time;

    public String id;
    public AtomicSwapTrade trade;
    public boolean switched;

    public enum Step {
        STARTING,
        EXCHANGING_KEYS,
        EXCHANGING_BAILIN_HASHES,
        EXCHANGING_SIGNATURES,
        WAITING_FOR_BAILIN,
        WAITING_FOR_PAYOUT, // alice-only
        COMPLETE,
        WAITING_FOR_REFUND,
        CANCELED
    }
    private Step step = Step.STARTING;

    // the state machine for a swap
    public AtomicSwap(String id, AtomicSwapTrade trade, long time) {
        this.id = checkNotNull(id);
        this.trade = checkNotNull(trade);
        this.time = time;
        keys = new ArrayList[2];
        bailinHashes = new Sha256Hash[2];
        payoutHashes = new Sha256Hash[2];
        refundHashes = new Sha256Hash[2];
        bailinTxs = new Transaction[2];
        payoutSigs = new byte[2][3][];
        refundSigs = new byte[2][2][];
        listeners = new HashMap<StateListener, Executor>();
    }

    // returns true if we are still in the setup stage, e.g. have not yet
    // committed to any transactions
    public boolean isSettingUp() {
        lock.lock();
        try {
            return step.ordinal() <= Step.EXCHANGING_SIGNATURES.ordinal();
        } finally {
            lock.unlock();
        }
    }

    // returns true if we have started communicating with the other party to
    // start the atomic swap
    public boolean isStarted() {
        return !getStep().equals(Step.STARTING);
    }

    // returns true if we have either successfully finished the swap, or have
    // canceled it and recovered our funds
    public boolean isDone() {
        Step step = getStep();
        return step == Step.COMPLETE || step == Step.CANCELED;
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
        if(step != previous) {
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

    public void setPayoutSig(boolean aliceTx, int i, ECKey.ECDSASignature sig) {
        lock.lock();
        try {
            checkState(payoutSigs[aliceTx ? 0 : 1][i] == null);
            payoutSigs[aliceTx ? 0 : 1][i] = sig.encodeToDER();
        } finally {
            lock.unlock();
        }
    }

    public TransactionSignature getPayoutSig(boolean aliceTx, int i) {
        lock.lock();
        try {
            if(payoutSigs[aliceTx ? 0 : 1][i] == null) return null;
            ECKey.ECDSASignature sig = ECKey.ECDSASignature.decodeFromDER(payoutSigs[aliceTx ? 0 : 1][i]);
            return new TransactionSignature(sig, Transaction.SigHash.ALL, false);
        } finally {
            lock.unlock();
        }
    }

    public void setRefundSig(boolean aliceTx, int i, ECKey.ECDSASignature sig) {
        lock.lock();
        try {
            checkState(refundSigs[aliceTx ? 0 : 1][i] == null);
            refundSigs[aliceTx ? 0 : 1][i] = sig.encodeToDER();
        } finally {
            lock.unlock();
        }
    }

    public TransactionSignature getRefundSig(boolean aliceTx, int i) {
        lock.lock();
        try {
            if(refundSigs[aliceTx ? 0 : 1][i] == null) return null;
            ECKey.ECDSASignature sig = ECKey.ECDSASignature.decodeFromDER(refundSigs[aliceTx ? 0 : 1][i]);
            return new TransactionSignature(sig, Transaction.SigHash.ALL, false);
        } finally {
            lock.unlock();
        }
    }

    public long getLocktime(boolean alice) {
        int period = REFUND_PERIOD * (alice ? 1 : 2) * 60;
        return getTime() + period;
    }

    public long getTimeUntilRefund(boolean alice) {
        long secondsLeft = getLocktime(alice) - System.currentTimeMillis() / 1000;
        secondsLeft += REFUND_BROADCAST_DELAY; // wait some extra time to make sure we're over the locktime
        return secondsLeft;
    }

    public Script getMultisigRedeem() {
        lock.lock();
        try {
            List<ECKey> multiSigKeys = new ArrayList<ECKey>(2);
            multiSigKeys.add(keys[0].get(0));
            multiSigKeys.add(keys[1].get(0));
            return ScriptBuilder.createMultiSigOutputScript(2, multiSigKeys);
        } finally {
            lock.unlock();
        }
    }

    public Script getHashlockScript(boolean alice) {
        lock.lock();
        try {
            if (alice) return new Script(getX());

            return new ScriptBuilder()
                    .op(ScriptOpCodes.OP_HASH160)
                    .data(getXHash())
                    .op(ScriptOpCodes.OP_EQUALVERIFY)
                    .data(getKeys(true).get(0).getPubKey())
                    .op(ScriptOpCodes.OP_CHECKSIG)
                    .build();

        } finally {
            lock.unlock();
        }
    }

    public Script getPayoutOutput(NetworkParameters params, boolean alice) {
        Address address = getKeys(alice).get(2).toAddress(params);
        return ScriptBuilder.createOutputScript(address);
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
            x = (byte[]) in.readObject();
            xHash = (byte[]) in.readObject();
            bailinHashes = (Sha256Hash[]) in.readObject();
            payoutHashes = (Sha256Hash[]) in.readObject();
            refundHashes = (Sha256Hash[]) in.readObject();
            bailinTxs = (Transaction[]) in.readObject();
            payoutSigs = (byte[][][]) in.readObject();
            refundSigs = (byte[][][]) in.readObject();
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

            listeners = new HashMap<StateListener, Executor>();

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

