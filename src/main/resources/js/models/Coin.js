(function(coinswap) {

coinswap.Coin = Backbone.Model.extend({
  defaults: {
    balance: 0,
    connected: false,
    synced: false,
    address: ''
  },

  initialize: function() {
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

    this.on('address', function(address) {
      this.set('address', address);
    });

    this.on('transaction', function(tx) {
      transactions.add(tx, { merge: true });
    });
  },

  newAddress: function(cb) {
    this.trigger('address:new');
  }
});

coinswap.CoinCollection = Backbone.Collection.extend({
  model: coinswap.Coin,

  initialize: function() {
    var t = this;
    
    this.index = 0;
    this.on('add', function(model) {
      model.set('index', t.index++);
    });
  }
});

})(coinswap);
