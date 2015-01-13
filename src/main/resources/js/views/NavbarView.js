(function(coinswap) {

coinswap.NavbarView = Backbone.View.extend({
  template: _.template($('#template-navbar').html()),
  tagName: 'header',
  className: 'navbar navbar-default',

  initialize: function() {
    this.render();

    this.listenTo(this.model, 'change:balance change:pending', this.render);
  },

  render: function() {
    console.log('rendering navbar')
    this.$el.html(this.template(this.model.attributes));
  }
});

})(coinswap);
