package com.zaga.handler;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaga.entity.queryentity.podMetrics.MetricDTO;
import com.zaga.entity.queryentity.podMetrics.PodMetricDTO;
import com.zaga.entity.queryentity.podMetrics.PodMetricsResponseData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.bson.Document;

@ApplicationScoped
public class PodMetricsHandler {

  @Inject
  MongoClient mongoClient;


    public List<PodMetricsResponseData> getAllPodMetricsByDate(LocalDate from, LocalDate to, int page, int pageSize, int minutesAgo) {
        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("------------DB call startTimestamp------ " + startTime);
    
        MongoDatabase database = mongoClient.getDatabase("OtelPodMetrics");
        MongoCollection<Document> collection = database.getCollection("PodMetricDTO");
    
        List<PodMetricsResponseData> result;
    
        if (minutesAgo > 0) {
            result = executeAggregationPipelineWithMinutesAgo(database, collection, minutesAgo,page,pageSize);
        } else {
            result = executeAggregationPipeline(database, collection, from, to,page,pageSize);
        }
    
        LocalDateTime endTime = LocalDateTime.now();
        System.out.println("------------DB call endTimestamp------ " + endTime);
        System.out.println("-----------DB call ended Timestamp------ " + Duration.between(startTime, endTime));
    
        return result;
    }
    

// @SuppressWarnings("unchecked")
public List<PodMetricsResponseData> executeAggregationPipeline(
        MongoDatabase database,
        MongoCollection<Document> collection,
        LocalDate from,
        LocalDate to,
        int page,
        int pageSize
    ) {
        int skip = (page - 1) * pageSize; // Calculate the number of documents to skip
    
        List<Document> pipeline = Arrays.asList(
    new Document("$addFields", 
        new Document("justDate", 
            new Document("$dateToString", 
                new Document("format", "%m-%d-%Y").append("date", "$date")))),
    new Document("$match", 
        new Document("justDate", 
            new Document("$gte", from.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")))
                .append("$lte", to.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))))),
    new Document("$sort", new Document("date", 1)), // sort by date ascending
    new Document("$group", 
        new Document("_id", 
            new Document("namespaceName", "$namespaceName")
                .append("podName", "$podName"))
                .append("namespaceName", new Document("$first", "$namespaceName"))
                .append("podName", new Document("$first", "$podName"))
                .append("metrics", new Document("$push", "$$ROOT"))
                .append("totalCount", new Document("$sum", 1))),
    new Document("$project", 
        new Document("_id", 0)
            .append("namespaceName", "$_id.namespaceName")
            .append("podName", "$_id.podName")
            .append("metrics", 
                new Document("$slice", Arrays.asList("$metrics", skip, pageSize)))
            .append("totalCount", 1))
);

AggregateIterable<Document> aggregationResult = collection.aggregate(pipeline, Document.class);

List<PodMetricsResponseData> resultList = new ArrayList<>();
int totalCount = 0; 

for (Document doc : aggregationResult) {
    List<Document> metrics = (List<Document>) doc.get("metrics");
    if (metrics != null && !metrics.isEmpty()) {
        totalCount += doc.getInteger("totalCount"); 
        String namespaceName = doc.getString("namespaceName");
        String podName = doc.getString("podName");
        PodMetricsResponseData responseData = new PodMetricsResponseData();
        responseData.setNamespaceName(namespaceName);
        responseData.setTotalCount(totalCount); 
        // responseData.setPodName(podName);
        List<PodMetricDTO> podMetricsList = new ArrayList<>();

        List<MetricDTO> metricDTOs = new ArrayList<>();
        for (Document metricDoc : metrics) {
            MetricDTO metricData = new MetricDTO();
            metricData.setCpuUsage(metricDoc.getDouble("cpuUsage"));
            metricData.setDate(metricDoc.getDate("date"));
            metricData.setMemoryUsage(metricDoc.getLong("memoryUsage"));
            metricDTOs.add(metricData);
        }

        PodMetricDTO podMetricsData = new PodMetricDTO();
        podMetricsData.setPodName(podName);
        podMetricsData.setNamespaceName(namespaceName);
        podMetricsData.setMetrics(metricDTOs);
        podMetricsList.add(podMetricsData);

        responseData.setPods(podMetricsList);

        resultList.add(responseData);
    }
}

resultList.sort(Comparator.comparing(PodMetricsResponseData::getNamespaceName));

return resultList;
}
    
    

    public List<PodMetricsResponseData> executeAggregationPipelineWithMinutesAgo(
        MongoDatabase database, MongoCollection<Document> collection,
        int minutesAgo, int page, int pageSize) {
    LocalDateTime currentTime = LocalDateTime.now().minusMinutes(minutesAgo);
    int skip = (page - 1) * pageSize;

    List<Document> pipeline = Arrays.asList(
        new Document("$match",
            new Document("date",
                new Document("$gte", Date.from(currentTime.atZone(ZoneId.systemDefault()).toInstant()))
            )
        ),
        new Document("$sort", new Document("date", 1)),
        new Document("$group",
            new Document("_id",
                new Document("namespaceName", "$namespaceName")
                    .append("podName", "$podName"))
                .append("namespaceName", new Document("$first", "$namespaceName"))
                .append("podName", new Document("$first", "$podName"))
                .append("metrics", new Document("$push",
                    new Document("cpuUsage", "$cpuUsage")
                        .append("memoryUsage", "$memoryUsage")
                        .append("date", "$date")))
                .append("totalCount", new Document("$sum", 1)) // Calculate total count for each pod
        ),
        new Document("$project",
            new Document("_id", 0)
                .append("namespaceName", "$_id.namespaceName")
                .append("podName", "$_id.podName")
                .append("metrics",
                    new Document("$slice", Arrays.asList("$metrics", skip, pageSize)))
                .append("totalCount", 1)
        )
    );

  
    AggregateIterable<Document> aggregationResult = collection.aggregate(pipeline, Document.class);
    List<PodMetricsResponseData> resultList = new ArrayList<>();
int totalCount = 0;

for (Document doc : aggregationResult) {
    List<Document> metrics = (List<Document>) doc.get("metrics");
    if (metrics != null && !metrics.isEmpty()) {
        totalCount += doc.getInteger("totalCount");
        String namespaceName = doc.getString("namespaceName");
        String podName = doc.getString("podName");
        PodMetricsResponseData responseData = new PodMetricsResponseData();
        responseData.setNamespaceName(namespaceName);
        responseData.setTotalCount(totalCount); 
        // responseData.setPodName(podName);
        List<PodMetricDTO> podMetricsList = new ArrayList<>();

        List<MetricDTO> metricDTOs = new ArrayList<>();
        for (Document metricDoc : metrics) {
            MetricDTO metricData = new MetricDTO();
            metricData.setCpuUsage(metricDoc.getDouble("cpuUsage"));
            metricData.setDate(metricDoc.getDate("date"));
            metricData.setMemoryUsage(metricDoc.getLong("memoryUsage"));
            metricDTOs.add(metricData);
        }

        PodMetricDTO podMetricsData = new PodMetricDTO();
        podMetricsData.setPodName(podName);
        podMetricsData.setNamespaceName(namespaceName);
        podMetricsData.setMetrics(metricDTOs);
        podMetricsList.add(podMetricsData);

        responseData.setPods(podMetricsList);
        

        resultList.add(responseData);
    }
}

resultList.sort(Comparator.comparing(PodMetricsResponseData::getNamespaceName));
return resultList;
}

}




    
