(function(coinswap) {

var LIST_MAX_ITEMS = 20;

coinswap.FeedView = Backbone.View.extend({
  template: $('#template-feed').html(),
  rowTemplate: _.template($('#template-feed-row').html()),

  initialize: function() {
    this.render();
    this.listenTo(this.model, 'initialize', this.onAppInitialize);
  },

  onAppInitialize: function() {
    //coinswap.trade.on('feed', this.addRow.bind(this));
  },

  render: function() {
    console.log('rendering feed view');
    this.$el.html(this.template);
  },

  addRow: function(data) {
    this.$el.find('.list').prepend(this.rowTemplate(data));
    while(this.$el.find('.list li').length > LIST_MAX_ITEMS) {
      var children = this.$el.find('.list').children();
      children.eq(-1).remove();
    }
  }
});

})(coinswap);
