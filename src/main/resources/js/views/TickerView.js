(function(coinswap) {

var margin = 14;

coinswap.TickerView = Backbone.View.extend({
  template: _.template($('#template-ticker').html()),
  className: 'ticker',

  initialize: function() {
    this.listenTo(this.model, 'change', this.render);
    this.render();
  },

  render: function() {
    this.$el.html(this.template(this.model.attributes));
    this.drawChart();
  },

  drawChart: function() {
    var width = this.$el.find('.chart').width() || 240,
      height = this.$el.find('.chart').height() || 90;

    var data = _.map(this.model.get('history'), function(point) {
      return point[1];
    });

    var x = d3.time.scale().range([margin, width - margin])
      .domain([0, 48]);
    var y = d3.scale.linear().range([height - margin, margin])
      .domain(d3.extent(data));

    var valueline = d3.svg.line()
      .interpolate('cardinal')
      .tension(0.5)
      .x(function(d, i) { return x(i); })
      .y(function(d) { return y(d); });

    d3.select(this.$el.find('.chart').get(0))
      .attr('width', width)
      .attr('height', height)
      .append('path')
        .attr('class', 'line')
        .attr('d', valueline(data));
  }
});

})(coinswap);
