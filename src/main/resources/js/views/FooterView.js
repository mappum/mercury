(function(coinswap) {

coinswap.FooterView = Backbone.View.extend({
  template: _.template($('#template-footer').html()),

  initialize: function() {
    _.bindAll(this, 'render');
    coinswap.trade.on('disconnect', this.render);
    coinswap.trade.on('connect', this.render);
    this.render();
  },

  render: function() {
    console.log('rendering footer');
    this.$el.html(this.template({ connected: coinswap.trade.isConnected() }));
  }
});

})(coinswap);
