var map;
var markers = [];

function map_init(lat, lng) {
    var myOptions = {
		zoom: 12,
		center: new google.maps.LatLng(lat,lng),
		mapTypeId: google.maps.MapTypeId.ROADMAP
    };
    map = new google.maps.Map(document.getElementById("map_canvas"), myOptions);

    // load data
    $.ajax("/api/geos", {
	//	data: {lat: lat, lng: lng},
	success:
	function(response) {
	    displayIncidents(map, response);
	},
	error: 
	function(XHR, textStatus, errorThrown) {alert("error: " + textStatus + "; " + errorThrown);}
    });

}

// left out of maps api
function clearMarkers() {
    for(var i=0; i < markers.length; i++){
        markers[i].setMap(null);
    }
    markers = new Array();
};


// really should take other params, prob in a map
function map_update(start, end) {
    // load data
    $.ajax("/api/geos", {
	data: {min: start, max: end},
	success:
	function(response) {
	    displayIncidents(map, response);
	},
	error: 
	function(XHR, textStatus, errorThrown) {alert("error: " + textStatus + "; " + errorThrown);}
    });
}


function displayIncidents(map, incidents) {
    clearMarkers();
    var z = 1000;
    var w = new google.maps.InfoWindow();
    for (idx in incidents) {
	var incident = incidents[idx];
	if ('geo' in incident && null !== incident.geo) {
	    var loc = incident.geo;
	    var marker = new google.maps.Marker({
		position: new google.maps.LatLng(loc.lat,loc.lng),
		map: map,
		zIndex: z--,
		title: incident.name});
	    markers.push(marker);
	    prepMarker(marker, incident, map, w);
	}
    }
}

function incidentTimeString(incident) {
    return new Date(incident.time).toLocaleString();
}

function formatIncidentList(incidents){
    var acc = "";
    $('.gm-style').removeClass('gm-style');
    for (idx in incidents) {
	var incident = incidents[idx];
	acc = acc  + "<div class='incident'><span class='incidenttype'>" + incident.type + '</span> ' 
	    + "<span class='datestamp'>" + incidentTimeString(incident) + '</span><br/>' 
	    + "<span class='description'>" + incident.description + "</span></div>";
    }
    return acc;
}

function showIncidentDetails(response, map, marker, w) {
    w.setContent(formatIncidentList(response));
    w.open(map, marker);
};

function prepMarker(marker, incident, map, w) {
    google.maps.event.addListener(marker, 'click', 
				  function() {
				      $.ajax("/api",
					     { data: {lat: incident.geo.lat, lng: incident.geo.lng},
					       success:
					       function(response) {
						   showIncidentDetails(response, map, marker, w);
					       },
					       error: 
					       function(XHR, textStatus, errorThrown) {
						   alert("error: " + textStatus + "; " + errorThrown);}
					     });
				  });};

function prepare_form() {
    $("#update").click(function(event) {

	var start = $('#start_datepicker').datepicker("getDate").getTime();
	var end = $('#end_datepicker').datepicker("getDate").getTime();

	map_update(start, end);
	return false;
    });
}

$(document).ready(function() {
    prepare_form();
    map_init(37.621592, -122.4885218)
    ;});

