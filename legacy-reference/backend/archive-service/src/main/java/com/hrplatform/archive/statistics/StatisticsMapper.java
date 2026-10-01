package com.hrplatform.archive.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatisticsMapper {
    StatisticsAggregate aggregateInScope(@Param("scopeType") String scopeType,
                                         @Param("employeeId") Long employeeId,
                                         @Param("departmentId") Long departmentId);

    class StatisticsAggregate {
        private long activeEmployeeCount;
        private long archiveDocumentCount;
        private long ocrTaskCount;
        private long ocrSucceededCount;
        private long ocrFailedCount;
        private long pendingReviewCount;
        private long completeEmployeeCount;

        public StatisticsAggregate() {
        }

        public StatisticsAggregate(long activeEmployeeCount, long archiveDocumentCount, long ocrTaskCount,
                                   long ocrSucceededCount, long ocrFailedCount, long pendingReviewCount,
                                   long completeEmployeeCount) {
            this.activeEmployeeCount = activeEmployeeCount;
            this.archiveDocumentCount = archiveDocumentCount;
            this.ocrTaskCount = ocrTaskCount;
            this.ocrSucceededCount = ocrSucceededCount;
            this.ocrFailedCount = ocrFailedCount;
            this.pendingReviewCount = pendingReviewCount;
            this.completeEmployeeCount = completeEmployeeCount;
        }

        public long getActiveEmployeeCount() { return activeEmployeeCount; }
        public void setActiveEmployeeCount(long value) { this.activeEmployeeCount = value; }
        public long getArchiveDocumentCount() { return archiveDocumentCount; }
        public void setArchiveDocumentCount(long value) { this.archiveDocumentCount = value; }
        public long getOcrTaskCount() { return ocrTaskCount; }
        public void setOcrTaskCount(long value) { this.ocrTaskCount = value; }
        public long getOcrSucceededCount() { return ocrSucceededCount; }
        public void setOcrSucceededCount(long value) { this.ocrSucceededCount = value; }
        public long getOcrFailedCount() { return ocrFailedCount; }
        public void setOcrFailedCount(long value) { this.ocrFailedCount = value; }
        public long getPendingReviewCount() { return pendingReviewCount; }
        public void setPendingReviewCount(long value) { this.pendingReviewCount = value; }
        public long getCompleteEmployeeCount() { return completeEmployeeCount; }
        public void setCompleteEmployeeCount(long value) { this.completeEmployeeCount = value; }
    }
}
