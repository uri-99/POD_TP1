package ar.edu.itba.pod.server.service;

import ar.edu.itba.pod.callbacks.NotificationHandler;
import ar.edu.itba.pod.interfaces.SeatManagerService;
import ar.edu.itba.pod.models.*;
import ar.edu.itba.pod.models.AlternativeFlightResponse;
import ar.edu.itba.pod.models.exceptions.flightExceptions.IllegalFlightStateException;
import ar.edu.itba.pod.models.exceptions.notFoundExceptions.FlightNotFoundException;
import ar.edu.itba.pod.models.exceptions.seatExceptions.NoAvailableSeatsException;
import ar.edu.itba.pod.server.utils.ServerStore;
import ar.edu.itba.pod.server.models.Flight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class SeatManagerServiceImpl implements SeatManagerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeatManagerServiceImpl.class);
    private final ServerStore store;

    public SeatManagerServiceImpl(ServerStore store) {
        this.store = store;
    }

    private Flight getPendingFlight(String flightCode) {
        Flight flight;
        synchronized (store.getPendingFlights()) {
            flight = Optional.ofNullable(store.getPendingFlights().get(flightCode))
                    .orElseThrow(IllegalFlightStateException::new);
            flight.getStateLock().lock();
        }

        flight.getSeatsLock().lock();
        return flight;
    }

    private Flight getNonConfirmedFlight(String flightCode) {
        Flight flight;
        synchronized (store.getFlightCodes()) {
            FlightState state = Optional.ofNullable(store.getFlightCodes().get(flightCode))
                    .orElseThrow(FlightNotFoundException::new);
            if (state.equals(FlightState.CONFIRMED))
                throw new IllegalFlightStateException();

            Map<String, Flight> flights = store.getFlightsByState(state);
            synchronized (flights) {
                flight = flights.get(flightCode);
                flight.getStateLock().lock();
                flight.getSeatsLock().lock(); // To avoid concurrent modifications when reticketing
            }
        }
        return flight;
    }

    @Override
    public boolean isAvailable(String flightCode, int row, char seat) throws RemoteException {
        Flight flight = getPendingFlight(flightCode);
        try {
            return flight.checkSeat(row, seat);
        } finally {
            flight.getSeatsLock().unlock();
            flight.getStateLock().unlock();
        }
    }

    @Override
    public void assign(String flightCode, String passenger, int row, char seat) throws RemoteException {
        Flight flight = getPendingFlight(flightCode);
        try {
            flight.assignSeat(row, seat, passenger);
        } finally {
            flight.getSeatsLock().unlock();
            flight.getStateLock().unlock();
        }

        LOGGER.info("Assigned seat " + row + seat + " to passenger " + passenger + " on flight " +
                flightCode);

        syncNotify(flightCode, passenger, handler ->
                store.submitNotificationTask(() -> {
                    try {
                        handler.notifyAssignSeat(new Notification(flightCode,
                                flight.getDestination(), flight.getRows()[row].getRowCategory(),
                                row, seat));
                    } catch (RemoteException e) {
                        LOGGER.error("Error notifying seat assigned", e);
                    }
                }));
    }

    @Override
    public void changeSeat(String flightCode, String passenger, int freeRow, char freeSeat) throws RemoteException {
        Flight flight = getPendingFlight(flightCode);
        Integer row;
        Character col;
        try {
            Ticket ticket = flight.getTicket(passenger);
            row = ticket.getRow();
            col = ticket.getCol();
            flight.changeSeat(freeRow, freeSeat, passenger);
        } finally {
            flight.getSeatsLock().unlock();
            flight.getStateLock().unlock();
        }

        LOGGER.info("Changed " + passenger + " seat from " + row + col + " to " + freeRow +
                freeSeat + " on flight " + flightCode);

        syncNotify(flightCode, passenger, handler -> {
            try {
                RowCategory category = null;
                if (row != null)
                    category = flight.getRows()[row].getRowCategory();

                handler.notifyChangeSeat(new Notification(flightCode, flight.getDestination(),
                        category, row, col,
                        flight.getRows()[freeRow].getRowCategory(),
                        freeRow, freeSeat));
            } catch (RemoteException e) {
                LOGGER.error("Error notifying seat changed", e);
            }
        });
    }

    @Override
    public List<AlternativeFlightResponse> listAlternativeFlights(String flightCode, String passenger) throws RemoteException {
        Flight flight = getNonConfirmedFlight(flightCode);

        String destination;
        RowCategory category;

        category = flight.getTicket(passenger).getCategory();
        flight.getSeatsLock().unlock();
        flight.getStateLock().unlock();

        destination = flight.getDestination();

        List<Flight> alternativeFlights;

        synchronized (store.getPendingFlights()) {
            alternativeFlights = store.getPendingFlights().values().stream()
                    .filter(f -> f.getDestination().equals(destination)).collect(Collectors.toList());
        }

        List<AlternativeFlightResponse> toReturn = new ArrayList<>();

        alternativeFlights.forEach(alternative -> {
            alternative.getSeatsLock().lock();
            Map<RowCategory, Integer> availableSeats = new HashMap<>();
            for (int i = category.ordinal(); i >= 0; i--) {
                int available = alternative.getAvailableByCategory(RowCategory.values()[i]);
                if (i > 0) {
                    availableSeats.put(RowCategory.values()[i], available);
                }
            }
            alternative.getSeatsLock().unlock();

            if (availableSeats.keySet().size() > 0)
                toReturn.add(new AlternativeFlightResponse(alternative.getCode(), destination, availableSeats));
        });

        return toReturn;
    }

    @Override
    public void changeFlight(String passenger, String oldFlightCode, String newFlightCode) throws RemoteException {
        Flight oldFlight = getNonConfirmedFlight(oldFlightCode);
        Flight newFlight = null;

        try {
            newFlight = getPendingFlight(newFlightCode);
            if (newFlight.getAllAvailableByCategory(oldFlight.getTicket(passenger).getCategory()) == 0)
                throw new NoAvailableSeatsException();
            oldFlight.changeFlight(passenger, newFlight);
        } finally {
            if (newFlight != null) {
                newFlight.getSeatsLock().unlock();
                newFlight.getStateLock().unlock();
            }
            oldFlight.getSeatsLock().unlock();
            oldFlight.getStateLock().unlock();
        }

        Notification notification = new Notification(oldFlightCode, oldFlight.getDestination(),
                newFlightCode);

        store.changeTicketsNotification(passenger, notification);
    }

    private void syncNotify(String flightCode, String passenger, Consumer<NotificationHandler> handlerConsumer) {
        List<NotificationHandler> handlers = store.getHandlers(flightCode, passenger);

        synchronized (handlers) {
            for (NotificationHandler handler : handlers) {
                store.submitNotificationTask(() -> handlerConsumer.accept(handler));
            }
        }
    }
}
