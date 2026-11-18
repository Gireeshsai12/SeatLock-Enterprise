package com.seatlock.events;

import com.seatlock.inventory.InventoryService;
import com.seatlock.shared.PageWindow;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService events;
    private final InventoryService inventory;

    public EventController(EventService events, InventoryService inventory) {
        this.events = events;
        this.inventory = inventory;
    }

    @GetMapping
    List<Map<String,Object>> list(@RequestParam(defaultValue="") String q,
            @RequestParam(required=false) Event.Category category, @RequestParam(defaultValue="upcoming") String sort,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size) {
        return events.list(q, category, sort, new PageWindow(page,size));
    }

    @GetMapping("/{id}")
    Event event(@PathVariable long id) { return events.get(id); }

    @GetMapping("/{id}/seats")
    List<Map<String,Object>> seats(@PathVariable long id) {
        events.get(id);
        return inventory.availability(id);
    }
}
