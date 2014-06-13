(function(coinswap) {

coinswap.ReceiveView = Backbone.View.extend({
  events: {
    'click .dropdown .dropdown-menu li': 'onDropdownSelect',
    'change .dropdown-coin': 'updateModel',
    'click .generate': 'newAddress'
  },

  template: _.template($('#template-receive').html()),
  className: 'receive',

  initialize: function(options) {
    _.bindAll(this, 'render');

    this.render();

    var currency = this.$el.find('.dropdown-coin');
    currency.find('.dropdown-toggle .value').text(options.id);

    this.updateModel();
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
    this.$el.html(this.template({}));

    var currency = this.$el.find('.dropdown-coin');
    var menu = currency.find('.dropdown-menu');
    this.collection.each(function(coin) {
      var el = $('<li>').html('<a><strong class="value">'+coin.id+'</strong>' + 
        ' <span class="alt"> - '+coin.get('name')+'</span></a>');
      menu.append(el);
    });

    this.delegateEvents();
  },

  updateModel: function() {
    if(this.model)
      this.stopListening(this.model, 'change:address', this.updateAddress);

    var currency = this.$el.find('.dropdown-coin');
    var id = currency.find('.dropdown-toggle .value').text();
    console.log("updateModel: " + id)
    this.model = this.collection.get(id);
    this.listenTo(this.model, 'change:address', this.updateAddress);
    this.updateAddress();
  },

  updateAddress: function() {
    var address = this.model.get('address');
    var addressEl = this.$el.find('.address');
    addressEl.val(address);
  },

  newAddress: function() {
    this.model.newAddress();
  }
});

})(coinswap);
