package com.zaga.controller;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.entity.queryentity.metric.MetricDTO;
import com.zaga.handler.MetricQueryHandler;
import com.zaga.repo.MetricQueryRepo;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MetricController {

  

    @Inject
    MetricQueryHandler metricQueryHandler;

    @Inject
    MetricQueryRepo metricQueryRepo;
   
    @GET
    @Path("/getAllMetricData")
    public List<MetricDTO> getAllMetricDatas() {
        return metricQueryHandler.getAllMetricData();
    }
    @GET
    @Path("/getByserviceNameAndMinutesAgo")
    public Response getByserviceName(
        @QueryParam("timeAgoMinutes") @DefaultValue("60") int timeAgoMinutes,
        @QueryParam("serviceName") String serviceName
    ) {
        List<MetricDTO> metricData = metricQueryRepo.getMetricData(timeAgoMinutes, serviceName);
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String responseJson = objectMapper.writeValueAsString(metricData);
    
            return Response.ok(responseJson, MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error converting response to JSON")
                    .build();
        }
    }
    

}
