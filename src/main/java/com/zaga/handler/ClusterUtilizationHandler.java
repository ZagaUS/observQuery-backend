package com.zaga.handler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.BsonNull;
import org.bson.Document;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.zaga.entity.queryentity.cluster_utilization.ClusterUtilizationDTO;
import com.zaga.entity.queryentity.cluster_utilization.response.ClusterResponse;
import com.zaga.repo.ClusterUtilizationDTORepo;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClusterUtilizationHandler {

    @Inject
    ClusterUtilizationDTORepo clusterUtilizationDTORepo;

        @Inject
        MongoClient mongoClient;
        
        public List<ClusterUtilizationDTO> getAllClusterData() {
        return clusterUtilizationDTORepo.listAll();
        }

        // @CacheResult(cacheName = "wcluster-details")
        @CacheInvalidate(cacheName = "wcluster-details")
        public List<ClusterResponse> getAllClusterByDateAndTime(
        LocalDate from,
        LocalDate to,
        int minutesAgo,
        String clusterName,
        String nodeName
        ) {
        LocalDateTime startTime = LocalDateTime.now();
        MongoDatabase database = mongoClient.getDatabase("OtelClusterUtilization");
        MongoCollection<Document> collection = database.getCollection(
        "ClusterDTO"
        );

        List<ClusterResponse> result;

        if (from != null && to != null) {
        result =
                executeAggregationPipeline(
                collection,
                from,
                to,
                clusterName,
                nodeName
                );
        }
        else if (from != null && minutesAgo > 0) {
        result =
                executeAnotherLogic(collection, from, minutesAgo,clusterName,nodeName);
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

        // @CacheResult(cacheName = "mongodb-aggregation-one")
        // @CacheInvalidate(cacheName = "mongodb-aggregation-one")
        public List<ClusterResponse> executeAggregationPipeline(
        MongoCollection<Document> collection,
        LocalDate from,
        LocalDate to,
        String clusterName,
        String nodeName) {

        System.out.println("--------------[CLUSTER NAME / executeAggregationPipeline]------  " + clusterName);
                List<Document> pipeline = Arrays.asList(
                new Document("$addFields", 
                        new Document("justDate", 
                        new Document("$dateToString", 
                                new Document("format", "%m-%d-%Y")
                                .append("date", "$date")
                        )
                        )
                ), 
                new Document("$match", 
        new Document("$and", 
                Arrays.asList(
                new Document("justDate", 
                        new Document("$gte",from.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")))
                        .append("$lte",   to.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")))
                )
                )
        )
        ),
        new Document("$sort", 
                        new Document("date", 1L)
                ));

                System.out.println("------------------------------------------------------------");
                System.out.println(pipeline);
                System.out.println("------------------------------------------------------------");

                // matchPipeline with clusterName,NodeName , if Nodename == null then match with clustername only
                List<Document> aggregatedDocuments = matchPipeline(pipeline,clusterName,nodeName);
                // groupPipeline with clusterName,NodeName , if Nodename == null then group by only clustername
                List<Document> aggregatedGroupDocuments = groupPipeLine(aggregatedDocuments, clusterName, nodeName);
                System.out.println(aggregatedGroupDocuments);
                System.out.println("------------------------------------------------------------");
                AggregateIterable<Document> aggregationResult = collection.aggregate(aggregatedGroupDocuments);
                return resultData(aggregationResult);
        }
        
        @CacheResult(cacheName = "mongodb-aggregation-two")
        public List<ClusterResponse> executeAnotherLogic(
        MongoCollection<Document> collection,
        LocalDate from,
        Integer minutesAgo,
        String clusterName,
        String nodeName)
        {
                System.out.println("--------------[CLUSTER NAME / executeAnotherLogic]------  " + clusterName);
                List<Document> pipeline = Arrays.asList(
                new Document("$match", new Document("$expr", new Document("$and", Arrays.asList(
                        new Document("$gte", Arrays.asList("$date",
                        new Document("$subtract", Arrays.asList(new java.util.Date(), minutesAgo * 60L * 1000L)))), 
                        new Document("$lte", Arrays.asList("$date", 
                                new java.util.Date()))
                )))),
                new Document("$sort", 
                        new Document("date", 1L)
                ));
                System.out.println("------------------------------------------------------------");
                System.out.println(pipeline);
                System.out.println("------------------------------------------------------------");
                
                // matchPipeline with clusterName,NodeName , if Nodename == null then match with clustername only
                List<Document> aggregatedDocuments = matchPipeline(pipeline,clusterName,nodeName);
                // groupPipeline with clusterName,NodeName , if Nodename == null then group by only clustername
                List<Document> aggregatedGroupDocuments = groupPipeLine(aggregatedDocuments, clusterName, nodeName);
                System.out.println(aggregatedGroupDocuments);
                System.out.println("------------------------------------------------------------");
                AggregateIterable<Document> aggregationResult = collection.aggregate(aggregatedGroupDocuments);
                return resultData(aggregationResult);
        }


        public List<ClusterResponse> resultData(AggregateIterable<Document> aggregationResult){
                List<ClusterResponse> result = new ArrayList<>();
                for (Document document : aggregationResult) {
                        ClusterResponse clusterResponse = new ClusterResponse();
                        clusterResponse.setCpuUsage(document.getDouble("cpuUsage"));
                        clusterResponse.setMemoryUsage(document.getDouble("memoryUsage"));
                        clusterResponse.setMemoryAvailable(document.getDouble("memoryAvailable"));
                        clusterResponse.setFileSystemCapacity(document.getDouble("fileSystemCapacity"));
                        clusterResponse.setFileSystemUsage(document.getDouble("fileSystemUsage"));
                        clusterResponse.setFileSystemAvailable(document.getDouble("fileSystemAvailable"));
                        result.add(clusterResponse);
                        }
                return result;
        }

        public List<Document> groupPipeLine(List<Document> pipeline, String clusterName,String nodeName){
                List<Document> groupPipeLine;
                if(nodeName != null){
                        groupPipeLine = Arrays.asList(new Document("$group", 
                        new Document("_id", 
                        new Document("nodeName","$nodeName")
                                .append("clusterName", "$clusterName"))
                                .append("avgCpuUsage", 
                        new Document("$avg", "$cpuUsage"))
                                .append("avgMemoryUsage", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$memoryUsage", 1024L))))
                                .append("avgMemoryAvailable", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$memoryAvailable", 1024L))))
                                .append("avgFileSystemCapacity", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemCapacity", 1024L * 1024L * 1024L))))
                                .append("avgFileSystemUsage", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemUsage", 1024L * 1024L * 1024L))))
                                .append("avgFileSystemAvailable", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemAvailable", 1024L * 1024L * 1024L))))), 
                        new Document("$project", 
                        new Document("cpuUsage", "$avgCpuUsage")
                                .append("memoryUsage", "$avgMemoryUsage")
                                .append("memoryAvailable", "$avgMemoryAvailable")
                                .append("fileSystemCapacity", "$avgFileSystemCapacity")
                                .append("fileSystemUsage", "$avgFileSystemUsage")
                                .append("fileSystemAvailable", "$avgFileSystemAvailable")));
                }else{
                        groupPipeLine = Arrays.asList(new Document("$group", 
                        new Document("_id", 
                        new Document("clusterName","$clusterName"))
                                .append("avgCpuUsage", 
                        new Document("$avg", "$cpuUsage"))
                                .append("avgMemoryUsage", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$memoryUsage", 1024L))))
                                .append("avgMemoryAvailable", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$memoryAvailable", 1024L))))
                                .append("avgFileSystemCapacity", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemCapacity", 1024L * 1024L * 1024L))))
                                .append("avgFileSystemUsage", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemUsage", 1024L * 1024L * 1024L))))
                                .append("avgFileSystemAvailable", 
                        new Document("$avg", 
                        new Document("$divide", Arrays.asList("$fileSystemAvailable", 1024L * 1024L * 1024L))))), 
                        new Document("$project", 
                        new Document("cpuUsage", "$avgCpuUsage")
                                .append("memoryUsage", "$avgMemoryUsage")
                                .append("memoryAvailable", "$avgMemoryAvailable")
                                .append("fileSystemCapacity", "$avgFileSystemCapacity")
                                .append("fileSystemUsage", "$avgFileSystemUsage")
                                .append("fileSystemAvailable", "$avgFileSystemAvailable")));
                }
                
                // List<Document> aggregatedList = Stream.concat(pipeline.stream(), groupPipeLine.stream()).toList();
                List<Document> aggregatedList = Stream.concat(pipeline.stream(), groupPipeLine.stream()).collect(Collectors.toList());

                return aggregatedList;
        }
        public List<Document> matchPipeline(List<Document> pipeline,String clusterName,String nodeName){
                
                List<Document> matchPipeLine;
                if(nodeName != null){
                        System.out.println("------------[MATCH PIPELINE / IF]---------- " + clusterName + "  " + nodeName);
                        matchPipeLine = Arrays.asList(new Document("$match", 
                        new Document("clusterName", clusterName)
                                .append("nodeName", nodeName)));
                }else{
                        System.out.println("------------[MATCH PIPELINE / ELSE]---------- " + clusterName + "  " + nodeName);
                        matchPipeLine = Arrays.asList(new Document("$match", 
                        new Document("clusterName", clusterName)));
                }
                // List<Document> matchFilter = Stream.concat(pipeline.stream(), matchPipeLine.stream()).toList();
                List<Document> matchFilter = Stream.concat(pipeline.stream(), matchPipeLine.stream()).collect(Collectors.toList());
                return matchFilter;
        }
}
