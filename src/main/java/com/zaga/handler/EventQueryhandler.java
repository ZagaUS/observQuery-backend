package com.zaga.handler;


import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.zaga.entity.otelevent.ScopeLogs;
import com.zaga.entity.otelevent.scopeLogs.LogRecords;
import com.zaga.entity.otelevent.scopeLogs.logRecord.Body;
import com.zaga.entity.queryentity.events.EventsDTO;
import com.zaga.repo.EventDTORepo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventQueryhandler {
    
@Inject
EventDTORepo eventDTORepo;

 @Inject
  MongoClient mongoClient;


public List<EventsDTO> getAllEvent() {

    return eventDTORepo.listAll();
}


 public List<EventsDTO> getAllEventsByDateAndTime(
    LocalDate from,
    LocalDate to,
    int minutesAgo,
    String nodeName,
    String clusterName
  ) {
    LocalDateTime startTime = LocalDateTime.now();
    MongoDatabase database = mongoClient.getDatabase("OtelEvent");
    MongoCollection<Document> collection = database.getCollection(
      "EventsDTO"
    );

    List<EventsDTO> result;

    if (from != null && to != null) {
      result =
        executeAggregationPipeline(
          collection,
          from,
          to, nodeName, clusterName
        );
    }
     else if (from != null && minutesAgo > 0) {
      result =
        executeAnotherLogic(collection, from, minutesAgo, nodeName, clusterName);
    } 
    else {
      System.out.println(
        "Invalid parameters. Provide either 'from' or 'minutesAgo'."
      );
      result = Collections.emptyList();
    }

    LocalDateTime endTime = LocalDateTime.now();
    return result;
  }

public List<EventsDTO> executeAggregationPipeline(
   MongoCollection<Document> collection,
   LocalDate from,
   LocalDate to, String nodeName, String clusterName) {
           List<Bson> pipeline = new ArrayList<>();
        pipeline.add(
            Aggregates.addFields(
                new Field<>("justDate", 
                    new Document("$dateToString", 
                        new Document("format", "%m-%d-%Y")
                            .append("date", "$createdTime")
                    )
                )
            )
        );
        pipeline.add(
            Aggregates.match(
                Filters.and(
                    Filters.gte("justDate", from.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))),
                    Filters.lte("justDate", to.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")))
                )
            )
        );
        pipeline.add(Aggregates.sort(Sorts.descending("createdTime")));

        if (clusterName != null && !clusterName.isEmpty()) {
            if (nodeName != null && !nodeName.isEmpty()) {
                pipeline.add(
                    Aggregates.match(
                        Filters.and(
                            Filters.eq("nodeName", nodeName),
                            Filters.eq("clusterName", clusterName)
                        )
                    )
                );
            } else {
                pipeline.add(
                    Aggregates.match(
                        Filters.eq("clusterName", clusterName)
                    )
                );
            }
        }
        
new Document("$project", new Document("_id", 0)
            .append("nodeName", 1)
            .append("objectKind", 1)
            .append("objectName", 1)
            .append("scopeLogs", 1)
            .append("severityText", 1)
            .append("createdTime", 1)
            .append("clusterName", 1)
        );

    AggregateIterable<Document> aggregationResult = collection.aggregate(pipeline);

    List<EventsDTO> result = new ArrayList<>();
        for (Document document : aggregationResult) {
            EventsDTO eventsDTO = new EventsDTO();
            eventsDTO.setCreatedTime(document.getDate("createdTime"));
            eventsDTO.setNodeName(document.getString("nodeName"));
            eventsDTO.setObjectKind(document.getString("objectKind"));
            eventsDTO.setObjectName(document.getString("objectName"));
            eventsDTO.setSeverityText(document.getString("severityText"));
            eventsDTO.setSeverityText(document.getString("clusterName"));

            // Handle scopeLogs field
            List<Document> scopeLogsDocs = document.get("scopeLogs", List.class);
            if (scopeLogsDocs != null) {
                List<ScopeLogs> scopeLogsList = new ArrayList<>();
                for (Document scopeLogDoc : scopeLogsDocs) {
                    ScopeLogs scopeLogs = new ScopeLogs();
                    scopeLogs.setScope(scopeLogDoc.get("scope", Map.class));

                    List<Document> logRecordsDocs = scopeLogDoc.get("logRecords", List.class);
                    if (logRecordsDocs != null) {
                        List<LogRecords> logRecordsList = new ArrayList<>();
                        for (Document logRecordDoc : logRecordsDocs) {
                            LogRecords logRecord = new LogRecords();
                            logRecord.setTimeUnixNano(logRecordDoc.getString("timeUnixNano"));
                            logRecord.setSeverityNumber(logRecordDoc.getInteger("severityNumber"));
                            logRecord.setSeverityText(logRecordDoc.getString("severityText"));
                            logRecord.setSpanId(logRecordDoc.getString("spanId"));
                            logRecord.setTraceId(logRecordDoc.getString("traceId"));

                            Body body = new Body();
                            Document bodyDoc = logRecordDoc.get("body", Document.class);
                            if (bodyDoc != null) {
                            body.setStringValue(bodyDoc.getString("stringValue"));
                            }
                            logRecord.setBody(body);

                            logRecord.setAttributes(logRecordDoc.get("attributes", List.class));
                            logRecordsList.add(logRecord);
                        }
                        scopeLogs.setLogRecords(logRecordsList);
                    }
                    scopeLogsList.add(scopeLogs);
                }
                eventsDTO.setScopeLogs(scopeLogsList);
            }

            result.add(eventsDTO);
        }
        System.out.println("----------events data date wise------- " + result.size());

    return result;
   }    

