(function(coinswap) {

coinswap.TradeView = Backbone.View.extend({
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .dropdown-coin': 'updateDropdowns',
    'click .buysell .btn': 'updateBuysell',
    'keypress .values input': 'updateInputs',
    'keydown .values input': 'updateInputs',
    'keyup .values input': 'updateInputs',
    'change .values input': 'updateInputs',
    'click .accept': 'submit'
  },

  template: _.template($('#template-trade').html()),
  orderTemplate: _.template($('#template-trade-order').html()),
  className: 'container trade',

  initialize: function() {
    _.bindAll(this, 'render', 'submit');
    this.listenTo(this.model, 'change:pair', this.updatePair);
    this.listenTo(this.model, 'change:buy', this.updatePair);
    this.listenTo(this.model, 'change:price', this.updateValues);
    this.listenTo(this.model, 'change:quantity', this.updateValues);
    this.listenTo(this.model, 'change:total', this.updateValues);
    this.listenTo(this.model, 'change:bestBid', this.updateBest);
    this.listenTo(this.model, 'change:bestAsk', this.updateBest);
    this.render();
    this.updatePair();
    this.updateValues();
    this.updateOrders();

    this.model.set('price', this.model.get('bestBid'));
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
    this.$el.html(this.template(this.model.attributes));
    this.delegateEvents();
  },

  updateDropdowns: function() {
    var dropdowns = this.$el.find('.dropdown-coin');
    var coins = this.model.get('coins');

    var pair = [
      dropdowns.eq(0).find('.dropdown-toggle .value').text(),
      dropdowns.eq(1).find('.dropdown-toggle .value').text()
    ];

    this.model.set('pair', pair);
  },

  updatePair: function(e) {
    var ids = this.model.get('pair');
    var pair = this.model.getPair();
    var pairs = pair[0].get('pairs');

    var dropdowns = this.$el.find('.dropdown-coin');
    dropdowns.eq(0).find('.dropdown-toggle .value').text(ids[0]);
    dropdowns.eq(1).find('.dropdown-toggle .value').text(ids[1]);

    var menus = dropdowns.find('.dropdown-menu');
    menus.empty();

    var coins = this.model.get('coins');
    coins.each(function(coin) {
      var el = $('<li>').html('<a><strong class="value">'+coin.id+'</strong>' + 
        ' <span class="alt"> - '+coin.get('name')+'</span></a>');

      if(pairs.indexOf(coin.id) !== -1) menus.append(el);
      else menus.eq(0).append(el);
    });

    this.$el.find('.price, .total')
      .find('.symbol').html(pair[1].get('symbol'));
    this.$el.find('.quantity')
      .find('.symbol').html(pair[0].get('symbol'));

    var overview = this.$el.find('.overview');
    overview
      .find('.type')
        .removeClass(!this.model.get('buy') ? 'buy' : 'sell')
        .addClass(this.model.get('buy') ? 'buy' : 'sell')
        .text(this.model.get('buy') ? 'buy' : 'sell')
      .parent().find('.symbol:eq(0)').html(pair[0].get('symbol'))
      .parent().find('.alt:eq(0)').html(pair[0].id);
    overview.find('.symbol:eq(1)').html(pair[1].get('symbol'));
    overview.find('.alt:eq(1)').html(pair[1].id);
  },

  updateBuysell: function(e) {
    var selection = $(e.currentTarget);
    var buy = selection.hasClass('buy');
    this.model.set('buy', buy);
    this.updateBest();
  },

  updateInputs: function(e) {
    // TODO: error if value is NaN

    var keys = ['price', 'quantity', 'total'];
    var container = $(e.target).parent().parent();
    for(var i = 0; i < keys.length; i++) {
      if(container.hasClass(keys[i])) {
        var val = $(e.target).val();
        if(!val || !+val) return;

        try {
          var val = parseFloat(val);
          return this.model.set(keys[i], val);
        } catch(err) {}
      }
    }
  },

  updateValues: function() {
    this.$el.find('.values .price input').val(this.model.get('price'));
    this.$el.find('.values .quantity input').val(this.model.get('quantity'));
    this.$el.find('.values .total input').val(this.model.get('total'));
    this.$el.find('.overview .quantity').text(this.model.get('quantity'));
    this.$el.find('.overview .total').text(this.model.get('total'));
  },

  updateBest: function() {
    var m = this.model;
    var bestPrice = m.get(m.get('buy') ? 'bestBid' : 'bestAsk');
    m.set('price', bestPrice);
  },

  updateOrders: function() {
    console.log('updateOrders')
    var t = this;
    var orders = coinswap.trade.orders();
    var ordersEl = this.$el.find('.orders').empty();
    try{
    _.each(orders, function(order) {
      order.symbols = [
        t.model.getPair()[0].get('symbol'),
        t.model.getPair()[1].get('symbol')
      ];
      var el = $('<li class="list-group-item order">').html(t.orderTemplate(order));
      ordersEl.append(el);
    });
  } catch(err) {
    console.log(err+'')
  }
  },

  submit: function() {
    var t = this;
    this.$el.find('.accept')
      .addClass('disabled')
      .text('Submitted trade');

    setTimeout(function(){
      t.$el.find('.accept')
        .removeClass('disabled')
        .text('Accept Trade');
    }, 3000);

    var m = this.model;
    coinswap.trade.submit(m.get('buy'),
      m.get('pair')[0], m.get('pair')[1],
      m.get('quantity'), m.get('total'));
  }
});

})(coinswap);
