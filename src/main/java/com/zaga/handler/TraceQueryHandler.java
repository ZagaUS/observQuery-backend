package com.zaga.handler;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.zaga.entity.otellog.ScopeLogs;
import com.zaga.entity.otellog.scopeLogs.LogRecord;
import com.zaga.entity.otellog.scopeLogs.logRecord.LogAttribute;
import com.zaga.entity.oteltrace.scopeSpans.Spans;
import com.zaga.entity.queryentity.log.LogDTO;
import com.zaga.entity.queryentity.trace.DBMetric;
import com.zaga.entity.queryentity.trace.KafkaMetrics;
import com.zaga.entity.queryentity.trace.SpanDTO;
import com.zaga.entity.queryentity.trace.StatusCodeRange;
import com.zaga.entity.queryentity.trace.TraceDTO;
import com.zaga.entity.queryentity.trace.TraceMetrics;
import com.zaga.entity.queryentity.trace.TraceQuery;
import com.zaga.entity.queryentity.trace.TraceSpanDTO;
import com.zaga.repo.LogQueryRepo;
import com.zaga.repo.TraceQueryRepo;

import io.quarkus.cache.CacheResult;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

@ApplicationScoped
public class TraceQueryHandler {

  @Inject
  TraceQueryRepo traceQueryRepo;

  @Inject
  LogQueryRepo logQueryRepo;

  @Inject
  MongoClient mongoClient;

  // getting all the datas from traceDTO entity
  public List<TraceDTO> getSampleTrace() {
    List<TraceDTO> traceList = traceQueryRepo.listAll();
    return traceList;
  }

  public List<TraceDTO> getTraceProduct() {
    List<TraceDTO> traceList = traceQueryRepo.listAll();
    return traceList;
  }

  private Bson createCustomDateFilter(LocalDate from, LocalDate to) {
    return Filters.and(
        Filters.gte("createdTime", from.atStartOfDay()),
        Filters.lt("createdTime", to.plusDays(1).atStartOfDay()));
  }

  private FindIterable<Document> getFilteredResults(TraceQuery query, int page, int pageSize, LocalDate from,
      LocalDate to, int minutesAgo) {
    List<Bson> filters = new ArrayList<>();

    if (from != null && to != null) {
      Bson timeFilter = createCustomDateFilter(from, to);
      filters.add(timeFilter);
    } else if (minutesAgo > 0) {
      LocalDate currentDate = LocalDate.now();

      if (from != null && from.isEqual(currentDate)) {
        // If the date is the current date, apply time filter based on minutes ago
        long currentTimeInMillis = System.currentTimeMillis();
        long timeAgoInMillis = currentTimeInMillis - (minutesAgo * 60 * 1000);

        // Ensure that the time filter doesn't go beyond the current day
        long startOfDayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (timeAgoInMillis < startOfDayMillis) {
          timeAgoInMillis = startOfDayMillis;
        }

        Bson timeFilter = Filters.gte("createdTime", new Date(timeAgoInMillis));
        filters.add(timeFilter);
      } else if (from != null) {
        // If a specific date is provided, use it for filtering
        Bson timeFilter = createCustomDateFilter(from, from);
        filters.add(timeFilter);
      }
    }

    if (query.getMethodName() != null && !query.getMethodName().isEmpty()) {
      Bson methodNameFilter = Filters.in("methodName", query.getMethodName());
      filters.add(methodNameFilter);
    }

    if (query.getServiceName() != null && !query.getServiceName().isEmpty()) {
      Bson serviceNameFilter = Filters.in("serviceName", query.getServiceName());
      filters.add(serviceNameFilter);
    }

    if (query.getDuration() != null) {
      Bson durationFilter = Filters.and(
          Filters.gte("duration", query.getDuration().getMin()),
          Filters.lte("duration", query.getDuration().getMax()));
      filters.add(durationFilter);
    }

    List<Bson> statusCodeFilters = new ArrayList<>();
    if (query.getStatusCode() != null && !query.getStatusCode().isEmpty()) {
      for (StatusCodeRange statusCodeRange : query.getStatusCode()) {
        statusCodeFilters.add(
            Filters.and(
                Filters.gte("statusCode", statusCodeRange.getMin()),
                Filters.lte("statusCode", statusCodeRange.getMax())));
      }
    }

    if (!statusCodeFilters.isEmpty()) {
      Bson statusCodeFilter = Filters.or(statusCodeFilters);
      filters.add(statusCodeFilter);
    }

    Bson filter = Filters.and(filters);

    MongoCollection<Document> collection = mongoClient
        .getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    Bson projection = Projections.excludeId();
    Bson sort = Sorts.descending("createdTime");

    return collection
        .find(filter)
        .projection(projection)
        .sort(sort)
        .skip((page - 1) * pageSize)
        .limit(pageSize);
  }