private List<EventsDTO> executeAnotherLogic(
        MongoCollection<Document> collection,
        LocalDate from,
        Integer minutesAgo,
        String nodeName,
        String clusterName) {

    List<Bson> pipeline = new ArrayList<>();

    pipeline.add(
            Aggregates.match(
                    Filters.and(
                            Filters.gte("createdTime", Date.from(Instant.now().minus(Duration.ofMinutes(minutesAgo)))),
                            Filters.lte("createdTime", Date.from(Instant.now()))
                    )
            )
    );

    if (clusterName != null && !clusterName.isEmpty()) {
        if (nodeName != null && !nodeName.isEmpty()) {
        pipeline.add(
                    Aggregates.match(
                            Filters.and(
                                    Filters.eq("nodeName", nodeName),
                                    Filters.eq("clusterName", clusterName)
                            )
                    )
            );
        } else {
          pipeline.add(
                    Aggregates.match(
                            Filters.eq("clusterName", clusterName)
                    )
            );
        }
    }

    pipeline.add(Aggregates.project(
            Projections.fields(
                    Projections.excludeId(),
                    Projections.include("nodeName", "objectKind", "objectName", "scopeLogs", "severityText", "createdTime", "clusterName")
            )
    ));

    AggregateIterable<Document> aggregationResult = null;
    try {
        aggregationResult = collection.aggregate(pipeline);
    } catch (MongoCommandException e) {
       System.err.println("MongoDB command exception: " + e.getMessage());
        e.printStackTrace();
        return Collections.emptyList();
    }

    List<EventsDTO> result = new ArrayList<>();
    for (Document document : aggregationResult) {
        EventsDTO eventsDTO = new EventsDTO();
        eventsDTO.setCreatedTime(document.getDate("createdTime"));
        eventsDTO.setNodeName(document.getString("nodeName"));
        eventsDTO.setObjectKind(document.getString("objectKind"));
        eventsDTO.setObjectName(document.getString("objectName"));
        eventsDTO.setSeverityText(document.getString("severityText"));
        eventsDTO.setClusterName(document.getString("clusterName"));

        List<Document> scopeLogsDocs = document.get("scopeLogs", List.class);
        if (scopeLogsDocs != null) {
            List<ScopeLogs> scopeLogsList = new ArrayList<>();
            for (Document scopeLogDoc : scopeLogsDocs) {
                ScopeLogs scopeLogs = new ScopeLogs();
                scopeLogs.setScope(scopeLogDoc.get("scope", Map.class));

                List<Document> logRecordsDocs = scopeLogDoc.get("logRecords", List.class);
                if (logRecordsDocs != null) {
                    List<LogRecords> logRecordsList = new ArrayList<>();
                    for (Document logRecordDoc : logRecordsDocs) {
                        LogRecords logRecord = new LogRecords();
                        logRecord.setTimeUnixNano(logRecordDoc.getString("timeUnixNano"));
                        logRecord.setSeverityNumber(logRecordDoc.getInteger("severityNumber"));
                        logRecord.setSeverityText(logRecordDoc.getString("severityText"));
                        logRecord.setSpanId(logRecordDoc.getString("spanId"));
                        logRecord.setTraceId(logRecordDoc.getString("traceId"));

                        Body body = new Body();
                        Document bodyDoc = logRecordDoc.get("body", Document.class);
                        if (bodyDoc != null) {
                            body.setStringValue(bodyDoc.getString("stringValue"));
                        }
                        logRecord.setBody(body);

                        logRecord.setAttributes(logRecordDoc.get("attributes", List.class));
                        logRecordsList.add(logRecord);
                    }
                    scopeLogs.setLogRecords(logRecordsList);
                }
                scopeLogsList.add(scopeLogs);
            }
            eventsDTO.setScopeLogs(scopeLogsList);
        }

        result.add(eventsDTO);
    }
    System.out.println("----------events data minutes wise------- " + result.size());
    return result;
}



