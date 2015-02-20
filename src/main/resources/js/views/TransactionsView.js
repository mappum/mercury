(function(coinswap) {

coinswap.TransactionsView = Backbone.View.extend({
  template: $('#template-transactions').html(),
  rowTemplate: _.template($('#template-transactions-row').html()),
  className: 'transactions',

  initialize: function() {
    _.bindAll(this, 'addTransaction');
    var t = this;
    this.render();

    this.removed = false;

    var combined = new coinswap.TransactionCollection;
    this.collection.each(function(coin) {
      var transactions = coin.get('transactions');
      t.listenTo(transactions, 'add', t.addTransaction);
      transactions.each(function(tx){ combined.add(tx); });
    });
    combined.each(this.addTransaction);

    this.initialized = true;
  },

  render: function() {
    this.$el.html(this.template);
  },

  addTransaction: function(tx) {
    var t = this;
    var row = $('<tr>');

    function updateRow() {
      if(t.initialized) {
        if(!t.removed && !$.contains(document.documentElement, t.el))
          t.removed = true;
        if(t.removed)
          return tx.off('change', updateRow);
      }
      row.html(t.rowTemplate(tx.attributes));

      row.find('[data-toggle="tooltip"]').tooltip({
        animation: false,
        container: row
      });
    }

    updateRow();
    this.$el.find('tbody').prepend(row);
    tx.on('change', updateRow);

    row.find('[data-toggle="tooltip"]').tooltip({
      animation: false,
      container: row
    });
  }
});

})(coinswap);
