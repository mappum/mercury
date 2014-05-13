(function(window) {
  var handlers = {};

  window.controller = {
    emit: function(id, event, data) {
      if(data == null) {
        data = event;
        event = id;
        id = '';
      }

      if(!handlers[id]) return;
      if(!handlers[id][event]) return;

      for(var cb in handlers[id][event]) {
        cb(data);
      }
    },

    on: function(id, event, cb) {
      if(cb == null) {
        cb = event;
        event = id;
        id = null;
      }

      if(!handlers[id]) handlers[id] = {};
      
      var h = handlers[id][event];
      if(!h) handlers[id][event] = [];

      handlers[id][event].push(cb);
    }
  };
})(window);

$(function(){
  $('.btn').button();
});

document.write(navigator);