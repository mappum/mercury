var coinswap = window.coinswap = {};

coinswap.App = Backbone.Model.extend({
  defaults: {
    balance: 0
  },

  initialize: function(options) {
    var t = this;
    _.bindAll(t, 'onAddCoin', 'updateBalance');

    var router = new coinswap.Router;
    this.set('router', router);
    this.listenTo(router, 'route', function(page, args) {
      t.set('page', { id: page, args: args });
    });

    var coins = new coinswap.CoinCollection;
    this.set('coins', coins);

    this.listenTo(coins, 'add', this.onAddCoin);

    this.on('initialized', function() {
      console.log('app initialized');
      this.set('initialized', true);
      coinswap.trade.on('ticker', t.updateBalance);

      coinswap.trade.on('version', function(version) {
        if(version > coinswap.version) {
          t.set('update', true);
        }
      });
    });
  },

  onAddCoin: function(coin) {
    var t = this;
    coin.on('change:balance change:pending', function() {
      t.updateBalance();
    });
    t.updateBalance();
  },

  updateBalance: function() {
    var totalBalance = '0',
        totalPending = '0';

    this.get('coins').each(function(coin) {
      var balance = coin.get('balance'),
          pending = coin.get('pending');

      if(coin.id !== 'BTC') {
        var ticker = coinswap.trade.ticker(coin.id, 'BTC');
        var bestBid = ticker ? ticker.bestBid : '0';
        balance = coinmath.multiply(balance, bestBid);
        pending = coinmath.multiply(pending, bestBid);
      }

      totalBalance = coinmath.add(totalBalance, balance);
      totalPending = coinmath.add(totalPending, pending);
    });

    // TODO: add a field for balance converted to chosen fiat
    this.set({
      balance: coinmath.format(totalBalance),
      pending: coinmath.format(totalPending)
    });
  }
});

coinswap.MainView = Backbone.View.extend({
  initialize: function() {
    _.bindAll(this, 'render');

    this.listenTo(this.model, 'change:page', this.render);
    this.listenTo(this.model, 'change:update', this.showUpdateAlert);
    this.listenTo(this.model.get('coins'), 'add', this.render);

    this.render();
  },

  render: function() {
    this.$el.empty();

    // javafx webview doesn't alzways properly redraw the whole page.
    // we do this hack to make sure it redraws
    var middleEl = $('#middle');
    middleEl.hide();
    setTimeout(middleEl.show.bind(middleEl), 0);

    var page = this.model.get('page');
    console.log('rendering ' + page.id);

    try {
      this[page.id].apply(this, page.args);
    } catch(e) {
      console.log('Uncaught exception: ' + e);
    }
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

  trades: function(filter) {
    var view = new coinswap.TradesView();
    this.$el.append(view.el);
  },

  send: function(id) {
    var coins = this.model.get('coins');
    var view = new coinswap.SendView({ collection: coins, id: id });
    this.$el.append(view.el);
  },

  data: function(){}
});

coinswap.Router = Backbone.Router.extend({
  routes: {
    '': 'home',
    '/*': 'home',
    'trade': 'trade',
    'trade/:coin': 'trade',
    'send': 'send',
    'send/:coin': 'send',
    'receive': 'receive',
    'receive/:coin': 'receive',
    'transactions': 'transactions',
    'transactions/:coin': 'transactions',
    'data': 'data',
    'data/:coin': 'data',
    'data/:coin1/:coin2': 'data',
    'trades': 'trades',
    'trades/:coin': 'trades',
    'trades/:coin1/:coin2': 'trades'
  }
});

function init() {
  var app = coinswap.app = new coinswap.App;
  Backbone.history.start();

  new coinswap.AlertsView({
    el: $('#alerts'),
    model: coinswap.app
  });

  new coinswap.MainView({
    el: $('#main'),
    model: coinswap.app
  });

  new coinswap.NavbarView({
    el: $('#left'),
    model: coinswap.app
  });

  new coinswap.ControlsView({
    el: $('header .controls'),
    model: new coinswap.History
  });

  new coinswap.TickerListView({
    el: $('#right .tickers'),
    model: coinswap.app
  });

  app.on('initialized', function() {
    new coinswap.OrderListView({
      el: $('#right .orders'),
      model: coinswap.app
    });

    new coinswap.TradeListView({
      el: $('#right .trades'),
      model: coinswap.app
    });

    new coinswap.FooterView({
      el: $('footer')
    });
  });
}

$(function() {
  try {
    init();
  } catch(e) {
    console.log('Uncaught exception: ' + e)
  }
});
