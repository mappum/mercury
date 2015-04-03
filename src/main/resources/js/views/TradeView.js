(function(coinswap) {

coinswap.TradeView = Backbone.View.extend({
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .dropdown-coin': 'updateDropdowns',
    'click .buysell .btn': 'updateBuysell',
    'keypress .values input': 'updateInputs',
    'keydown .values input': 'updateInputs',
    'keyup .values input': 'updateInputs',
    'click .accept': 'submit',
    'click .advanced': 'toggleAdvanced'
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
    this.listenTo(coinswap.app, 'change:balance', this.updatePair);
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
    this.$el.find('.collapse').collapse({
      toggle: false
    });
    this.$el.find('[data-toggle="tooltip"]').tooltip({
      animation: false,
      container: this.$el
    });
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
        .find('.value').text(pair[i].get('balance')).parent()
        .find('.currency').text(ids[i]);
    }.bind(this));

    this.$el.find('.trade-orderbook thead .currency').each(function(i, el) {
      $(el).text(ids[i]);
    });

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

    this.updateFee();
    this.updateOrderbook();
  },

  updateBuysell: function(e) {
    var selection = $(e.currentTarget);
    var buy = selection.hasClass('buy');
    this.model.set('buy', buy);
    this.updateFee();
    this.updateBest();
  },

  updateFee: function() {
    var pair = this.model.getPair();
    var buy = this.model.get('buy');

    var fee = this.$el.find('.fee');
    fee.find('.symbol').html(pair[+buy].get('symbol'));
    fee.find('.value').text(this.getFee());
  },

  getFee: function() {
    // TODO: measure actual transaction size instead of assuming <1kb
    var pair = this.model.getPair();
    var buy = this.model.get('buy');
    var baseFee = pair[+buy].get('fee');
    return coinmath.multiply(baseFee, '2');
  },

  updateInputs: function(e) {
    var keys = ['price', 'quantity', 'total'];
    var container = $(e.target).parent().parent();
    for(var i = 0; i < keys.length; i++) {
      if(container.hasClass(keys[i])) {
        var val = $(e.target).val();

        try {
          var val = coinmath.truncate(val);
          return this.model.set(keys[i], val);
        } catch(err) {}
      }
    }
  },

  updateValues: function() {
    var m = this.model;

    this.clearError();
    this.$el.find('.values .price input').val(m.get('price'));
    this.$el.find('.values .quantity input').val(m.get('quantity'));
    this.$el.find('.values .total input').val(m.get('total'));
    this.$el.find('.overview .quantity').text(m.get('quantity'));
    this.$el.find('.overview .total').text(m.get('total'));
  },

  setError: function(message) {
    this.$el.find('.values').addClass('has-error').find('.error').html(message);
    this.$el.find('.accept').addClass('disabled');
  },

  clearError: function() {
    this.$el.find('.values').removeClass('has-error').find('.error').text('');
    this.$el.find('.accept').removeClass('disabled');
  },

  validateValues: function() {
    var m = this.model;
    var pair = m.getPair();
    var cm = coinmath;
    var fee = this.getFee();

    var empty = cm.compare(m.get('quantity'), '0') === 0
      || cm.compare(m.get('total'), '0') === 0;

    if(!m.get('buy') && cm.compare(cm.add(m.get('quantity'), fee), pair[0].get('balance')) === 1) {
      this.setError('Not enough '+m.get('pair')[0]+' in wallet');

    } else if(m.get('buy') && cm.compare(cm.add(m.get('total'), fee), pair[1].get('balance')) === 1) {
      this.setError('Not enough '+m.get('pair')[1]+' in wallet');

    } else if(!empty && cm.compare(m.get('quantity'), pair[0].get('fee')) === -1) {
      this.setError('Order must be at least <strong>'+pair[0].get('fee')+' <span class="alt">'+m.get('pair')[0]+'</span></strong>');

    } else if(!empty && cm.compare(m.get('total'), pair[1].get('fee')) === -1) {
      this.setError('Order must be at least <strong>'+pair[1].get('fee')+' <span class="alt">'+m.get('pair')[1]+'</span></strong>');

    } else {
      this.clearError();
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
    if(depth.bids.length) {
      _.each(depth.bids, function(bid) {
        var row = $('<tr>')
          .append($('<td class="quantity"></td>').text(bid[1]))
          .append($('<td class="price"></td>').text(bid[2]));
        bidsEl.append(row);
      });
    } else {
      var row = $('<tr>')
        .append($('<td>').text('There are no bid orders for this pair.'))
        .append($('<td>'));
      bidsEl.append(row);
    }

    var asksEl = this.$el.find('.asks').empty();
    if(depth.asks.length) {
      _.each(depth.asks, function(ask) {
        var row = $('<tr>')
          .append($('<td class="quantity"></td>').text(ask[1]))
          .append($('<td class="price"></td>').text(ask[2]));
        asksEl.prepend(row);
      });
    } else {
      var row = $('<tr>')
        .append($('<td>').text('There are no ask orders for this pair.'))
        .append($('<td>'));
      asksEl.append(row);
    }
  },

  submit: function() {
    this.validateValues();
    if(this.$el.find('.values').hasClass('has-error')) return;

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
  },

  toggleAdvanced: function() {
    if(this.$el.find('.collapse').hasClass('in')) {
      this.$el.find('.advanced i').removeClass('fa-angle-up').addClass('fa-angle-down');
    } else {
      this.$el.find('.advanced i').removeClass('fa-angle-down').addClass('fa-angle-up');
    }
  }
});

})(coinswap);
