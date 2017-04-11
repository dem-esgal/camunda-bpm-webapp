  'use strict';
  var uploadTemplate = require('../plugins/actions/deploy/deploy-new-war-dialog');

  module.exports = [
    '$scope',
    'Views',
	  '$modal',
	  '$q',
	  function(
    $scope,
    Views,
	$modal,
	$q
  ) {

      $scope.deploymentsData = $scope.repositoryData.newChild($scope);
      $scope.deploymentsVars = { read: [ 'deploymentsData' ] };
      $scope.deploymentsPlugins = Views.getProviders({ component: 'cockpit.repository.deployments.list' });
      $scope.uploadWar = function() {
			  var promise = $q.defer();
		  $modal.open({
			  resolve: {
				  basePath: function() { return ""; }
			  },
			  controller: uploadTemplate.controller,
			  template: uploadTemplate.template
		  })
				  .result.then(function() {
				  // updated the variable, need to get the new data
				  // reject the promise anyway
				  promise.reject();
			  }, function() {
				  // did not update the variable, reject the promise
				  promise.reject();
			  });

			  return promise.promise;
		  };

    }];
