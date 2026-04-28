package dev.kruhlmann.imgfloat.model.db.imgfloat;

public enum CopyrightReportStatus {
    PENDING,
    DISMISSED,
    RESOLVED,
    /** Sysadmin sent a notice to the broadcaster; awaiting their acknowledgement. */
    NOTIFIED
}
