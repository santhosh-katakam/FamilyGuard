package com.familyguard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
@Validated
class DeviceController {
  final EventService events;

  DeviceController(EventService e) {
    events = e;
  }

  record Batch(@NotNull @Size(max = 200) @Valid List<EventService.Input> events) {}

  @PostMapping("/sync")
  Map<String, Object> sync(Authentication auth, @Valid @RequestBody Batch batch) {
    return events.ingest(UUID.fromString(auth.getName()), batch.events());
  }

  @GetMapping("/preferences")
  Map<String, Object> preferences() {
    return events.prefs();
  }
}
