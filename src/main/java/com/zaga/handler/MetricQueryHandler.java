package com.zaga.handler;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.zaga.entity.queryentity.metric.MetricDTO;
import com.zaga.repo.MetricQueryRepo;

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

    public List<MetricDTO> getMetricData(LocalDate from, LocalDate to, String serviceName, int minutesAgo) {
        Bson timeFilter;
    
        // Rearrange 'from' and 'to' if 'to' is earlier than 'from'
        if (from != null && to != null && to.isBefore(from)) {
            LocalDate temp = from;
            from = to;
            to = temp;
        }
    
        if (from != null && to != null) {
            timeFilter = createCustomDateFilter(from, to);
        } else if (minutesAgo > 0) {
            LocalDateTime currentDateTime = LocalDateTime.now();
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        
            // Calculate 'fromDateTime' based on 'minutesAgo' but ensure it doesn't go beyond the start of today
            LocalDateTime fromDateTime = currentDateTime.minusMinutes(minutesAgo);
            if (fromDateTime.isBefore(startOfToday)) {
                fromDateTime = startOfToday;
            }
        
            // Set 'toDateTime' to the current time
            LocalDateTime toDateTime = currentDateTime;
        
            timeFilter = Filters.and(
                Filters.gte("date", fromDateTime),
                Filters.lt("date", toDateTime)
            );
        }
         else {
           
            throw new IllegalArgumentException("Either date range or minutesAgo must be provided");
        }
    
    //  Bson serviceNameFilter = Filters.regex("serviceName", Pattern.compile(serviceName, Pattern.CASE_INSENSITIVE));
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
