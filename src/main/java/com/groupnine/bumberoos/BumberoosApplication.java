package com.groupnine.bumberoos;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.services.StaffService;

import java.util.ArrayList;
import java.util.List;


@SpringBootApplication
public class BumberoosApplication {

	public static void main(String[] args) {
		SpringApplication.run(BumberoosApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(StaffService staffService) {
		return args -> {
			System.out.println("===== StaffService test start =====");

			// ADD
			StaffEntity staff = new StaffEntity(
					false, "test person", "driver", "09171234567",
					"paranaque city", true,
					new ArrayList<>(List.of("First Aid")),
					new ArrayList<>(List.of("Professional License"))
			);
			StaffEntity added = staffService.addStaff(staff);
			int id = added.getId();
			System.out.println("Added: " + added.getName() + " (id " + id + ")");

			// FIND
			System.out.println("Found: " + staffService.findById(id).getName());
			System.out.println("Find unknown id (expect null): " + staffService.findById(-999));

			// EDIT (null = keep current, -1 = keep availability)
			StaffEntity edited = staffService.editStaff(id, "updated person", null, null, null, null, null, null, 1);
			System.out.println("Edited name: " + edited.getName()
					+ ", role kept: " + edited.getRole()
					+ ", availability: " + edited.getStaffAvailabilityAsString());

			// GET ALL
			System.out.println("Total staff: " + staffService.getAllStaff().size());

			// REMOVE
			System.out.println("Removed (expect true): " + staffService.removeStaff(id));
			System.out.println("Removed again (expect false): " + staffService.removeStaff(id));

			// NEXT ID
			System.out.println("Next ID: " + staffService.getNextId());

			System.out.println("===== StaffService test end =====");
		};
	}

}
