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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.zaga.entity.queryentity.events.EventsDTO;
import com.zaga.entity.queryentity.openshift.UserCredentials;
import com.zaga.handler.EventQueryhandler;
import com.zaga.handler.cloudPlatform.OpenshiftLoginHandler;
import com.zaga.repo.OpenshiftCredsRepo;

import io.fabric8.openshift.client.OpenShiftClient;
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

        @Inject
    OpenshiftLoginHandler openshiftLoginHandler;

        @Inject
    OpenshiftCredsRepo openshiftCredsRepo;    

    //get all aggregation api
    
    @GET
    @Path("/getall-Events-aggregation")
    public Response getAllEventsDataByDateAndTime(
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo,
            @QueryParam("nodeName") String nodeName,
            @QueryParam("clusterName") String clusterName,
            @QueryParam("userName") String userName
            ) {

                OpenShiftClient openShiftClient = openshiftLoginHandler.commonClusterLogin(userName, clusterName);
                String nodeString = nodeName;
                if(nodeName == null){nodeString="ClusterMethod";}
                Response response = openshiftLoginHandler.viewClusterCapacity(openShiftClient, nodeString);
                UserCredentials userCredentials = openshiftCredsRepo.getUser(userName);
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(userCredentials);
                System.out.println("--------jsonEle----" + jsonElement);
                JsonArray jsonArray = jsonElement.getAsJsonObject().get("environments").getAsJsonArray();
                System.out.println("---------jsonArr"+ jsonArray);
                
                String OPENSHIFTCLUSTERNAME = null;
                for (JsonElement jsonElement2 : jsonArray) {
                    String dbClusterName = jsonElement2.getAsJsonObject().get("clusterName").getAsString();
                    String openshiftClusterName = jsonElement2.getAsJsonObject().get("openshiftClusterName").getAsString();
        
                    if (clusterName.equalsIgnoreCase(dbClusterName)) {
                        OPENSHIFTCLUSTERNAME = openshiftClusterName;
                        break;
                    }
                }
        
        List<EventsDTO> eventsList = handler.getAllEventsByDateAndTime(from, to, minutesAgo, nodeName, OPENSHIFTCLUSTERNAME);
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
    @QueryParam("clusterName") String clusterName,
    @QueryParam("userName") String userName) {
                OpenShiftClient openShiftClient = openshiftLoginHandler.commonClusterLogin(userName, clusterName);
        String nodeString = nodeName;
        if(nodeName == null){nodeString="ClusterMethod";}
        Response response = openshiftLoginHandler.viewClusterCapacity(openShiftClient, nodeString);
        UserCredentials userCredentials = openshiftCredsRepo.getUser(userName);
        Gson gson = new Gson();
        JsonElement jsonElement = gson.toJsonTree(userCredentials);
        JsonArray jsonArray = jsonElement.getAsJsonObject().get("environments").getAsJsonArray();
        String OPENSHIFTCLUSTERNAME = null;
        for (JsonElement jsonElement2 : jsonArray) {
            String dbClusterName = jsonElement2.getAsJsonObject().get("clusterName").getAsString();
            String openshiftClusterName = jsonElement2.getAsJsonObject().get("openshiftClusterName").getAsString();

            if (clusterName.equalsIgnoreCase(dbClusterName)) {
                OPENSHIFTCLUSTERNAME = openshiftClusterName;
                break;
            }
        }

        List<EventsDTO> eventsList = handler.getRecentEvents(minutesAgo, OPENSHIFTCLUSTERNAME, nodeName);
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