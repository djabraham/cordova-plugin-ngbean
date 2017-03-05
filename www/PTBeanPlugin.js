
module.exports = {

  find: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "find");
  },
  select: function (successCallback, errorCallback, addr) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "select", [addr]);
  },
  connect: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "connect");
  },
  disconnect: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "disconnect");
  },
  serial: function (successCallback, errorCallback, data) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "serial", [data]);
  },
  temperature: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "temperature");
  },
  led: function (successCallback, errorCallback, r, g, b) {
    cordova.exec(successCallback, errorCallback, "PTBeanPlugin", "led", [r, g, b]);
  }

};
