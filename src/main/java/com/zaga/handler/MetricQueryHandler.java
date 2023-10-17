package com.zaga.handler;


import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.zaga.entity.queryentity.metric.MetricDTO;
import com.zaga.repo.MetricQueryRepo;

import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricQueryHandler {

    @Inject
    MetricQueryRepo metricQueryRepo;

    @Inject
    MongoClient mongoClient;

    public List<MetricDTO> getAllMetricData() {
        return metricQueryRepo.listAll();
    }

    // public List<MetricDTO> getMetricData(LocalDate from,LocalDate to,String serviceName) {
    //    List<MetricDTO> results = metricQueryRepo.getMetricData(from,to, serviceName);
    //     return results;
    // }
   
public List<MetricDTO> getMetricData(LocalDate from, LocalDate to, String serviceName) {
    Bson timeFilter = createCustomDateFilter(from, to);
    Bson serviceNameFilter = Filters.eq("serviceName", serviceName);

    Bson finalFilter = Filters.and(timeFilter, serviceNameFilter);

    MongoCollection<Document> collection = mongoClient
            .getDatabase("OtelMetric")
            .getCollection("MetricDTO");

    List<MetricDTO> filteredResults = new ArrayList<>();
    
    try (MongoCursor<Document> cursor = collection.find(finalFilter).iterator()) {
        while (cursor.hasNext()) {
            Document document = cursor.next();
            MetricDTO metricDTO = convertDocumentToMetricDTO(document);
            filteredResults.add(metricDTO);
        }
    }
    
    return filteredResults;
}
private MetricDTO convertDocumentToMetricDTO(Document document) {

    MetricDTO metricDTO = new MetricDTO();

    metricDTO.setDate(document.getDate("date")); 
    metricDTO.setServiceName(document.getString("serviceName")); 
    metricDTO.setCpuUsage(document.getDouble("cpuUsage"));
    metricDTO.setMemoryUsage(document.getInteger("memoryUsage"));



    return metricDTO;
}

private Bson createCustomDateFilter(LocalDate from, LocalDate to) {
    return Filters.and(
            Filters.gte("date", from.atStartOfDay()),
            Filters.lt("date", to.plusDays(1).atStartOfDay())
    );
  }

}
