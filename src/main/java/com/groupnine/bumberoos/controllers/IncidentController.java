package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.services.IncidentService;
import org.springframework.web.bind.annotation.RestController;

// @RestController
public class IncidentController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService){
        this.incidentService = incidentService;
    }

    // ------------------ CONTROLLERS ------------------ //



}
