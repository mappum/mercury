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

    protected Transaction createPayout(boolean alice) {
        int i = alice ^ swap.switched ? 1 : 0;
        NetworkParameters params = currencies[i].getParams();

        Transaction tx = new Transaction(params);
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);
        tx.addInput(swap.getBailinHash(!alice), 0, OP_NOP_SCRIPT);
        tx.addInput(swap.getBailinHash(!alice), 1, OP_NOP_SCRIPT);

        Address address = swap.getKeys(alice).get(2).toAddress(params);
        Script output = ScriptBuilder.createOutputScript(address);
        tx.addOutput(swap.trade.quantities[i], output);

        return tx;
    }

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

        return tx;
    }

    public boolean settingUp() {
        return swap.isSettingUp();
    }
}
