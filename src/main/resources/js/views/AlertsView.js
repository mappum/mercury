(function(coinswap) {

coinswap.AlertsView = Backbone.View.extend({
  template: _.template($('#template-alert').html()),

  initialize: function() {
    this.listenTo(this.model, 'change:update', this.addUpdateAlert);

    this.addAlert({
      level: 'warning',
      content: '<strong><i class="fa fa-warning"></i> WARNING:</strong> This is alpha software. Please use this version of Mercury for <u>testing purposes only</u>, and do not rely on it for large sums of money. We\'ll let you know when it\'s ready for primetime.'
    });
  },

  addAlert: function(options) {
    var alertEl = $(this.template(options));
    this.$el.append(alertEl);
  },

  addUpdateAlert: function() {
    this.addAlert({
      level: 'info',
      content: 'A new version of Mercury is available. You can download it <a href="#" onclick="desktop.browse(\'http://mercuryex.com/download\')">here</a>.'
    });
  }
});

})(coinswap);
