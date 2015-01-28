(function(coinswap) {

coinswap.TickerListView = Backbone.View.extend({
  initialize: function() {
    this.listenTo(this.model, 'initialized', this.render);

    var coins = this.model.get('coins');
    this.listenTo(coins, 'add', this.render);
  },

  render: function() {
    var t = this;

    if(!t.model.get('initialized')) return;
    console.log('rendering ticker list');
    t.$el.empty();

    var coins = t.model.get('coins');
    coins.each(function(coin) {
      _.each(coin.get('pairs'), function(id) {
        var coin2 = coins.get(id);
        if(!coin2) return;

        if(coin.get('index') > coin2.get('index')) return;
        t.addTicker([coin2.id, coin.id]);
      });
    });
  },

  addTicker: function(pair) {
    var el = $('<div class="ticker">');
    this.$el.append(el);

    var tickerView = new coinswap.TickerView({
      el: el,
      model: new coinswap.Ticker({ pair: pair })
    });
  }
});

})(coinswap);
