package ar.edu.itba.pod.server.service;

import ar.edu.itba.pod.callbacks.NotificationHandler;
import ar.edu.itba.pod.interfaces.NotificationService;
import ar.edu.itba.pod.models.Flight;
import ar.edu.itba.pod.models.FlightState;
import ar.edu.itba.pod.server.ServerStore;

import java.rmi.RemoteException;
import java.util.Optional;

public class NotificationServiceImpl implements NotificationService {

    private final ServerStore store;

    public NotificationServiceImpl(ServerStore store) {
        this.store = store;
    }

    @Override
    public void registerPassenger(String flightCode, String passenger, NotificationHandler handler)
            throws RemoteException {
        store.getFlightsLock().lock();

        try {
            Flight flight = Optional.ofNullable(store.getFlights().get(flightCode))
                    .filter(f -> !f.getState().equals(FlightState.CONFIRMED))
                    .orElseThrow(IllegalArgumentException::new); // TODO: custom exception

            flight.getTickets().stream().filter(t -> t.getPassenger().equals(passenger))
                    .findFirst().orElseThrow(IllegalArgumentException::new); // TODO: custom exception
        } finally {
            store.getFlightsLock().unlock();
        }

        store.registerUser(flightCode, passenger, handler);
    }
}