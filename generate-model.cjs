const fs=require('fs'),path=require('path');
const root=__dirname;function write(p,s){p=path.join(root,p);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,s)}
const events={PermissionStatus:'permission_status',InstalledAppEvent:'installed_app_events',AppUsageLog:'app_usage_logs',NotificationEvent:'notification_events',CallEvent:'call_events',LocationHistory:'location_history',DeviceStatus:'device_status'};
for(const [name,table] of Object.entries(events)){
 write(`backend/src/main/java/com/familyguard/${name}.java`,`package com.familyguard;\nimport jakarta.persistence.*;\n@Entity @Table(name="${table}")\nclass ${name} extends EventBase {}\n`);
 write(`backend/src/main/java/com/familyguard/${name}Repository.java`,`package com.familyguard;\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.util.*;\nimport java.time.Instant;\ninterface ${name}Repository extends JpaRepository<${name},UUID> { List<${name}> findTop10000ByOrderByOccurredAtDesc(); void deleteByOccurredAtBefore(Instant before); }\n`);
}
let sql=`CREATE TABLE admin_users (id integer PRIMARY KEY CHECK(id=1), email varchar(255) NOT NULL UNIQUE, password_hash varchar(255) NOT NULL, refresh_hash varchar(255), refresh_expires timestamp with time zone, session_version bigint NOT NULL DEFAULT 0, enabled boolean NOT NULL DEFAULT true);
CREATE TABLE child_devices (id uuid PRIMARY KEY, singleton boolean NOT NULL UNIQUE CHECK(singleton=true), name varchar(255) NOT NULL, token_hash varchar(255) NOT NULL UNIQUE, last_seen timestamp with time zone, battery integer CHECK(battery BETWEEN 0 AND 100), offline_alerted boolean NOT NULL DEFAULT false, monitoring boolean NOT NULL DEFAULT false, created_at timestamp with time zone);
CREATE TABLE device_pairings (id integer PRIMARY KEY CHECK(id=1), code_hash varchar(255), expires_at timestamp with time zone);
CREATE TABLE privacy_preferences (id integer PRIMARY KEY CHECK(id=1), value text NOT NULL);
CREATE TABLE security_audit_logs (id uuid PRIMARY KEY, occurred_at timestamp with time zone, action varchar(255) NOT NULL);
CREATE INDEX audit_time_idx ON security_audit_logs(occurred_at);
CREATE TABLE sync_queue (id uuid PRIMARY KEY, device_id uuid NOT NULL REFERENCES child_devices(id) ON DELETE CASCADE, received_at timestamp with time zone);
CREATE INDEX receipt_time_idx ON sync_queue(received_at);
CREATE TABLE alerts (id uuid PRIMARY KEY, device_id uuid REFERENCES child_devices(id) ON DELETE CASCADE, occurred_at timestamp with time zone, kind varchar(255), message varchar(255), read boolean NOT NULL DEFAULT false);
CREATE INDEX alerts_time_idx ON alerts(occurred_at DESC);
`;
for(const table of Object.values(events))sql+=`CREATE TABLE ${table} (id uuid PRIMARY KEY, device_id uuid NOT NULL REFERENCES child_devices(id) ON DELETE CASCADE, occurred_at timestamp with time zone NOT NULL, received_at timestamp with time zone NOT NULL, kind varchar(32) NOT NULL, package_name varchar(255), payload text NOT NULL);\nCREATE INDEX ${table}_time_idx ON ${table}(device_id,occurred_at DESC);\nCREATE INDEX ${table}_package_idx ON ${table}(package_name,occurred_at DESC);\n`;
write('backend/src/main/resources/db/migration/V1__foundation.sql',sql);write('database/V1__foundation.sql',sql);
