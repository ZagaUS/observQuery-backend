package com.zaga.entity.queryentity.events;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zaga.entity.otelevent.ScopeLogs;

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
@MongoEntity(collection = "EventsDTO", database = "OtelEvent")
public class EventsDTO {
    private Date createdTime;
    private String nodeName;
    private String objectKind;
    private String objectName;
    private String objectFieldPath;
    private String clusterName;
    private String severityText;
    private List<ScopeLogs> scopeLogs;

}
