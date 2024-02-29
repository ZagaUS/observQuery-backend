package com.zaga.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.entity.queryentity.events.EventsDTO;
import com.zaga.handler.EventQueryhandler;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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
            @QueryParam("minutesAgo") int minutesAgo) {
        try {
            List<EventsDTO> allEvents = handler.getAllEvent();

            if (minutesAgo > 0) {
                Instant currentInstant = Instant.now();
                Instant fromInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

                allEvents = filterEventsByMinutesAgo(allEvents, fromInstant, currentInstant);
            }
            allEvents.sort(Comparator.comparing(EventsDTO::getCreatedTime).reversed());


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
            if (eventInstant.isAfter(fromInstant) && eventInstant.isBefore(currentInstant)) {
                matchingEvents.add(event);
            }
        }
    
        ObjectMapper mapper = new ObjectMapper();
        String json;
        try {
            json = mapper.writeValueAsString(matchingEvents);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing JSON").build();
        }
    
     return Response.ok(json).build();
    }
    
    





    //aggregation api
    
    @GET
    @Path("/getall-Events-aggregation")
    public Response getAllEventsDataByDateAndTime(
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo,
            @QueryParam("nodeName") String nodeName,
            @QueryParam("clusterName") String clusterName
            ) {
        List<EventsDTO> eventsList = handler.getAllEventsByDateAndTime(from, to, minutesAgo, nodeName, clusterName);
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult;
        try {
            jsonResult = mapper.writeValueAsString(eventsList);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(jsonResult).build();
    }


    @GET
    @Path("/get-recent-events")
    public Response getRecentEvents(@QueryParam("minutesAgo") @DefaultValue("30") int minutesAgo) {
        List<EventsDTO> eventsList = handler.getRecentEvents(minutesAgo);
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult;
        try {
            jsonResult = mapper.writeValueAsString(eventsList);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(jsonResult).build();
    }


}