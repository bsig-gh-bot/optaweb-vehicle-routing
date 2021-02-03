/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaweb.vehiclerouting.service.location;

import static java.util.Comparator.comparingLong;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.optaweb.vehiclerouting.domain.Coordinates;
import org.optaweb.vehiclerouting.domain.Location;
import org.optaweb.vehiclerouting.service.error.ErrorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs location-related use cases.
 */
@ApplicationScoped
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository repository;
    private final LocationPlanner planner; // TODO move to RoutingPlanService (SRP)
    private final DistanceMatrix distanceMatrix;
    private final Event<ErrorEvent> errorEventEvent;

    @Inject
    LocationService(
            LocationRepository repository,
            LocationPlanner planner,
            DistanceMatrix distanceMatrix,
            Event<ErrorEvent> errorEventEvent) {
        this.repository = repository;
        this.planner = planner;
        this.distanceMatrix = distanceMatrix;
        this.errorEventEvent = errorEventEvent;
    }

    public synchronized boolean createLocation(Coordinates coordinates, String description) {
        Objects.requireNonNull(coordinates);
        Objects.requireNonNull(description);
        // TODO if (router.isLocationAvailable(coordinates))
        return submitToPlanner(repository.createLocation(coordinates, description));
    }

    public synchronized boolean addLocation(Location location) {
        return submitToPlanner(Objects.requireNonNull(location));
    }

    private boolean submitToPlanner(Location location) {
        try {
            DistanceMatrixRow distanceMatrixRow = distanceMatrix.addLocation(location);
            planner.addLocation(location, distanceMatrixRow);
        } catch (Exception e) {
            logger.error(
                    "Failed to calculate distances for location {}, it will be discarded",
                    location.fullDescription(), e);
            errorEventEvent.fire(new ErrorEvent(
                    this,
                    "Failed to calculate distances for location " + location.fullDescription()
                            + ", it will be discarded.\n" + e.toString()));
            repository.removeLocation(location.id());
            return false; // do not proceed to planner
        }
        return true;
    }

    public synchronized void removeLocation(long id) {
        Optional<Location> optionalLocation = repository.find(id);
        if (!optionalLocation.isPresent()) {
            errorEventEvent.fire(
                    new ErrorEvent(this, "Location [" + id + "] cannot be removed because it doesn't exist."));
            return;
        }
        Location removedLocation = optionalLocation.get();
        List<Location> locations = repository.locations();
        if (locations.size() > 1) {
            Location depot = locations.stream()
                    .min(comparingLong(Location::id))
                    .orElseThrow(() -> new IllegalStateException(
                            "Impossible. Locations have size (" + locations.size() + ") but the stream is empty."));
            if (removedLocation.equals(depot)) {
                errorEventEvent.fire(
                        new ErrorEvent(this, "You can only remove depot if there are no visits."));
                return;
            }
        }

        planner.removeLocation(removedLocation);
        repository.removeLocation(id);
        distanceMatrix.removeLocation(removedLocation);
    }

    public synchronized void removeAll() {
        planner.removeAllLocations();
        repository.removeAll();
        distanceMatrix.clear();
    }
}
