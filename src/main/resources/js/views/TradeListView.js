(function(coinswap) {

coinswap.TradeListView = Backbone.View.extend({
  template: _.template($('#template-trade-list').html()),
  tradeTemplate: _.template($('#template-trade-listitem').html()),

  initialize: function() {
    _.bindAll(this, 'render');
    coinswap.trade.on('swap', this.render);
    this.render();
  },

  render: function() {
    var t = this;

    console.log('rendering trade list');

    var swaps = coinswap.trade.pendingSwaps();
    t.$el.html(t.template({ swaps: swaps }));

    var listEl = t.$el.find('ul.list');
    _.each(swaps, function(swap) {
      var el = $(t.tradeTemplate(swap));
      listEl.append(el);
    });
  }
});

})(coinswap);
