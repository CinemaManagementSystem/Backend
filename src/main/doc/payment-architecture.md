                    PaymentController
                           │
                           ↓
                    PaymentService
                           │
                 paymentMethod?
                    /          \
                   /            \
                CASH             KHQR
                 │                 │
                 │                 ↓
                 │          BakongService
                 │                 │
                 │          Generate QR
                 │                 │
                 ↓                 ↓
             Payment           Payment
             PENDING            PENDING
                 │                 │
          Staff confirms      Customer pays
                 │                 │
                 └────────┬────────┘
                          ↓
                    Payment SUCCESS
                          ↓
                  Booking CONFIRMED

PaymentService
├── CASH
└── KHQR → BakongService

Payment
│
├── CASH
│    └── Staff confirms payment
│
└── KHQR
     ├── khqrString
     ├── md5Hash
     ├── expiresAt
     └── BakongService
           ├── generateQr()
           └── checkTransactionByMd5()