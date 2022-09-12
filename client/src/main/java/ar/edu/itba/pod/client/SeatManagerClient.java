package ar.edu.itba.pod.client;


import ar.edu.itba.pod.client.parsers.SeatManagerParser;
import ar.edu.itba.pod.interfaces.SeatManagerService;
import ar.edu.itba.pod.models.AlternativeFlightResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;

public class SeatManagerClient {
    private static final Logger logger = LoggerFactory.getLogger(SeatManagerClient.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        SeatManagerParser parser = new SeatManagerParser();
        parser.parse();

        logger.info("Flight Notifications Client Starting ...");

        //TODO: loockup con parser.getServerAddress.get()
        SeatManagerService service = (SeatManagerService) Naming.lookup("//127.0.0.1:1099/" + "seatManagerService");

        switch (parser.getAction().get()) {
            case STATUS:
                System.out.println("Is available: " + service.isAvailable(parser.getFlightCode(), parser.getRow().orElseThrow(RuntimeException::new), parser.getColumn().orElseThrow(RuntimeException::new)));
                break;
            case ASSIGN:
                service.assign(parser.getFlightCode(), parser.getPassenger().orElseThrow(RuntimeException::new), parser.getRow().orElseThrow(RuntimeException::new), parser.getColumn().orElseThrow(RuntimeException::new));
                break;
            case ALTERNATIVES:
                List<AlternativeFlightResponse> res = service.listAlternativeFlights(parser.getFlightCode(), parser.getPassenger().orElseThrow(RuntimeException::new));
                printAlternatives(res);
                break;
            case MOVE:
                service.changeSeat(parser.getFlightCode(), parser.getPassenger().orElseThrow(RuntimeException::new), parser.getRow().orElseThrow(RuntimeException::new), parser.getColumn().orElseThrow(RuntimeException::new));
                break;
            case CHANGE_TICKET:
                service.changeFlight(parser.getPassenger().orElseThrow(RuntimeException::new), parser.getOriginalFlightCode().orElseThrow(RuntimeException::new), parser.getFlightCode());
                break;
            default:
                throw new IllegalArgumentException("Invalid operation");
        }
    }

    public static void printAlternatives(List<AlternativeFlightResponse> alternatives) {
        for (AlternativeFlightResponse flight : alternatives) {
            flight.getAvailableSeats().forEach(((category, available) -> {
                System.out.printf("%s | %s | %d %s\n", flight.getDestination(), flight.getFlightCode(), available, (String) category.toString());
            }));
        }
    }
}
