alter table martech_activation_attempts
  alter column proposal_id drop not null;

alter table martech_activation_attempts
  add column context_id varchar(255);

create index martech_activation_attempts_context_idx
  on martech_activation_attempts (context_id, attempted_at);
