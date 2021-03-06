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

package com.spotify.helios.testing;

import com.google.common.base.Optional;

import com.spotify.helios.common.Json;
import com.spotify.helios.testing.descriptors.TemporaryJobEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.experimental.results.PrintableResult.testResult;
import static org.junit.experimental.results.ResultMatchers.hasSingleFailureContaining;

public class TempJobFailureTest extends TemporaryJobsTestBase {

  @ClassRule
  public static final TemporaryFolder reportDir = new TemporaryFolder();

  @Test
  public void testDeploymentFailure() throws Exception {
    final long start = System.currentTimeMillis();

    assertThat(testResult(TempJobFailureTestImpl.class),
               hasSingleFailureContaining("AssertionError: Unexpected job state"));
    final long end = System.currentTimeMillis();
    assertTrue("Test should not time out", (end-start) < Jobs.TIMEOUT_MILLIS);

    final byte[] testReport = Files.readAllBytes(reportDir.getRoot().listFiles()[0].toPath());
    final TemporaryJobEvent[] events = Json.read(testReport, TemporaryJobEvent[].class);

    for (final TemporaryJobEvent event : events) {
      if (event.getStep().equals("test")) {
        assertFalse("test should be reported as failed", event.isSuccess());
      }
    }
  }

  public static class TempJobFailureTestImpl {

    @Rule
    public final TemporaryJobs temporaryJobs = temporaryJobsBuilder()
        .hostFilter(".*")
        .client(client)
        .prober(new TestProber())
        .testReportDirectory(reportDir.getRoot().getAbsolutePath())
        .prefixDirectory(prefixDirectory.toString())
        .jobPrefix(Optional.of(testTag).get())
        .build();

    @Test
    public void testThatThisFailsQuickly() throws InterruptedException {
      temporaryJobs.job()
          .image(BUSYBOX)
          .command("false")
          .deploy(testHost1);
      Thread.sleep(Jobs.TIMEOUT_MILLIS);
    }
  }
}
