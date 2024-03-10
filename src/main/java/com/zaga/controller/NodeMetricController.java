package com.zaga.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.zaga.entity.queryentity.node.NodeMetricDTO;
import com.zaga.entity.queryentity.openshift.UserCredentials;
import com.zaga.handler.NodeMetricHandler;
import com.zaga.handler.cloudPlatform.OpenshiftLoginHandler;
import com.zaga.repo.NodeDTORepo;
import com.zaga.repo.OpenshiftCredsRepo;

import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/node")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NodeMetricController {
    @Inject
    NodeMetricHandler nodeMetricHandler;

    @Inject
    NodeDTORepo nodeDTORepo;

         @Inject
    OpenshiftLoginHandler openshiftLoginHandler;

        @Inject
    OpenshiftCredsRepo openshiftCredsRepo;  


    // @GET
    // @Path("/getAllNodeMetricData")
    // public List<NodeMetricDTO> getAllNodeMetricData(
    //     @QueryParam("from") LocalDate from,
    //     @QueryParam("to") LocalDate to,
    //     @QueryParam("minutesAgo") int minutesAgo
    // ) {
    //     return nodeMetricHandler.getAllNodeMetricData();
    // }
//     @GET
//     @Path("/getAllNodeMetricData")
//     public Response getAllNodeMetricData(
//             @QueryParam("from") LocalDate from,
//             @QueryParam("to") LocalDate to,
//             @QueryParam("minutesAgo") int minutesAgo
//     ) {
//         try {
//             List<NodeMetricDTO> allNodeMetrics = nodeMetricHandler.getAllNodeMetricData();

//             if (from != null && to != null) {
//                 Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
//                 Instant toInstant = to.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(86399); // Adjusted to end of day
// System.out.println("------Number of data date wise" + allNodeMetrics.size());
//                 allNodeMetrics = filterMetricsByDateRange(allNodeMetrics, fromInstant, toInstant);
//             } else if (minutesAgo > 0) {
//                 Instant currentInstant = Instant.now();
//                 Instant fromInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

//                 allNodeMetrics = filterMetricsByMinutesAgo(allNodeMetrics, fromInstant, currentInstant);
//             }
//             System.out.println("-----------Number of data in the specified time range: " + allNodeMetrics.size());
//             ObjectMapper objectMapper = new ObjectMapper();
//             String responseJson = objectMapper.writeValueAsString(allNodeMetrics);

//             return Response.ok(responseJson).build();
//         } catch (Exception e) {
//             e.printStackTrace();

//             return Response
//                     .status(Response.Status.INTERNAL_SERVER_ERROR)
//                     .entity("An error occurred: " + e.getMessage())
//                     .build();
//         }
//     }

@GET
@Path("/getAllNodeMetricData")
public Response getAllNodeMetricData(
        @QueryParam("from") LocalDate from,
        @QueryParam("to") LocalDate to,
        @QueryParam("minutesAgo") int minutesAgo,
        @QueryParam("clusterName") String clusterName,
        @QueryParam("nodeName") String nodeName,
        @QueryParam("userName") String userName
) {
    OpenShiftClient openShiftClient = openshiftLoginHandler.commonClusterLogin(userName, clusterName);
    String nodeString = nodeName;
    if (nodeName == null) {
        nodeString = "ClusterMethod";
    }
    Response response = openshiftLoginHandler.viewClusterCapacity(openShiftClient, nodeString);
    UserCredentials userCredentials = openshiftCredsRepo.getUser(userName);
    Gson gson = new Gson();
    JsonElement jsonElement = gson.toJsonTree(userCredentials);
    System.out.println("--------jsonEle----" + jsonElement);
    JsonArray jsonArray = jsonElement.getAsJsonObject().get("environments").getAsJsonArray();
    System.out.println("---------jsonArr" + jsonArray);

    String OPENSHIFTCLUSTERNAME = null;
    for (JsonElement jsonElement2 : jsonArray) {
        String dbClusterName = jsonElement2.getAsJsonObject().get("clusterName").getAsString();
        String openshiftClusterName = jsonElement2.getAsJsonObject().get("openshiftClusterName").getAsString();

        if (clusterName.equalsIgnoreCase(dbClusterName)) {
            OPENSHIFTCLUSTERNAME = openshiftClusterName;
            break;
        }
    }
    try {
        List<NodeMetricDTO> allNodeMetrics = nodeMetricHandler.getAllNodeMetricData(nodeName, OPENSHIFTCLUSTERNAME);

        if (from != null && to != null) {
            Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant toInstant = to.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(86399); // Adjusted to end of day
            System.out.println("------Number of data date wise" + allNodeMetrics.size());
            allNodeMetrics = filterMetricsByDateRange(allNodeMetrics, fromInstant, toInstant);
        } else if (minutesAgo > 0) {
            Instant currentInstant = Instant.now();
            Instant fromInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

            allNodeMetrics = filterMetricsByMinutesAgo(allNodeMetrics, fromInstant, currentInstant);
        }
        System.out.println("-----------Number of data in the specified time range: " + allNodeMetrics.size());
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult;
        try {
            jsonResult = mapper.writeValueAsString(allNodeMetrics);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(jsonResult).build();
    } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    } finally {
        // Any cleanup code can be put here
    }
}




    private List<NodeMetricDTO> filterMetricsByDateRange(List<NodeMetricDTO> metrics, Instant from, Instant to) {
        return metrics.stream()
                .filter(metric -> isWithinDateRange(metric.getDate().toInstant(), from, to))
                .collect(Collectors.toList());
    }

    private List<NodeMetricDTO> filterMetricsByMinutesAgo(List<NodeMetricDTO> metrics, Instant fromInstant, Instant toInstant) {
        return metrics.stream()
                .filter(metric -> isWithinDateRange(metric.getDate().toInstant(), fromInstant, toInstant))
                .collect(Collectors.toList());
    }

    private boolean isWithinDateRange(Instant targetInstant, Instant from, Instant to) {
        return !targetInstant.isBefore(from) && !targetInstant.isAfter(to);
    }
    
}
