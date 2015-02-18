(function(coinswap) {

coinswap.TradesView = Backbone.View.extend({
  template: $('#template-trades').html(),
  rowTemplate: _.template($('#template-trades-row').html()),
  className: 'trades',

  initialize: function() {
    _.bindAll(this, 'render');
    console.log('initializing TradesView')
    this.render();
  },

  render: function() {
    var t = this;
    t.$el.html(t.template);

    var tbody = t.$el.find('tbody');
    var trades = coinswap.trade.swaps();
    console.log(trades[0]+'')
    _.each(trades, function(trade) {
      var el = $(t.rowTemplate(trade));
      tbody.prepend(el);
    });
  }
});

})(coinswap);
