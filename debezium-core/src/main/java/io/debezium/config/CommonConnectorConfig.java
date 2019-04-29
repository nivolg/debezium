/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Width;

import io.debezium.heartbeat.Heartbeat;
import io.debezium.config.Field.ValidationOutput;
import io.debezium.relational.history.KafkaDatabaseHistory;

/**
 * Configuration options common to all Debezium connectors.
 *
 * @author Gunnar Morling
 */
public class CommonConnectorConfig {

    public static final int DEFAULT_MAX_QUEUE_SIZE = 8192;
    public static final int DEFAULT_MAX_BATCH_SIZE = 2048;
    public static final long DEFAULT_POLL_INTERVAL_MILLIS = 500;
    public static final String DATABASE_CONFIG_PREFIX = "database.";

    public static final Field TOMBSTONES_ON_DELETE = Field.create("tombstones.on.delete")
            .withDisplayName("Change the behaviour of Debezium with regards to delete operations")
            .withType(Type.BOOLEAN)
            .withWidth(Width.SHORT)
            .withImportance(Importance.MEDIUM)
            .withDefault(true)
            .withValidation(Field::isBoolean)
            .withDescription("Whether delete operations should be represented by a delete event and a subsquent" +
                    "tombstone event (true) or only by a delete event (false). Emitting the tombstone event (the" +
                    " default behavior) allows Kafka to completely delete all events pertaining to the given key once" +
                    " the source record got deleted.");

    public static final Field MAX_QUEUE_SIZE = Field.create("max.queue.size")
            .withDisplayName("Change event buffer size")
            .withType(Type.INT)
            .withWidth(Width.SHORT)
            .withImportance(Importance.MEDIUM)
            .withDescription("Maximum size of the queue for change events read from the database log but not yet recorded or forwarded. Defaults to " + DEFAULT_MAX_QUEUE_SIZE + ", and should always be larger than the maximum batch size.")
            .withDefault(DEFAULT_MAX_QUEUE_SIZE)
            .withValidation(CommonConnectorConfig::validateMaxQueueSize);

    public static final Field MAX_BATCH_SIZE = Field.create("max.batch.size")
            .withDisplayName("Change event batch size")
            .withType(Type.INT)
            .withWidth(Width.SHORT)
            .withImportance(Importance.MEDIUM)
            .withDescription("Maximum size of each batch of source records. Defaults to " + DEFAULT_MAX_BATCH_SIZE + ".")
            .withDefault(DEFAULT_MAX_BATCH_SIZE)
            .withValidation(Field::isPositiveInteger);

    public static final Field POLL_INTERVAL_MS = Field.create("poll.interval.ms")
            .withDisplayName("Poll interval (ms)")
            .withType(Type.LONG)
            .withWidth(Width.SHORT)
            .withImportance(Importance.MEDIUM)
            .withDescription("Frequency in milliseconds to wait for new change events to appear after receiving no events. Defaults to " + DEFAULT_POLL_INTERVAL_MILLIS + "ms.")
            .withDefault(DEFAULT_POLL_INTERVAL_MILLIS)
            .withValidation(Field::isPositiveInteger);

    public static final Field SNAPSHOT_DELAY_MS = Field.create("snapshot.delay.ms")
            .withDisplayName("Snapshot Delay (milliseconds)")
            .withType(Type.LONG)
            .withWidth(Width.MEDIUM)
            .withImportance(Importance.LOW)
            .withDescription("The number of milliseconds to delay before a snapshot will begin.")
            .withDefault(0L)
            .withValidation(Field::isNonNegativeLong);

    public static final Field SNAPSHOT_FETCH_SIZE = Field.create("snapshot.fetch.size")
            .withDisplayName("Snapshot fetch size")
            .withType(Type.INT)
            .withWidth(Width.MEDIUM)
            .withImportance(Importance.MEDIUM)
            .withDescription("The maximum number of records that should be loaded into memory while performing a snapshot")
            .withValidation(Field::isNonNegativeInteger);

    private final Configuration config;
    private final boolean emitTombstoneOnDelete;
    private final int maxQueueSize;
    private final int maxBatchSize;
    private final Duration pollInterval;
    private final String logicalName;
    private final String heartbeatTopicsPrefix;
    private final Duration snapshotDelayMs;
    private final int snapshotFetchSize;

    protected CommonConnectorConfig(Configuration config, String logicalName) {
        this.config = config;
        this.emitTombstoneOnDelete = config.getBoolean(CommonConnectorConfig.TOMBSTONES_ON_DELETE);
        this.maxQueueSize = config.getInteger(MAX_QUEUE_SIZE);
        this.maxBatchSize = config.getInteger(MAX_BATCH_SIZE);
        this.pollInterval = config.getDuration(POLL_INTERVAL_MS, ChronoUnit.MILLIS);
        this.logicalName = logicalName;
        this.heartbeatTopicsPrefix = config.getString(Heartbeat.HEARTBEAT_TOPICS_PREFIX);
        this.snapshotDelayMs = Duration.ofMillis(config.getLong(SNAPSHOT_DELAY_MS));
        this.snapshotFetchSize = config.getInteger(SNAPSHOT_FETCH_SIZE, () -> defaultSnapshotFetchSize(config));
    }

    /**
     * Returns the number of records to return per fetch by default.
     * <p><b>Important:</b> Each connector config must override this method to specify its default value.</p>
     *
     * @param config configuration
     * @return the default fetch size
     */
    protected int defaultSnapshotFetchSize(Configuration config) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Provides access to the "raw" config instance. In most cases, access via typed getters for individual properties
     * on the connector config class should be preferred.
     */
    public Configuration getConfig() {
        return config;
    }

    public boolean isEmitTombstoneOnDelete() {
        return emitTombstoneOnDelete;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public String getHeartbeatTopicsPrefix() {
        return heartbeatTopicsPrefix;
    }

    public Duration getSnapshotDelay() {
        return snapshotDelayMs;
    }

    public int getSnapshotFetchSize() {
        return snapshotFetchSize;
    }

    private static int validateMaxQueueSize(Configuration config, Field field, Field.ValidationOutput problems) {
        int maxQueueSize = config.getInteger(field);
        int maxBatchSize = config.getInteger(MAX_BATCH_SIZE);
        int count = 0;
        if (maxQueueSize <= 0) {
            problems.accept(field, maxQueueSize, "A positive queue size is required");
            ++count;
        }
        if (maxQueueSize <= maxBatchSize) {
            problems.accept(field, maxQueueSize, "Must be larger than the maximum batch size");
            ++count;
        }
        return count;
    }

    protected static int validateServerNameIsDifferentFromHistoryTopicName(Configuration config, Field field, ValidationOutput problems) {
        String serverName = config.getString(field);
        String historyTopicName = config.getString(KafkaDatabaseHistory.TOPIC);

        if (Objects.equals(serverName, historyTopicName)) {
            problems.accept(field, serverName, "Must not have the same value as " + KafkaDatabaseHistory.TOPIC.name());
            return 1;
        }

        return 0;
    }

}
