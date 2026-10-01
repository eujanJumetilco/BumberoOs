package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaffServiceTest {

    @Test
    void addStaff_assignsId() {
        StaffService service = new StaffService();
        StaffEntity staff = new StaffEntity(
                false, "test person", "driver", "09171234567",
                "paranaque city", true,
                new ArrayList<>(List.of("First Aid")),
                new ArrayList<>(List.of("Professional License"))
        );

        StaffEntity added = service.addStaff(staff);

        assertTrue(added.getId() > 0);
        assertEquals("Test Person", added.getName());

        service.removeStaff(added.getId()); // cleanup
    }

}
