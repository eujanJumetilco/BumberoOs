package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import org.springframework.web.bind.annotation.RestController;


// @RestController
public class StaffController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final StaffEntity staffEntity;

    public StaffController(StaffEntity staffEntity){
        this.staffEntity = staffEntity;
    }

    // ------------------ CONTROLLERS ------------------ //


}
