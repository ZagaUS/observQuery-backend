package com.zaga.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.entity.oteltrace.scopeSpans.Spans;
import com.zaga.entity.queryentity.trace.TraceDTO;
import com.zaga.entity.queryentity.trace.TraceMetrics;
import com.zaga.entity.queryentity.trace.TraceQuery;
import com.zaga.handler.TraceQueryHandler;
import com.zaga.repo.TraceQueryRepo;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



@Path("/traces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TraceController {

  @Inject
  TraceQueryHandler traceQueryHandler;

  @Inject
  TraceQueryRepo traceQueryRepo;

  @GET
  @Path("/getAllTraceData")
  public Response getAllDetails() {
    try {
      List<TraceDTO> traceList = traceQueryHandler.getSampleTrace();

      ObjectMapper objectMapper = new ObjectMapper();
      String responseJson = objectMapper.writeValueAsString(traceList);

      return Response.ok(responseJson).build();
    } catch (Exception e) {

      e.printStackTrace();
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("An error occurred: " + e.getMessage())
          .build();
    }
  }


@GET
@Path("/findById")
public Response findById(@QueryParam("traceId") String traceId) {
    if (traceId == null || traceId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("traceId query parameter is required")
                .build();
    }

    List<TraceDTO> data = traceQueryRepo.find("traceId = ?1", traceId).list();

    if (data.isEmpty()) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("No TraceDTO found for traceId: " + traceId)
                .build();
    }

    Map<String, Object> response = new HashMap<>();
    response.put("data", data);

    try {
        ObjectMapper objectMapper = new ObjectMapper();
        String responseJson = objectMapper.writeValueAsString(response);

        return Response.ok(responseJson).build();
    } catch (Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error converting response to JSON")
                .build();
    }
}

  
@POST
@Path("/TraceQueryFilter")
public Response queryTraces(
    TraceQuery traceQuery,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("pageSize") @DefaultValue("10") int pageSize,
    @QueryParam("minutesAgo") @DefaultValue("60") int minutesAgo) {
    try {

        List<TraceDTO> traceList = traceQueryHandler.searchTracesPaged(traceQuery,page, pageSize, minutesAgo);

        long totalCount = traceQueryHandler.countQueryTraces(traceQuery,minutesAgo);

        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("totalCount", totalCount);
        jsonResponse.put("data", traceList);

        ObjectMapper objectMapper = new ObjectMapper();
        String responseJson = objectMapper.writeValueAsString(jsonResponse);

        return Response.ok(responseJson).build();
    } catch (Exception e) {
        e.printStackTrace();

        return Response
            .status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("An error occurred: " + e.getMessage())
            .build();
    }
}



  @GET
  @Path("/getAllDataByPagination")
  public Response findRecentData(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("pageSize") @DefaultValue("10") int pageSize) {
    try {
      long totalCount = traceQueryHandler.countData();
      // long totalPages = (long) Math.ceil((double) totalCount / pageSize);
      List<TraceDTO> recentData = traceQueryHandler.findRecentDataPaged(
          page,
          pageSize);

      Map<String, Object> jsonResponse = new HashMap<>();
      jsonResponse.put("totalCount", totalCount);
      jsonResponse.put("data", recentData);

      ObjectMapper objectMapper = new ObjectMapper();
      String responseJson = objectMapper.writeValueAsString(jsonResponse);

      return Response.ok(responseJson).build();
    } catch (Exception e) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(e.getMessage())
          .build();
    }
  }



// @GET
// @Path("/getErroredDataForLastTwo")
// public Response findRecentDataPaged(
//     @QueryParam("page") @DefaultValue("1") int page,
//     @QueryParam("pageSize") @DefaultValue("10") int pageSize,
//     @QueryParam("serviceName") String serviceName) {

//      try {
//         // Corrected method name to match the one in TraceQueryHandler
//         Map<String, Object> result = traceQueryHandler.findByMatchingWithTotalCount(page, pageSize, serviceName);

//         ObjectMapper objectMapper = new ObjectMapper();
//         String responseJson = objectMapper.writeValueAsString(result);

//         return Response.ok(responseJson).build();
//     } catch (Exception e) {
//         e.printStackTrace();

//         return Response
//                 .status(Response.Status.INTERNAL_SERVER_ERROR)
//                 .entity("An error occurred: " + e.getMessage())
//                 .build();
//     }
// }

// @GET
// @Path("/getErroredDataForLastTwo")
// @Produces(MediaType.APPLICATION_JSON)
// public Response findRecentDataPaged(
//         @QueryParam("page") @DefaultValue("1") int page,
//         @QueryParam("pageSize") @DefaultValue("10") int pageSize,
//         @QueryParam("serviceName") String serviceName) {

//     Map<String, Object> result = traceQueryHandler.findByMatchingWithTotalCount(page, pageSize, serviceName);

//     List<TraceDTO> paginatedData = (List<TraceDTO>) result.get("data");
//     Long totalCount = (Long) result.get("totalCount");

//     if (paginatedData == null || paginatedData.isEmpty()) {
//         return Response.ok(Collections.emptyList()).build();
//     }

//     Map<String, Object> responseMap = new HashMap<>();
//     responseMap.put("data", paginatedData);
//     responseMap.put("totalCount", totalCount);

//      try {
//         ObjectMapper objectMapper = new ObjectMapper();
//         String jsonResponse = objectMapper.writeValueAsString(responseMap);
//         return Response.ok(jsonResponse).build();
//     } catch (JsonProcessingException e) {
//         return Response.serverError().entity("Error converting to JSON").build();
//     }
// }

