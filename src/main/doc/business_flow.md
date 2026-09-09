                    CINEMA SYSTEM
                         │
        ┌────────────────┼────────────────┐
        │                │                │
      MOVIE           THEATER          CUSTOMER
        │                │                │
        │              SCREEN           WALLET
        │                │                │
        │              SEAT               │
        │                │                │
        └─────── SHOW ───┘                │
                 │                        │
                 ▼                        ▼
              BOOKING ◄───────────────────┘
        ┌────────┼────────┐
        │        │        │
      SEATS    ORDER   PAYMENT
                 │
           ┌─────┴─────┐
           │           │
       PRODUCTS    ORDER_ITEMS
           │
     ┌─────┼─────┐
     │     │     │
  POPCORN DRINK FOOD
        │
        ▼
     TICKET
        │
     QR CODE