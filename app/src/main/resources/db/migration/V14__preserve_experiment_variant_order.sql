alter table experiment_definition_variants
    add column variant_order integer;

with ordered_variants as (
    select experiment_id,
           variant_name,
           row_number() over (partition by experiment_id order by variant_name) - 1 as ordinal
    from experiment_definition_variants
)
update experiment_definition_variants variants
set variant_order = ordered_variants.ordinal
from ordered_variants
where variants.experiment_id = ordered_variants.experiment_id
  and variants.variant_name = ordered_variants.variant_name;

alter table experiment_definition_variants
    alter column variant_order set not null;

alter table experiment_definition_variants
    add constraint experiment_definition_variants_order_unique
    unique (experiment_id, variant_order);
