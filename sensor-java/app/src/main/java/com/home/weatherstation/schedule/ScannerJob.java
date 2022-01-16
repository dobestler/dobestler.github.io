package com.home.weatherstation.schedule;

import com.home.weatherstation.ScannerService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScannerJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(ScannerJob.class.getSimpleName());

    public ScannerJob() {
        super();
    }
    public void execute(JobExecutionContext jExeCtx) {
        logger.debug("Running scheduled scan ...");
        ScannerService.getInstance().scanAndUpload();
    }
}
