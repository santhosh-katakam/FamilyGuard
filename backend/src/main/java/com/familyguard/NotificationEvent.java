package com.familyguard;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_events")
class NotificationEvent extends EventBase {}
