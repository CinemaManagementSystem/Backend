                         CINEMA SYSTEM
                              │
       ┌──────────────────────┼──────────────────────┐
       │                      │                      │
     MOVIE                  THEATER               CUSTOMER
       │                      │                      │
       │                   SCREEN                    │
       │                      │                      │
       │                     SEAT                    │
       │                      │                      │
       └─────────────── SHOWTIME ────────────────────┘
                              │
                              ▼
                           BOOKING
                  ┌───────────┼────────────┐
                  │           │            │
            BOOKING_SEATS    ORDER       PAYMENT
                  │           │
                  │       ORDER_ITEMS
                  │           │
                  │        PRODUCT
                  │           │
                  │     ┌─────┼─────┐
                  │     │     │     │
                  │  POPCORN DRINK FOOD
                  │
                  ▼
                TICKET
                  │
                QR CODE