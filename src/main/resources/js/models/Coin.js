(function(coinswap) {

coinswap.Coin = Backbone.Model.extend({
  defaults: {
    balance: 0,
    connected: false,
    synced: false
  },

  initialize: function() {
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
  }
});

})(coinswap);
