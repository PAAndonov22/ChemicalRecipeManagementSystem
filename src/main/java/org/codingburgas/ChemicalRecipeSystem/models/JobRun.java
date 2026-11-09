package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Entity;

/** Execution log of a scheduled job. */
@Entity
public class JobRun extends BaseEntity {
    private String jobName;
    private int created;
    private String message;

    protected JobRun() {
    }

    public JobRun(String jobName, int created, String message) {
        this.jobName = jobName;
        this.created = created;
        this.message = message;
    }

    public String getJobName() { return jobName; }
    public int getCreated() { return created; }
    public String getMessage() { return message; }
}