//    private List<EventsDTO> executeAnotherLogic(
//             MongoCollection<Document> collection,
//             LocalDate from,
//             Integer minutesAgo) {
        // List<Document> pipeline = Arrays.asList(
        //         new Document("$match", new Document("$expr", new Document("$and", Arrays.asList(
        //                 new Document("$gte", Arrays.asList("$createdTime",
        //                         new Document("$subtract", Arrays.asList(new java.util.Date(), minutesAgo * 60L * 1000L)))),
        //                 new Document("$lte", Arrays.asList("$createdTime",
        //                         new java.util.Date()))
        //         )))),
        //         new Document("$sort",
        //                 new Document("date", 1L)
        //         ),
        //         new Document("$sort", new Document("createdTime", -1)),
//                new Document("$project", new Document("_id", 0)
//                         .append("nodeName", 1)
//                         .append("objectKind", 1)
//                         .append("objectName", 1)
//                         .append("scopeLogs", 1)
//                         .append("severityText", 1)
//                         .append("createdTime", 1)
//                 )
//         );

//         AggregateIterable<Document> aggregationResult = collection.aggregate(pipeline);

//         List<EventsDTO> result = new ArrayList<>();
//         for (Document document : aggregationResult) {
//             EventsDTO eventsDTO = new EventsDTO();
//             eventsDTO.setCreatedTime(document.getDate("createdTime"));
//             eventsDTO.setNodeName(document.getString("nodeName"));
//             eventsDTO.setObjectKind(document.getString("objectKind"));
//             eventsDTO.setObjectName(document.getString("objectName"));
//             eventsDTO.setSeverityText(document.getString("severityText"));

//             // Handle scopeLogs field
//             List<Document> scopeLogsDocs = document.get("scopeLogs", List.class);
//             if (scopeLogsDocs != null) {
//                 List<ScopeLogs> scopeLogsList = new ArrayList<>();
//                 for (Document scopeLogDoc : scopeLogsDocs) {
//                     ScopeLogs scopeLogs = new ScopeLogs();
//                     scopeLogs.setScope(scopeLogDoc.get("scope", Map.class));

//                     List<Document> logRecordsDocs = scopeLogDoc.get("logRecords", List.class);
//                     if (logRecordsDocs != null) {
//                         List<LogRecords> logRecordsList = new ArrayList<>();
//                         for (Document logRecordDoc : logRecordsDocs) {
//                             LogRecords logRecord = new LogRecords();
//                             logRecord.setTimeUnixNano(logRecordDoc.getString("timeUnixNano"));
//                             logRecord.setSeverityNumber(logRecordDoc.getInteger("severityNumber"));
//                             logRecord.setSeverityText(logRecordDoc.getString("severityText"));
//                             logRecord.setSpanId(logRecordDoc.getString("spanId"));
//                             logRecord.setTraceId(logRecordDoc.getString("traceId"));

//                             // Handle Body field
//                             Body body = new Body();
//                             Document bodyDoc = logRecordDoc.get("body", Document.class);
//                             if (bodyDoc != null) {
//                                 body.setStringValue(bodyDoc.getString("stringValue"));
//                             }
//                             logRecord.setBody(body);

