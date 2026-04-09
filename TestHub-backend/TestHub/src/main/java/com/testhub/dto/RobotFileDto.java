package com.testhub.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class RobotFileDto {

    @Data
    @Builder
    public static class TestCaseItem {
        private Long   id;
        private String name;
        private int    position;
        private String tags;
    }

    @Data
    @Builder
    public static class RobotFileItem {
        private Long              id;
        private String            relativePath;
        private String            suiteName;
        private long              sizeBytes;
        private List<TestCaseItem> testCases;
    }

    @Data
    @Builder
    public static class ProjectTree {
        private Long                  projectId;
        private String                projectName;
        private String                testsDir;
        private List<RobotFileItem>   files;
        private int                   totalFiles;
        private int                   totalTestCases;
    }
}