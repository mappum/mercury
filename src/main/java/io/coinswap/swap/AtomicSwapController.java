package io.coinswap.swap;

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
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOP;

public abstract class AtomicSwapController {
    public static final int VERSION = 0;

    protected final AtomicSwap swap;
    protected Currency[] currencies;
    protected boolean switched;

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

        switched = !currencies[1].supportsHashlock();
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

                byte[] payoutSigBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("payout"))),
                        refundSigBytes = Base64.getDecoder().decode((String) checkNotNull(data.get("refund")));
                ECKey.ECDSASignature payoutSig = ECKey.ECDSASignature.decodeFromDER(payoutSigBytes),
                        refundSig = ECKey.ECDSASignature.decodeFromDER(refundSigBytes);

                Script redeem = getMultisigRedeem();

                Transaction payoutTx = createPayout(!fromAlice);
                Sha256Hash payoutSigHash = payoutTx.hashForSignature(0, redeem, Transaction.SigHash.ALL, false);
                checkState(swap.getKeys(fromAlice).get(0).verify(payoutSigHash, payoutSig));
                log.info("Verified payout signature");
                swap.setPayoutSig(fromAlice, payoutSig);

                Transaction refundTx = createRefund(!fromAlice, true);
                Sha256Hash refundSigHash = refundTx.hashForSignature(0, redeem, Transaction.SigHash.ALL, false);
                checkState(swap.getKeys(fromAlice).get(0).verify(refundSigHash, refundSig));
                log.info("Verified refund signature");
                swap.setRefundSig(fromAlice, refundSig);
            }
        } finally {
            lock.unlock();
        }
    }

    protected Transaction createPayout(boolean alice) {
        int i = alice ^ switched ? 1 : 0;
        NetworkParameters params = currencies[i].getParams();

        Transaction tx = new Transaction(params);
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);
        tx.addInput(swap.getBailinHash(!alice), 0, OP_NOP_SCRIPT);
        tx.addInput(swap.getBailinHash(!alice), 1, OP_NOP_SCRIPT);

        Script outRedeem = ScriptBuilder.createOutputScript(swap.getKeys(alice).get(1));
        Script outP2sh = ScriptBuilder.createP2SHOutputScript(outRedeem);
        tx.addOutput(swap.trade.quantities[i], outP2sh);

        return tx;
    }

    protected Transaction createRefund(boolean alice, boolean timelocked) {
        int i = alice ^ switched ? 0 : 1;

        Transaction tx = new Transaction(currencies[i].getParams());
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);
        tx.addInput(swap.getBailinHash(alice), 0, OP_NOP_SCRIPT);

        Script redeem = ScriptBuilder.createOutputScript(swap.getKeys(alice).get(2));
        Script p2sh = ScriptBuilder.createP2SHOutputScript(redeem);
        Coin fee = currencies[i].getParams().getMinFee();
        tx.addOutput(swap.trade.quantities[i].subtract(fee), p2sh);

        if(timelocked) {
            // TODO: use CHECKLOCKTIMEVERIFY in bailin instead of refund TX timelock (when available)
            tx.setLockTime(swap.getLocktime(alice));
            tx.getInput(0).setSequenceNumber(0);
        }

        return tx;
    }

    protected Script getMultisigRedeem() {
        List<ECKey> multiSigKeys = new ArrayList<ECKey>(2);
        multiSigKeys.add(swap.getKeys(true).get(0));
        multiSigKeys.add(swap.getKeys(false).get(0));
        return ScriptBuilder.createMultiSigOutputScript(2, multiSigKeys);
    }

    public boolean settingUp() {
        return swap.getStep().ordinal() <= AtomicSwap.Step.EXCHANGING_SIGNATURES.ordinal();
    }
}
