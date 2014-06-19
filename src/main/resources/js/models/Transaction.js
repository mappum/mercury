(function(coinswap) {

coinswap.Transaction = Backbone.Model.extend({});

coinswap.TransactionCollection = Backbone.Collection.extend({
  model: coinswap.Transaction,
  comparator: 'date'
});

})(coinswap);
