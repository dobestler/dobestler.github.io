package com.home.weatherstation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Properties;

public class ExceptionReporter {

    private static final String TAG = ExceptionReporter.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private final Properties prop;

    public ExceptionReporter() {
        super();
        prop = new Properties();
        prop.put("mail.smtp.auth", true);
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");

        /*
         TO RUN THIS ON RASPBERRY I NEEDED TO DISABLE
         jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, RC4, DES, MD5withRSA,
            DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL,
            include jdk.disabled.namedCurves
         in /usr/lib/jvm/java-1.11.0-openjdk-armhf/conf/security/java.security
         TO PREVENT
         18:13:51.346 [main] ERROR ExceptionReporter - Failed to send email
        javax.mail.MessagingException: Could not convert socket to TLS
        	at com.sun.mail.smtp.SMTPTransport.startTLS(SMTPTransport.java:1907)
        	at com.sun.mail.smtp.SMTPTransport.protocolConnect(SMTPTransport.java:666)
        	at javax.mail.Service.connect(Service.java:317)
        	at javax.mail.Service.connect(Service.java:176)
        	at javax.mail.Service.connect(Service.java:125)
        	at javax.mail.Transport.send0(Transport.java:253)
        	at javax.mail.Transport.send(Transport.java:124)
        	at com.home.weatherstation.ExceptionReporter.sendEmail(ExceptionReporter.java:90)
        	at com.home.weatherstation.ExceptionReporter.sendException(ExceptionReporter.java:42)
        	at com.home.weatherstation.ExceptionReporter.sendException(ExceptionReporter.java:35)
        	at com.home.weatherstation.App.main(App.java:23)
        Caused by: javax.net.ssl.SSLHandshakeException: No appropriate protocol (protocol is disabled or cipher suites are inappropriate)
        	at java.base/sun.security.ssl.HandshakeContext.<init>(HandshakeContext.java:170)
        	at java.base/sun.security.ssl.ClientHandshakeContext.<init>(ClientHandshakeContext.java:103)
        	at java.base/sun.security.ssl.TransportContext.kickstart(TransportContext.java:221)
        	at java.base/sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:443)
        	at java.base/sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:421)
        	at com.sun.mail.util.SocketFetcher.configureSSLSocket(SocketFetcher.java:527)
        	at com.sun.mail.util.SocketFetcher.startTLS(SocketFetcher.java:464)
        	at com.sun.mail.smtp.SMTPTransport.startTLS(SMTPTransport.java:1902)
        	... 10 common frames omitted
         */
    }

    public void sendException(final Throwable t) {
        sendException(t, "", "");
    }

    public void sendException(final Throwable t, final String logTag, final String logMessage) {
        LoggerFactory.getLogger(logTag).error(logMessage, t);
        String subject = "Non-Fatal Exception";
        String body = stackTraceToString(t);
        sendEmail(subject, body);
    }

    public void sendIncompleteScansAlert(long numberOfIncompleteScans, String deviceName) {
        logger.info("Sending incomplete scan alert email...");
        String subject = "Weatherstation Alert: Incomplete scans for " + deviceName;
        String body1 = String.format(Locale.getDefault(), "%d incomplete scans in a row!", numberOfIncompleteScans);
        sendEmail(subject, body1);
    }

    public void sendThresholdRecoveredAlert(double recoveringValue, int lastXdays, float lowerThreshold, float upperThreshold) {
        logger.info("Sending Threshold Recovered alert email...");
        String subject = String.format("Weatherstation Alert: %s threshold recovered", "Humidity");
        String body = String.format(Locale.getDefault(), "Measured avg. for the last %d days = %s \n" +
                "Lower threshold = %s\n" +
                "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(recoveringValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold));
        sendEmail(subject, body);
    }

    public void sendThresholdExceededAlert(double exceedingValue, int lastXdays, float lowerThreshold, float upperThreshold) {
        logger.info("Sending Threshold Exceeded alert email...");
        String subject = String.format("Weatherstation Alert: %s threshold exceeded", "Humidity");
        String body = String.format(Locale.getDefault(), "Measured avg. for the last %d days = %s \n" +
                "Lower threshold = %s\n" +
                "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(exceedingValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold));
        sendEmail(subject, body);
    }

    private void sendEmail(String subject, String body) {

        Session session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(BuildConfig.ALERT_EMAIL_FROM, BuildConfig.ALERT_EMAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(BuildConfig.ALERT_EMAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(BuildConfig.ALERT_EMAIL_TO));
            message.setSubject(subject);
            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setContent(body, "text/html; charset=utf-8");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(mimeBodyPart);
            message.setContent(multipart);
            Transport.send(message);
        } catch (MessagingException e) {
            logger.error("Failed to send email", e);
        }
    }

    private String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

}
