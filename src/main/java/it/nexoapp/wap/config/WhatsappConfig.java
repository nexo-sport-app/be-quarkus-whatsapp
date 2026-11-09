package it.nexoapp.wap.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * WhatsApp microservice settings, mapped from configuration properties with the {@code wap} prefix.
 * <p>
 * Every property is optional and falls back to a default when not set.
 */
@StaticInitSafe
@ConfigMapping(prefix = "wap")
public interface WhatsappConfig {

    /**
     * Messaging settings (properties with the {@code wap.messaging} prefix).
     */
    Messaging messaging();

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Settings of the event messaging, mapped from the {@code wap.messaging} prefix.
     */
    interface Messaging {

        /**
         * Name of the topic exchange the outbox relay publishes this service's events on
         * (property {@code wap.messaging.exchange}). Optional, defaults to {@code nexo.wap}.
         */
        @WithDefault("nexo.wap")
        String exchange();

    }

}