//                             logRecord.setAttributes(logRecordDoc.get("attributes", List.class));
//                             logRecordsList.add(logRecord);
//                         }
//                         scopeLogs.setLogRecords(logRecordsList);
//                     }
//                     scopeLogsList.add(scopeLogs);
//                 }
//                 eventsDTO.setScopeLogs(scopeLogsList);
//             }

//             result.add(eventsDTO);
//         }
//         System.out.println("----------events data------- " + result.size());
//         return result;
       
//     }   



    public List<EventsDTO> getRecentEvents(int minutesAgo) {
        MongoDatabase database = mongoClient.getDatabase("OtelEvent");
        MongoCollection<Document> collection = database.getCollection("EventsDTO");
    
        List<EventsDTO> result;
    
        if (minutesAgo == 30) {
            result = executeRecentLogic(collection, 30);
        } else {
            System.out.println("Invalid parameters. 'minutesAgo' must be 30.");
            result = Collections.emptyList();
        }
    
        LocalDateTime endTime = LocalDateTime.now();
        return result;
    }
    
    




  private List<EventsDTO> executeRecentLogic(
        MongoCollection<Document> collection, Integer minutesAgo) {
    LocalDateTime currentTime = LocalDateTime.now();
    LocalDateTime thirtyMinutesAgo = currentTime.minusMinutes(30);

    List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document("$and", Arrays.asList(
                    new Document("createdTime", new Document("$gte", Date.from(thirtyMinutesAgo.atZone(ZoneId.systemDefault()).toInstant()))),
                    new Document("createdTime", new Document("$lte", Date.from(currentTime.atZone(ZoneId.systemDefault()).toInstant())))
            ))),
            new Document("$sort", new Document("createdTime", -1)),
            new Document("$project", new Document("_id", 0)
                    .append("nodeName", 1)
                    .append("objectKind", 1)
                    .append("objectName", 1)
                    .append("scopeLogs", 1)
                    .append("severityText", 1)
                    .append("createdTime", 1)
            )
    );

    AggregateIterable<Document> aggregationResult = collection.aggregate(pipeline);

    List<EventsDTO> result = new ArrayList<>();
    for (Document document : aggregationResult) {
        EventsDTO eventsDTO = new EventsDTO();
        eventsDTO.setCreatedTime(document.getDate("createdTime"));
        eventsDTO.setNodeName(document.getString("nodeName"));
        eventsDTO.setObjectKind(document.getString("objectKind"));
        eventsDTO.setObjectName(document.getString("objectName"));
        eventsDTO.setSeverityText(document.getString("severityText"));

        List<Document> scopeLogsDocs = document.get("scopeLogs", List.class);
        if (scopeLogsDocs != null) {
            List<ScopeLogs> scopeLogsList = new ArrayList<>();
            for (Document scopeLogDoc : scopeLogsDocs) {
                ScopeLogs scopeLogs = new ScopeLogs();
                scopeLogs.setScope(scopeLogDoc.get("scope", Map.class));

                List<Document> logRecordsDocs = scopeLogDoc.get("logRecords", List.class);
                if (logRecordsDocs != null) {
                    List<LogRecords> logRecordsList = new ArrayList<>();
                    for (Document logRecordDoc : logRecordsDocs) {
                        LogRecords logRecord = new LogRecords();
                        logRecord.setTimeUnixNano(logRecordDoc.getString("timeUnixNano"));
                        logRecord.setSeverityNumber(logRecordDoc.getInteger("severityNumber"));
                        logRecord.setSeverityText(logRecordDoc.getString("severityText"));
                        logRecord.setSpanId(logRecordDoc.getString("spanId"));
                        logRecord.setTraceId(logRecordDoc.getString("traceId"));

                        Body body = new Body();
                        Document bodyDoc = logRecordDoc.get("body", Document.class);
                        if (bodyDoc != null) {
                            body.setStringValue(bodyDoc.getString("stringValue"));
                        }
                        logRecord.setBody(body);

                        logRecord.setAttributes(logRecordDoc.get("attributes", List.class));
                        logRecordsList.add(logRecord);
                    }
                    scopeLogs.setLogRecords(logRecordsList);
                }
                scopeLogsList.add(scopeLogs);
            }
            eventsDTO.setScopeLogs(scopeLogsList);
        }

        result.add(eventsDTO);
    }
    System.out.println("----------events data------- " + result.size());
    return result;
}

}






