package io.coinswap.client;

import fr.cryptohash.SHA256;
import io.coinswap.swap.AtomicSwap;
import io.mappum.altcoinj.core.Sha256Hash;
import io.mappum.altcoinj.core.Transaction;
import io.mappum.altcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public class SwapCollection implements AtomicSwap.StateListener {
    private static final Logger log = LoggerFactory.getLogger(SwapCollection.class);
    protected final ReentrantLock lock = Threading.lock(SwapCollection.class.getName());

    protected Map<StateListener, Executor> listeners;

    protected File file;

    protected List<AtomicSwap> list;
    protected Map<Sha256Hash, AtomicSwap> map;

    public SwapCollection(File file) {
        this.file = file;
        list = new ArrayList<AtomicSwap>();
        map = new HashMap<Sha256Hash, AtomicSwap>();
        listeners = new HashMap<StateListener, Executor>();
    }

    public AtomicSwap get(int i) {
        lock.lock();
        try {
            return list.get(i);
        } finally {
            lock.unlock();
        }
    }

    public AtomicSwap get(Sha256Hash txid) {
        lock.lock();
        try {
            AtomicSwap output = map.get(txid);
            if (output != null) return output;

            // fall back to O(n) search of the list (indexes in map might not have been updated)
            for (AtomicSwap swap : list) {
                if (txid.equals(swap.getBailinHash(true)) ||
                txid.equals(swap.getBailinHash(false)) ||
                txid.equals(swap.getPayoutHash(true)) ||
                txid.equals(swap.getPayoutHash(false)) ||
                txid.equals(swap.getRefundHash(true)) ||
                txid.equals(swap.getRefundHash(false))) {
                    addToMap(swap);
                    return swap;
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    public AtomicSwap get(Transaction tx) {
        lock.lock();
        try {
            AtomicSwap output = map.get(tx.getHash());
            if (output != null) return output;

            // if hash isn't found, look up hash of parent transaction (only works for payouts/refunds)
            if((tx.getInputs().size() != 1 && tx.getInputs().size() != 2) || tx.getOutputs().size() != 1) return null;
            Sha256Hash parentTxid = tx.getInput(0).getOutpoint().getHash();
            return get(parentTxid);
        } finally {
            lock.unlock();
        }
    }

    public List<AtomicSwap> getPending() {
        List<AtomicSwap> output = new ArrayList<AtomicSwap>();
        for(AtomicSwap swap : list) {
            if(swap.getStep() != AtomicSwap.Step.COMPLETE
            && swap.getStep() != AtomicSwap.Step.CANCELED) {
                output.add(swap);
            }
        }
        return output;
    }

    public List<AtomicSwap> getAll() {
        List<AtomicSwap> output = new ArrayList<AtomicSwap>(list.size());
        for(AtomicSwap swap : list) {
            output.add(swap);
        }
        return output;
    }

    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }

    public void add(AtomicSwap swap) {
        lock.lock();
        try {
            list.add(swap);
            addToMap(swap);
            if(swap.getStep().ordinal() < AtomicSwap.Step.COMPLETE.ordinal()) {
                swap.addEventListener(this);
            }
        } finally {
            lock.unlock();
        }
    }

    private void addToMap(AtomicSwap swap) {
        if (swap.getBailinHash(true) != null) map.put(swap.getBailinHash(true), swap);
        if (swap.getBailinHash(false) != null) map.put(swap.getBailinHash(false), swap);
        if (swap.getPayoutHash(true) != null) map.put(swap.getPayoutHash(true), swap);
        if (swap.getPayoutHash(false) != null) map.put(swap.getPayoutHash(false), swap);
        if (swap.getRefundHash(true) != null) map.put(swap.getRefundHash(true), swap);
        if (swap.getRefundHash(false) != null) map.put(swap.getRefundHash(false), swap);
    }

    private File temporaryFile() {
        return new File(file.getAbsolutePath()+".tmp");
    }

    public void save() {
        lock.lock();
        try {
            log.info("Saving swap collection");

            FileOutputStream stream = new FileOutputStream(temporaryFile());
            ObjectOutput out = new ObjectOutputStream(stream);
            out.writeObject(list);
            stream.close();
            out.close();

            boolean renamed = temporaryFile().renameTo(file);
            if(!renamed) {
                file.delete();
                temporaryFile().renameTo(file);
            }

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public static SwapCollection load(File file) {
        try {
            if(!file.exists()) return null;

            SwapCollection output = new SwapCollection(file);

            FileInputStream stream = new FileInputStream(file);
            ObjectInputStream in = new ObjectInputStream(stream);
            output.list = (List<AtomicSwap>) in.readObject();
            stream.close();
            in.close();

            output.map = new HashMap<Sha256Hash, AtomicSwap>();
            for(AtomicSwap swap : output.list) {
                output.addToMap(swap);
            }

            return output;

        } catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void onStepChange(AtomicSwap.Step step, final AtomicSwap swap) {
        addToMap(swap);
        save();

        for(final StateListener listener : listeners.keySet()) {
            listeners.get(listener).execute(new Runnable() {
                @Override
                public void run() {
                    listener.onStateChange(swap);
                }
            });
        }
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

    public interface StateListener {
        public void onStateChange(AtomicSwap swap);
    }
}
