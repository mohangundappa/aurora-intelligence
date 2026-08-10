alter table experiment_governance_audit
  add column actor_verification_status varchar(40)
    not null default 'SELF_DECLARED_UNVERIFIED';
