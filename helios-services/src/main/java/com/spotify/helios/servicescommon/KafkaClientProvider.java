/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.servicescommon;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class KafkaClientProvider {

  private static final Logger log = LoggerFactory.getLogger(KafkaClientProvider.class);

  private static final String KAFKA_HELIOS_CLIENT_ID = "Helios";
  private static final int KAFKA_QUORUM_PARAMETER = 1;
  public static final int MAX_BLOCK_TIMEOUT = 5000;

  private final Optional<ImmutableMap<String, Object>> partialConfigs;

  public KafkaClientProvider(@Nullable final List<String> brokerList) {
    partialConfigs = Optional.fromNullable(brokerList).transform(
        new Function<List<String>, ImmutableMap<String, Object>>() {
      @Nullable
      @Override
      public ImmutableMap<String, Object> apply(List<String> input) {
        return ImmutableMap.<String, Object>builder()
            .put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Joiner.on(',').join(input))
            .put(ProducerConfig.CLIENT_ID_CONFIG, KAFKA_HELIOS_CLIENT_ID)

            // how many acknowledgments from the leader does the producer need to consider the
            // request complete?
            //
            // 1 = the leader will write the record to its local log but will respond without
            // awaiting full acknowledgement from all followers. In this case should the leader fail
            // immediately after acknowledging the record but before the followers have replicated
            // it then the record will be lost.
            //
            // 0 = the producer will not wait for any acknowledgment from the server at all. The
            // record will be immediately added to the socket buffer and considered sent. No
            // guarantee can be made that the server has received the record in this case, and the
            // retries configuration will not take effect (as the client won't generally know of any
            // failures). The offset given back for each record will always be set to -1.
            .put(ProducerConfig.ACKS_CONFIG, KAFKA_QUORUM_PARAMETER)

            // The first time data is sent to a topic we must fetch metadata about that topic to
            // know which servers host the topic's partitions. This fetch to succeed before throwing
            // an exception back to the client.
            .put(ProducerConfig.METADATA_FETCH_TIMEOUT_CONFIG, MAX_BLOCK_TIMEOUT)

            // the KafkaProducer maintains an in-memory buffer of a certain size (setting is
            // buffer.memory, default is 32MB) of records that have not yet been transmitted to the
            // brokers. When this buffer is full, additional calls to KafkaProducer.send(record)
            // will block. We don't want blocking. Note that with this set to false, instead the
            // call to KafkaProducer.send() will throw a BufferExhaustedException exception.
            .put(ProducerConfig.BLOCK_ON_BUFFER_FULL_CONFIG, false)

            .build();
      }
    });
  }

  static KafkaClientProvider getTestingProvider() {
    return new KafkaClientProvider(null);
  }

  public <K, V> Optional<KafkaProducer<K, V>> getProducer(@NotNull final Serializer<K> ks,
                                                          @NotNull final Serializer<V> vs) {
    try {
      return partialConfigs.transform(
          new Function<ImmutableMap<String, Object>, KafkaProducer<K, V>>() {
            @Nullable
            @Override
            public KafkaProducer<K, V> apply(ImmutableMap<String, Object> input) {
              return new KafkaProducer<>(input, ks, vs);
            }
          });
    } catch (final Exception e) {
      log.warn("error while generating KafkaProducer - {}", e);
      return Optional.absent();
    }
  }
}
