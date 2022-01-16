package com.home.weatherstation;

import com.home.weatherstation.schedule.ScannerJob;
import com.home.weatherstation.schedule.ThresholdCheckerJob;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

public class App {
    private static final String TAG = App.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static final String SCAN_TIMES = "0 0/15 * * * ?"; // Every 15min 0sec
    private static final String THRESHOLD_CHECK_TIMES = "0 0 0/6 * * ?"; // Every 6h 0min 0sec

    private static final int SERVER_PORT = 8887;
    private static Server websocketServer;

    public static void main(String[] args) throws Exception {
        logger.info("Starting App version " + BuildConfig.VERSION);

        // Websocket Server for listening to incoming "push" messages, e.g. to trigger a scan from the html client
        startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(App::stopWebsocketServer));

        // Scheduler for automatic scanning and thresholds checking
        schedule(SCAN_TIMES, ScannerJob.class);
        schedule(THRESHOLD_CHECK_TIMES, ThresholdCheckerJob.class);
    }

    private static void startServer() throws Exception {
        CertificateProvider.init();
        SSLContext context = CertificateProvider.getContext(BuildConfig.WEBSOCKET_SERVER_SSL_CERT_FILENAME, BuildConfig.WEBSOCKET_SERVER_SSL_KEY_FILENAME, BuildConfig.WEBSOCKET_SERVER_SSL_JKS_PASSWORD);

        logger.info("Starting Websocket Server ...");
        websocketServer = new Server(SERVER_PORT);
        websocketServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(context));
        websocketServer.setConnectionLostTimeout(30);
        websocketServer.start();
    }



//    private static byte[] getBytes(File file) {
//        byte[] bytesArray = new byte[(int) file.length()];
//
//        FileInputStream fis = null;
//        try {
//            fis = new FileInputStream(file);
//            fis.read(bytesArray); //read file into bytes[]
//            fis.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return bytesArray;
//    }

    private static void schedule(String scheduledTimes, Class<? extends Job> jobClass) {
        try {
            JobDetail job = JobBuilder.newJob(jobClass)
                    .withIdentity(jobClass.getSimpleName())
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .startNow()
                    .withSchedule(CronScheduleBuilder.cronSchedule(scheduledTimes))
                    .build();

            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            scheduler.scheduleJob(job, trigger);

            logger.info("Scheduled " + jobClass.getSimpleName() + " at " + scheduledTimes);

        } catch (SchedulerException e) {
            logger.error("Scheduling Exception", e);
        }
    }

    private static void stopWebsocketServer() {
        try {
            logger.info("Stopping Server ...");
            websocketServer.stop(1000);
            logger.info("Server stopped.");
        } catch (InterruptedException e) {
            logger.error("Failed to stop server.", e);
        }
    }
}