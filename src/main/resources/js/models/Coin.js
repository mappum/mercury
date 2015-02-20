(function(coinswap) {

coinswap.Coin = Backbone.Model.extend({
  defaults: {
    balance: '0',
    pending: '0',
    connected: false,
    synced: false,
    address: '',
    pairs: []
  },

  initialize: function() {
    _.bindAll(this, 'updateBalance');

    var transactions = new coinswap.TransactionCollection;
    this.set('transactions', transactions);

    this.on('peers:connected', function(o) {
      this.set({
        connected: this.get('connected') || o.peers >= o.maxPeers,
        maxPeers: o.maxPeers,
        peers: o.peers
      });
    });

    this.on('sync:start', function(blocks) {
      this.set({
        syncBlocks: blocks
      });
    });

    this.on('sync:done', function() {
      this.set({
        synced: true
      });
    });

    this.on('transaction', function(tx) {
      this.updateBalance();
      transactions.add(tx, { merge: true });
    });

    this.on('initialized', function() {
      this.updateBalance();
    });

    this.on('changed', function() {
      this.updateBalance();
    });
  },

  updateBalance: function() {
    if(!this.controller) return;
    this.set({
      balance: this.controller.balance(),
      pending: this.controller.pendingBalance(),
    });
  },

  newAddress: function(cb) {
    var address = this.controller.newAddress();
    this.set('address', address);
    return address;
  },

  isAddressValid: function(address) {
    return this.controller.isAddressValid(address);
  },

  send: function(address, amount) {
    return this.controller.send(address, amount + '');
  }
});

coinswap.CoinCollection = Backbone.Collection.extend({
  model: coinswap.Coin,
  comparator: 'index'
});

})(coinswap);
