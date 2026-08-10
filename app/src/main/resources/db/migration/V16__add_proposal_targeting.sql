alter table experiment_proposals add column target_audience text;
alter table experiment_proposals add column targeting_signal varchar(120);

update experiment_proposals
set target_audience = 'Existing proposal audience',
    targeting_signal = 'unspecified'
where target_audience is null;

alter table experiment_proposals alter column target_audience set not null;
alter table experiment_proposals alter column targeting_signal set not null;
