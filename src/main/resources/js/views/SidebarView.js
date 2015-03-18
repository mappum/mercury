(function(coinswap) {

coinswap.SidebarView = Backbone.View.extend({
  events: {
    'click .feed-button': 'showFeed',
    'click .tickers-button': 'showTickers'
  },

  template: $('#template-sidebar').html(),
  className: 'sidebar',

  initialize: function() {
    this.render();
    this.listenTo(this.model, 'initialize', this.onAppInitialize);

    new coinswap.TickerListView({
      el: this.$el.find('.tickers'),
      model: this.model
    });

    new coinswap.FeedView({
      el: this.$el.find('.feed'),
      model: this.model
    });
  },

  onAppInitialize: function() {
    new coinswap.OrderListView({
      el: this.$el.find('.orders'),
      model: this.model
    });

    new coinswap.TradeListView({
      el: this.$el.find('.trades'),
      model: this.model
    });
  },

  showFeed: function() {
    this.$el.find('.feed-button').addClass('active');
    this.$el.find('.tickers-button').removeClass('active');
    this.$el.find('.feed').removeClass('hidden');
    this.$el.find('.tickers').addClass('hidden');
  },

  showTickers: function() {
    this.$el.find('.tickers-button').addClass('active');
    this.$el.find('.feed-button').removeClass('active');
    this.$el.find('.tickers').removeClass('hidden');
    this.$el.find('.feed').addClass('hidden');
  },

  render: function() {
    console.log('rendering sidebar');
    this.$el.html(this.template);
    this.delegateEvents();
  }
});

})(coinswap);
