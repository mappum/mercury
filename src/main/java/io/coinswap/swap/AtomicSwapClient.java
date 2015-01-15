package io.coinswap.swap;

import io.coinswap.client.Currency;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import io.coinswap.net.Connection;
import net.minidev.json.JSONObject;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/*
 * A client for performing cross-chain atomic transfers.
 * Loosely based on the Atomic Cross Chain Transfers BIP draft by Noel Tiernan.
 * (https://github.com/TierNolan/bips/blob/bip4x/bip-atom.mediawiki)
 */
public class AtomicSwapClient extends AtomicSwapController implements Connection.ReceiveListener {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwapClient.class);

    private static int REFUND_BROADCAST_DELAY = 10;

    // alice = trader on blockchain that accepts hashlocked transactions
    // bob = other trader, generates x for hashlock
    private final boolean alice;
    private final int a, b;
    private List<ECKey> myKeys;

    private final Connection connection;

    ScheduledThreadPoolExecutor refundScheduler;

    public AtomicSwapClient(AtomicSwap swap, Connection connection, Currency[] currencies) {
        super(swap, currencies);

        // we are alice if we are buying and the second currency supports hashlock TXs,
        // or if we are selling and the second currency doesn't support them.
        // otherwise, we are bob
        alice = !swap.trade.buy ^ switched;
        a = swap.trade.buy ? 1 : 0;
        b = a ^ 1;

        this.connection = checkNotNull(connection);
        connection.onMessage(swap.getChannelId(!swap.trade.buy), this);

        refundScheduler = new ScheduledThreadPoolExecutor(1);
    }

    public void start() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.VERSION);
        message.put("version", VERSION);
        connection.write(message);
    }

    private void sendKeys() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.KEYS_REQUEST);

        myKeys = new ArrayList<ECKey>(4);
        myKeys.add(currencies[b].getWallet().wallet().freshReceiveKey());
        myKeys.add(currencies[a].getWallet().wallet().freshReceiveKey());
        myKeys.add(currencies[b].getWallet().wallet().freshReceiveKey());
        swap.setKeys(alice, myKeys);
        List<String> keyStrings = new ArrayList<String>(3);
        for(ECKey key : myKeys)
            keyStrings.add(Base64.getEncoder().encodeToString(key.getPubKey()));
        message.put("keys", keyStrings);

        if (!alice) {
            ECKey xKey = currencies[b].getWallet().wallet().freshReceiveKey();
            myKeys.add(xKey);
            swap.setXKey(xKey);
            message.put("x", Base64.getEncoder().encodeToString(swap.getXHash()));
        }

        connection.write(message);
    }

    private void sendBailinHash() throws InsufficientMoneyException {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.BAILIN_HASH_REQUEST);

        Transaction tx = new Transaction(currencies[a].getParams());
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_PLEDGE);

        // first output is p2sh 2-of-2 multisig, with keys A1 and B1
        // amount is how much we are trading to other party
        Script p2sh = ScriptBuilder.createP2SHOutputScript(getMultisigRedeem());
        tx.addOutput(swap.trade.quantities[a], p2sh);

        // second output for Alice's bailin is p2sh pay-to-pubkey with key B1
        // for Bob's bailin is hashlocked with x, pay-to-pubkey with key A1
        // amount is the fee for the payout tx
        // this output is used to lock the payout tx until x is revealed,
        //   but isn't required for the refund tx
        Script xScript;
        if (alice) {
            xScript = ScriptBuilder.createP2SHOutputScript(swap.getXHash());
        } else {
            xScript = getHashlockScript(false);
        }
        Coin fee = currencies[a].getParams().getMinFee();
        tx.addOutput(fee, xScript);

        Wallet.SendRequest req = Wallet.SendRequest.forTx(tx);
        req.changeAddress = currencies[a].getWallet().wallet().getChangeAddress();
        req.shuffleOutputs = false;
        req.feePerKb = fee;
        currencies[a].getWallet().wallet().completeTx(req);

        swap.setBailinTx(alice, tx);

        message.put("hash", Base64.getEncoder().encodeToString(tx.getHash().getBytes()));
        connection.write(message);
    }

    private void sendSignatures() {
        Transaction payout = createPayout(!alice),
                    refund = createRefund(!alice, true);

        Script multisigRedeem = getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash sigHashPayout = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false),
                   sigHashRefund = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature sigPayout = key.sign(sigHashPayout),
                             sigRefund = key.sign(sigHashRefund);

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.EXCHANGE_SIGNATURES);
        message.put("payout", Base64.getEncoder().encodeToString(sigPayout.encodeToDER()));
        message.put("refund", Base64.getEncoder().encodeToString(sigRefund.encodeToDER()));
        connection.write(message);
    }

    private ECKey.ECDSASignature getCourtesyRefundSignature() {
        Transaction refund = createRefund(!alice, false);

        Script multisigRedeem = getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash sigHash = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        return key.sign(sigHash);
    }

    private void broadcastBailin() {
        log.info("Broadcasting bailin");
        Transaction bailin = swap.getBailinTx(alice);
        currencies[a].getWallet().peerGroup().broadcastTransaction(bailin);

        waitForRefundTimelock();
    }

    private void broadcastPayout() {
        Transaction payout = createPayout(alice);

        // TODO: create this signature earlier so we don't have to decrypt keys again
        Script multisigRedeem = getMultisigRedeem();
        ECKey key = myKeys.get(0);
        Sha256Hash multisigSighash = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature mySig = key.sign(multisigSighash),
                otherSig = swap.getPayoutSig(!alice);

        List<TransactionSignature> sigs = new ArrayList<TransactionSignature>(2);
        sigs.add(new TransactionSignature(alice ? mySig : otherSig, Transaction.SigHash.ALL, false));
        sigs.add(new TransactionSignature(alice ? otherSig : mySig, Transaction.SigHash.ALL, false));
        Script multisigScriptSig = ScriptBuilder.createP2SHMultiSigInputScript(sigs, multisigRedeem);
        payout.getInput(0).setScriptSig(multisigScriptSig);

        Script hashlockScriptSig;
        if(alice) {
            // now that we've seen X (revealed when Bob spent his payout), we can provide X
            Script hashlockScript = getHashlockScript(false);
            Sha256Hash hashlockSighash = payout.hashForSignature(1, hashlockScript, Transaction.SigHash.ALL, false);
            TransactionSignature hashlockSig =
                    new TransactionSignature(myKeys.get(0).sign(hashlockSighash).toCanonicalised(), Transaction.SigHash.ALL, false);
            hashlockScriptSig = new ScriptBuilder()
                    .data(hashlockSig.encodeToBitcoin())
                    .data(swap.getX())
                    .build();

        } else {
            // sign using 4th key and provide p2sh script, revealing X to alice
            Script hashlockRedeem = getHashlockScript(true);
            Sha256Hash hashlockSighash = payout.hashForSignature(1, hashlockRedeem, Transaction.SigHash.ALL, false);
            TransactionSignature hashlockSig =
                    new TransactionSignature(myKeys.get(3).sign(hashlockSighash).toCanonicalised(), Transaction.SigHash.ALL, false);
            hashlockScriptSig = new ScriptBuilder()
                    .data(hashlockSig.encodeToBitcoin())
                    .data(swap.getX())
                    .build();
        }
        payout.getInput(1).setScriptSig(hashlockScriptSig);

        log.info("Broadcasting payout");
        log.info(payout.toString());
        payout.verify();
        currencies[b].getWallet().peerGroup().broadcastTransaction(payout);
        // TODO: make sure payout got accepted

        refundScheduler.shutdownNow();

        swap.setStep(AtomicSwap.Step.COMPLETE);
    }

    private Script getHashlockScript(boolean alice) {
        if(alice) return new Script(swap.getX());

        return new ScriptBuilder()
                .op(ScriptOpCodes.OP_HASH160)
                .data(swap.getXHash())
                .op(ScriptOpCodes.OP_EQUALVERIFY)
                .data(swap.getKeys(true).get(0).getPubKey())
                .op(ScriptOpCodes.OP_CHECKSIG)
                .build();

    }

    @Override
    public void onReceive(Map data) {
        try {
            onMessage(!alice, data);

            String method = (String) data.get("method");

            if (method.equals(AtomicSwapMethod.VERSION)) {
                swap.setStep(AtomicSwap.Step.EXCHANGING_KEYS);
                sendKeys();

            } else if (method.equals(AtomicSwapMethod.KEYS_REQUEST)) {
                swap.setStep(AtomicSwap.Step.EXCHANGING_BAILIN_HASHES);
                sendBailinHash();

            } else if (method.equals(AtomicSwapMethod.BAILIN_HASH_REQUEST)) {
                swap.setStep(AtomicSwap.Step.EXCHANGING_SIGNATURES);
                listenForBailin();
                sendSignatures();

            } else if (method.equals(AtomicSwapMethod.EXCHANGE_SIGNATURES)) {
                swap.setStep(AtomicSwap.Step.WAITING_FOR_BAILIN);
                if(!alice) {
                    broadcastBailin();
                }

            } else if (method.equals(AtomicSwapMethod.CANCEL_TRANSACTION)) {
                // If the other party wants to cancel, we will honor it if we aren't yet able to commit
                if(settingUp()) {
                    cancel();
                }
            }
        } catch(Exception ex) {
            log.error(ex.getMessage());
            ex.printStackTrace();

            // If an exception happens and we aren't able to commit yet, cancel the swap.
            // Otherwise, we will just ignore it and keep going.
            if(settingUp()) {
                cancel();
            }
        }
    }

    private void listenForBailin() {
        Wallet w = currencies[b].getWallet().wallet();
        AtomicSwapClient parent = this;

        List<Script> scripts = new ArrayList<>(1);
        scripts.add(ScriptBuilder.createP2SHOutputScript(getMultisigRedeem()));
        w.addWatchedScripts(scripts);

        WalletEventListener listener = new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                if(!tx.getHash().equals(swap.getBailinHash(!alice))) return;
                // TODO: support mutated bailin (will have different hash) - not sure how to handle this

                w.removeEventListener(this);
                log.info("Received other party's bailin via coin network: " + tx.toString());

                // TODO: use deeper confirmations for high-value swaps
                tx.getConfidence().getDepthFuture(currencies[b].getConfirmationDepth()).addListener(
                    new Runnable() {
                        @Override
                        public void run() {
                            parent.onBailin(tx);
                            // TODO: stop watching script to keep our Bloom filter small (no API to do this yet)
                        }
                    }, Threading.SAME_THREAD
                );
            }
        };
        w.addEventListener(listener);
        // TODO: listen for this script when starting up and we have pending swaps
    }

    private void onBailin(Transaction tx) {
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_PLEDGE);

        if(alice) {
            swap.setStep(AtomicSwap.Step.WAITING_FOR_PAYOUT);
            log.info("Other party's bailin confirmed. Now broadcasting our bailin.");
            listenForPayout();
            broadcastBailin();

        } else {
            log.info("Other party's bailin confirmed. Now committing to swap.");
            broadcastPayout();
        }
    }

    private void listenForPayout() {
        checkState(alice);

        Transaction payout = createPayout(false);

        Wallet w = new Wallet(currencies[a].getParams());
        List<Script> scripts = new ArrayList<>(1);
        scripts.add(payout.getOutput(0).getScriptPubKey());
        w.addWatchedScripts(scripts);
        w.addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                log.info("Received other party's payout via coin network: " + tx.toString());
                tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);

                Script xScript = tx.getInput(1).getScriptSig();
                swap.setX(xScript.getChunks().get(1).data);

                broadcastPayout();
                currencies[b].getWallet().peerGroup().removeWallet(w);
            }
        });
        currencies[a].getWallet().peerGroup().addWallet(w);
        // TODO: listen for this script when starting up and we have pending swaps
    }

    private void cancel() {
        log.info("Cancelling swap");
        checkState(swap.getStep().ordinal() < AtomicSwap.Step.COMPLETE.ordinal());

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.CANCEL_TRANSACTION);

        if(swap.getStep().ordinal() <= AtomicSwap.Step.EXCHANGING_SIGNATURES.ordinal()) {
            log.info("Still in setup stage, no refund necessary");
            swap.setStep(AtomicSwap.Step.CANCELED);

        } else {
            if(alice && swap.getStep() == AtomicSwap.Step.WAITING_FOR_BAILIN) {
                log.info("We haven't broadcasted our bailin yet, no refund necessary");
                swap.setStep(AtomicSwap.Step.CANCELED);
            } else {
                log.info("Our bailin has been broadcasted, now waiting for refund");
                swap.setStep(AtomicSwap.Step.WAITING_FOR_REFUND);
            }

            // TODO: figure out when it is safe to give the courtesy refund signature.
            // It is unsafe e.g. if we are Alice and both parties have broadcasted bailins. Bob would then be able to
            // take both his refund and his payout.
            //message.put("refundSig", Base64.getEncoder().encodeToString(getCourtesyRefundSignature().encodeToDER()));
        }

        connection.write(message);
    }

    private void broadcastRefund() {
        Transaction refund = createRefund(alice, true);

        // TODO: create this signature earlier so we don't have to decrypt keys again
        Script multisigRedeem = getMultisigRedeem();
        ECKey key = myKeys.get(0);
        Sha256Hash multisigSighash = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature mySig = key.sign(multisigSighash),
                otherSig = swap.getRefundSig(!alice);

        List<TransactionSignature> sigs = new ArrayList<TransactionSignature>(2);
        sigs.add(new TransactionSignature(alice ? mySig : otherSig, Transaction.SigHash.ALL, false));
        sigs.add(new TransactionSignature(alice ? otherSig : mySig, Transaction.SigHash.ALL, false));
        Script multisigScriptSig = ScriptBuilder.createP2SHMultiSigInputScript(sigs, multisigRedeem);
        refund.getInput(0).setScriptSig(multisigScriptSig);

        log.info("Broadcasting refund");
        log.info(refund.toString());
        refund.verify();
        currencies[a].getWallet().peerGroup().broadcastTransaction(refund);
        // TODO: make sure refund got accepted

        swap.setStep(AtomicSwap.Step.COMPLETE);
    }

    private void waitForRefundTimelock() {
        long secondsLeft = swap.getLocktime(alice) - System.currentTimeMillis() / 1000;
        secondsLeft += REFUND_BROADCAST_DELAY; // wait some extra time to make sure we're over the locktime
        log.info("Refund TX will unlock in ~" + (secondsLeft / 60) + " minutes");

        refundScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                broadcastRefund();
            }
        }, secondsLeft, TimeUnit.SECONDS);
    }

    @Override
    public boolean settingUp() {
        return super.settingUp() || (alice && swap.getStep() == AtomicSwap.Step.WAITING_FOR_BAILIN);
    }
}
