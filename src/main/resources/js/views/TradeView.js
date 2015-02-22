(function(coinswap) {

coinswap.TradeView = Backbone.View.extend({
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .dropdown-coin': 'updateDropdowns',
    'click .buysell .btn': 'updateBuysell',
    'keypress .values input': 'updateInputs',
    'keydown .values input': 'updateInputs',
    'keyup .values input': 'updateInputs',
    'click .accept': 'submit'
  },

  template: _.template($('#template-trade').html()),
  className: 'trade',

  initialize: function() {
    _.bindAll(this, 'render', 'submit');
    this.listenTo(this.model, 'change:pair', this.updatePair);
    this.listenTo(this.model, 'change:buy', this.updatePair);
    this.listenTo(this.model, 'change:price', this.updateValues);
    this.listenTo(this.model, 'change:quantity', this.updateValues);
    this.listenTo(this.model, 'change:total', this.updateValues);
    this.listenTo(this.model, 'change:bestBid', this.updateBest);
    this.listenTo(this.model, 'change:bestAsk', this.updateBest);
    coinswap.trade.on('depth', this.updateOrderbook.bind(this));
    this.render();
    this.updatePair();
    this.updateValues();
    this.updateOrderbook();

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

    this.$el.find('.trade-balances .balance').each(function(i, el) {
      $(el)
        .find('.symbol').html(pair[i].get('symbol')).parent()
        .find('.value').text(this.model.get('balances')[i]).parent()
        .find('.currency').text(ids[i]);
    }.bind(this));

    var dropdowns = this.$el.find('.dropdown-coin');
    dropdowns.eq(0).find('.dropdown-toggle .value').text(ids[0]);
    dropdowns.eq(1).find('.dropdown-toggle .value').text(ids[1]);
    dropdowns.eq(0).find('.dropdown-toggle .logo')
      .attr('src', 'images/'+ids[0].toLowerCase()+'.png');
    dropdowns.eq(1).find('.dropdown-toggle .logo')
      .attr('src', 'images/'+ids[1].toLowerCase()+'.png');

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
    overview.find('.type')
        .removeClass(!this.model.get('buy') ? 'buy' : 'sell')
        .addClass(this.model.get('buy') ? 'buy' : 'sell')
        .text(this.model.get('buy') ? 'buy' : 'sell');
    overview.find('.alt:eq(0)').html(pair[0].id);
    overview.find('.alt:eq(1)').html(pair[1].id);

    this.updateOrderbook();
  },

  updateBuysell: function(e) {
    var selection = $(e.currentTarget);
    var buy = selection.hasClass('buy');
    this.model.set('buy', buy);
    this.updateBest();
  },

  updateInputs: function(e) {
    var keys = ['price', 'quantity', 'total'];
    var container = $(e.target).parent().parent();
    for(var i = 0; i < keys.length; i++) {
      if(container.hasClass(keys[i])) {
        var val = $(e.target).val();
        if(!val || !+val) return;

        try {
          var val = coinmath.truncate(val);
          return this.model.set(keys[i], val);
        } catch(err) {}
      }
    }
  },

  updateValues: function() {
    var m = this.model;
    this.$el.find('.values .price input').val(m.get('price'));
    this.$el.find('.values .quantity input').val(m.get('quantity'));
    this.$el.find('.values .total input').val(m.get('total'));
    this.$el.find('.overview .quantity').text(m.get('quantity'));
    this.$el.find('.overview .total').text(m.get('total'));

    // TODO: take transaction fees into account
    if((!m.get('buy') && coinmath.compare(m.get('quantity'), m.get('balances')[0]) === 1)
    || (m.get('buy') && coinmath.compare(m.get('total'), m.get('balances')[1]) === 1)) {
      this.$el.find('.values').addClass('has-error');
      this.$el.find('.accept').addClass('disabled');
    } else {
      this.$el.find('.values').removeClass('has-error');
      this.$el.find('.accept').removeClass('disabled');
    }
  },

  updateBest: function() {
    var m = this.model;
    var bestPrice = m.get(m.get('buy') ? 'bestBid' : 'bestAsk');
    if(bestPrice) m.set('price', bestPrice);
  },

  updateOrderbook: function(pair) {
    if(pair && pair !== this.model.get('pair').join('/').toLowerCase()) return;

    var ids = this.model.get('pair');
    var depth = coinswap.trade.depth(ids[0], ids[1], 14);

    var bidsEl = this.$el.find('.bids').empty();
    _.each(depth.bids, function(bid) {
      var row = $('<tr>')
        .append($('<td class="quantity"></td>').text(bid[1]))
        .append($('<td class="price"></td>').text(bid[2]));
      bidsEl.append(row);
    });

    var asksEl = this.$el.find('.asks').empty();
    _.each(depth.asks, function(ask) {
      var row = $('<tr>')
        .append($('<td class="quantity"></td>').text(ask[1]))
        .append($('<td class="price"></td>').text(ask[2]));
      asksEl.prepend(row);
    });
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
      m.get('quantity'), m.get('total'), function(err, res) {
        if(err) return;
      });
  }
});

})(coinswap);
