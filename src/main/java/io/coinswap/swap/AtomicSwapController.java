package io.coinswap.swap;

import com.google.common.collect.ImmutableList;
import io.coinswap.client.Currency;
import io.coinswap.net.Connection;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOP;

public abstract class AtomicSwapController {
    public static final int VERSION = 0;

    protected final AtomicSwap swap;
    protected Currency[] currencies;

    protected ScheduledThreadPoolExecutor refundScheduler;

    private static final Script OP_NOP_SCRIPT = new ScriptBuilder().op(OP_NOP).build();

    protected final ReentrantLock lock = Threading.lock(AtomicSwapController.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwapController.class);

    protected AtomicSwapController(AtomicSwap swap, Currency[] currencies) {
        this.swap = checkNotNull(swap);
        checkNotNull(swap.trade);

        this.currencies = checkNotNull(currencies);
        checkState(currencies.length == 2);
        checkNotNull(currencies[0]);
        checkNotNull(currencies[1]);
        checkState(swap.trade.quantities[0].isGreaterThan(currencies[0].getParams().getMinFee()));
        checkState(swap.trade.quantities[1].isGreaterThan(currencies[1].getParams().getMinFee()));

        this.swap.switched = !currencies[1].supportsHashlock();

        refundScheduler = new ScheduledThreadPoolExecutor(1);
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
                checkState(keyStrings.size() >= 3);

                List<ECKey> keys = new ArrayList<ECKey>(3);
                for(String s : keyStrings)
                    keys.add(ECKey.fromPublicOnly(Base64.getDecoder().decode(checkNotNull(s))));
                swap.setKeys(fromAlice, keys);

                if (!fromAlice) {
                    byte[] xHash = Base64.getDecoder().decode((String) checkNotNull(data.get("x")));
                    swap.setXHash(xHash);
                }

            } else if (method.equals(AtomicSwapMethod.BAILIN_HASH_REQUEST)) {
                checkState(swap.getStep() == AtomicSwap.Step.EXCHANGING_BAILIN_HASHES);
                checkState(swap.getBailinHash(fromAlice) == null);

                byte[] hashBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("hash")));
                Sha256Hash hash = new Sha256Hash(hashBytes);
                swap.setBailinHash(fromAlice, hash);

            } else if (method.equals(AtomicSwapMethod.EXCHANGE_SIGNATURES)) {
                checkState(swap.getStep() == AtomicSwap.Step.EXCHANGING_SIGNATURES);

                // deserialize signatures in message
                List<String> ownPayoutSigStrings = (List<String>) checkNotNull(data.get("myPayout"));
                byte[]
                    payoutSigBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("payout"))),
                    refundSigBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("refund"))),
                    ownPayoutSigBytes[] = new byte[][] {
                            Base64.getDecoder().decode(checkNotNull(ownPayoutSigStrings.get(0))),
                            Base64.getDecoder().decode(checkNotNull(ownPayoutSigStrings.get(1)))},
                    ownRefundSigBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("myRefund")));
                ECKey.ECDSASignature
                    payoutSig = ECKey.ECDSASignature.decodeFromDER(payoutSigBytes),
                    refundSig = ECKey.ECDSASignature.decodeFromDER(refundSigBytes),
                    ownPayoutSigs[] = new ECKey.ECDSASignature[] {
                            ECKey.ECDSASignature.decodeFromDER(ownPayoutSigBytes[0]),
                            ECKey.ECDSASignature.decodeFromDER(ownPayoutSigBytes[1])},
                    ownRefundSig = ECKey.ECDSASignature.decodeFromDER(ownRefundSigBytes);

                // verify signature for counterparty's payout
                Script redeem = swap.getMultisigRedeem();
                Transaction payoutTx = createPayout(!fromAlice);
                Sha256Hash payoutSigHash = payoutTx.hashForSignature(0, redeem, Transaction.SigHash.ALL, false);
                checkState(swap.getKeys(fromAlice).get(0).verify(payoutSigHash, payoutSig));
                log.info("Verified payout signature");

                // verify signature for counterparty's refund
                Transaction refundTx = createRefund(!fromAlice, true);
                Sha256Hash refundSigHash = refundTx.hashForSignature(0, redeem, Transaction.SigHash.ALL, false);
                checkState(swap.getKeys(fromAlice).get(0).verify(refundSigHash, refundSig));
                log.info("Verified refund signature");

                // save signatures in AtomicSwap state
                swap.setPayoutSig(fromAlice, fromAlice ? 0 : 1, ownPayoutSigs[0]);
                swap.setPayoutSig(fromAlice, 2, ownPayoutSigs[1]);
                swap.setRefundSig(fromAlice, fromAlice ? 0 : 1, ownRefundSig);
                swap.setPayoutSig(!fromAlice, fromAlice ? 0 : 1, payoutSig);
                swap.setRefundSig(!fromAlice, fromAlice ? 0 : 1, refundSig);
            }
        } finally {
            lock.unlock();
        }
    }

    // TODO: make this a method of AtomicSwap
    protected Transaction createPayout(boolean alice) {
        int i = alice ^ swap.switched ? 1 : 0;
        NetworkParameters params = currencies[i].getParams();

        Transaction tx = new Transaction(params);
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);

        // create inputs
        tx.addInput(swap.getBailinHash(!alice), 0, OP_NOP_SCRIPT);
        tx.addInput(swap.getBailinHash(!alice), 1, OP_NOP_SCRIPT);

        // create output
        tx.addOutput(swap.trade.quantities[i], swap.getPayoutOutput(params, alice));

        // if we have both signatures, we can create the scriptsig to spend the bailin multisig output
        if(swap.getPayoutSig(alice, 0) != null && swap.getPayoutSig(alice, 1) != null) {
            List<TransactionSignature> sigs = ImmutableList.of(
                    swap.getPayoutSig(alice, 0),
                    swap.getPayoutSig(alice, 1));
            Script multisigScriptSig = ScriptBuilder.createP2SHMultiSigInputScript(sigs, swap.getMultisigRedeem());
            tx.getInput(0).setScriptSig(multisigScriptSig);
        }

        // if we know X and the hashlock signature, we can create the hashlock scriptsig
        if(swap.getX() != null && swap.getPayoutSig(alice, 2) != null) {
            Script hashlockScriptSig;
            if (alice) {
                // now that we've seen X (revealed when Bob spent his payout), we can provide X
                hashlockScriptSig = new ScriptBuilder()
                        .data(swap.getPayoutSig(alice, 2).encodeToBitcoin())
                        .data(swap.getX())
                        .data(swap.getHashlockScript(!alice).getProgram())
                        .build();

            } else {
                // sign using 4th key and provide p2sh script, revealing X to alice
                hashlockScriptSig = new ScriptBuilder()
                        .data(swap.getPayoutSig(alice, 2).encodeToBitcoin())
                        .data(swap.getX())
                        .build();
            }
            tx.getInput(1).setScriptSig(hashlockScriptSig);
        }

        return tx;
    }

    // TODO: make this a method of AtomicSwap
    protected Transaction createRefund(boolean alice, boolean timelocked) {
        int i = alice ^ swap.switched ? 0 : 1;
        NetworkParameters params = currencies[i].getParams();

        Transaction tx = new Transaction(currencies[i].getParams());
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);
        tx.addInput(swap.getBailinHash(alice), 0, OP_NOP_SCRIPT);

        Address address = swap.getKeys(alice).get(1).toAddress(params);
        Script output = ScriptBuilder.createOutputScript(address);
        Coin fee = currencies[i].getParams().getMinFee();
        tx.addOutput(swap.trade.quantities[i].subtract(fee), output);

        if(timelocked) {
            // TODO: use CHECKLOCKTIMEVERIFY in bailin instead of refund TX timelock (when available)
            tx.setLockTime(swap.getLocktime(alice));
            tx.getInput(0).setSequenceNumber(0);
        }

        // if we have both signatures, we can create the scriptsig to spend the bailin multisig output
        if(swap.getRefundSig(alice, 0) != null && swap.getRefundSig(alice, 1) != null) {
            List<TransactionSignature> sigs = ImmutableList.of(
                    swap.getRefundSig(alice, 0),
                    swap.getRefundSig(alice, 1));
            Script multisigScriptSig = ScriptBuilder.createP2SHMultiSigInputScript(sigs, swap.getMultisigRedeem());
            tx.getInput(0).setScriptSig(multisigScriptSig);
        }

        return tx;
    }

    protected void broadcastRefund(boolean alice) {
        checkState(swap.getTimeUntilRefund(alice) <= 0);

        Transaction refund = createRefund(alice, true);
        swap.setRefundHash(alice, refund.getHash());
        log.info("Broadcasting refund");
        log.info(refund.toString());
        currencies[alice ? 0 : 1].getWallet().peerGroup().broadcastTransaction(refund);
        // TODO: make sure refund got accepted

        swap.setStep(AtomicSwap.Step.CANCELED);
    }

    protected void waitForRefundTimelock(boolean alice) {
        long secondsLeft = swap.getTimeUntilRefund(alice);
        log.info("Refund TX will unlock in ~" + (secondsLeft / 60) + " minutes");

        refundScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                broadcastRefund(alice);
            }
        }, secondsLeft, TimeUnit.SECONDS);
    }

    protected void finish() {
        refundScheduler.shutdownNow();
        log.info("Swap " + swap.id + " finished.");
    }

    public boolean settingUp() {
        return swap.isSettingUp();
    }
}
