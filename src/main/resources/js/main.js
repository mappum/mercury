var coinswap = window.coinswap = {};

coinswap.App = Backbone.Model.extend({
  initialize: function(options) {
    var t = this;

    var router = new coinswap.Router;
    this.set('router', router);
    this.listenTo(router, 'route', function(page, args) {
      t.set('page', { id: page, args: args });
    });

    var coins = new coinswap.CoinCollection;
    this.set('coins', coins);
  }
});

coinswap.MainView = Backbone.View.extend({
  initialize: function() {
    _.bindAll(this, 'render');

    this.listenTo(this.model, 'change:page', this.render);
    this.listenTo(this.model.get('coins'), 'add', this.render);

    this.render();
  },

  render: function() {
    this.$el.empty();

    var page = this.model.get('page');
    console.log('rendering ' + page.id);

    this[page.id].apply(this, page.args);
  },

  home: function() {
    var t = this;

    var coins = this.model.get('coins');
    coins.each(function(coin) {
      var view = new coinswap.CoinView({ model: coin });
      t.$el.append(view.el);
    });
  },

  trade: function(id) {
    var coins = this.model.get('coins');
    var model = new coinswap.Trade({ coins: coins }, { id: id });
    var view = new coinswap.TradeView({ model: model });
    this.$el.append(view.el);
  },

  receive: function(id) {
    var coins = this.model.get('coins');
    var view = new coinswap.ReceiveView({ collection: coins, id: id });
    this.$el.append(view.el);
  },

  transactions: function(filter) {
    var coins = this.model.get('coins');
    var view = new coinswap.TransactionsView({ collection: coins, filter: filter });
    this.$el.append(view.el);
  },

  send: function(id) {
    var coins = this.model.get('coins');
    var view = new coinswap.SendView({ collection: coins, id: id });
    this.$el.append(view.el);
  }
});

coinswap.Router = Backbone.Router.extend({
  routes: {
    '': 'home',
    '/*': 'home',
    'trade': 'trade',
    'trade/:coin': 'trade',
    'send': 'send',
    'send/:coin': 'send',
    'receive/:coin': 'receive',
    'transactions': 'transactions',
    'transactions/:coin': 'transactions',
    'data': 'data',
    'data/:coin': 'data',
    'data/:coin1/:coin2': 'data'
  }
});

$(function() {
  coinswap.app = new coinswap.App;
  Backbone.history.start();

  var mainView = new coinswap.MainView({
    el: $('#main'),
    model: coinswap.app
  });
});