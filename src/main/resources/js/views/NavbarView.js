(function(coinswap) {

coinswap.NavbarView = Backbone.View.extend({
  template: _.template($('#template-navbar').html()),
  tagName: 'header',
  className: 'navbar navbar-default',

  initialize: function() {
    this.render();

    this.listenTo(this.model, 'change:balance change:pending change:page', this.render);
  },

  render: function() {
    console.log('rendering navbar')
    this.$el.html(this.template(this.model.attributes));

    this.$el.find('[data-toggle="tooltip"]').tooltip({
      animation: false,
      container: this.$el
    });
  }
});

})(coinswap);