  // getTrace by multiple queries like serviceName, method, duration and
  // statuscode from TraceDTO entity
  public List<TraceDTO> searchTracesPaged(TraceQuery query, int page, int pageSize, LocalDate from, LocalDate to,
      int minutesAgo) {

    // Swap 'from' and 'to' if 'to' is earlier than 'from'
    if (from != null && to != null && to.isBefore(from)) {
      LocalDate temp = from;
      from = to;
      to = temp;
    }

    FindIterable<Document> result = getFilteredResults(query, page, pageSize, from, to, minutesAgo);

    List<TraceDTO> traceDTOList = new ArrayList<>();
    try (MongoCursor<Document> cursor = result.iterator()) {
      while (cursor.hasNext()) {
        Document document = cursor.next();
        TraceDTO traceDTO = new TraceDTO();

        traceDTO.setTraceId(document.getString("traceId"));
        traceDTO.setServiceName(document.getString("serviceName"));
        Object durationObject = document.get("duration");
        if (durationObject instanceof Integer) {
          traceDTO.setDuration(((Integer) durationObject).longValue());
        } else if (durationObject instanceof Long) {
          traceDTO.setDuration((Long) durationObject);
        }

        Object statusCodeObject = document.get("statusCode");
        if (statusCodeObject instanceof Integer) {
          traceDTO.setStatusCode(((Integer) statusCodeObject).longValue());
        } else if (statusCodeObject instanceof Long) {
          traceDTO.setStatusCode((Long) statusCodeObject);
        }
        traceDTO.setMethodName(document.getString("methodName"));
        traceDTO.setOperationName(document.getString("operationName"));
        traceDTO.setSpanCount(document.getString("spanCount"));
        traceDTO.setCreatedTime(document.getDate("createdTime"));
        List<Spans> spansList = (List<Spans>) document.get("spans");
        traceDTO.setSpans(spansList);

        traceDTOList.add(traceDTO);
      }
    }

    return traceDTOList;
  }

  public long countQueryTraces(TraceQuery query, LocalDate from, LocalDate to, int minutesAgo) {
    // Swap 'from' and 'to' if 'to' is earlier than 'from'
    if (from != null && to != null && to.isBefore(from)) {
      LocalDate temp = from;
      from = to;
      to = temp;
    }

    FindIterable<Document> result = getFilteredResults(query, 0, Integer.MAX_VALUE, from, to, minutesAgo);

    long totalCount = result.into(new ArrayList<>()).size();
    return totalCount;
  }

