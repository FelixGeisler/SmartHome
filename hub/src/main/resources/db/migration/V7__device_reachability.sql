-- Track each device's reachability so the dashboard can show what is actually online: a reachable
-- flag (existing rows default to reachable) and when the hub last heard from the device.

alter table devices add column reachable boolean default true not null;
alter table devices add column last_seen_at timestamp(6) with time zone;
