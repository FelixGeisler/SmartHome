-- Persisted hub settings: a small key/value table that remembers runtime connection settings (the
-- MQTT broker, the Hue bridge credentials, the assistant API key) so they survive a restart and are
-- restored on the next boot instead of being configured again by hand. The value column is wide
-- enough to hold an API key.

create table hub_setting (
    setting_key   varchar(255)  not null primary key,
    setting_value varchar(4096) not null
);
