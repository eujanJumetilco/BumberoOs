package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.services.StaffService;
import org.springframework.web.bind.annotation.RestController;


// @RestController
public class StaffController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final StaffService staffService;

    public StaffController(StaffService staffService){
        this.staffService = staffService;
    }

    // ------------------ CONTROLLERS ------------------ //


}
