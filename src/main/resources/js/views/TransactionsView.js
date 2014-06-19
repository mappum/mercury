(function(coinswap) {

coinswap.TransactionsView = Backbone.View.extend({
  template: _.template($('#template-transactions').html()),
  rowTemplate: _.template($('#template-transactions-row').html()),
  className: 'container transactions',

  initialize: function() {
    _.bindAll(this, 'addTransaction');
    var t = this;
    this.render();
    this.collection.each(function(coin) {
      var transactions = coin.get('transactions');
      t.listenTo(transactions, 'add', t.addTransaction);
      transactions.each(t.addTransaction);
    });
  },

  render: function() {
    this.$el.html(this.template({}));
  },

  addTransaction: function(tx) {
    var row = $('<tr>').html(this.rowTemplate(tx.attributes));
    this.$el.find('tbody').prepend(row);
  }
});

})(coinswap);
