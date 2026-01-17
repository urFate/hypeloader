package io.github.urfate.hypeloader;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.util.logging.Level;

public class HypeLoaderAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[HypeLoader] Installing loader agent...");

        try {
            new AgentBuilder.Default()
                    .disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(new AgentBuilder.Listener() {
                        @Override
                        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

                        @Override
                        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
                            HytaleLogger.getLogger().at(Level.FINEST).log("[HypeLoader] Loader injection successful!");
                        }

                        @Override
                        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {}

                        @Override
                        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                            System.err.println("[HypeLoader] Error transforming: " + typeName);
                            throwable.printStackTrace();
                        }

                        @Override
                        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
                    })
                    .type(ElementMatchers.is(InitialPacketHandler.class))
                    .transform((builder, type, classLoader, module, protectionDomain) -> {
                        HytaleLogger.getLogger().at(Level.INFO).log("[HypeLoader] Injecting agent handler...");

                        return builder
                                .method(ElementMatchers.named("handle")
                                        .and(ElementMatchers.takesArgument(0, Connect.class))
                                        .and(ElementMatchers.isPublic())
                                )
                                .intercept(MethodDelegation.to(InitialAgentPacketHandler.class));
                    })
                    .installOn(inst);

            HytaleLogger.getLogger().at(Level.FINEST).log("[HypeLoader] Successfully installed!");
        } catch (Exception e) {
            HytaleLogger.getLogger().at(Level.SEVERE).log("[HypeLoader] Failed to install: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }
}
