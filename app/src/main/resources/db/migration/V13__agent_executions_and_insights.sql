create table agent_executions (
  execution_id uuid primary key,
  objective_id varchar(120) not null references marketing_objectives(objective_id),
  agent_type varchar(80) not null,
  model varchar(120) not null,
  model_version varchar(120) not null,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  status varchar(30) not null,
  input_token_count integer,
  output_token_count integer,
  estimated_cost numeric,
  latency_milliseconds bigint not null check (latency_milliseconds >= 0),
  output_snapshot jsonb,
  errors jsonb not null,
  correlation_id varchar(255) not null
);

alter table agent_tool_calls
  add constraint agent_tool_calls_execution_fk
  foreign key (execution_id) references agent_executions(execution_id);

create table marketing_insights (
  insight_id uuid primary key,
  objective_id varchar(120) not null references marketing_objectives(objective_id),
  subject varchar(255) not null,
  finding text not null,
  metrics jsonb not null,
  evidence_refs jsonb not null,
  correlation_id varchar(255) not null,
  created_at timestamptz not null
);

create index agent_executions_objective_idx on agent_executions(objective_id);
create index marketing_insights_objective_idx on marketing_insights(objective_id);
