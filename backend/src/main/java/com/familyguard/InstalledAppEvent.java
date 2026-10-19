package com.familyguard;

import jakarta.persistence.*;

@Entity
@Table(name = "installed_app_events")
class InstalledAppEvent extends EventBase {}