@GET
@Path("/getErroredDataForLastTwo")
@Produces(MediaType.APPLICATION_JSON)
public Response findErroredDataForLastTwo(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("pageSize") @DefaultValue("10") int pageSize,
        @QueryParam("serviceName") String serviceName) {

    try {
        List<TraceDTO> traces = traceQueryHandler.findErrorsLastTwoHours(serviceName);

        int totalCount = traces.size();
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);

        if (startIndex >= endIndex || traces.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("data", Collections.emptyList());
            emptyResponse.put("totalCount", 0);

            return Response.ok(emptyResponse).build();
        }

        List<TraceDTO> erroredData = traces.subList(startIndex, endIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("data", erroredData);
        response.put("totalCount", totalCount);

        return Response.ok(response).build();
    } catch (Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Internal Server Error")
                .build();
    }
}



  
  @GET
  @Path("/count")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Long> getTraceCount() {
    return traceQueryHandler.getTraceCountWithinHour();
  }

  @GET
  @Path("/TraceSumaryChartDataCount")
  @Produces(MediaType.APPLICATION_JSON)
  public List<TraceMetrics> getTraceMetricsCount(@QueryParam("timeAgoMinutes") @DefaultValue("60") int timeAgoMinutes, 
        @QueryParam("serviceNameList") List<String> serviceNameList) {
    return traceQueryHandler.getTraceMetricCount(timeAgoMinutes,serviceNameList);
  }


  
  


//get data by traceId and also have same traceId then merge it as a one
@GET
@Path("/findByTraceId")
public Response findByTraceId(@QueryParam("traceId") String traceId) {
    if (traceId == null || traceId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("traceId query parameter is required")
            .build();
    }

    List<TraceDTO> data = traceQueryRepo.find("traceId = ?1", traceId).list();

    if (data.isEmpty()) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("No TraceDTO found for traceId: " + traceId)
            .build();
    }

    List<TraceDTO> dto;
    if (data.size() > 1) {
        dto = traceQueryHandler.mergeTraces(data);
    } else {
        dto = data;
        for (TraceDTO trace : dto) {
            List<Spans> orderedSpanData = traceQueryHandler.sortingParentChildOrder(trace.getSpans());
            trace.setSpans(orderedSpanData);
        }
    }

    for (TraceDTO trace : dto) {
        for (Spans span : trace.getSpans()) {
            System.out.println(
                "Span ID: " + span.getSpanId() + ", Parent Span ID: " + span.getParentSpanId() + ", Name: "
                    + span.getName());
        }
    }

    Map<String, Object> response = new HashMap<>();
    response.put("data", dto); 

    try {
        ObjectMapper objectMapper = new ObjectMapper();
        String responseJson = objectMapper.writeValueAsString(response);

        return Response.ok(responseJson).build();
    } catch (Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("Error converting response to JSON")
            .build();
    }
}
  
  




@GET
@Path("/getalldata-sortorder")
@Produces(MediaType.APPLICATION_JSON)
public Response sortOrderTrace(
    @QueryParam("sortOrder") String sortOrder,
    @QueryParam("page") int page,
    @QueryParam("pageSize") int pageSize,
    @QueryParam("minutesAgo") int minutesAgo, @QueryParam("serviceNameList") List<String> serviceNameList) {

      if (page <= 0 || pageSize <= 0 || minutesAgo < 0) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid page, pageSize, or minutesAgo parameters.")
                .build();
    }
    List<TraceDTO> traces;
        if ("new".equalsIgnoreCase(sortOrder)) {
        traces = traceQueryHandler.getAllTracesOrderByCreatedTimeDesc(serviceNameList);
          } else if ("old".equalsIgnoreCase(sortOrder)) {
        traces = traceQueryHandler.getAllTracesAsc(serviceNameList);
          } else if ("error".equalsIgnoreCase(sortOrder)) {
        traces = traceQueryHandler.findAllOrderByErrorFirst(serviceNameList);
    } else if ("peakLatency".equalsIgnoreCase(sortOrder)) {
        traces = traceQueryHandler.findAllOrderByDuration(serviceNameList);
    } else {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid sortOrder parameter. Use 'new', 'old', or 'error','peakLatency'.")
                .build();
    }

    if (minutesAgo > 0) {
    Date cutoffDate = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo));
    traces = traces.stream()
    .filter(trace -> {
        Date createdTime = trace.getCreatedTime();
        return createdTime != null && createdTime.after(cutoffDate);
    })
    .collect(Collectors.toList());
}

    int startIndex = (page - 1) * pageSize;
    int endIndex = Math.min(startIndex + pageSize, traces.size());

if (startIndex >= endIndex || traces.isEmpty()) {
        Map<String, Object> emptyResponse = new HashMap<>();
        emptyResponse.put("data", Collections.emptyList());
        emptyResponse.put("totalCount", 0);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String responseJson = objectMapper.writeValueAsString(emptyResponse);

            return Response.ok(responseJson).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error converting response to JSON")
                    .build();
        }
    }

  List<TraceDTO> paginatedTraces = traces.subList(startIndex, endIndex);
    int totalCount = traces.size();

    Map<String, Object> response = new HashMap<>();
    response.put("data", paginatedTraces);
    response.put("totalCount", totalCount);
 try {
        ObjectMapper objectMapper = new ObjectMapper();
        String responseJson = objectMapper.writeValueAsString(response);

        return Response.ok(responseJson).build();
    } catch (Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("Error converting response to JSON")
            .build();
    }
}




}