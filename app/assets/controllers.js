angular.module('spark.controllers', [])

.controller(
		'SparkController',
		function($scope) {
			// Initialize data fields
			$scope.serverTime = "Nothing Here Yet";

			// Statistics
			$scope.rawCount = 0;
			$scope.pubGeoCount = 0;
			$scope.mongoCount = 0;			

			// events from server handler
			$scope.handleServerEvent = function(e) {
				$scope.$apply(function() {
					var raw = JSON.parse(e.data);
					var target = raw.target;
					var data = raw.data;
					if (target == "logs") {
						var file = {
							id : data.id,
							fileName : data.fileName,
							size : data.size ,
							storage : data.storage ,
							ratio : data.storage * 100 / data.size,
							format : data.format,
							contentType : data.contentType
						};
						//console.log("file - " + file.fileName);
						$scope.files.unshift(file);
					} else if (target == "rawCount") {
						$scope.rawCount += data;
					} else if (target == "pubGeoCount") { 
						$scope.pubGeoCount += data;
					} else if (target == "mongoCount") { 
						$scope.mongoCount += data;
					} else if (target == "geoImpsCounts") { 
						
						//[{"key":"FL","count":14},{"key":"CO","count":17},{"key":"HI","count":16},{"key":"CA","count":18},{"key":"NY","count":15}]
						for (var i = 0; i < data.length; i++) {
							var pair = data[i];
							updateGeoChart(pair.key, pair.count);
						}
						
					} else if (target == "geoAvgBids") {
						//  {"ts":1427787041808,"data":[{"key":"FL","count":9},{"key":"CO","count":11},{"key":"HI","count":12},{"key":"CA","count":13},{"key":"NY","count":8}]}
						var ts = data.ts;
						var counts = [ 0, 0, 0, 0, 0 ];
						for (var j = 0; j < data.data.length; j++) {
							var pr = data.data[j];
							if ( pr.key == "CO") { counts[0] = pr.count; }
							if ( pr.key == "CA") { counts[1] = pr.count; }
							if ( pr.key == "FL") { counts[2] = pr.count; }
							if ( pr.key == "HI") { counts[3] = pr.count; }
							if ( pr.key == "NY") { counts[4] = pr.count; }
						}
						updateAreaChart( ts, counts);
					}
					else { 
						console.log("Unhandled - target = " + target + ", data = " + data);
					}
				});
			};

			// sse socket
			$scope.startSocket = function(uuid) {
				$scope.stopSocket();
				$scope.images = [];
				var url = "/sse/" + uuid;
				console.log(url);
				$scope.socket = new EventSource(url);
				$scope.socket.addEventListener("message",
						$scope.handleServerEvent, false);
			};

			$scope.stopSocket = function() {
				if (typeof $scope.socket != 'undefined') {
					$scope.socket.close();
				}
			};

		});
