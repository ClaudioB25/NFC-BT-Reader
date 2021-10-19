var exec = cordova.require("cordova/exec");

 function NFCBLE(){
	 
 }
 
 NFCBLE.prototype.start = function (successCallback, errorCallback) {
	exec(
			function(result) {
                successCallback(result);
            },
            function(error) {            	 
                errorCallback(error);
            },
            'BleBizPlugin',  // deve essere uguale al feature name che trovi in config.xml
            'listen'
        );
 }

 NFCBLE.prototype.ping = function (successCallback, errorCallback) {
	exec(
			function(result) {
                successCallback(result);
            },
            function(error) {
                errorCallback(error);
            },
            'BleBizPlugin',  // deve essere uguale al feature name che trovi in config.xml
            'ping'
        );
 }
 
 
var nfcBLE = new NFCBLE();
module.exports = nfcBLE;