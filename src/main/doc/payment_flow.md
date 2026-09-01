POST /api/payments
        │
        ▼
PaymentService
        │
        ├── Create Payment PENDING
        │
        ▼
BakongService
        │
        ├── Generate valid KHQR
        ├── Get KHQR string
        ├── Get MD5
        └── Store expiry for YOUR application
        │
        ▼
Return QR to frontend
        │
        ▼
Customer scans with Bakong/bank app
        │
        ▼
Bakong Network
        │
        ▼
Your backend checks transaction by MD5
        │
        ▼
PAID
        │
        ▼
Booking → CONFIRMED