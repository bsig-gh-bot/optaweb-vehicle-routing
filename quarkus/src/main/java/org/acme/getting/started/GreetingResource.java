package org.acme.getting.started;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.optaweb.vehiclerouting.domain.Coordinates;
import org.optaweb.vehiclerouting.domain.Location;
import org.optaweb.vehiclerouting.service.error.ErrorEvent;
import org.optaweb.vehiclerouting.service.location.LocationService;
import org.optaweb.vehiclerouting.service.vehicle.VehicleService;

@Path("/hello")
@ApplicationScoped
public class GreetingResource {

    @Inject
    Event<ErrorEvent> errorEventEvent;
    @Inject
    VehicleService vehicleService;
    @Inject
    LocationService locationService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        errorEventEvent.fire(new ErrorEvent(this, "Hello RESTEasy"));
        vehicleService.createVehicle();
        locationService.addLocation(new Location(1, Coordinates.valueOf(1, 2)));
        return "Hello RESTEasy";
    }
}
