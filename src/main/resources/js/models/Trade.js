(function(coinswap) {

coinswap.Trade = Backbone.Model.extend({
  defaults: {
    buy: true,
    pair: ['LTC', 'BTC'],
    price: 0,
    quantity: 0,
    total: 0
  },

  initialize: function(attributes, options) {
    if(options.id) {
      var pair = this.get('pair');
      var coins = this.get('coins');
      pair[0] = options.id;
      pair[1] = coins.get(pair[0]).get('pairs')[0];
    }

    this.on('change:pair', this.updatePair);
    this.on('change:price', this.updateValues);
    this.on('change:quantity', this.updateValues);
    this.on('change:total', this.updateTotal);
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
  },

  updateValues: function() {
    this.set('total', +this.get('price') * +this.get('quantity'));
  },
  
  updateTotal: function() {
    this.set('quantity', +this.get('total') / +this.get('price'));
  }
});

})(coinswap);
