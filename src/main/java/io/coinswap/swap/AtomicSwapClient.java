package io.coinswap.swap;

import io.coinswap.client.Currency;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.KeyChain;
import io.coinswap.net.Connection;
import net.minidev.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOP;

/*
 * A client for performing cross-chain atomic transfers.
 * Loosely based on the Atomic Cross Chain Transfers BIP draft by Noel Tiernan.
 * (https://github.com/TierNolan/bips/blob/bip4x/bip-atom.mediawiki)
 */
public class AtomicSwapClient extends AtomicSwapController implements Connection.ReceiveListener {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AtomicSwapClient.class);

    private List<ECKey> myKeys;

    // alice = trader on blockchain that accepts hashlocked transactions
    // bob = other trader, generates x for hashlock
    private final boolean alice;

    private final Connection connection;

    private final int a, b;

    public AtomicSwapClient(AtomicSwap swap, Connection connection, boolean alice, Currency[] currencies) {
        super(swap, currencies);

        this.connection = checkNotNull(connection);
        connection.onMessage(swap.getChannelId(alice), this);

        this.alice = alice;
        a = alice ? 0 : 1;
        b = a ^ 1;
    }

    public void start() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(alice));
        message.put("method", AtomicSwapMethod.VERSION);
        message.put("version", VERSION);
        connection.write(message);
    }

    private void sendKeys() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(alice));
        message.put("method", AtomicSwapMethod.KEYS_REQUEST);

        myKeys = new ArrayList<ECKey>(3);
        myKeys.add(currencies[b].getWallet().wallet().freshReceiveKey());
        myKeys.add(currencies[a].getWallet().wallet().freshReceiveKey());
        myKeys.add(currencies[b].getWallet().wallet().freshReceiveKey());
        if(!alice) myKeys.add(currencies[b].getWallet().wallet().freshReceiveKey());
        swap.setKeys(alice, myKeys);
        List<String> keyStrings = new ArrayList<String>(3);
        for(ECKey key : myKeys)
            keyStrings.add(Base58.encode(key.getPubKey()));
        message.put("keys", keyStrings);

        if (!alice) {
            swap.setXKey(currencies[0].getWallet().wallet().freshReceiveKey());
            message.put("x", Base58.encode(swap.getXHash()));
        }

        connection.write(message);
    }

    private void sendBailinHash() {
        try {
            JSONObject message = new JSONObject();
            message.put("channel", swap.getChannelId(alice));
            message.put("method", AtomicSwapMethod.BAILIN_HASH_REQUEST);

            Transaction tx = new Transaction(currencies[0].getParams());

            // first output is p2sh 2-of-2 multisig, with keys A1 and B1
            // amount is how much we are trading to other party
            Script p2sh = ScriptBuilder.createP2SHOutputScript(getMultisigRedeem());
            tx.addOutput(swap.trade.quantities[a], p2sh);

            // second output for Alice's bailin is p2sh pay-to-pubkey with key B1
            // for Bob's bailin is p2sh, hashlocked with x, pay-to-pubkey with key A1
            // amount is the fee for the payout tx
            // this output is used to lock the payout tx until x is revealed,
            //   but isn't required for the refund tx
            Script xScript;
            if (alice) {
                xScript = ScriptBuilder.createP2SHOutputScript(swap.getXHash());
            } else {
                Script hashlockRedeem = getHashlockRedeem();
                xScript = ScriptBuilder.createP2SHOutputScript(hashlockRedeem);
            }
            Coin fee = currencies[a].getParams().getMinFee();
            tx.addOutput(fee, xScript);

            Wallet.SendRequest req = Wallet.SendRequest.forTx(tx);
            req.changeAddress = currencies[a].getWallet().wallet().getChangeAddress();
            req.shuffleOutputs = false;
            req.feePerKb = fee;
            currencies[a].getWallet().wallet().completeTx(req);

            swap.setBailinTx(alice, tx);

            message.put("hash", Base58.encode(tx.getHash().getBytes()));
            connection.write(message);
        } catch(InsufficientMoneyException ex) {
            log.error(ex.getMessage());
        }
    }

    private void sendSignatures() {
        Transaction payout = createPayout(!alice),
                    refund = createRefund(!alice);

        Script multisigRedeem = getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash sigHashPayout = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false),
                   sigHashRefund = refund.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature sigPayout = key.sign(sigHashPayout),
                             sigRefund = key.sign(sigHashRefund);

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(alice));
        message.put("method", AtomicSwapMethod.EXCHANGE_SIGNATURES);
        message.put("payout", Base58.encode(sigPayout.encodeToDER()));
        message.put("refund", Base58.encode(sigRefund.encodeToDER()));
        connection.write(message);
    }

    private void sendBailin() {
        Transaction bailin = swap.getBailinTx(alice);

        JSONObject message = new JSONObject();
        message.put("channel", swap.getChannelId(alice));
        message.put("method", AtomicSwapMethod.EXCHANGE_BAILIN);
        // TODO: serialize bailin
    }

    private void broadcastPayout() {
        Transaction payout = createPayout(alice);

        // TODO: create this signature earlier so we don't have to decrypt keys again
        Script multisigRedeem = getMultisigRedeem();
        ECKey key = swap.getKeys(alice).get(0);
        Sha256Hash multisigSighash = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature mySig = key.sign(multisigSighash),
                otherSig = swap.getPayoutSig(!alice);

        List<TransactionSignature> sigs = new ArrayList<>(2);
        sigs.add(new TransactionSignature(alice ? mySig : otherSig, Transaction.SigHash.ALL, false));
        sigs.add(new TransactionSignature(alice ? otherSig : mySig, Transaction.SigHash.ALL, false));
        Script multisigScript = ScriptBuilder.createP2SHMultiSigInputScript(sigs, multisigRedeem);
        payout.addInput(swap.getBailinHash(!alice), 0, multisigScript);

        Sha256Hash hashlockSighash = payout.hashForSignature(0, multisigRedeem, Transaction.SigHash.ALL, false);
        Script hashlockScript;
        if(alice) {
            // now that we've seen X (revealed when Bob spent his payout), we can provide X
            TransactionSignature hashlockSig =
                    new TransactionSignature(myKeys.get(0).sign(hashlockSighash), Transaction.SigHash.ALL, false);
            hashlockScript = new ScriptBuilder()
                    .data(swap.getX())
                    .data(hashlockSig.encodeToBitcoin())
                    .build();

        } else {
            // sign using 4th key and provide p2sh script, revealing X to alice
            TransactionSignature hashlockSig =
                    new TransactionSignature(myKeys.get(3).sign(hashlockSighash), Transaction.SigHash.ALL, false);
            Script hashlockRedeem = getHashlockRedeem();
            hashlockScript = new ScriptBuilder()
                    .data(hashlockSig.encodeToBitcoin())
                    .data(hashlockRedeem.getProgram())
                    .build();
        }
        payout.addInput(swap.getBailinHash(!alice), 1, hashlockScript);

        currencies[b].getWallet().peerGroup().broadcastTransaction(payout);
    }

    private Script getHashlockRedeem() {
        checkState(!alice);
        return new ScriptBuilder()
            .op(ScriptOpCodes.OP_HASH160)
            .data(swap.getXHash())
            .op(ScriptOpCodes.OP_EQUALVERIFY)
            .data(myKeys.get(3).getPubKey())
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
                if(!alice) listenForBailin();
                else listenForPayout();

                swap.setStep(AtomicSwap.Step.EXCHANGING_SIGNATURES);
                sendSignatures();

            } else if (method.equals(AtomicSwapMethod.EXCHANGE_SIGNATURES)) {
                sendBailin();

                // TODO: don't broadcast bailin until we've seen other party's valid bailin
                Transaction bailin = swap.getBailinTx(alice);
                currencies[a].getWallet().peerGroup().broadcastTransaction(bailin);
            }
        } catch(Exception ex) {
            log.error(ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void listenForBailin() {
        checkState(!alice);

        Wallet w = new Wallet(currencies[b].getParams());
        List<Script> scripts = new ArrayList<>(1);
        scripts.add(ScriptBuilder.createP2SHOutputScript(getMultisigRedeem()));
        w.addWatchedScripts(scripts);
        w.addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                checkState(tx.getHash().equals(swap.getBailinHash(!alice)));
                // TODO: support mutated bailin (will have different hash) - not sure how to handle this

                if(tx.getConfidence().getDepthInBlocks() < currencies[b].getConfirmationDepth()) return;

                log.info("received other party's bailin via coin network: " + tx.toString());
                broadcastPayout();
                currencies[b].getWallet().peerGroup().removeWallet(w);
                // TODO: maybe we shouldn't remove the listener right away
            }
        });
        currencies[b].getWallet().peerGroup().addWallet(w);
        // TODO: listen for this script when starting up and we have pending swaps
    }

    public void listenForPayout() {
        checkState(alice);

        Transaction payout = createPayout(false);

        Wallet w = new Wallet(currencies[b].getParams());
        List<Script> scripts = new ArrayList<>(1);
        scripts.add(ScriptBuilder.createP2SHOutputScript(payout.getOutput(0).getScriptPubKey()));
        w.addWatchedScripts(scripts);
        w.addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                log.info("received other party's payout via coin network: " + tx.toString());
                Script xScript = tx.getInput(1).getScriptSig();
                log.info("x redeem: " + xScript.toString());
                ECKey xKey = ECKey.fromPublicOnly(xScript.getChunks().get(3).data);
                swap.setXKey(xKey);

                broadcastPayout();
                currencies[b].getWallet().peerGroup().removeWallet(w);
            }
        });
        currencies[b].getWallet().peerGroup().addWallet(w);
        // TODO: listen for this script when starting up and we have pending swaps
    }
}
