/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.ffwd.signalfx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.signalfx.endpoint.SignalFxEndpoint;
import com.signalfx.metrics.auth.AuthToken;
import com.signalfx.metrics.auth.StaticAuthToken;
import com.signalfx.metrics.connection.EventReceiverFactory;
import com.signalfx.metrics.connection.HttpDataPointProtobufReceiverFactory;
import com.signalfx.metrics.connection.HttpEventProtobufReceiverFactory;
import com.signalfx.metrics.errorhandler.OnSendErrorHandler;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.spotify.ffwd.output.BatchedPluginSink;
import com.spotify.ffwd.output.FlushingPluginSink;
import com.spotify.ffwd.output.OutputPlugin;
import com.spotify.ffwd.output.OutputPluginModule;
import com.spotify.ffwd.output.PluginSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public class SignalFxOutputPlugin implements OutputPlugin {
    public static final String DEFAULT_ID = "signalfx";
    public static final String DEFAULT_SOURCE_NAME = "ffwd/java";
    public static final Long DEFAULT_FLUSH_INTERVAL = 500L;
    public static final int DEFAULT_SO_TIMEOUT = 10000;

    private final String id;
    private final String sourceName;
    private final String authToken;
    private final Long flushInterval;
    private final int soTimeout;

    @JsonCreator
    public SignalFxOutputPlugin(
        @JsonProperty("id") String id, @JsonProperty("sourceName") String sourceName,
        @JsonProperty("authToken") String authToken,
        @JsonProperty("flushInterval") Long flushInterval,
        @JsonProperty("soTimeout") Integer soTimeout
    ) {
        this.id = Optional.ofNullable(id).orElse(DEFAULT_ID);
        this.sourceName = Optional.ofNullable(sourceName).orElse(DEFAULT_SOURCE_NAME);
        this.authToken = Optional
            .ofNullable(authToken)
            .orElseThrow(() -> new IllegalArgumentException("authToken: must be defined"));
        this.flushInterval = Optional.ofNullable(flushInterval).orElse(DEFAULT_FLUSH_INTERVAL);
        this.soTimeout = Optional.ofNullable(soTimeout).orElse(DEFAULT_SO_TIMEOUT);
    }

    @Override
    public Module module(final Key<PluginSink> key, final String id) {
        return new OutputPluginModule(id) {
            @Provides
            Supplier<AggregateMetricSender> sender() {
                final Collection<OnSendErrorHandler> handlers = ImmutableList.of(metricError -> {
                    log.error(metricError.toString());
                });

                return () -> {
                    final SignalFxEndpoint endpoint = new SignalFxEndpoint();
                    final HttpDataPointProtobufReceiverFactory dataPoints =
                        new HttpDataPointProtobufReceiverFactory(endpoint).setVersion(2);

                    BasicHttpClientConnectionManager connectionManager =
                        new BasicHttpClientConnectionManager();
                    SocketConfig socketConfigWithSoTimeout = SocketConfig
                        .copy(connectionManager.getSocketConfig())
                        .setSoTimeout(soTimeout)
                        .build();
                    connectionManager.setSocketConfig(socketConfigWithSoTimeout);
                    dataPoints.setHttpClientConnectionManager(connectionManager);

                    final EventReceiverFactory events =
                        new HttpEventProtobufReceiverFactory(endpoint);
                    final AuthToken auth = new StaticAuthToken(authToken);

                    return new AggregateMetricSender(sourceName, dataPoints, events, auth,
                        handlers);
                };
            }

            @Override
            protected void configure() {
                bind(BatchedPluginSink.class).to(SignalFxPluginSink.class);
                bind(key).toInstance(new FlushingPluginSink(flushInterval));

                expose(key);
            }
        };
    }

    @Override
    public String id(int index) {
        return this.id;
    }
}
