(function(coinswap) {

coinswap.Ticker = Backbone.Model.extend({
  defaults: {
    pair: ['', ''],
    bestBid: 0,
    bestAsk: 0,
    last: 0,
    change: 0,
    volume: 0,
    history: [[0,0], [0,0]]
  },

  initialize: function(attributes, options) {
    _.bindAll(this, 'update');
    coinswap.trade.on('ticker:' + this.pairId().toLowerCase(), this.update);
    this.update();
  },

  update: function() {
    console.log('updating ticker: ' + this.pairId());
    var pair = this.get('pair');
    var data = coinswap.trade.ticker(pair[0], pair[1]);
    this.set(data);
  },

  pairId: function() {
    return this.get('pair').join('/');
  }
});

})(coinswap);
