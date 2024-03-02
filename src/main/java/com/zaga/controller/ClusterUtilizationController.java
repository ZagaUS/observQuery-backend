package com.zaga.controller;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.zaga.entity.queryentity.cluster_utilization.ClusterUtilizationDTO;
import com.zaga.entity.queryentity.cluster_utilization.response.ClusterResponse;
import com.zaga.entity.queryentity.openshift.UserCredentials;
import com.zaga.handler.ClusterUtilizationHandler;
import com.zaga.handler.cloudPlatform.OpenshiftLoginHandler;
import com.zaga.repo.ClusterUtilizationDTORepo;
import com.zaga.repo.OpenshiftCredsRepo;

import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;


@Path("/clusterUtilization")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ClusterUtilizationController{
    
    @Inject
    OpenshiftLoginHandler openshiftLoginHandler;
    
    @Inject
    ClusterUtilizationHandler clusterUtilizationHandler;

    @Inject
    ClusterUtilizationDTORepo clusterUtilizationDTORepo;

    @Inject
    OpenshiftCredsRepo openshiftCredsRepo;    

    @GET
    @Path("/getAllClusterUtilization_nodelevelData")
    public Response getAllClusterUtilization_NodelevelData(
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo
    ) {
        try {
            List<ClusterUtilizationDTO> allClusterUtilization = clusterUtilizationHandler.getAllClusterData();

            if (from != null && to != null) {
                Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant toInstant = to.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(86399); // Adjusted to end of day

                allClusterUtilization = filterMetricsByDateRange(allClusterUtilization, fromInstant, toInstant);
            } else if (minutesAgo > 0) {
                Instant currentInstant = Instant.now();
                Instant fromInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

                allClusterUtilization = filterMetricsByMinutesAgo(allClusterUtilization, fromInstant, currentInstant);
            }
            System.out.println("Number of data in the specified time range: " + allClusterUtilization.size());
            ObjectMapper objectMapper = new ObjectMapper();
            String responseJson = objectMapper.writeValueAsString(allClusterUtilization);

            return Response.ok(responseJson).build();
        } catch (Exception e) {
            e.printStackTrace();

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred: " + e.getMessage())
                    .build();
        }
    }

    private List<ClusterUtilizationDTO> filterMetricsByDateRange(List<ClusterUtilizationDTO> metrics, Instant from, Instant to) {
        return metrics.stream()
                .filter(metric -> isWithinDateRange(metric.getDate().toInstant(), from, to))
                .collect(Collectors.toList());
    }

    private List<ClusterUtilizationDTO> filterMetricsByMinutesAgo(List<ClusterUtilizationDTO> metrics, Instant fromInstant, Instant toInstant) {
        return metrics.stream()
                .filter(metric -> isWithinDateRange(metric.getDate().toInstant(), fromInstant, toInstant))
                .collect(Collectors.toList());
    }

    private boolean isWithinDateRange(Instant targetInstant, Instant from, Instant to) {
        return !targetInstant.isBefore(from) && !targetInstant.isAfter(to);
    }


    @GET
    @Path("/multi-level_nodeInfo")
    public List<ClusterResponse> getAllClusterDataByDateAndTime(
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo,
            @QueryParam("clusterName") String clusterName,
            @QueryParam("nodeName") String nodeName,
            @QueryParam("userName") String userName
            ) {
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
            String openshiftClusterName = jsonElement2.getAsJsonObject().get("openshiftClusterName") == null ? null : jsonElement2.getAsJsonObject().get("openshiftClusterName").getAsString();

            if (clusterName.equalsIgnoreCase(dbClusterName)) {
                OPENSHIFTCLUSTERNAME = openshiftClusterName;
                break;
            }
        }
        List<ClusterResponse> clusterResponses = clusterUtilizationHandler.getAllClusterByDateAndTime(from, to , minutesAgo, OPENSHIFTCLUSTERNAME, nodeName);
        if(clusterResponses.size() > 0 ){
            for (ClusterResponse clusterResponse : clusterResponses) {
                clusterResponse.setCpuCapacity((Integer)response.getEntity());
            }
        }
        return clusterResponses;
    }
  
}
