package io.github.urfate.hypeloader;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.ConnectAccept;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SetupPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.login.AuthenticationPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.login.PasswordPacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import io.netty.handler.codec.quic.QuicStreamChannel;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

public class InitialAgentPacketHandler {

    @RuntimeType
    @Advice.OnMethodEnter()
    public static void intercept(@This InitialPacketHandler handler, @Argument(0) Connect packet) {
        try {
            Class<?> handlerClass = handler.getClass();

            Field receivedConnectField = handlerClass.getDeclaredField("receivedConnect");
            receivedConnectField.setAccessible(true);
            receivedConnectField.setBoolean(handler, true);

            Method clearTimeoutMethod = handlerClass.getSuperclass().getDeclaredMethod("clearTimeout");
            clearTimeoutMethod.setAccessible(true);
            clearTimeoutMethod.invoke(handler);

            Method logConnectionTimingsMethod = PacketHandler.class.getDeclaredMethod(
                    "logConnectionTimings",
                    io.netty.channel.Channel.class,
                    String.class,
                    Level.class
            );
            logConnectionTimingsMethod.setAccessible(true);
            logConnectionTimingsMethod.invoke(null,handler.getChannel(), "Connect", Level.FINE);

            String clientProtocolHash = packet.protocolHash;
            if (clientProtocolHash.length() > 64) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Invalid Protocol Hash! " + clientProtocolHash.length());
                return;
            }

            String expectedHash = "6708f121966c1c443f4b0eb525b2f81d0a8dc61f5003a692a8fa157e5e02cea9";
            if (!clientProtocolHash.equals(expectedHash)) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler,
                        "Incompatible protocol!\nServer: " + expectedHash + "\nClient: " + clientProtocolHash);
                return;
            }

            if (HytaleServer.get().isShuttingDown()) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Server is shutting down!");
                return;
            }

            if (!HytaleServer.get().isBooted()) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler,
                        "Server is booting up! Please try again in a moment. [" +
                                PluginManager.get().getState() + "]");
                return;
            }

            if (packet.uuid == null) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Missing UUID");
                return;
            }

            if (packet.username == null || packet.username.isEmpty()) {
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Missing username");
                return;
            }

            if (packet.referralData != null && packet.referralData.length > 4096) {
                HytaleLogger.getLogger().at(Level.WARNING).log(
                        "Rejecting connection from %s - referral data too large: %d bytes (max: %d)",
                        packet.username,
                        packet.referralData.length,
                        4096
                );
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler,
                        "Referral data exceeds maximum size of 4096 bytes");
                return;
            }

            boolean isEditorClient = (packet.clientType == ClientType.Editor);
            Options.AuthMode authMode = Options.getOptionSet().valueOf(Options.AUTH_MODE);

            boolean isTcpConnection = !(handler.getChannel() instanceof QuicStreamChannel);
            if (isTcpConnection) {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "TCP connection from %s - only insecure auth supported",
                        NettyUtil.formatRemoteAddress(handler.getChannel())
                );
            }

            Field editorHandlerSupplierField = handlerClass.getDeclaredField("EDITOR_PACKET_HANDLER_SUPPLIER");
            editorHandlerSupplierField.setAccessible(true);
            AuthenticationPacketHandler.AuthHandlerSupplier editorSupplier = (AuthenticationPacketHandler.AuthHandlerSupplier) editorHandlerSupplierField.get(null);

            if (authMode == Options.AuthMode.AUTHENTICATED) {
                HytaleLogger.getLogger().at(Level.WARNING).log(
                        "[HypeLoader] Rejecting connection from %s - authentication disabled. Consider disabling authenticated login on the server to use HypeLoader.",
                        NettyUtil.formatRemoteAddress(handler.getChannel()),
                        authMode
                );
                Method disconnectMethod = handlerClass.getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Authentication disabled. Consider disabling authenticated login on the server to use HypeLoader.");
                return;
            }

            if (authMode == Options.AuthMode.OFFLINE) {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "[HypeLoader] Allowing offline connection for %s (%s)",
                        packet.username,
                        packet.uuid
                );
            }

            if (!isEditorClient) {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "[HypeLoader] Starting HypeLoader flow for %s (%s) from %s",
                        packet.username,
                        packet.uuid,
                        NettyUtil.formatRemoteAddress(handler.getChannel())
                );

                NettyUtil.setChannelHandler(handler.getChannel(), new PasswordPacketHandler(handler.getChannel(), new ProtocolVersion(clientProtocolHash), (packet.language != null) ? packet.language : "en-US", packet.uuid, packet.username, packet.referralData, packet.referralSource, null, SetupPacketHandler::new));
            } else {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "Starting development flow for editor %s (%s) from %s",
                        packet.username,
                        packet.uuid,
                        NettyUtil.formatRemoteAddress(handler.getChannel())
                );

                Method generatePasswordChallengeMethod = handlerClass.getDeclaredMethod(
                        "generatePasswordChallengeIfNeeded",
                        UUID.class
                );
                generatePasswordChallengeMethod.setAccessible(true);
                byte[] passwordChallenge = (byte[]) generatePasswordChallengeMethod.invoke(
                        handler,
                        packet.uuid
                );

                Method writeMethod = handlerClass.getDeclaredMethod("write", Packet.class);
                writeMethod.setAccessible(true);
                writeMethod.invoke(handler, new ConnectAccept(passwordChallenge));

                PasswordPacketHandler.SetupHandlerSupplier setupSupplier = editorSupplier != null
                        ? editorSupplier::create
                        : SetupPacketHandler::new;

                PasswordPacketHandler passwordHandler = new PasswordPacketHandler(
                        handler.getChannel(),
                        new ProtocolVersion(clientProtocolHash),
                        (packet.language != null) ? packet.language : "en-US",
                        packet.uuid,
                        packet.username,
                        packet.referralData,
                        packet.referralSource,
                        passwordChallenge,
                        setupSupplier
                );

                NettyUtil.setChannelHandler(handler.getChannel(), passwordHandler);
            }
        } catch (Exception e) {
            System.err.println("[HypeLoader] Error in handle method: " + e.getMessage());
            e.printStackTrace();

            try {
                Method disconnectMethod = handler.getClass().getDeclaredMethod("disconnect", String.class);
                disconnectMethod.setAccessible(true);
                disconnectMethod.invoke(handler, "Internal server error");
            } catch (Exception ex) {}
        }
    }
}