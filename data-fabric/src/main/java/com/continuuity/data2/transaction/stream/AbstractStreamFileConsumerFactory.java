/*
 * Copyright 2014 Continuuity,Inc. All Rights Reserved.
 */
package com.continuuity.data2.transaction.stream;

import com.continuuity.common.queue.QueueName;
import com.continuuity.data.DataSetAccessor;
import com.continuuity.data.file.FileReader;
import com.continuuity.data.stream.MultiLiveStreamFileReader;
import com.continuuity.data.stream.StreamEventOffset;
import com.continuuity.data.stream.StreamFileOffset;
import com.continuuity.data.stream.StreamUtils;
import com.continuuity.data2.queue.ConsumerConfig;
import com.continuuity.data2.transaction.queue.QueueConstants;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.twill.filesystem.Location;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Abstract base class for implementing {@link StreamConsumerFactory} using
 * {@link MultiLiveStreamFileReader}.
 */
public abstract class AbstractStreamFileConsumerFactory implements StreamConsumerFactory {

  private final StreamAdmin streamAdmin;
  private final StreamConsumerStateStoreFactory stateStoreFactory;
  private final String tablePrefix;

  protected AbstractStreamFileConsumerFactory(DataSetAccessor dataSetAccessor, StreamAdmin streamAdmin,
                                              StreamConsumerStateStoreFactory stateStoreFactory) {
    this.streamAdmin = streamAdmin;
    this.stateStoreFactory = stateStoreFactory;
    this.tablePrefix = dataSetAccessor.namespace(QueueConstants.STREAM_TABLE_PREFIX, DataSetAccessor.Namespace.SYSTEM);
  }

  /**
   * Creates a {@link StreamConsumer}.
   *
   * @param tableName name of the table for storing process states
   * @param streamConfig configuration of the stream to consume from
   * @param consumerConfig configuration of the consumer
   * @param stateStore The {@link StreamConsumerStateStore} for recording consumer state
   * @param reader The {@link FileReader} to read stream events from
   * @return A new instance of {@link StreamConsumer}
   */
  protected abstract StreamConsumer create(
    String tableName, StreamConfig streamConfig,
    ConsumerConfig consumerConfig, StreamConsumerStateStore stateStore,
    FileReader<StreamEventOffset, Iterable<StreamFileOffset>> reader) throws IOException;

  /**
   * Gathers stream file offsets.
   *
   * @param partitionLocation Location of the partition directory
   * @param fileOffsets for collecting stream file offsets
   */
  protected abstract void getFileOffsets(Location partitionLocation,
                                         Collection<? super StreamFileOffset> fileOffsets) throws IOException;

  @Override
  public final StreamConsumer create(QueueName streamName, String namespace,
                                     ConsumerConfig consumerConfig) throws IOException {
    StreamConfig streamConfig = StreamUtils.ensureExists(streamAdmin, streamName.getSimpleName());

    String tableName = getTableName(streamName, namespace);
    StreamConsumerStateStore stateStore = stateStoreFactory.create(streamConfig);
    StreamConsumerState consumerState = stateStore.get(consumerConfig.getGroupId(), consumerConfig.getInstanceId());

    return create(tableName, streamConfig, consumerConfig, stateStore, createReader(streamConfig, consumerState));
  }

  private String getTableName(QueueName streamName, String namespace) {
    return String.format("%s.%s.%s", tablePrefix, streamName.getSimpleName(), namespace);
  }

  private MultiLiveStreamFileReader createReader(StreamConfig streamConfig,
                                                 StreamConsumerState consumerState) throws IOException {
    Location streamLocation = streamConfig.getLocation();
    Preconditions.checkNotNull(streamLocation, "Stream location is null for %s", streamConfig.getName());

    if (!Iterables.isEmpty(consumerState.getState())) {
      // Has existing offsets, just resume from there.
      return new MultiLiveStreamFileReader(streamConfig, consumerState.getState());
    }

    // TODO: Support starting from some time rather then from beginning.
    // Otherwise, search for files with the smallest partition start time
    // If no partition exists for the stream, start with one partition earlier than current time to make sure
    // no event will be lost if events start flowing in about the same time.
    long startTime = StreamUtils.getPartitionStartTime(System.currentTimeMillis() - streamConfig.getPartitionDuration(),
                                                       streamConfig.getPartitionDuration());

    for (Location partitionLocation : streamLocation.list()) {
      if (!partitionLocation.isDirectory()) {
        // Partition should be a directory
        continue;
      }

      long partitionStartTime = StreamUtils.getPartitionStartTime(partitionLocation.getName());
      if (partitionStartTime < startTime) {
        startTime = partitionStartTime;
      }
    }

    // Create file offsets
    // TODO: Be able to support dynamic name of stream writer instances.
    // Maybe it's done through MultiLiveStreamHandler to alter list of file offsets dynamically
    Location partitionLocation = StreamUtils.createPartitionLocation(streamLocation,
                                                                     startTime, streamConfig.getPartitionDuration());
    List<StreamFileOffset> fileOffsets = Lists.newArrayList();
    getFileOffsets(partitionLocation, fileOffsets);

    return new MultiLiveStreamFileReader(streamConfig, fileOffsets);
  }
}
