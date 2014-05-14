var coinswap = window.coinswap = {};

coinswap.App = Backbone.Model.extend({
  initialize: function() {
    var coins = new Backbone.Collection;
    this.set('coins', coins);
    this.listenTo(coins, 'add', function(model) {
      $('#home').append(new coinswap.CoinView({ model: model }).el);
    });
  }
});

coinswap.app = new coinswap.App;

$(function(){
  $('.btn').button();
});