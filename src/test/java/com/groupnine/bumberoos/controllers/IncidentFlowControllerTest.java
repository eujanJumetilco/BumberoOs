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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end HTTP tests against the real stack on H2. Rolled back after each test. */
// These tests are about incident behaviour, so every request runs as a signed-in admin.
// Sign-in, sessions and roles are covered by SecurityIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
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

    // ===== arrival, commander and orders ===== //

    @Test
    void patch_recordsArrivalAndCommander_andOrdersAreListedInResponse() throws Exception {
        long id = createIncidentId();

        patchJson("/api/incidents/" + id, """
                {"arrivedAt":"2026-10-15T03:20:00","commanderName":"fire insp. ana reyes"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedAt", is("2026-10-15T03:20:00")))
                .andExpect(jsonPath("$.commanderName", is("Fire Insp. Ana Reyes")));

        postJson("/api/incidents/" + id + "/orders", """
                {"instruction":"Lay a supply line to the corner hydrant"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commanderOrders", hasSize(1)))
                .andExpect(jsonPath("$.commanderOrders[0].instruction", is("Lay a supply line to the corner hydrant")))
                .andExpect(jsonPath("$.commanderOrders[0].issuedAt", notNullValue()));

        mvc.perform(get("/api/incidents/" + id))
                .andExpect(jsonPath("$.commanderOrders", hasSize(1)))
                .andExpect(jsonPath("$.commanderName", is("Fire Insp. Ana Reyes")));
    }

    @Test
    void order_blankInstruction_is400_unknownIncident_is404() throws Exception {
        long id = createIncidentId();

        postJson("/api/incidents/" + id + "/orders", "{\"instruction\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("instruction")));
        postJson("/api/incidents/999999/orders", "{\"instruction\":\"x\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void order_onResolvedIncident_is409() throws Exception {
        long id = createIncidentId();
        postJson("/api/incidents/" + id + "/resolve", "{}").andExpect(status().isOk());

        postJson("/api/incidents/" + id + "/orders", "{\"instruction\":\"x\"}")
                .andExpect(status().isConflict());
    }

    // ===== staff ID ===== //

    @Test
    void staffId_isAcceptedReturnedAndUnique() throws Exception {
        postJson("/api/staff", """
                {"name":"ana reyes","role":"driver","employed":true,"badgeNumber":"2516528"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.badgeNumber", is("2516528")));

        postJson("/api/staff", """
                {"name":"ben cruz","role":"driver","employed":true,"badgeNumber":"2516528"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("2516528")));

        postJson("/api/staff", """
                {"name":"ben cruz","role":"driver","employed":true,"badgeNumber":"12"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignedStaff_showTheirStaffIdOnTheIncident() throws Exception {
        StaffEntity ana = staff("ana reyes", StaffAvailability.AVAILABLE);
        staffService.editStaff(ana.getId(), null, null, null, null, null, null, null, null, "2516528");
        long id = createIncidentId();

        mvc.perform(put("/api/incidents/" + id + "/staff/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff[0].badgeNumber", is("2516528")));
    }

    // ===== map position ===== //

    @Test
    void create_withCoordinates_returnsThemAndPatchMovesThePin() throws Exception {
        String body = postJson("/api/incidents", """
                {"nature":"EMERGENCY","incidentType":"fire","location":"zone 3","reportedBy":"jc",
                 "latitude":14.5995,"longitude":120.9842}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude", is(14.5995)))
                .andExpect(jsonPath("$.longitude", is(120.9842)))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        patchJson("/api/incidents/" + id, "{\"latitude\":14.6,\"longitude\":121.0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude", is(14.6)))
                .andExpect(jsonPath("$.longitude", is(121.0)));
    }

    @Test
    void incidentWithoutCoordinates_hasNullPosition() throws Exception {
        long id = createIncidentId();

        mvc.perform(get("/api/incidents/" + id))
                .andExpect(jsonPath("$.latitude", nullValue()))
                .andExpect(jsonPath("$.longitude", nullValue()));
    }

    @Test
    void coordinates_mustBeInRangeAndComeAsAPair() throws Exception {
        postJson("/api/incidents", """
                {"nature":"EMERGENCY","incidentType":"fire","location":"zone 3","reportedBy":"jc",
                 "latitude":95,"longitude":120}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("latitude")));

        postJson("/api/incidents", """
                {"nature":"EMERGENCY","incidentType":"fire","location":"zone 3","reportedBy":"jc",
                 "latitude":14.5}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("together")));

        long id = createIncidentId();
        patchJson("/api/incidents/" + id, "{\"longitude\":120.9}").andExpect(status().isBadRequest());
    }
}
