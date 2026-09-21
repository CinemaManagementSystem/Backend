-- Apply after Hibernate has created/updated the membership tables.
-- PostgreSQL partial indexes enforce the critical invariant without blocking
-- historical EXPIRED/CANCELLED memberships for the same user.

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_memberships_one_active
    ON user_memberships(customer_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS ux_membership_usage_source
    ON membership_usage(user_membership_id, benefit_type, usage_period, source_type, source_id);
