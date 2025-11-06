package com.productionsupport.model;

/**
 * Enum for supported operational task types
 */
public enum TaskType {
    CANCEL_CASE("Cancel Case"),
    UPDATE_CASE_STATUS("Update Case Status"),
    UNKNOWN("Unknown");

    private final String displayName;

    TaskType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get task type by ID string (for backward compatibility)
     */
    public static TaskType fromString(String taskId) {
        if (taskId == null) {
            return UNKNOWN;
        }
        
        try {
            return TaskType.valueOf(taskId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}

