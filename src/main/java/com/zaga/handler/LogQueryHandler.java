package com.zaga.handler;


import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.zaga.entity.otellog.ScopeLogs;
import com.zaga.entity.otellog.scopeLogs.LogRecord;
import com.zaga.entity.otellog.scopeLogs.Scope;
import com.zaga.entity.otellog.scopeLogs.logRecord.Body;
import com.zaga.entity.queryentity.log.LogDTO;
import com.zaga.entity.queryentity.log.LogMetrics;
import com.zaga.entity.queryentity.log.LogQuery;
import com.zaga.repo.LogQueryRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class LogQueryHandler {

    @Inject
    LogQueryRepo logQueryRepo;

    @Inject
    MongoClient mongoClient;
    
    
    public List<LogDTO> getLogsByServiceName(String serviceName, int page, int pageSize) {
        return logQueryRepo.findByServiceName(serviceName, page, pageSize);
    }

    public long getTotalLogCountByServiceName(String serviceName) {
        return logQueryRepo.countByServiceName(serviceName);
    }
    
    public List<LogDTO> findLogDataPaged(int page, int pageSize) {
        List<LogDTO> logList = logQueryRepo.listAll();
           
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, logList.size());
    
        return logList.subList(startIndex, endIndex);
    }
    
    public long countLogRecords() {
        System.out.println(
          "LogQueryHandler.countLogRecords()" + logQueryRepo.count()
        );
        return logQueryRepo.count();
    }




// search log in a filter query time based
        // public List<LogDTO> searchlogPaged(LogQuery logQuery, int page, int pageSize, int minutesAgo) {
    //     List<String> serviceNames = logQuery.getServiceName();
    //     List<String> severityTexts = logQuery.getSeverityText();
    
    //     List<LogDTO> logList = logQueryRepo.listAll(); 
    
    //     List<LogDTO> filteredLogList = new ArrayList<>();
    
    //     for (LogDTO logDTO : logList) {
    //         if ((serviceNames == null || serviceNames.isEmpty() || serviceNames.contains(logDTO.getServiceName())) &&
    //             (severityTexts == null || severityTexts.isEmpty() || severityTexts.contains(logDTO.getSeverityText()))) {
    //             filteredLogList.add(logDTO);
    //         }
    //     }
    
    //     return filteredLogList;
    // }
    


    //filtering datas date based
    public List<LogDTO> searchLogByDate(LogQuery logQuery, LocalDate from, LocalDate to) {
        List<String> serviceNames = logQuery.getServiceName();
        List<String> severityTexts = logQuery.getSeverityText();
    
        List<LogDTO> logList = logQueryRepo.listAll();
    
        return logList.stream()
                .filter(logDTO -> (serviceNames == null || serviceNames.isEmpty() || serviceNames.contains(logDTO.getServiceName())) &&
                        (severityTexts == null || severityTexts.isEmpty() || severityTexts.contains(logDTO.getSeverityText())) &&
                        (isWithinDateRange(logDTO.getCreatedTime(), from.atStartOfDay(), to.plusDays(1).atStartOfDay())))
                .collect(Collectors.toList());
    }
    
       private boolean isWithinDateRange(Date logTimestamp, LocalDateTime from, LocalDateTime to) {
        LocalDateTime logDateTime = logTimestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    
        return (logDateTime.isEqual(from) || logDateTime.isAfter(from)) &&
                (logDateTime.isEqual(to) || logDateTime.isBefore(to));
    }



       
  //sort orer decending 
  public List<LogDTO> getAllLogssOrderByCreatedTimeDesc(List<String> serviceNameList) {
    return logQueryRepo.findAllOrderByCreatedTimeDesc(serviceNameList);
  }


//sort order ascending
public List<LogDTO> getAllLogssAsc(List<String> serviceNameList) {
    return logQueryRepo.findAllOrderByCreatedTimeAsc(serviceNameList);
}

