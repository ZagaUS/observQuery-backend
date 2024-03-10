package com.zaga.entity.queryentity.node;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties("id")
@MongoEntity(collection = "NodeMetricDTO",database = "OtelClusterUtilization")
public class NodeMetricDTO extends PanacheMongoEntity{
    private Date date;
    private String clusterName;
    private Double cpuUsage;
    private Long memoryUsage;
    private String nodeName;
}
