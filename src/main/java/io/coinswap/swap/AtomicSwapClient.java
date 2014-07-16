package io.coinswap.swap;

import com.google.bitcoin.core.*;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.script.ScriptOpCodes;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.KeyChain;
import io.coinswap.net.Connection;
import net.jcip.annotations.GuardedBy;
import net.minidev.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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

    private final io.coinswap.client.Coin[] coins;
    private final Connection connection;

    private final int a, b;

    public AtomicSwapClient(AtomicSwap swap, Connection connection, boolean alice, io.coinswap.client.Coin[] coins) {
        super(swap);

        this.connection = checkNotNull(connection);
        connection.onMessage(swap.id, this);

        this.alice = alice;
        a = alice ? 0 : 1;
        b = a ^ 1;

        this.coins = checkNotNull(coins);
        checkState(coins.length == 2);
        checkNotNull(coins[0]);
        checkNotNull(coins[1]);
    }

    public void start() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.id);
        message.put("method", AtomicSwapMethod.VERSION);
        message.put("version", VERSION);
        connection.write(message);
    }

    private void sendKeys() {
        JSONObject message = new JSONObject();
        message.put("channel", swap.id);
        message.put("method", AtomicSwapMethod.KEYS_REQUEST);

        myKeys = (List<ECKey>)(List<?>)
            coins[b].getWallet().wallet().freshKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, 3);
        swap.setKeys(alice, myKeys);
        List<String> keyStrings = new ArrayList<String>(3);
        for(ECKey key : myKeys)
            keyStrings.add(Base58.encode(key.getPubKey()));
        message.put("keys", keyStrings);

        if (!alice) {
            swap.setXKey(coins[0].getWallet().wallet().freshReceiveKey());
            message.put("x", Base58.encode(swap.getXHash()));
        }

        connection.write(message);
    }

    private void sendBailinHash() {
        try {
            JSONObject message = new JSONObject();
            message.put("channel", swap.id);
            message.put("method", AtomicSwapMethod.BAILIN_HASH_REQUEST);

            Transaction tx = new Transaction(coins[0].getParams());

            // first output is p2sh 2-of-2 multisig, with keys A1 and B1
            // amount is how much we are trading to other party
            List<ECKey> multiSigKeys = new ArrayList<ECKey>(2);
            multiSigKeys.add(swap.getKeys(alice).get(0));
            multiSigKeys.add(swap.getKeys(!alice).get(0));
            Script redeem = ScriptBuilder.createMultiSigOutputScript(2, multiSigKeys);
            byte[] redeemHash = Utils.Hash160(redeem.getProgram());
            Script p2sh = ScriptBuilder.createP2SHOutputScript(redeemHash);
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
                Script xRedeem = new ScriptBuilder()
                        .op(ScriptOpCodes.OP_HASH160)
                        .data(swap.getXHash())
                        .op(ScriptOpCodes.OP_EQUALVERIFY)
                        .data(swap.getKeys(true).get(0).getPubKey())
                        .op(ScriptOpCodes.OP_CHECKSIG)
                        .build();
                byte[] scriptHash = Utils.Hash160(xRedeem.getProgram());
                xScript = ScriptBuilder.createP2SHOutputScript(scriptHash);
            }
            // TODO: get actual fee amount
            tx.addOutput(Coin.valueOf(10000), xScript);

            Wallet.SendRequest req = Wallet.SendRequest.forTx(tx);
            req.changeAddress = coins[a].getWallet().wallet().getChangeAddress();
            req.shuffleOutputs = false;
            // TODO: get actual fee amount
            req.feePerKb = Coin.valueOf(10000);
            coins[a].getWallet().wallet().completeTx(req);

            swap.setBailinTx(alice, tx);

            log.info(tx.toString());

            message.put("hash", Base58.encode(tx.getHash().getBytes()));
            connection.write(message);
        } catch(InsufficientMoneyException ex) {
            log.error(ex.getMessage());
        }
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

            }
        } catch(Exception ex) {
            log.error(ex.getMessage());
        }
    }
}