//sort order error data decending
public List<LogDTO> getErrorLogsByServiceNamesOrderBySeverityAndCreatedTimeDesc(List<String> serviceNameList) {
    MongoDatabase database = mongoClient.getDatabase("OtelLog");
    MongoCollection<LogDTO> logDTOCollection = database.getCollection("LogDTO", LogDTO.class);

    Bson matchStage = Aggregates.match(Filters.in("serviceName", serviceNameList));

    Bson addSortFieldStage = Aggregates.addFields(new Field<>("customSortField", new Document("$cond",
            Arrays.asList(
                    new Document("$in", Arrays.asList("$severityText", Arrays.asList("ERROR", "SEVERE"))),
                    0,
                    1
            )
    )));

    Bson sortStage = Aggregates.sort(Sorts.orderBy(
            Sorts.ascending("customSortField"),
            Sorts.descending("createdTime")
    ));

    Bson projectStage = Aggregates.project(Projections.exclude("customSortField"));

    List<LogDTO> result = logDTOCollection.aggregate(Arrays.asList(matchStage, addSortFieldStage, sortStage, projectStage))
            .into(new ArrayList<>());

    return result;
}


public List<LogMetrics> getLogMetricCount(int timeAgoMinutes, List<String> serviceNameList) {
    List<LogDTO> logList = logQueryRepo.listAll(); 
    Map<String, LogMetrics> metricsMap = new HashMap<>();

    Instant cutoffTime = Instant.now().minus(timeAgoMinutes, ChronoUnit.MINUTES);

    for (LogDTO logDTO : logList) {
        Date logCreateTime = logDTO.getCreatedTime();
        if (logCreateTime != null) {
            Instant logInstant = logCreateTime.toInstant();

            if (!logInstant.isBefore(cutoffTime) && serviceNameList.contains(logDTO.getServiceName())) {
                String serviceName = logDTO.getServiceName();

                LogMetrics metrics = metricsMap.get(serviceName);
                if (metrics == null) {
                    metrics = new LogMetrics();
                    metrics.setServiceName(serviceName);
                    metrics.setErrorCallCount(0L); 
                    metrics.setWarnCallCount(0L);
                    metrics.setDebugCallCount(0L);
                }

                calculateCallCounts(logDTO, metrics);

                metricsMap.put(serviceName, metrics);
            }
        }
    }

    return new ArrayList<>(metricsMap.values());
}



private void calculateCallCounts(LogDTO logDTO, LogMetrics metrics) {
    for (ScopeLogs scopeLogs : logDTO.getScopeLogs()) {
        for (LogRecord logRecord : scopeLogs.getLogRecords()) {
            String severityText = logRecord.getSeverityText(); 
            if ("ERROR".equals(severityText) || "SEVERE".equals(severityText)) {
                metrics.setErrorCallCount(metrics.getErrorCallCount() + 1);
            } else if ("WARN".equals(severityText)) {
                metrics.setWarnCallCount(metrics.getWarnCallCount() + 1);
            } else if ("DEBUG".equals(severityText)) {
                metrics.setDebugCallCount(metrics.getDebugCallCount() + 1);
            }
        }
    }
}