  // filter api sort
  // Sort by created time in descending order
  public List<TraceDTO> getTraceFilterOrderByCreatedTimeDesc(List<TraceDTO> traceList) {
    return traceList.stream()
        .sorted(Comparator.comparing(TraceDTO::getCreatedTime, Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

  // Sort by created time in ascending order
  public List<TraceDTO> getTraceFilterAsc(List<TraceDTO> traceList) {
    return traceList.stream()
        .sorted(Comparator.comparing(TraceDTO::getCreatedTime))
        .collect(Collectors.toList());
  }

  // Sort by error first
  public List<TraceDTO> getTraceFilterOrderByErrorFirst(List<TraceDTO> traceList) {
    return traceList.stream()
        .sorted(Comparator
            .comparing(TraceDTO::getStatusCode, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TraceDTO::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());
  }

  // Sort by duration in descending order
  public List<TraceDTO> getTraceFilterOrderByDuration(List<TraceDTO> traceList) {
    return traceList.stream()
        .filter(trace -> trace.getDuration() != null)
        .sorted(Comparator.comparing(TraceDTO::getDuration, Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

 
  // sort order decending
  @CacheResult(cacheName = "trace-get-all-sorting-decending")
  public List<TraceDTO> getAllTracesOrderByCreatedTimeDesc(List<String> serviceNameList) {
    return traceQueryRepo.findAllOrderByCreatedTimeDesc(serviceNameList);
  }

  // sort order ascending
  @CacheResult(cacheName = "trace-get-all-sorting-ascending")
  public List<TraceDTO> getAllTracesAsc(List<String> serviceNameList) {
    return traceQueryRepo.findAllOrderByCreatedTimeAsc(serviceNameList);
  }

  // sort order error first
  @CacheResult(cacheName = "trace-get-all-sorting-errorFirst")
  public List<TraceDTO> findAllOrderByErrorFirst(List<String> serviceNameList) {
    MongoCollection<Document> traceCollection = mongoClient
        .getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    List<TraceDTO> allTraces = traceCollection.find(TraceDTO.class).into(new ArrayList<>());

    List<TraceDTO> sortedTraces = allTraces.stream()
        .filter(trace -> serviceNameList.contains(trace.getServiceName())) // Filter by service name list
        .sorted(Comparator
            // Sort by error status first (statusCode >= 400 && statusCode <= 599)
            .comparing((TraceDTO trace) -> {
              Long statusCode = trace.getStatusCode();
              return (statusCode != null && statusCode >= 400 && statusCode <= 599) ? 0 : 1;
            })
            // Then sort by status code in descending order
            .thenComparing(TraceDTO::getStatusCode, Comparator.nullsLast(Comparator.reverseOrder()))
            // Finally, sort by created time in descending order
            .thenComparing(TraceDTO::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());
    return sortedTraces;
  }

  @CacheResult(cacheName = "trace-get-all-sorting-peakedValueFirst")
  public List<TraceDTO> findAllOrderByDuration(List<String> serviceNameList) {
    MongoCollection<Document> traceCollection = mongoClient
        .getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    List<TraceDTO> allTraces = traceCollection.find(TraceDTO.class).into(new ArrayList<>());

    List<TraceDTO> sortedTraces = allTraces.stream()
        .filter(trace -> serviceNameList.contains(trace.getServiceName()))
        .filter(trace -> trace.getDuration() != null) // Add a null check for duration
        .sorted(Comparator
            .comparing(TraceDTO::getDuration, Comparator.reverseOrder()))
        .collect(Collectors.toList());

    return sortedTraces;
  }

  // getByTraceId sort the spans and if some traceId Has same value it will merge
  // the value
  public List<Spans> sortingParentChildOrder(List<Spans> spanData) {
    Map<String, List<Spans>> spanTree = new HashMap<>();

    List<Spans> rootSpans = new ArrayList<>();

    for (Spans span : spanData) {
      // String spanId = span.getSpanId();
      String parentId = span.getParentSpanId();
      if (parentId == null || parentId.isEmpty()) {
        rootSpans.add(span);
      } else {
        spanTree.computeIfAbsent(parentId, k -> new ArrayList<>()).add(span);
      }
    }

    List<Spans> sortedSpans = new ArrayList<>();

    for (Spans rootSpan : rootSpans) {
      sortSpans(rootSpan, spanTree, sortedSpans);
    }

    return sortedSpans;
  }

  private void sortSpans(Spans span, Map<String, List<Spans>> spanTree, List<Spans> sortedSpans) {
    sortedSpans.add(span);
    List<Spans> childSpans = spanTree.get(span.getSpanId());
    if (childSpans != null) {
      for (Spans childSpan : childSpans) {
        sortSpans(childSpan, spanTree, sortedSpans);
      }
    }
  }

  // Method to merge spans with the same traceId

  public List<TraceDTO> mergeTraces(List<TraceDTO> traces) {
    Map<String, TraceDTO> parentTraces = new HashMap<>();
    Map<String, TraceDTO> childTraces = new HashMap<>();

    for (TraceDTO trace : traces) {
      String traceId = trace.getTraceId();
      boolean hasNullParentSpan = false;

      for (Spans span : trace.getSpans()) {
        if (span.getParentSpanId() == null || span.getParentSpanId().isEmpty()) {
          hasNullParentSpan = true;
          break;
        }
      }

      if (hasNullParentSpan) {
        parentTraces.put(traceId, trace);
      } else {
        childTraces.put(traceId, trace);
      }
    }

    for (TraceDTO parentTrace : parentTraces.values()) {
      String traceId = parentTrace.getTraceId();
      TraceDTO childTrace = childTraces.get(traceId);

      if (childTrace != null) {
        // Merge the spans of the child trace into the parent trace
        parentTrace.getSpans().addAll(childTrace.getSpans());
      }
    }

    // Sort the spans within each merged trace
    for (TraceDTO mergedTrace : parentTraces.values()) {
      mergedTrace.setSpans(sortingParentChildOrder(mergedTrace.getSpans()));
    }

    return new ArrayList<>(parentTraces.values());
  }

  public List<TraceSpanDTO> getModifiedTraceSpanDTO(List<TraceDTO> mergedTraces) {
    List<TraceSpanDTO> traceSpanDTOList = new ArrayList<>();

    for (TraceDTO trace : mergedTraces) {
      String traceID = trace.getTraceId();
      List<Spans> spans = trace.getSpans();

      List<SpanDTO> spanDTOList = new ArrayList<>();

      for (Spans span : spans) {
        SpanDTO spanDTO = new SpanDTO();
        spanDTO.setSpans(span);

        List<LogDTO> logDTOs = fetchLogDTOsForSpanId(span.getSpanId());

        // Filter the LogDTO objects with severityText "ERROR" or "SEVERE"
        List<LogDTO> matchingLogDTOs = logDTOs.stream()
            .filter(logDTO -> "ERROR".equals(logDTO.getSeverityText()) || "SEVERE".equals(logDTO.getSeverityText()))
            .collect(Collectors.toList());

        if (!matchingLogDTOs.isEmpty()) {
          spanDTO.setErrorStatus(true);
          spanDTO.setLogAttributes(matchingLogDTOs.stream()
              .flatMap(logDTO -> extractLogAttributes(logDTO).stream())
              .collect(Collectors.toList()));

          // Set traceId and spanId from the first matching LogRecord (assuming there's at
          // least one)
          LogRecord firstMatchingLogRecord = matchingLogDTOs.get(0).getScopeLogs().get(0).getLogRecords().get(0);
          spanDTO.setLogTraceId(firstMatchingLogRecord.getTraceId());
          spanDTO.setLogSpanId(firstMatchingLogRecord.getSpanId());
          spanDTO.setErrorMessage(firstMatchingLogRecord.getBody()); // Assuming Body is a string
        }

        spanDTOList.add(spanDTO);
      }

      // Create a new TraceSpanDTO with the same properties but using the modified
      // spanDTOList
      TraceSpanDTO traceSpanDTO = new TraceSpanDTO();

      traceSpanDTO.setTraceId(traceID);
      traceSpanDTO.setServiceName(trace.getServiceName());
      traceSpanDTO.setMethodName(trace.getMethodName());
      traceSpanDTO.setOperationName(trace.getOperationName());
      traceSpanDTO.setDuration(trace.getDuration());
      traceSpanDTO.setStatusCode(trace.getStatusCode());
      traceSpanDTO.setSpanCount(trace.getSpanCount());
      traceSpanDTO.setCreatedTime(trace.getCreatedTime());
      traceSpanDTO.setSpanDTOs(spanDTOList);

      traceSpanDTOList.add(traceSpanDTO);
    }

    return traceSpanDTOList;
  }

  // Function to fetch LogDTO objects based on spanId (adjust this based on your
  // data source)
  public List<LogDTO> fetchLogDTOsForSpanId(String spanId) {
    PanacheQuery<LogDTO> query = logQueryRepo.find("spanId", spanId);
    List<LogDTO> logDTOs = query.list();

    return logDTOs;
  }

  private List<LogAttribute> extractLogAttributes(LogDTO logDTO) {
    List<LogAttribute> logAttributes = new ArrayList<>();

    List<ScopeLogs> scopeLogs = logDTO.getScopeLogs();
    if (scopeLogs != null) {
      for (ScopeLogs scopeLog : scopeLogs) {
        List<LogRecord> logRecords = scopeLog.getLogRecords();
        if (logRecords != null) {
          for (LogRecord logRecord : logRecords) {
            // Extract the LogAttributes from the LogRecord's attributes list
            List<LogAttribute> attributes = logRecord.getAttributes();
            if (attributes != null) {
              logAttributes.addAll(attributes);
            }
          }
        }
      }
    }

    return logAttributes;
  }

  public List<LogDTO> getErroredLogDTO(List<TraceDTO> mergedTraces) {

    List<LogDTO> matchingLogDTOList = new ArrayList<>();
    for (TraceDTO trace : mergedTraces) {
      String traceID = trace.getTraceId();
      List<Spans> spans = trace.getSpans();
      List<LogDTO> logDTOList = logQueryRepo.find("traceId", traceID).list();

      for (LogDTO logDTO : logDTOList) {
        for (Spans span : spans) {
          if (logDTO.getSpanId().equals(span.getSpanId())) {
            // Add the matching LogDTO to the list
            matchingLogDTOList.add(logDTO);
          }
        }

      }
    }
    List<LogDTO> filteredLogDTOList = matchingLogDTOList.stream()
        .filter(logDTO -> "ERROR".equals(logDTO.getSeverityText()) || "SEVERE".equals(logDTO.getSeverityText()))
        .collect(Collectors.toList());

    return filteredLogDTOList;
  }

  public List<DBMetric> getAllDBMetrics(List<String> serviceNameList, LocalDate from, LocalDate to, int minutesAgo) {
    MongoCollection<Document> collection = mongoClient.getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    // Match service names
    Bson serviceNameFilter = Filters.in("serviceName", serviceNameList);

    List<Bson> pipeline = new ArrayList<>();

    if (from != null && to != null) {
      // Date-wise filtering
      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant())),
          Filters.lt("createdTime", Date.from(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())))));
    } else if (minutesAgo == 1440) {
      // Fetch data for today
      LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
      LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
      LocalDateTime endOfToday = startOfToday.plusDays(1);

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(startOfToday.atZone(ZoneId.systemDefault()).toInstant())),
          Filters.lt("createdTime", Date.from(endOfToday.atZone(ZoneId.systemDefault()).toInstant())))));
    } else if (minutesAgo > 0) {
      // Time-based filtering
      LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(minutesAgo);

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(thresholdTime.atZone(ZoneId.systemDefault()).toInstant())))));
    }

    pipeline.add(Aggregates.unwind("$spans"));
    pipeline.add(Aggregates.match(Filters.and(
        Filters.in("serviceName", serviceNameList),
        Filters.regex("spans.attributes.key", "^db", "m"))));
    pipeline.add(Aggregates.project(Projections.fields(
        Projections.computed("serviceName", "$serviceName"),
        Projections.computed("startTimeUnixNano", "$spans.startTimeUnixNano"),
        Projections.computed("endTimeUnixNano", "$spans.endTimeUnixNano"))));

    AggregateIterable<Document> result = collection.aggregate(pipeline);

    Map<String, DBMetric> dbMetricMap = new HashMap<>();

    result.forEach((Consumer<? super Document>) document -> {
      String serviceName = getAsStringOrFallback(document, "serviceName", "Unknown");

      String startTimeUnixNanoStr = document.getString("startTimeUnixNano");
      String endTimeUnixNanoStr = document.getString("endTimeUnixNano");

      long startTimeUnixNano = Long.parseLong(startTimeUnixNanoStr);
      long endTimeUnixNano = Long.parseLong(endTimeUnixNanoStr);

      ZonedDateTime startIST = Instant.ofEpochSecond(0, startTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      ZonedDateTime endIST = Instant.ofEpochSecond(0, endTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      long dbduration = ChronoUnit.MILLIS.between(startIST, endIST);

      String key = serviceName;
      DBMetric dbMetric = dbMetricMap.computeIfAbsent(key, k -> new DBMetric(serviceName, 0L, 0L));

      dbMetric.setDbCallCount(dbMetric.getDbCallCount() + 1);
      if (dbduration > 50) {
        dbMetric.setDbPeakLatencyCount(Math.max(dbMetric.getDbPeakLatencyCount(), dbduration));
      }
    });

    List<DBMetric> resultList = new ArrayList<>(dbMetricMap.values());

    return resultList;
  }

  public List<DBMetric> getAllDBPeakLatency(List<String> serviceNameList, LocalDate from, LocalDate to,
      int minutesAgo, int minPeakLatency, int maxPeakLatency) {
    MongoCollection<Document> collection = mongoClient.getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    // Match service names
    Bson serviceNameFilter = Filters.in("serviceName", serviceNameList);

    List<Bson> pipeline = new ArrayList<>();

    if (from != null && to != null) {
      // Date-wise filtering
      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant())),
          Filters.lt("createdTime", Date.from(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())))));
    } else if (minutesAgo == 1440) {
      // Fetch data for today
      LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
      LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
      LocalDateTime endOfToday = startOfToday.plusDays(1);

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(startOfToday.atZone(ZoneId.systemDefault()).toInstant())),
          Filters.lt("createdTime", Date.from(endOfToday.atZone(ZoneId.systemDefault()).toInstant())))));
    } else if (minutesAgo > 0) {
      // Time-based filtering
      LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(minutesAgo);

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^db", "m"),
          serviceNameFilter,
          Filters.gte("createdTime", Date.from(thresholdTime.atZone(ZoneId.systemDefault()).toInstant())))));
    }

    pipeline.add(Aggregates.unwind("$spans"));
    pipeline.add(Aggregates.match(Filters.and(
        Filters.in("serviceName", serviceNameList),
        Filters.regex("spans.attributes.key", "^db", "m"))));
    pipeline.add(Aggregates.project(Projections.fields(
        Projections.computed("serviceName", "$serviceName"),
        Projections.computed("startTimeUnixNano", "$spans.startTimeUnixNano"),
        Projections.computed("endTimeUnixNano", "$spans.endTimeUnixNano"))));

    AggregateIterable<Document> result = collection.aggregate(pipeline);

    Map<String, DBMetric> dbMetricMap = new HashMap<>();

    result.forEach((Consumer<? super Document>) document -> {
      String serviceName = getAsStringOrFallback(document, "serviceName", "Unknown");

      String startTimeUnixNanoStr = document.getString("startTimeUnixNano");
      String endTimeUnixNanoStr = document.getString("endTimeUnixNano");

      long startTimeUnixNano = Long.parseLong(startTimeUnixNanoStr);
      long endTimeUnixNano = Long.parseLong(endTimeUnixNanoStr);

      ZonedDateTime startIST = Instant.ofEpochSecond(0, startTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      ZonedDateTime endIST = Instant.ofEpochSecond(0, endTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      long dbduration = ChronoUnit.MILLIS.between(startIST, endIST);



      String key = serviceName;
      DBMetric dbMetric = dbMetricMap.computeIfAbsent(key, k -> new DBMetric(serviceName, 0L, 0L));

      
     if (dbduration >= minPeakLatency && dbduration <= maxPeakLatency) { 
        // Update the count based on the maximum dbDuration
        dbMetric.setDbPeakLatencyCount(Math.max(dbMetric.getDbPeakLatencyCount(), dbduration));
    }


     });
     

    List<DBMetric> resultList = new ArrayList<>(dbMetricMap.values());

    return resultList;
  }

  
  public List<KafkaMetrics> getAllKafkaMetrics(List<String> serviceNames, LocalDate from, LocalDate to,
      int minutesAgo) {
    MongoCollection<Document> collection = mongoClient.getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    List<Bson> pipeline = new ArrayList<>();
    LocalDateTime currentTime = LocalDateTime.now();

    if (from != null && to != null) {
      // Date-wise filtering
      ZonedDateTime fromZoned = from.atStartOfDay(ZoneId.systemDefault()).toInstant().atZone(ZoneId.systemDefault());
      ZonedDateTime toZoned = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
          .atZone(ZoneId.systemDefault());

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^messaging", "m"),
          Filters.in("serviceName", serviceNames),
          Filters.gte("createdTime", Date.from(fromZoned.toInstant())),
          Filters.lt("createdTime", Date.from(toZoned.toInstant())))));
    } else if (minutesAgo > 0) {
      // Time-based filtering
      LocalDateTime thresholdTime = currentTime.minusMinutes(minutesAgo);
      ZonedDateTime thresholdZoned = thresholdTime.atZone(ZoneId.systemDefault());

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^messaging", "m"),
          Filters.in("serviceName", serviceNames),
          Filters.gte("createdTime", Date.from(thresholdZoned.toInstant())))));
    }

    pipeline.add(Aggregates.unwind("$spans"));
    pipeline.add(Aggregates.match(Filters.and(
        Filters.in("serviceName", serviceNames),
        Filters.regex("spans.attributes.key", "^messaging", "m"))));
    pipeline.add(Aggregates.project(Projections.fields(
        Projections.computed("serviceName", "$serviceName"),
        Projections.computed("startTimeUnixNano", "$spans.startTimeUnixNano"),
        Projections.computed("endTimeUnixNano", "$spans.endTimeUnixNano"))));

    AggregateIterable<Document> result = collection.aggregate(pipeline);

    Map<String, KafkaMetrics> kafkaMetricsMap = new HashMap<>();

    result.forEach((Consumer<? super Document>) document -> {
      String serviceName = getAsStringOrFallback(document, "serviceName", "Unknown");

      String startTimeUnixNanoStr = document.getString("startTimeUnixNano");
      String endTimeUnixNanoStr = document.getString("endTimeUnixNano");

      long startTimeUnixNano = Long.parseLong(startTimeUnixNanoStr);
      long endTimeUnixNano = Long.parseLong(endTimeUnixNanoStr);

      ZonedDateTime startIST = Instant.ofEpochSecond(0, startTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      ZonedDateTime endIST = Instant.ofEpochSecond(0, endTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      long kafkaDuration = ChronoUnit.MILLIS.between(startIST, endIST);

      String key = serviceName;
      KafkaMetrics kafkaMetrics = kafkaMetricsMap.computeIfAbsent(key, k -> new KafkaMetrics(serviceName, 0L, 0L));

      kafkaMetrics.setKafkaCallCount(kafkaMetrics.getKafkaCallCount() + 1);
      if (kafkaDuration > 5) {
        kafkaMetrics.setKafkaPeakLatency(kafkaMetrics.getKafkaPeakLatency() + 1);
      }
    });

    List<KafkaMetrics> resultList = new ArrayList<>(kafkaMetricsMap.values());

    return resultList;
  }

  public List<KafkaMetrics> getAllKafkaPeakLatency(List<String> serviceNames, LocalDate from, LocalDate to,
      int minutesAgo, int minPeakLatency, int maxPeakLatency) {
    MongoCollection<Document> collection = mongoClient.getDatabase("OtelTrace")
        .getCollection("TraceDTO");

    List<Bson> pipeline = new ArrayList<>();
    LocalDateTime currentTime = LocalDateTime.now();

    if (from != null && to != null) {
      // Date-wise filtering
      ZonedDateTime fromZoned = from.atStartOfDay(ZoneId.systemDefault()).toInstant().atZone(ZoneId.systemDefault());
      ZonedDateTime toZoned = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
          .atZone(ZoneId.systemDefault());

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^messaging", "m"),
          Filters.in("serviceName", serviceNames),
          Filters.gte("createdTime", Date.from(fromZoned.toInstant())),
          Filters.lt("createdTime", Date.from(toZoned.toInstant())))));
    } else if (minutesAgo > 0) {
      // Time-based filtering
      LocalDateTime thresholdTime = currentTime.minusMinutes(minutesAgo);
      ZonedDateTime thresholdZoned = thresholdTime.atZone(ZoneId.systemDefault());

      pipeline.add(Aggregates.match(Filters.and(
          Filters.regex("spans.attributes.key", "^messaging", "m"),
          Filters.in("serviceName", serviceNames),
          Filters.gte("createdTime", Date.from(thresholdZoned.toInstant())))));
    }

    pipeline.add(Aggregates.unwind("$spans"));
    pipeline.add(Aggregates.match(Filters.and(
        Filters.in("serviceName", serviceNames),
        Filters.regex("spans.attributes.key", "^messaging", "m"))));
    pipeline.add(Aggregates.project(Projections.fields(
        Projections.computed("serviceName", "$serviceName"),
        Projections.computed("startTimeUnixNano", "$spans.startTimeUnixNano"),
        Projections.computed("endTimeUnixNano", "$spans.endTimeUnixNano"))));

    AggregateIterable<Document> result = collection.aggregate(pipeline);

    Map<String, KafkaMetrics> kafkaMetricsMap = new HashMap<>();

    result.forEach((Consumer<? super Document>) document -> {
      String serviceName = getAsStringOrFallback(document, "serviceName", "Unknown");

      String startTimeUnixNanoStr = document.getString("startTimeUnixNano");
      String endTimeUnixNanoStr = document.getString("endTimeUnixNano");

      long startTimeUnixNano = Long.parseLong(startTimeUnixNanoStr);
      long endTimeUnixNano = Long.parseLong(endTimeUnixNanoStr);

      ZonedDateTime startIST = Instant.ofEpochSecond(0, startTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      ZonedDateTime endIST = Instant.ofEpochSecond(0, endTimeUnixNano).atZone(ZoneId.of("Asia/Kolkata"));
      long kafkaDuration = ChronoUnit.MILLIS.between(startIST, endIST);

      String key = serviceName;
      KafkaMetrics kafkaMetrics = kafkaMetricsMap.computeIfAbsent(key, k -> new KafkaMetrics(serviceName, 0L, 0L));

      // kafkaMetrics.setKafkaCallCount(kafkaMetrics.getKafkaCallCount() + 1);
      // if (kafkaDuration > peakLatency) {
      //   kafkaMetrics.setKafkaPeakLatency(kafkaMetrics.getKafkaPeakLatency() + 1);
      // }
      if (kafkaDuration >= minPeakLatency && kafkaDuration <= maxPeakLatency) {
        kafkaMetrics.setKafkaPeakLatency(kafkaMetrics.getKafkaPeakLatency() + 1);
      }
    });

    List<KafkaMetrics> resultList = new ArrayList<>(kafkaMetricsMap.values());

    return resultList;
  }

  // Utility method to safely retrieve a string value or use a default fallback
  private String getAsStringOrFallback(Document document, String key, String fallback) {
    Object value = document.get(key);
    if (value instanceof String) {
      return (String) value;
    }
    return fallback;
  }

  @CacheResult(cacheName = "trace-summery-chart")
  public List<TraceMetrics> getAllTraceMetricCount(List<String> serviceNameList, LocalDate from, LocalDate to,
      int minutesAgo) {
    List<TraceDTO> traceList = traceQueryRepo.listAll();
    Map<String, TraceMetrics> metricsMap = new HashMap<>();

    Instant fromInstant = null;
    Instant toInstant = null;

    if (from != null && to != null) {
      // If both from and to are provided, consider the date range
      Instant startOfFrom = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
      Instant startOfTo = to.atStartOfDay(ZoneId.systemDefault()).toInstant();

      // Ensure that fromInstant is earlier than toInstant
      fromInstant = startOfFrom.isBefore(startOfTo) ? startOfFrom : startOfTo;
      toInstant = startOfFrom.isBefore(startOfTo) ? startOfTo : startOfFrom;

      toInstant = toInstant.plus(1, ChronoUnit.DAYS);
    } else if (minutesAgo > 0) {
      // If minutesAgo is provided, calculate the time range based on minutesAgo
      Instant currentInstant = Instant.now();
      Instant minutesAgoInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

      // Calculate the start of the current day
      Instant startOfCurrentDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

      // Ensure that fromInstant is later than the start of the current day
      if (minutesAgoInstant.isBefore(startOfCurrentDay)) {
        fromInstant = startOfCurrentDay;
      } else {
        fromInstant = minutesAgoInstant;
      }

      toInstant = currentInstant;
    } else {
      // Handle the case when neither date range nor minutesAgo is provided
      throw new IllegalArgumentException("Either date range or minutesAgo must be provided");
    }

    for (TraceDTO traceDTO : traceList) {
      Date traceCreateTime = traceDTO.getCreatedTime();
      if (traceCreateTime == null) {
        continue;
      }

      Instant traceInstant = traceCreateTime.toInstant();

      if (!serviceNameList.contains(traceDTO.getServiceName()) ||
          !traceInstant.isAfter(fromInstant) ||
          !traceInstant.isBefore(toInstant)) {
        // Skip processing if the service name is not in the specified list
        // or if traceInstant is not within the specified range
        continue;
      }

      String serviceName = traceDTO.getServiceName();
      TraceMetrics metrics = metricsMap.get(serviceName);

      if (metrics == null) {
        metrics = new TraceMetrics();
        metrics.setServiceName(serviceName);
        metrics.setApiCallCount(0L);
        metrics.setPeakLatency(0L);
        metrics.setTotalErrorCalls(0L);
        metrics.setTotalSuccessCalls(0L);
        metricsMap.put(serviceName, metrics);
      }

      calculateTraces(traceDTO, metrics);
    }

    return new ArrayList<>(metricsMap.values());
  }

  private void calculateTraces(TraceDTO traceDTO, TraceMetrics metrics) {
    if (traceDTO == null) {
      return;
    }

    if (traceDTO.getStatusCode() != null) {
      if (traceDTO.getStatusCode() >= 400 && traceDTO.getStatusCode() <= 599) {
        metrics.setTotalErrorCalls(metrics.getTotalErrorCalls() + 1);
      } else if (traceDTO.getStatusCode() >= 200 && traceDTO.getStatusCode() <= 299) {
        metrics.setTotalSuccessCalls(metrics.getTotalSuccessCalls() + 1);
      }
    }

   
    if (metrics.getApiCallCount() == null) {
      return;
    }

    metrics.setApiCallCount(metrics.getTotalErrorCalls() + metrics.getTotalSuccessCalls());

    
    if (traceDTO.getDuration() != null && traceDTO.getDuration() > 500) {
      metrics.setPeakLatency(metrics.getPeakLatency() + 1);
    } else {
      
    }
  }

  @CacheResult(cacheName = "trace-summery-chart-peakLatency")
  public List<TraceMetrics> getPeaKLatency(List<String> serviceNameList, LocalDate from, LocalDate to, int minutesAgo,
      int minpeakLatency, int maxpeakLatency) {
    List<TraceDTO> traceList = traceQueryRepo.listAll();
    Map<String, TraceMetrics> metricsMap = new HashMap<>();

    Instant fromInstant = null;
    Instant toInstant = null;

    if (from != null && to != null) {
      Instant startOfFrom = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
      Instant startOfTo = to.atStartOfDay(ZoneId.systemDefault()).toInstant();

      fromInstant = startOfFrom.isBefore(startOfTo) ? startOfFrom : startOfTo;
      toInstant = startOfFrom.isBefore(startOfTo) ? startOfTo : startOfFrom;

      toInstant = toInstant.plus(1, ChronoUnit.DAYS);
    } else if (minutesAgo > 0) {
      Instant currentInstant = Instant.now();
      Instant minutesAgoInstant = currentInstant.minus(minutesAgo, ChronoUnit.MINUTES);

      // Calculate the start of the current day
      Instant startOfCurrentDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

      if (minutesAgoInstant.isBefore(startOfCurrentDay)) {
        fromInstant = startOfCurrentDay;
      } else {
        fromInstant = minutesAgoInstant;
      }

      toInstant = currentInstant;
    } else {
      throw new IllegalArgumentException("Either date range or minutesAgo must be provided");
    }

    for (TraceDTO traceDTO : traceList) {
      Date traceCreateTime = traceDTO.getCreatedTime();
      if (traceCreateTime == null) {
        continue;
      }

      Instant traceInstant = traceCreateTime.toInstant();

      if (!serviceNameList.contains(traceDTO.getServiceName()) ||
          !traceInstant.isAfter(fromInstant) ||
          !traceInstant.isBefore(toInstant)) {
        continue;
      }

      String serviceName = traceDTO.getServiceName();
      TraceMetrics metrics = metricsMap.get(serviceName);

      if (metrics == null) {
        metrics = new TraceMetrics();
        metrics.setServiceName(serviceName);
        // metrics.setApiCallCount(0L);
        metrics.setPeakLatency(0L);
        // metrics.setTotalErrorCalls(0L);
        // metrics.setTotalSuccessCalls(0L);
        metricsMap.put(serviceName, metrics);
      }

      calculatePeakLatency(traceDTO, metrics, minpeakLatency, maxpeakLatency);
    }

    return new ArrayList<>(metricsMap.values());
  }

  
private void calculatePeakLatency(TraceDTO traceDTO, TraceMetrics metrics, int minpeakLatency, int maxpeakLatency) {
    if (traceDTO == null) {
      return;
    }
    Long duration = traceDTO.getDuration();
    if (duration != null && duration >= minpeakLatency && duration <= maxpeakLatency) {
        metrics.setPeakLatency(metrics.getPeakLatency() + 1);
    } else {
    }
  }

}
