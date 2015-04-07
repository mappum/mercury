(function(coinswap) {

var LIST_MAX_ITEMS = 20;

coinswap.FeedView = Backbone.View.extend({
  template: $('#template-feed').html(),
  rowTemplate: _.template($('#template-feed-row').html()),

  initialize: function() {
    _.bindAll(this, 'addRow');
    this.render();

    var coins = this.model.get('coins');
    var t = this;

    coinswap.trade.on('feed', function(data) {
      data.orders.sort(function(a, b) { return a.time - b.time; });

      _.each(data.orders, function(order) {
        var pair = [ coins.get(order.currencies[0].toUpperCase()), coins.get(order.currencies[1].toUpperCase()) ];
        order.symbols = [ pair[0].get('symbol'), pair[1].get('symbol') ];
        t.addRow(order);
      });
    });
  },

  render: function() {
    console.log('rendering feed view');
    this.$el.html(this.template);
  },

  addRow: function(data) {
    var row = $(this.rowTemplate(data));
    this.$el.find('.list').prepend(row);
    while(this.$el.find('.list li').length > LIST_MAX_ITEMS) {
      var children = this.$el.find('.list').children();
      children.eq(-1).remove();
    }

    row.find('[data-toggle="tooltip"]').tooltip({
      animation: false,
      container: this.$el
    });
  }
});

})(coinswap);
