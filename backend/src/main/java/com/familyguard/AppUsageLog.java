package com.familyguard;

import jakarta.persistence.*;

@Entity
@Table(name = "app_usage_logs")
class AppUsageLog extends EventBase {}
