-- Schedule triggers fire at a time of day rather than off a device reading, so a trigger no longer
-- requires a device, and gains a time and the weekdays it runs on (on_days empty means every day).

alter table automation_triggers alter column device_id set null;
alter table automation_triggers add column at_time time;
alter table automation_triggers add column on_days varchar(255);
