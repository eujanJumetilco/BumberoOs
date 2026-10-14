package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.services.InventoryService;
import com.groupnine.bumberoos.services.StaffService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end HTTP tests against the real stack on H2. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentFlowControllerTest {

    @Autowired MockMvc mvc;
    @Autowired StaffService staffService;
    @Autowired InventoryService inventoryService;

    private StaffEntity staff(String name, StaffAvailability availability) {
        StaffEntity s = new StaffEntity(name, "volunteer", null, null, true, new ArrayList<>(), new ArrayList<>());
        s.setStaffAvailability(availability);
        return staffService.addStaff(s);
    }

    private ItemEntity item(String name, int quantity, ItemCategory category) throws Exception {
        return inventoryService.addItem(new ItemEntity(ItemCondition.GOOD, name, null, quantity, category, null));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchJson(String url, String json) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createIncidentId() throws Exception {
        String body = postJson("/api/incidents", """
                {"nature":"EMERGENCY","incidentType":"fire","location":"zone 3","reportedBy":"jc"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    // ===== create ===== //

    @Test
    void create_missingNature_is400WithErrorBody() throws Exception {
        postJson("/api/incidents", """
                {"incidentType":"fire","location":"zone 3","reportedBy":"jc"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("nature")));
    }

    @Test
    void create_withStaffAndItems_returnsActiveAssignments() throws Exception {
        StaffEntity ana = staff("ana reyes", StaffAvailability.AVAILABLE);
        ItemEntity engine = item("engine 1", 1, ItemCategory.ENGINE);
        ItemEntity suits = item("fire suit", 10, ItemCategory.FIRE_SUIT);

        postJson("/api/incidents", """
                {"nature":"EMERGENCY","alarmLevel":"FIRST_ALARM","incidentType":"fire","location":"zone 3",
                 "reportedBy":"j. cruz","staffIds":[%d],
                 "items":[{"itemId":%d,"quantity":1},{"itemId":%d,"quantity":4}]}"""
                .formatted(ana.getId(), engine.getId(), suits.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.nature").value("EMERGENCY"))
                .andExpect(jsonPath("$.alarmLevel").value("FIRST_ALARM"))
                .andExpect(jsonPath("$.deployedStaffIds", contains(ana.getId().intValue())))
                .andExpect(jsonPath("$.staff[0].name").value("Ana Reyes"))
                .andExpect(jsonPath("$.staff[0].releasedAt").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.alarmHistory", hasSize(1)));

        mvc.perform(get("/api/staff/" + ana.getId()))
                .andExpect(jsonPath("$.availability").value("DISPATCHED"));
        mvc.perform(get("/api/items/" + suits.getId()))
                .andExpect(jsonPath("$.quantityDispatched").value(4))
                .andExpect(jsonPath("$.quantityAvailable").value(6))
                .andExpect(jsonPath("$.availability").value("PARTIALLY_DISPATCHED"));
    }

    // ===== assignment changes, resolve, reopen ===== //

    @Test
    void modifyAssignments_thenResolveAndReopen() throws Exception {
        long id = createIncidentId();
        StaffEntity ana = staff("ana reyes", StaffAvailability.AVAILABLE);
        ItemEntity suits = item("fire suit", 10, ItemCategory.FIRE_SUIT);
        ItemEntity engine = item("engine 1", 1, ItemCategory.ENGINE);

        mvc.perform(put("/api/incidents/" + id + "/staff/" + ana.getId() + "?role=Driver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff[0].role").value("Driver"));
        mvc.perform(put("/api/incidents/" + id + "/items/" + suits.getId() + "?quantity=2"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(put("/api/incidents/" + id + "/items/" + suits.getId() + "?quantity=5"))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        mvc.perform(put("/api/incidents/" + id + "/items/" + engine.getId() + "?quantity=1"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/incidents/" + id + "/staff/" + ana.getId()))
                .andExpect(jsonPath("$.deployedStaffIds", hasSize(0)))
                .andExpect(jsonPath("$.staff", hasSize(1)))
                .andExpect(jsonPath("$.staff[0].releasedAt").value(notNullValue()));
        mvc.perform(get("/api/staff?availability=AVAILABLE"))
                .andExpect(jsonPath("$[*].id", hasItem(ana.getId().intValue())));

        postJson("/api/incidents/" + id + "/resolve", """
                {"itemReturns":[{"itemId":%d,"condition":"DAMAGED"}]}""".formatted(engine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value(notNullValue()));
        mvc.perform(get("/api/items/" + suits.getId()))
                .andExpect(jsonPath("$.quantityDispatched").value(0));
        mvc.perform(get("/api/items/" + engine.getId()))
                .andExpect(jsonPath("$.condition").value("DAMAGED"))
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"));
        mvc.perform(get("/api/items?availableOnly=true"))
                .andExpect(jsonPath("$[*].id", hasItem(suits.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(engine.getId().intValue()))));

        mvc.perform(post("/api/incidents/" + id + "/resolve")) // no body: idempotent
                .andExpect(status().isOk());
        mvc.perform(post("/api/incidents/" + id + "/reopen"))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.deployedStaffIds", hasSize(0)));
    }

    @Test
    void resolve_withoutBodyIsAccepted() throws Exception {
        long id = createIncidentId();

        mvc.perform(post("/api/incidents/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    // ===== patch / filters ===== //

    @Test
    void patch_alarmEscalationBuildsHistory_andNonEmergencyAlarmIs400() throws Exception {
        long id = createIncidentId();

        patchJson("/api/incidents/" + id, "{\"alarmLevel\":\"SECOND_ALARM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmLevel").value("SECOND_ALARM"))
                .andExpect(jsonPath("$.alarmHistory", hasSize(1)));

        patchJson("/api/incidents/" + id, "{\"nature\":\"NON_EMERGENCY\"}")
                .andExpect(jsonPath("$.nature").value("NON_EMERGENCY"))
                .andExpect(jsonPath("$.alarmLevel").value(nullValue()));

        patchJson("/api/incidents/" + id, "{\"alarmLevel\":\"THIRD_ALARM\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("EMERGENCY")));
    }

    @Test
    void list_filtersByStatusAndNature() throws Exception {
        long emergency = createIncidentId();
        postJson("/api/incidents", """
                {"nature":"NON_EMERGENCY","incidentType":"standby","location":"plaza","reportedBy":"jc"}""")
                .andExpect(status().isCreated());
        postJson("/api/incidents/" + emergency + "/resolve", "{}").andExpect(status().isOk());

        mvc.perform(get("/api/incidents?status=RESOLVED&nature=EMERGENCY"))
                .andExpect(jsonPath("$[*].id", hasItem((int) emergency)));
        mvc.perform(get("/api/incidents?status=ONGOING&nature=EMERGENCY"))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) emergency))));
        mvc.perform(get("/api/incidents?nature=NON_EMERGENCY"))
                .andExpect(jsonPath("$[*].nature", everyItem(is("NON_EMERGENCY"))));
    }

    @Test
    void delete_ongoingIncidentFreesStaff() throws Exception {
        StaffEntity ana = staff("ana reyes", StaffAvailability.AVAILABLE);
        postJson("/api/incidents", """
                {"nature":"EMERGENCY","incidentType":"fire","location":"z","reportedBy":"jc","staffIds":[%d]}"""
                .formatted(ana.getId()))
                .andExpect(status().isCreated());
        long id = createIncidentId();

        mvc.perform(delete("/api/incidents/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/incidents/" + id)).andExpect(status().isNotFound());
    }

    // ===== error shapes ===== //

    @Test
    void notFound_usesErrorBodyEverywhere() throws Exception {
        mvc.perform(get("/api/incidents/-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("incident -1 not found"));
        mvc.perform(delete("/api/incidents/-1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("staff -1 not found"));
        mvc.perform(post("/api/incidents/-1/resolve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("incident -1 not found"));
        mvc.perform(put("/api/incidents/-1/staff/-2")).andExpect(status().isNotFound());
    }

    @Test
    void bad_quantity_is400() throws Exception {
        long id = createIncidentId();
        ItemEntity suits = item("fire suit", 10, ItemCategory.FIRE_SUIT);

        mvc.perform(put("/api/incidents/" + id + "/items/" + suits.getId() + "?quantity=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void conflicts_are409WithSpecificMessages() throws Exception {
        long id = createIncidentId();
        StaffEntity busy = staff("busy ben", StaffAvailability.DISPATCHED);
        ItemEntity suits = item("fire suit", 2, ItemCategory.FIRE_SUIT);

        mvc.perform(put("/api/incidents/" + id + "/staff/" + busy.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Busy Ben is DISPATCHED"));
        mvc.perform(put("/api/incidents/" + id + "/items/" + suits.getId() + "?quantity=3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("only 2")));
    }
}
