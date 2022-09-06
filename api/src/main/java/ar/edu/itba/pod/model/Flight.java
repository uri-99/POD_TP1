package ar.edu.itba.pod.model;

import java.util.Map;
import java.util.Set;

public class Flight {
    private final String code;
    private FlightState state = FlightState.PENDING;
    private final Plane plane;
    private final String destination;
    private final Map<String, Set<String>> ticketMap;

    public Flight(Plane plane, String code, String destination, Map<String, Set<String>> ticketMap) {
        this.plane = plane;
        this.code = code;
        this.destination = destination;
        this.ticketMap = ticketMap;
    }

    public Plane getPlane() {
        return plane;
    }

    public String getDestination() {
        return destination;
    }

    public Map<String, Set<String>> getTicketMap() {
        return ticketMap;
    }

    public String getCode() {
        return code;
    }

    public FlightState getState() {
        return state;
    }

    public void setState(FlightState state) {
        this.state = state;
    }

}
