alter table experiment_definitions
  add constraint experiment_definitions_lifecycle_status_check
  check (lifecycle_status in ('DRAFT', 'TESTED', 'APPROVED', 'DEPLOYED', 'RETIRED'));
