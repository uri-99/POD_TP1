package ar.edu.itba.pod.models;

import java.io.Serializable;

public class ResponseRow implements Serializable {

    private final RowCategory rowCategory;
    private final char[] passengerInitials;

    public ResponseRow(RowCategory rowCategory, char[] passengerInitials) {
        this.rowCategory = rowCategory;
        this.passengerInitials = passengerInitials;
    }

    public RowCategory getRowCategory() {
        return rowCategory;
    }

    public char[] getPassengerInitials() {
        return passengerInitials;
    }
}
