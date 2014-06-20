(function(coinswap) {

coinswap.SendView = Backbone.View.extend({
  events: {
    'blur .address .value': 'validateAddress',
    'blur .amount .value': 'validateAmount',
    'click .send': 'send',
    'click .address .paste': 'pasteAddress'
  },

  template: $('#template-send').html(),
  className: 'send',

  initialize: function(options) {
    _.bindAll(this, 'render', 'validateAddress',
      'validateAmount', 'send');

    this.render();

    if(options.id)
      this.setCurrency(options.id);
  },

  render: function() {
    this.$el.html(this.template);
    this.delegateEvents();
  },

  setCurrency: function(id) {
    this.$el.find('.currency').text(id);
    var altEl = this.$el.find('.amount .alt');
    if(id) altEl.removeClass('hidden');
    else altEl.addClass('hidden');
  },

  validateAddress: function(e) {
    var addressEl = this.$el.find('.address');
    var address = addressEl.find('.value').val().trim();
    var currency;

    if(address) {
      for(var i = 0; i < this.collection.length; i++) {
        var coin = this.collection.at(i);
        if(coin.isAddressValid(address)) {
          currency = coin.id;
          this.model = coin;
          break;
        }
      }
    }
    this.setCurrency(currency);

    addressEl[currency || !address ? 'removeClass' : 'addClass']('has-error');
  },

  validateAmount: function(e) {
    var amountEl = this.$el.find('.amount');
    var amount = amountEl.find('.value').val().trim();
    if(!+amount || +amount < 0) amountEl.addClass('has-error');
    else amountEl.removeClass('has-error');
  },

  send: function() {
    this.validateAddress();
    this.validateAmount();

    var addressEl = this.$el.find('.address');
    var amountEl = this.$el.find('.amount');
    if(addressEl.hasClass('has-error') || amountEl.hasClass('has-error')
    || !addressEl.find('.value').val() || !amountEl.find('.value').val())
      return;

    // TODO: show confirm/wallet unlock modal before committing to action

    var address = addressEl.find('.value').val().trim();
    var amount = amountEl.find('.value').val().trim();
    this.model.send(address, +amount);

    // TODO: give feedback that transaction happened
  },

  pasteAddress: function() {
    var address = clipboard.get().trim();
    if(!address) return;
    this.$el.find('.address .value').val(address);
    this.validateAddress();
  }
});

})(coinswap);
