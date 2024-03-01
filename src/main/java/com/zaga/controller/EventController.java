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

    //get all aggregation api
    
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
    public Response getRecentEvents(@QueryParam("minutesAgo") @DefaultValue("30") int minutesAgo,
    @QueryParam("nodeName") String nodeName,
    @QueryParam("clusterName") String clusterName) {
        List<EventsDTO> eventsList = handler.getRecentEvents(minutesAgo, clusterName, nodeName);
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult;
        try {
            jsonResult = mapper.writeValueAsString(eventsList);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        // System.out.println("------clusterName----"+ clusterName);
        // System.out.println("-------nodename----"+ nodeName);
        return Response.ok(jsonResult).build();
    }


}