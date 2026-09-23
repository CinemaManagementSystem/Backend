package com.cinema.booking.exception;

import lombok.Getter;

@Getter
public class SeatUnavailableException extends RuntimeException {

    private final Long showId;
    private final Long seatId;

    public SeatUnavailableException(Long showId, Long seatId) {
        super("This seat was just taken for the selected show. Please choose another seat.");
        this.showId = showId;
        this.seatId = seatId;
    }
}
