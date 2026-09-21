User
│
▼
MembershipPlan
│
├── MembershipBenefit
│
▼
UserMembership
│
├── MembershipUsage
│
└── Payment
│
▼
PAID
│
▼
Membership ACTIVE
│
├── Ticket discount
├── F&B discount
├── Free tickets
├── Priority booking
└── Points / rewards


table
membership_plans
-------------------------
id UUID
name
code
description
price
duration_months
is_active
sort_order
created_at
updated_at

[//]: # (------type----------)
public enum MembershipBenefitType {
TICKET_DISCOUNT,
FOOD_DISCOUNT,
FREE_TICKET,
PRIORITY_BOOKING,
POINT_MULTIPLIER,
LOUNGE_ACCESS
}

[//]: # status()
public enum MembershipStatus {
PENDING_PAYMENT,
ACTIVE,
EXPIRED,
CANCELLED
}

[//]: # Membership purchase flow()
Membership Page
↓
Choose Membership
↓
POST /api/memberships/subscribe
↓
Create UserMembership
status = PENDING_PAYMENT
↓
Create Payment
status = PENDING
↓
Generate KHQR
↓
Customer pays
↓
Backend verifies Bakong payment
↓
Payment = PAID
↓
UserMembership = ACTIVE
↓
started_at = now()
expires_at = now() + plan duration

[//]: # (dashboard)
Membership        ← add
├── Plans
├── Members
├── Benefits
├── Transactions
└── Reports\

[//]: # (Plans)
Admin should be able to manage:

Plan name
Price
Duration
Benefits
Active/Inactive
Sort order

[//]: # (Security rules)

[//]: # ()
[//]: # (You should enforce these rules on the backend:)

USER
✓ View plans
✓ Subscribe
✓ View own membership
✓ Cancel own membership
✓ Use benefits

USER
✗ Change membership price
✗ Activate membership manually
✗ Change expiry
✗ Change benefits

ADMIN
✓ Manage plans
✓ Manage benefits
✓ View memberships
✓ Cancel/extend memberships


[//]: # (Build these first:)

1. MembershipPlan
2. MembershipBenefit
3. UserMembership
4. MembershipUsage

5. Admin CRUD plans
6. Public membership page
7. Subscribe membership
8. Bakong KHQR payment
9. Activate membership after PAID
10. Show "My Membership"

11. Apply ticket discount
12. Apply F&B discount

13. Expiration scheduler
14. Admin members table

Leave these for version 2:

Auto renewal
Membership upgrade/downgrade
Referral rewards
Birthday rewards
Points marketplace
Family membership
Membership QR card
Tier progression
Apple/Google Wallet card