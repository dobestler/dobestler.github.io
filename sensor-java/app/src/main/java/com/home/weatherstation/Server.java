package com.home.weatherstation;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * A simple WebSocketServer implementation.
 */
public class Server extends WebSocketServer {

    private static final String TAG = Server.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    public Server(int port) {
        super(new InetSocketAddress(port));
    }

    public Server(InetSocketAddress address) {
        super(address);
    }

    public Server(int port, Draft_6455 draft) {
        super(new InetSocketAddress(port), Collections.<Draft>singletonList(draft));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.debug(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected.");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.debug(conn + " disconnected.");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug(conn + ": " + message);
        if (message != null && message.equals("scan_and_upload")) {
            ScannerService.getInstance().scanAndUpload();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        logger.debug(conn + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("", ex);
    }

    @Override
    public void onStart() {
        logger.debug("Server started!");
        setConnectionLostTimeout(100);
    }

}