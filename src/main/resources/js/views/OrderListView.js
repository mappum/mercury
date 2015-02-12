(function(coinswap) {

coinswap.OrderListView = Backbone.View.extend({
  events: {
    'click .cancel': 'cancel'
  },
  template: _.template($('#template-order-list').html()),
  orderTemplate: _.template($('#template-order').html()),

  initialize: function() {
    _.bindAll(this, 'render', 'cancel');
    coinswap.trade.on('orders:change', this.render);
    this.render();
  },

  render: function() {
    var t = this;

    console.log('rendering order list');

    var orders = coinswap.trade.orders();
    t.$el.html(t.template({ orders: orders }));

    var listEl = t.$el.find('ul');
    _.each(orders, function(order) {
      order.symbols = [
        t.model.get('coins').get(order.currencies[0]).get('symbol'),
        t.model.get('coins').get(order.currencies[1]).get('symbol')
      ];
      var el = $(t.orderTemplate(order));
      listEl.append(el);
    });

    t.delegateEvents();
  },

  cancel: function(e) {
    var id = $(e.currentTarget).attr('data-id');
    coinswap.trade.cancel(id);
  }
});

})(coinswap);
