package com.home.weatherstation.schedule;

import com.home.weatherstation.Storage;
import com.home.weatherstation.ThresholdCheckerService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThresholdCheckerJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdCheckerJob.class.getSimpleName());

    public ThresholdCheckerJob() {
        super();
    }

    public void execute(JobExecutionContext jExeCtx) {
        logger.debug("Running scheduled threshold checking ...");
        new ThresholdCheckerService(Storage.readAlertingConfig()).checkThresholds();
    }
}
