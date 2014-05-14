(function(coinswap) {

coinswap.Coin = Backbone.Model.extend({
  defaults: {
    balance: 0,
    initialized: false,
    syncing: false
  },

  initialize: function() {
    this.on('sync:start', function(blocks) {
      this.set({
        syncBlocks: blocks,
        syncing: true
      });
    });

    this.on('sync:done', function() {
      this.set({
        syncing: false,
        initialized: true
      });
    });
  }
});

})(coinswap);
