package com.zaga.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.entity.queryentity.node.NodeMetricDTO;
import com.zaga.handler.NodeMetricHandler;
import com.zaga.repo.NodeDTORepo;

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


    // @GET
    // @Path("/getAllNodeMetricData")
    // public List<NodeMetricDTO> getAllNodeMetricData(
    //     @QueryParam("from") LocalDate from,
    //     @QueryParam("to") LocalDate to,
    //     @QueryParam("minutesAgo") int minutesAgo
    // ) {
    //     return nodeMetricHandler.getAllNodeMetricData();
    // }
    @GET
    @Path("/getAllNodeMetricData")
    public Response getAllNodeMetricData(
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("minutesAgo") int minutesAgo
    ) {
        try {
            List<NodeMetricDTO> allNodeMetrics = nodeMetricHandler.getAllNodeMetricData();

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
            ObjectMapper objectMapper = new ObjectMapper();
            String responseJson = objectMapper.writeValueAsString(allNodeMetrics);

            return Response.ok(responseJson).build();
        } catch (Exception e) {
            e.printStackTrace();

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred: " + e.getMessage())
                    .build();
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
