package com.athaydes.rawhttp.duplex;

import rawhttp.core.client.RawHttpClient;
import rawhttp.core.client.TcpRawHttpClient;

import java.time.Duration;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Options that can be used to configure an instance of {@link RawHttpDuplex}.
 *
 * @see RawHttpDuplexOptions.Builder
 */
public class RawHttpDuplexOptions {

    private final RawHttpClient<?> client;
    private final Duration pingPeriod;
    private final ScheduledExecutorService pingScheduler;
    private final Supplier<BlockingDeque<MessageSender.Message>> messageQueueFactory;

    public RawHttpDuplexOptions(RawHttpClient<?> client, Duration pingPeriod,
                                ScheduledExecutorService pingScheduler,
                                Supplier<BlockingDeque<MessageSender.Message>> messageQueueFactory) {
        this.client = client;
        this.pingPeriod = pingPeriod;
        this.pingScheduler = pingScheduler;
        this.messageQueueFactory = messageQueueFactory;
    }

    public RawHttpClient<?> getClient() {
        return client;
    }

    public Duration getPingPeriod() {
        return pingPeriod;
    }

    public ScheduledExecutorService getPingScheduler() {
        return pingScheduler;
    }

    public BlockingDeque<MessageSender.Message> createMessageQueue() {
        return messageQueueFactory.get();
    }

    /**
     * @return a new builder of {@link RawHttpDuplexOptions}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder of {@link RawHttpDuplexOptions}.
     */
    public static class Builder {

        private RawHttpClient<?> client;
        private Duration pingPeriod;
        private ScheduledExecutorService pingScheduler;
        private Supplier<BlockingDeque<MessageSender.Message>> messageQueueFactory;

        private Builder() {
        }

        /**
         * @param client the HTTP client to use to establish duplex connections.
         * @return this builder
         */
        public Builder withClient(RawHttpClient<?> client) {
            this.client = client;
            return this;
        }

        /**
         * @param pingPeriod the period between pings that each end of a connection sends to the other to keep a
         *                   connection alive.
         * @return this builder
         */
        public Builder withPingPeriod(Duration pingPeriod) {
            this.pingPeriod = pingPeriod;
            return this;
        }

        /**
         * @param pingScheduler executor to use for scheduling ping messages.
         * @return this builder
         */
        public Builder withPingScheduler(ScheduledExecutorService pingScheduler) {
            this.pingScheduler = pingScheduler;
            return this;
        }

        /**
         * @param messageQueueFactory a factory of {@link BlockingDeque} to be used as a message queue for
         *                            {@link MessageSender} instances.
         * @return this builder
         */
        public Builder withMessageQueueFactory(Supplier<BlockingDeque<MessageSender.Message>> messageQueueFactory) {
            this.messageQueueFactory = messageQueueFactory;
            return this;
        }

        /**
         * Build a {@link RawHttpDuplexOptions} instance with the configured options.
         *
         * @return new instance
         */
        public RawHttpDuplexOptions build() {
            RawHttpClient<?> client = getOrDefault(this.client, TcpRawHttpClient::new);
            Duration pingPeriod = getOrDefault(this.pingPeriod, () -> Duration.ofSeconds(5));
            ScheduledExecutorService pinger = getOrDefault(this.pingScheduler, Executors::newSingleThreadScheduledExecutor);
            Supplier<BlockingDeque<MessageSender.Message>> messageQueueFactory = getOrDefault(
                    this.messageQueueFactory, Builder::defaultMessageQueue);
            return new RawHttpDuplexOptions(client, pingPeriod, pinger, messageQueueFactory);
        }

        private static Supplier<BlockingDeque<MessageSender.Message>> defaultMessageQueue() {
            return () -> new LinkedBlockingDeque<>(10);
        }

        private static <T> T getOrDefault(T value, Supplier<? extends T> defaultSupplier) {
            if (value == null) {
                return defaultSupplier.get();
            }
            return value;
        }
    }
}
