alter table model_candidates
  add column studio_client_id varchar(120);

update model_candidates
set studio_client_id = package->>'clientId'
where package ? 'clientId';

create or replace function prevent_model_candidate_package_mutation()
returns trigger
language plpgsql
as $$
begin
  if new.package is distinct from old.package
      or new.package_hash is distinct from old.package_hash
      or new.studio_client_id is distinct from old.studio_client_id then
    raise exception 'model candidate package and provenance are immutable';
  end if;
  return new;
end;
$$;

create trigger model_candidate_package_immutable
before update on model_candidates
for each row execute function prevent_model_candidate_package_mutation();
