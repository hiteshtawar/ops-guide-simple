package com.lca.productionsupport.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for task information returned by GET /api/v1/tasks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfo {
    private String taskId;
    private String taskName;
    private String description;
    private String exampleQuery;
    private List<ValidInput> validInputs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidInput {
        private String name;
        private List<String> list;
    }
}
