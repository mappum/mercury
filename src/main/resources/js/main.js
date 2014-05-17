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
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .trade .dropdown-coin:eq(0)': 'updateDropdowns'
  },

  templates: {
    trade: _.template($('#template-trade').html())
  },

  initialize: function() {
    _.bindAll(this, 'render');

    this.listenTo(this.model, 'change:page', this.render);
    this.listenTo(this.model.get('coins'), 'add', this.render);

    this.render();
  },

  onDropdownSelect: function(e) {
    var selection = $(e.currentTarget);
    var id = selection.find('.value').text();
    var dropdown = selection.parent().parent();
    var valueEl = dropdown.find('.dropdown-toggle .value');
    var value = valueEl.text();

    if(id.toLowerCase() !== value.toLowerCase()) {
      valueEl.text(id);
      dropdown.trigger('change', [ id, value, selection ]);
    }
  },

  render: function() {
    this.$el.empty();

    var page = this.model.get('page');
    console.log('rendering ' + page.id);

    var template = this.templates[page.id];
    if(template) {
      var data = this.model.toJSON();
      data.model = this.model;
      this.$el.html(template(data));
    }

    this.delegateEvents();

    if(this[page.id])
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
    this.updateDropdowns(id);
  },

  updateDropdowns: function(id) {
    if(typeof id === 'object') id = arguments[1];

    var dropdowns = this.$el.find('.dropdown-coin');
    var menus = dropdowns.find('.dropdown-menu');
    var coins = this.model.get('coins');

    id = id || 'BTC';
    dropdowns.eq(0).find('.dropdown-toggle .value').text(id);
    var coin = coins.get(id);
    var pairs = coin.get('pairs');

    menus.empty();
    coins.each(function(coin) {
      var el = $('<li>').html('<a><strong class="value">'+coin.id+'</strong>' + 
        ' <span class="alt"> - '+coin.get('name')+'</span></a>');
      if(pairs.indexOf(coin.id) !== -1) menus.append(el);
      else menus.eq(0).append(el);
    });

    id = dropdowns.eq(1).find('.dropdown-toggle .value').text();
    if(pairs.indexOf(id) === -1)
      dropdowns.eq(1).find('.dropdown-toggle .value').text(pairs[0]);

    this.delegateEvents();
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
    'receive': 'receive',
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