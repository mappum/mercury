(function(coinswap) {

coinswap.Trade = Backbone.Model.extend({
  defaults: {
    buy: true,
    pair: ['BTC', 'LTC']
  },

  initialize: function(attributes, options) {
    if(options.id) {
      var pair = this.get('pair');
      var coins = this.get('coins');
      pair[0] = options.id;
      pair[1] = coins.get(pair[0]).get('pairs')[0];
    }

    this.on('change:pair', this.updatePair);
  },

  updatePair: function() {
    var pair = this.getPair();
    var pairs = pair[0].get('pairs');
    if(pairs.indexOf(pair[1].id) === -1)
      this.get('pair')[1] = pairs[0];
  },

  getPair: function() {
    var pair = this.get('pair');
    var coins = this.get('coins');

    var models = [
      coins.get(pair[0]),
      coins.get(pair[1])
    ];

    if(models[0].get('index') < models[1].get('index'))
      models.reverse();

    return models;
  }
});

})(coinswap);
