(function(coinswap) {

coinswap.TradesView = Backbone.View.extend({
  template: $('#template-trades').html(),
  rowTemplate: _.template($('#template-trades-row').html()),
  className: 'trades',

  initialize: function() {
    _.bindAll(this, 'render');
    coinswap.trade.on('swap', this.render);
    this.render();
  },

  render: function() {
    var t = this;
    t.$el.html(t.template);

    var tbody = t.$el.find('tbody');
    var trades = coinswap.trade.swaps();
    _.each(trades, function(trade) {
      var el = $(t.rowTemplate(trade));
      tbody.prepend(el);
    });

    this.$el.find('[data-toggle="tooltip"]').tooltip({
      animation: false,
      container: this.$el,
      placement: 'bottom'
    });
  }
});

})(coinswap);
