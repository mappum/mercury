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
    var chartEl = this.$el.find('.chart');
    var data = _.map(this.model.get('history'), function(point) {
      return point[1];
    });

    var x = d3.time.scale().range([margin, (chartEl.width()||250) - margin])
      .domain([0, data.length - 1]);
    var y = d3.scale.linear().range([(chartEl.height()||90) - margin, margin])
      .domain(d3.extent(data));

    var valueline = d3.svg.line()
      .interpolate('cardinal')
      .tension(0.8)
      .x(function(d, i) { return x(i); })
      .y(function(d) { return y(d); });

    d3.select(chartEl.get(0))
      .attr('width', chartEl.width() || 250)
      .attr('height', chartEl.height() || 90)
      .append('path')
        .attr('class', 'line')
        .attr('d', valueline(data));
  }
});

})(coinswap);
