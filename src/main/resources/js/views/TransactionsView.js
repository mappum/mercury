(function(coinswap) {

coinswap.TransactionsView = Backbone.View.extend({
  template: $('#template-transactions').html(),
  rowTemplate: _.template($('#template-transactions-row').html()),
  className: 'container transactions',

  initialize: function() {
    _.bindAll(this, 'addTransaction');
    var t = this;
    this.render();

    var combined = new coinswap.TransactionCollection;
    this.collection.each(function(coin) {
      var transactions = coin.get('transactions');
      t.listenTo(transactions, 'add', t.addTransaction);
      transactions.each(function(tx){ combined.add(tx); });
    });
    combined.each(this.addTransaction);
  },

  render: function() {
    this.$el.html(this.template);
  },

  addTransaction: function(tx) {
    tx.window = window;
    var row = $('<tr>').html(this.rowTemplate(tx.attributes));
    this.$el.find('tbody').prepend(row);
  }
});

})(coinswap);
