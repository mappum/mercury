(function(coinswap) {

coinswap.TradeView = Backbone.View.extend({
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .dropdown-coin': 'updateDropdowns',
    'click .buysell .btn': 'updateBuysell',
    'keypress .values input': 'updateInputs',
    'keydown .values input': 'updateInputs',
    'keyup .values input': 'updateInputs',
    'change .values input': 'updateInputs'
  },

  template: _.template($('#template-trade').html()),
  className: 'container trade',

  initialize: function() {
    _.bindAll(this, 'render');
    this.listenTo(this.model, 'change:pair', this.updatePair);
    this.listenTo(this.model, 'change:buy', this.updatePair);
    this.listenTo(this.model, 'change:price', this.updateValues);
    this.listenTo(this.model, 'change:quantity', this.updateValues);
    this.listenTo(this.model, 'change:total', this.updateValues);
    this.render();
    this.updatePair();
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
  },

  updateInputs: function(e) {
    // TODO: error if value is NaN

    var keys = ['price', 'quantity', 'total'];
    var container = $(e.target).parent().parent();
    for(var i = 0; i < keys.length; i++) {
      if(container.hasClass(keys[i]))
        return this.model.set(keys[i], +$(e.target).val());
    }
  },

  updateValues: function() {
    this.$el.find('.values .price input').val(this.model.get('price'));
    this.$el.find('.values .quantity input').val(this.model.get('quantity'));
    this.$el.find('.values .total input').val(this.model.get('total'));
    this.$el.find('.overview .quantity').text(this.model.get('quantity'));
    this.$el.find('.overview .total').text(this.model.get('total'));
  }
});

})(coinswap);
