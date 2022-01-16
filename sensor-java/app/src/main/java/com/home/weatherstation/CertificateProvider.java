package com.home.weatherstation;

import jakarta.xml.bind.DatatypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class CertificateProvider {

    private static final String TAG = CertificateProvider.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    public static void init() {
        logger.info("Initializing Certificates Provider for SSL ...");
        java.security.Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider()
        );
    }

    /**
     * Read Certificate.pem and PrivateKey.pem files and insert them into a newly generated JKS Keystore using the configured password.
     */
    public static SSLContext getContext(String certificateFilename, String privateKeyFilename, String jksPassword)
            throws NoSuchAlgorithmException, IOException, CertificateException, InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException, KeyManagementException {
        SSLContext context;

        context = SSLContext.getInstance("TLS");

        InputStream certInputStream = null;
        InputStream keyInputStream = null;
        byte[] certBytes;
        byte[] keyBytes;
        try {
            certInputStream = App.class.getClassLoader().getResourceAsStream(certificateFilename);
            keyInputStream = App.class.getClassLoader().getResourceAsStream(privateKeyFilename);
            certBytes = parseDERFromPEM(certInputStream.readAllBytes(), "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
            keyBytes = parseDERFromPEM(keyInputStream.readAllBytes(), "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----");
        } catch (Exception e) {
            logger.error("Failed to read certificate or private key.", e);
            throw e;
        } finally {
            try {
                if (certInputStream != null) certInputStream.close();
                if (keyInputStream != null) keyInputStream.close();
            } catch (IOException e) {
                logger.error("Failed to close certificate or privatekey inputstream");
            }
        }

        X509Certificate cert = generateCertificateFromDER(certBytes, "X.509");
        RSAPrivateKey key = generatePrivateKeyFromDER(keyBytes, "RSA");

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        keystore.setCertificateEntry("cert-alias", cert);
        keystore.setKeyEntry("key-alias", key, jksPassword.toCharArray(), new Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, jksPassword.toCharArray());

        KeyManager[] km = kmf.getKeyManagers();

        context.init(km, null, null);
        logger.info("Successfully initialized SSLContext.");

        return context;
    }
    private static byte[] parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
        String data = new String(pem);
        String[] tokens = data.split(beginDelimiter);
        tokens = tokens[1].split(endDelimiter);
        return DatatypeConverter.parseBase64Binary(tokens[0]);
    }

    private static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes, String algorithm)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance(algorithm);
        return (RSAPrivateKey) factory.generatePrivate(spec);
    }

    private static X509Certificate generateCertificateFromDER(byte[] certBytes, String type)
            throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance(type);
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }
}
