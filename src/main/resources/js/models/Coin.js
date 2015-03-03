(function(coinswap) {

coinswap.Coin = Backbone.Model.extend({
  defaults: {
    id: '',
    balance: '0',
    pending: '0',
    connected: false,
    synced: false,
    address: '',
    pairs: [],
    fee: '0'
  },

  initialize: function() {
    _.bindAll(this, 'updateBalance');

    var transactions = new coinswap.TransactionCollection;
    this.set('transactions', transactions);

    this.on('peers:connected', function(o) {
      this.set({
        connected: this.get('connected') || o.peers >= o.maxPeers,
        maxPeers: o.maxPeers,
        peers: o.peers
      });
    });

    this.on('sync:start', function(blocks) {
      this.set({ syncBlocks: blocks });
    });
    this.on('sync:done', function() {
      this.set({ synced: true });
    });

    this.on('transaction', function(tx) {
      this.updateBalance();
      transactions.add(tx, { merge: true });
    });

    this.on('initialized', this.updateBalance);
    this.on('changed', this.updateBalance);
    coinswap.trade.on('orders:change', this.updateBalance);
  },

  updateBalance: function() {
    if(!this.controller) return;

    var inWallet = this.controller.balance();
    var inOrders = this.getLockedBalance();

    this.set({
      balance: coinmath.subtract(inWallet, inOrders),
      pending: this.controller.pendingBalance(),
    });
  },

  getLockedBalance: function() {
    var t = this;
    var orders = coinswap.trade.orders();
    var locked = '0';
    _.each(orders, function(order) {
      if(((order.currencies[0]+'') === t.get('id')) && !order.bid) {
        locked = coinmath.add(locked, order.amount);
      } else if(((order.currencies[1]+'') === t.get('id')) && order.bid) {
        locked = coinmath.add(locked, coinmath.multiply(order.amount, order.price));
      }
    });
    console.log('getLockedBalance(): ' + locked + ' ' + this.get('id'))
    return locked;
  },

  newAddress: function(cb) {
    var address = this.controller.newAddress();
    this.set('address', address);
    return address;
  },

  isAddressValid: function(address) {
    return this.controller.isAddressValid(address);
  },

  send: function(address, amount) {
    return this.controller.send(address, amount + '');
  }
});

coinswap.CoinCollection = Backbone.Collection.extend({
  model: coinswap.Coin,
  comparator: 'index'
});

})(coinswap);