public List<LogDTO> findByMatching(String serviceName) {
    LocalDateTime currentTime = LocalDateTime.now();
    LocalDateTime startTime = currentTime.minusHours(2);

    Instant currentInstant = currentTime.atZone(ZoneId.systemDefault()).toInstant();
    Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();

    Date currentDate = Date.from(currentInstant);
    Date startDate = Date.from(startInstant);

    List<LogDTO> logList = logQueryRepo.findByServiceNameAndCreatedTime(serviceName, startDate, currentDate);

    List<LogDTO> filteredLogList = new ArrayList<>();
    for (LogDTO logDTO : logList) {
        List<ScopeLogs> scopeLogsList = logDTO.getScopeLogs();
        boolean hasError = false;

        for (ScopeLogs scopeLogs : scopeLogsList) {
            for (LogRecord logRecord : scopeLogs.getLogRecords()) {
                if ("ERROR".equals(logRecord.getSeverityText())) {
                    hasError = true;
                    break;
                }
            }

            if (hasError) {
                filteredLogList.add(logDTO);
                break;
            }
        }
    }
    Collections.sort(filteredLogList, Comparator.comparing(LogDTO::getCreatedTime).reversed());

    return filteredLogList;
}




  // search functionality
  public List<LogDTO> searchLogs(String keyword) {
    List<LogDTO> results = new ArrayList<>();
    String regexPattern = ".*" + Pattern.quote(keyword) + ".*";
    BsonRegularExpression regex = new BsonRegularExpression(regexPattern, "i");

    try {
        MongoCollection<Document> collection = mongoClient
                .getDatabase("OtelLog")
                .getCollection("LogDTO");

        Document query = new Document("$or", List.of(
                new Document("scopeLogs.logRecords.body.stringValue", regex)
                ));

        MongoCursor<Document> cursor = collection.find(query).iterator();

        while (cursor.hasNext()) {
            Document document = cursor.next();
            LogDTO logResult = mapDocumentToLogDTO(document);
            results.add((LogDTO) logResult);
        }
    } catch (Exception e) {
    }

    return results;
}

private LogDTO mapDocumentToLogDTO(Document document) {
    
    LogDTO logDTO = new LogDTO();
    Gson gson = new Gson();
    String data = gson.toJson(document);
    // System.out.println("---data----  " + data);

    JsonObject jsonObject = JsonParser.parseString(data).getAsJsonObject();
    JsonArray jsonArray = jsonObject.getAsJsonArray("scopeLogs");
    // System.out.println("----scope---- " + jsonArray);

    logDTO.setServiceName(jsonObject.get("serviceName").getAsString());
    logDTO.setTraceId(jsonObject.get("traceId").getAsString());
    logDTO.setSpanId(jsonObject.get("spanId").getAsString());
    logDTO.setCreatedTime(document.getDate("createdTime"));
    logDTO.setSeverityText(jsonObject.get("severityText").getAsString());

    Scope scope = new Scope();
    List<LogRecord> logRecords = new ArrayList<LogRecord>();

    for(int i = 0 ; i < jsonArray.size() ; i++){
        JsonObject jsonObject2 = jsonArray.get(i).getAsJsonObject();
        scope.setName(jsonObject2.getAsJsonObject("scope").get("name").getAsString());

        JsonArray jsonArray2 = jsonObject2.getAsJsonArray("logRecords");
        
        for(int j = 0 ; j < jsonArray2.size() ; j++){
            LogRecord logRecord = new LogRecord();
            Body body = new Body();
            JsonObject jsonObject3 = jsonArray2.get(j).getAsJsonObject();
            System.out.println("-------" + jsonObject3.get("timeUnixNano").getAsString());
            logRecord.setTimeUnixNano(jsonObject3.get("timeUnixNano").getAsString());
            logRecord.setObservedTimeUnixNano(jsonObject3.get("observedTimeUnixNano").getAsString());
            logRecord.setSeverityNumber(jsonObject3.get("severityNumber").getAsInt());
            logRecord.setSeverityText(jsonObject3.get("severityText").getAsString());
            logRecord.setFlags(jsonObject3.get("flags").getAsInt());
            logRecord.setTraceId(jsonObject3.get("traceId").getAsString());
            logRecord.setSpanId(jsonObject3.get("spanId").getAsString());
            body.setStringValue(jsonObject3.getAsJsonObject("body").get("stringValue").getAsString());
            logRecord.setBody(body);               
            logRecords.add(logRecord);
        }

        System.out.println("----scope name ---- " + jsonObject2.getAsJsonObject("scope").get("name").getAsString());
    }
    
    ScopeLogs scopeLogs = new ScopeLogs();
    scopeLogs.setScope(scope);
    scopeLogs.setLogRecords(logRecords);
    List<ScopeLogs> scopeLogsArray = new ArrayList<ScopeLogs>();
    scopeLogsArray.add(scopeLogs);
    logDTO.setScopeLogs(scopeLogsArray);
  
    return logDTO;
}



}

