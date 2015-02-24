package io.coinswap.swap;

import com.google.common.collect.ImmutableList;
import io.coinswap.client.Currency;
import io.mappum.altcoinj.core.*;
import io.mappum.altcoinj.crypto.TransactionSignature;
import io.mappum.altcoinj.script.Script;
import io.mappum.altcoinj.script.ScriptBuilder;
import io.coinswap.net.Connection;
import net.minidev.json.JSONObject;
import io.mappum.altcoinj.utils.Threading;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/*
 * A client for performing cross-chain atomic transfers.
 * Loosely based on the Atomic Cross Chain Transfers BIP draft by Noel Tiernan.
 * (https://github.com/TierNolan/bips/blob/bip4x/bip-atom.mediawiki)
 */
public class AtomicSwapClient extends AtomicSwapController implements Connection.ReceiveListener {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwapClient.class);

    private final boolean alice;
    private final int a, b;
    private final Connection connection;

    public AtomicSwapClient(AtomicSwap swap, Connection connection, Currency[] currencies) {
        super(swap, currencies);
        checkState(!swap.isDone());

        // we are alice if we are buying and the second currency supports hashlock
        // TXs, or if we are selling and the second currency doesn't support them.
        // otherwise, we are bob
        alice = !swap.trade.buy ^ swap.switched;
        a = swap.trade.buy ? 1 : 0;
        b = a ^ 1;

        this.connection = checkNotNull(connection);
        connection.onMessage(swap.getChannelId(!swap.trade.buy), this);
    }

    public void start() {
        if(!swap.isStarted()) {
            JSONObject message = new JSONObject();
            message.put("channel", swap.getChannelId(!swap.trade.buy));
            message.put("method", AtomicSwapMethod.VERSION);
            message.put("version", VERSION);
            connection.write(message);
        } else {
            resume();
        }
    }

    // Resumes a swap (starts up the client from a saved AtomicSwap (e.g. if the
    public void resume() {
        checkState(swap.isStarted());

        // if we had already generated keys, reload the private keys
        if(swap.getKeys(alice) != null) {
            swap.setKeys(alice, getPrivateKeys());
        }

        if (swap.getTimeUntilRefund(alice) <= 0) {
            // if the timelock on our refund is passed, claim it
            broadcastRefund(alice);

        } else {
            // set up to broadcast refund once it's unlocked
            waitForRefundTimelock(alice);

            // TODO: make sure it's not possible to miss the transactions (syncing through the block that contained
            // it before we register this listener)
            if (swap.getStep() == AtomicSwap.Step.WAITING_FOR_BAILIN) {
                listenForBailin();
            } else if (swap.getStep() == AtomicSwap.Step.WAITING_FOR_PAYOUT) {
                listenForPayout();
            }

            // if we are resuming a swap that wasn't done setting up, just cancel it
            if (swap.isSettingUp()) {
                cancel();
            }
        }
    }

    private void sendKeys() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.KEYS_REQUEST);

        List<ECKey> myKeys = new ArrayList<ECKey>(4);
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
        Script p2sh = ScriptBuilder.createP2SHOutputScript(swap.getMultisigRedeem());
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
            xScript = ScriptBuilder.createP2SHOutputScript(swap.getHashlockScript(false));
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
        // create our signatures for counterparty's payout and refund
        Transaction payout = createPayout(!alice),
                    refund = createRefund(!alice, true);
        Script multisigRedeem = swap.getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash sigHashPayout = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false),
                   sigHashRefund = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature sigPayout = key.sign(sigHashPayout),
                             sigRefund = key.sign(sigHashRefund);
        swap.setPayoutSig(!alice, alice ? 0 : 1, sigPayout);
        swap.setRefundSig(!alice, alice ? 0 : 1, sigRefund);

        // create our signatures for our own payout and refund
        TransactionSignature[] myPayout = createPayoutSignatures();
        swap.setPayoutSig(alice, alice ? 0 : 1, myPayout[0]);
        swap.setPayoutSig(alice, 2, myPayout[1]); // sig for hashlock output
        TransactionSignature myRefund = createRefundSignature(alice);
        swap.setRefundSig(alice, alice ? 0 : 1, myRefund);

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.EXCHANGE_SIGNATURES);
        message.put("payout", Base64.getEncoder().encodeToString(sigPayout.encodeToDER()));
        message.put("refund", Base64.getEncoder().encodeToString(sigRefund.encodeToDER()));
        message.put("myPayout", ImmutableList.of(
                Base64.getEncoder().encodeToString(myPayout[0].encodeToDER()),
                Base64.getEncoder().encodeToString(myPayout[1].encodeToDER())));
        message.put("myRefund", Base64.getEncoder().encodeToString(myRefund.encodeToDER()));
        connection.write(message);
    }

    private ECKey.ECDSASignature getCourtesyRefundSignature() {
        Transaction refund = createRefund(!alice, false);

        Script multisigRedeem = swap.getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash sigHash = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        return key.sign(sigHash);
    }

    private void broadcastBailin() {
        log.info("Broadcasting bailin");
        Transaction bailin = swap.getBailinTx(alice);
        currencies[a].broadcastTransaction(bailin);

        waitForRefundTimelock(alice);
    }

    private void broadcastPayout() {
        Transaction payout = createPayout(alice);
        swap.setPayoutHash(alice, payout.getHash());

        log.info("Broadcasting payout");
        log.info(payout.toString());
        payout.verify();
        currencies[b].broadcastTransaction(payout);
        // TODO: make sure payout got accepted
    }

    private TransactionSignature[] createPayoutSignatures() {
        TransactionSignature[] signatures = new TransactionSignature[2];
        Transaction payout = createPayout(alice);
        List<ECKey> myKeys = swap.getKeys(alice);

        // sign the first output of the bailin (2-of-2 multisig, also requires a signature from counterparty)
        Script multisigRedeem = swap.getMultisigRedeem();
        Sha256Hash multisigSighash = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        signatures[0] = new TransactionSignature(myKeys.get(0).sign(multisigSighash).toCanonicalised(),
                Transaction.SigHash.ALL, false);

        // sign the second output of the bailin (for Bob, it's a p2sh pay-to-pubkey,
        // for Alice it requires the hash preimage of X and our pubkey sig)
        Script hashlockRedeem = swap.getHashlockScript(!alice);
        Sha256Hash hashlockSighash = payout.hashForSignature(1, hashlockRedeem, Transaction.SigHash.ALL, false);
        signatures[1] = new TransactionSignature(myKeys.get(alice ? 0 : 3).sign(hashlockSighash).toCanonicalised(),
                Transaction.SigHash.ALL, false);

        return signatures;
    }

    @Override
    public void onReceive(Map data) {
        try {
            // In the call to onMessage, we read the incoming message and
            // update the state of the AtomicSwap accordingly. If the message
            // is invalid, an exception will be thrown.
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
        // check to see if we have already received the bailin TX
        // TODO: handle mutated transaction, this will only find the TX if the hash is what we expected
        Transaction tx = currencies[b].getWallet().wallet().getTransaction(swap.getBailinHash(!alice));
        if(tx != null) {
            onBailin(tx);
            return;
        }

        // listen for the other party's bailin by adding a script listener for the multisig redeem script
        Wallet w = currencies[b].getWallet().wallet();
        List<Script> scripts = new ArrayList<>(1);
        scripts.add(ScriptBuilder.createP2SHOutputScript(swap.getMultisigRedeem()));
        w.addWatchedScripts(scripts);
        WalletEventListener listener = new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                onBailin(tx);
                w.removeEventListener(this);
            }
        };
        w.addEventListener(listener);
    }

    private void onBailin(Transaction tx) {
        if(!tx.getHash().equals(swap.getBailinHash(!alice))) {
            log.error("Received bailin with different hash, someone mutated the transaction! :(\n" +
                    "Expected: " + swap.getBailinHash(!alice).toString() + "\n" +
                    "Actual: " + tx.getHash().toString());
            // TODO: handle this failure
            return;
        }

        log.info("Received other party's bailin via coin network: " + tx.toString());
        AtomicSwapClient parent = this;
        int confirmDepth = 0;//currencies[b].getConfirmationDepth();
        // NOTE: we currently aren't waiting for confirms, which is dangerous
        // TODO: use deeper confirmations for high-value swaps

        if(tx.getConfidence().getDepthInBlocks() >= confirmDepth) {
            onBailinConfirm(tx);
        } else {
            tx.getConfidence().getDepthFuture(confirmDepth).addListener(
                    new Runnable() {
                        @Override
                        public void run() {
                            parent.onBailinConfirm(tx);
                            // TODO: stop watching script to keep our Bloom filter small (no API to do this yet)
                        }
                    }, Threading.SAME_THREAD
            );
        }
    }

    private void onBailinConfirm(Transaction tx) {
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_PLEDGE);

        if(alice) {
            swap.setStep(AtomicSwap.Step.WAITING_FOR_PAYOUT);
            log.info("Other party's bailin confirmed. Now broadcasting our bailin.");
            listenForPayout();
            broadcastBailin();

        } else {
            log.info("Other party's bailin confirmed. Now committing to swap.");
            broadcastPayout();
            swap.setStep(AtomicSwap.Step.COMPLETE);
            finish();
        }
    }

    private void listenForPayout() {
        checkState(alice);

        // check to see if we have already received the payout TX
        Transaction tx = currencies[a].getWallet().wallet().getTransaction(swap.getPayoutHash(!alice));
        if(tx != null) {
            onPayout(tx);
            return;
        }

        Wallet w = new Wallet(currencies[a].getParams());
        w.addWatchedScripts(ImmutableList.of(swap.getPayoutOutput(currencies[a].getParams(), !alice)));
        w.addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                Script xScript = tx.getInput(1).getScriptSig();
                byte[] x = xScript.getChunks().get(1).data;
                if (!Arrays.equals(Utils.Hash160(x), swap.getXHash())) return;

                onPayout(tx);
                currencies[a].getWallet().peerGroup().removeWallet(w);
            }
        });
        currencies[a].getWallet().peerGroup().addWallet(w);
    }

    private void onPayout(Transaction tx) {
        log.info("Received other party's payout via coin network: " + tx.toString());
        tx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);

        swap.setPayoutHash(!alice, tx.getHash());

        Script xScript = tx.getInput(1).getScriptSig();
        swap.setX(xScript.getChunks().get(1).data);

        broadcastPayout();
        swap.setStep(AtomicSwap.Step.COMPLETE);
        finish();
    }

    private void cancel() {
        log.info("Cancelling swap");
        checkState(!swap.isDone());

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(!swap.trade.buy));
        message.put("method", AtomicSwapMethod.CANCEL_TRANSACTION);

        if(swap.isSettingUp()) {
            log.info("Still in setup stage, no refund necessary");
            swap.setStep(AtomicSwap.Step.CANCELED);
            finish();

        } else {
            log.info("Our bailin has been broadcasted, now waiting for refund");
            swap.setStep(AtomicSwap.Step.WAITING_FOR_REFUND);
        }

        connection.write(message);
    }

    private TransactionSignature createRefundSignature(boolean alice) {
        Transaction refund = createRefund(alice, true);

        Script multisigRedeem = swap.getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash multisigSighash = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        return new TransactionSignature(key.sign(multisigSighash).toCanonicalised(), Transaction.SigHash.ALL, false);
    }

    private List<ECKey> getPrivateKeys() {
        List<ECKey> myKeys = swap.getKeys(alice),
            privateKeys = new ArrayList<>(myKeys.size());
        Wallet walletA = currencies[a].getWallet().wallet(),
            walletB = currencies[b].getWallet().wallet();
        privateKeys.add(walletB.findKeyFromPubKey(myKeys.get(0).getPubKey()));
        privateKeys.add(walletA.findKeyFromPubKey(myKeys.get(1).getPubKey()));
        privateKeys.add(walletB.findKeyFromPubKey(myKeys.get(2).getPubKey()));
        if(myKeys.size() == 4) privateKeys.add(walletB.findKeyFromPubKey(myKeys.get(3).getPubKey()));
        return privateKeys;
    }

    @Override
    protected void finish() {
        super.finish();
        connection.removeMessageListener(swap.getChannelId(!swap.trade.buy), this);
    }
}
