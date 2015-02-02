(function(coinswap) {

coinswap.History = Backbone.Model.extend({
  defaults: {
    i: 0,
    length: 0,
    navigating: false
  },

  initialize: function() {
    _.bindAll(this, 'onNavigate', 'back', 'forward');
    window.addEventListener('hashchange', this.onNavigate);
  },

  onNavigate: function(e) {
    if(!this.get('navigating')) {
      this.set({
        i: this.get('i') + 1,
        length: this.get('i') + 1
      });
    } else {
      this.set('navigating', false);
    }
  },

  back: function() {
    this.set({
      navigating: true,
      i: this.get('i') - 1
    });
    window.history.back();
  },

  forward: function() {
    this.set({
      navigating: true,
      i: this.get('i') + 1
    });
    window.history.forward();
  },

  hasForward: function() {
    return this.get('i') < this.get('length');
  },

  hasBack: function() {
    return this.get('i') > 0;
  }
});

})(coinswap);
