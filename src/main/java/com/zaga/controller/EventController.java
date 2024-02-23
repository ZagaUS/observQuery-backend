package com.zaga.controller;

import java.io.IOException;
import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.zaga.entity.queryentity.events.EventsDTO;
import com.zaga.handler.EventQueryhandler;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventController {

    @Inject
    EventQueryhandler handler;

    @GET
    @Path("/getAllEvents")
    public Response getAllEvents(
            // @QueryParam("from") LocalDate from,
            // @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo) {
        try {
            List<EventsDTO> allEvents = handler.getAllEvent();

            // if (from != null && to != null) {
            //     Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
            //     Instant toInstant = to.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(86399);                                                                   // day

            //     allEvents = filterEventsByDateRange(allEvents, fromInstant, toInstant);
            // }
              if (minutesAgo > 0) {
                Instant currentInstant = Instant.now();
                Instant fromInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

                allEvents = filterEventsByMinutesAgo(allEvents, fromInstant, currentInstant);
            }
            System.out.println("Number of data in the specified time range: " + allEvents.size());

            ObjectMapper objectMapper = new ObjectMapper();
            String responseJson = objectMapper.writeValueAsString(allEvents);
            return Response.ok(responseJson).build();

        } catch (Exception e) {
            e.printStackTrace();

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred: " + e.getMessage())
                    .build();
        }

    }

    // private List<EventsDTO> filterEventsByDateRange(List<EventsDTO> events, Instant from, Instant to) {
    //     return events.stream()
    //             .filter(event -> isWithinDateRange(event.getCreatedTime().toInstant(), from, to))
    //             .collect(Collectors.toList());
    // }

    private List<EventsDTO> filterEventsByMinutesAgo(List<EventsDTO> events, Instant fromInstant, Instant toInstant) {
        return events.stream()
                .filter(event -> isWithinDateRange(event.getCreatedTime().toInstant(), fromInstant, toInstant))
                .collect(Collectors.toList());
    }

    private boolean isWithinDateRange(Instant targetInstant, Instant from, Instant to) {
        return !targetInstant.isBefore(from) && !targetInstant.isAfter(to);
    }





    @GET
    @Path("/recentevent")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getFilteredEvents(@QueryParam("minutesAgo") int minutesAgo) {
        if (minutesAgo != 30) {
            throw new IllegalArgumentException("Only '30' minutes ago data is allowed");
        }
    
        Instant currentInstant = Instant.now();
        Instant fromInstant = currentInstant.minus(30, ChronoUnit.MINUTES);
    
        List<EventsDTO> events = handler.getAllEvent();
        List<EventsDTO> matchingEvents = new ArrayList<>();
    
        for (EventsDTO event : events) {
            Instant eventInstant = event.getCreatedTime().toInstant();
            // Check if the event occurred within the last 30 minutes
            if (eventInstant.isAfter(fromInstant) && eventInstant.isBefore(currentInstant)) {
                matchingEvents.add(event);
            }
        }
    
        // Convert matchingEvents to JSON
        ObjectMapper mapper = new ObjectMapper();
        String json;
        try {
            json = mapper.writeValueAsString(matchingEvents);
        } catch (JsonProcessingException e) {
            // Handle the exception appropriately
            e.printStackTrace();
            // Return an error response
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing JSON").build();
        }
    
        // Return the JSON response
        return Response.ok(json).build();
    }
    
    



}