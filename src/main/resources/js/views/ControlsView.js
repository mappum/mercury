(function(coinswap) {

coinswap.ControlsView = Backbone.View.extend({
  events: {
    'click .back': 'back',
    'click .forward': 'forward'
  },
  template: _.template($('#template-controls').html()),

  initialize: function() {
    _.bindAll(this, 'render', 'back', 'forward');
    this.listenTo(this.model, 'change', this.render);
    this.render();
  },

  render: function() {
    this.$el.html(this.template({
      hasBack: this.model.hasBack(),
      hasForward: this.model.hasForward(),
    }));
    this.delegateEvents();
  },

  back: function() {
    this.model.back();
  },

  forward: function() {
    this.model.forward();
  }
});

})(coinswap);
