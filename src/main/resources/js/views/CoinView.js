(function(coinswap) {
var months = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December'
];

coinswap.CoinView = Backbone.View.extend({
  template: _.template($('#template-coinview').html()),
  className: 'row-fluid well coin',

  initialize: function() {
    this.render();

    this.listenTo(this.model, 'change:synced change:connected', this.render);
    this.listenTo(this.model, 'sync:progress', this.syncProgress);
  },

  render: function() {
    this.$el.html(this.template(this.model.attributes));

    if(!this.model.get('synced') || !this.model.get('connected')) this.$el.addClass('syncing');
    else this.$el.removeClass('syncing');
  },

  syncProgress: function(o) {
    var total = this.model.get('syncBlocks');
    var done =  total - o.blocks;
    var percent = done / total * 100;
    this.$el.find('.sync-blocks').text(done);
    this.$el.find('.progress-bar').css('width', percent+'%');

    var date = new Date(o.date);
    var dateString = months[date.getMonth()] + ' ' + date.getDate() +
      ', ' + (date.getYear() + 1900);
    this.$el.find('.sync-date').text(dateString);
  }
});

})(coinswap);
