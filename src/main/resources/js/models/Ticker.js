(function(coinswap) {

coinswap.Ticker = Backbone.Model.extend({
  defaults: {
    pair: ['LTC', 'BTC'],
    bestBid: 0,
    bestAsk: 0,
    last: 0.000146,
    change: 2.43,
    volume: 0,
    history: [0,0,0,5,3.5,3.6,3.8,0,1,2,6,10,9,5,3,2]
  },

  initialize: function(attributes, options) {
    _.bindAll(this, 'update');
    coinswap.trade.on('ticker:' + this.pairId(), this.update);
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
