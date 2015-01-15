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
      coinswap.trade.on('ticker', t.updateBalance);
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

  var navbarView = new coinswap.NavbarView({
    el: $('#left'),
    model: coinswap.app
  });

  var x = d3.time.scale().range([0, 200]);
  var y = d3.scale.linear().range([140, 0]);
  var valueline = d3.svg.line()
      .interpolate('cardinal')
      .tension(0.8)
      .x(function(d, i) { return x(i); })
      .y(function(d) { return y(d); });
      
  var data = [1,2,5,6,8,4,6,4,2,14,11,19,10,8,5,3,2];
  var svg = d3.select(".chart")
      .append("svg")
          .attr("width", 200)
          .attr("height", 140);
  x.domain(d3.extent(data, function(d, i) { return i; }));
  y.domain([0, d3.max(data, function(d) { return d; })]);
  svg.append("path")
      .attr("class", "line")
      .attr("d", valueline(data));


});