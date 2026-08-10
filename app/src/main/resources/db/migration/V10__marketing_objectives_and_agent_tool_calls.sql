create table marketing_objectives (
  objective_id varchar(120) primary key,
  name varchar(255) not null,
  description text not null,
  business_goal text not null,
  target_kpi varchar(120) not null,
  target_value numeric not null check (target_value >= 0),
  target_audience text not null,
  constraints jsonb not null,
  start_date date not null,
  end_date date not null,
  status varchar(20) not null check (status in ('DRAFT','ACTIVE','COMPLETED','ARCHIVED')),
  created_by varchar(255) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null default now(),
  check (end_date >= start_date)
);

create table marketing_objective_audit (
  audit_id bigserial primary key,
  objective_id varchar(120) not null references marketing_objectives(objective_id),
  action varchar(30) not null,
  actor varchar(255) not null,
  from_status varchar(20) not null,
  to_status varchar(20) not null,
  created_at timestamptz not null default now()
);

create table workflow_stage_timings (
  timing_id uuid primary key,
  objective_id varchar(120) not null references marketing_objectives(objective_id),
  stage varchar(50) not null,
  elapsed_milliseconds bigint not null check (elapsed_milliseconds >= 0),
  recorded_by varchar(255) not null,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (completed_at >= started_at)
);

create table agent_tool_calls (
  call_id uuid primary key,
  execution_id uuid,
  tool_name varchar(120) not null,
  arguments jsonb not null,
  result_reference varchar(255) not null,
  result_snapshot jsonb not null,
  status varchar(30) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  created_at timestamptz not null default now()
);

create index workflow_stage_timings_objective_idx on workflow_stage_timings(objective_id);
create index agent_tool_calls_execution_idx on agent_tool_calls(execution_id);
