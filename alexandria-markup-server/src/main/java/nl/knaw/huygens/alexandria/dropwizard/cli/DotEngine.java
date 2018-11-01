package nl.knaw.huygens.alexandria.dropwizard.cli;

/*-
 * #%L
 * alexandria-markup-server
 * =======
 * Copyright (C) 2015 - 2018 Huygens ING (KNAW)
 * =======
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javax.ws.rs.WebApplicationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class DotEngine {
  private final String dotPath;
  private final ExecutorService processThreads = Executors.newCachedThreadPool();

  public DotEngine(String dotPath) {
    this.dotPath = dotPath;
    System.out.println("dotPath=" + dotPath);
  }

  public void renderAs(String format, String dot, OutputStream outputStream) throws IOException {
    final Process dotProc = new ProcessBuilder(dotPath, "-T" + format).start();
    final StringWriter errors = new StringWriter();
    try {
      CompletableFuture.allOf(
          processErrorStream(dotProc, errors),
          processOutputStream(dotProc, dot),
          processInputStream(dotProc, outputStream),
          waitForCompletion(dotProc, errors)
      ).exceptionally(t -> {
        throw new WebApplicationException(t);
      }).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new WebApplicationException(e);
    }
  }

  private CompletableFuture<Void> waitForCompletion(Process dotProc, StringWriter errors) {
    System.out.println("waitForCompletion started");
    return CompletableFuture.runAsync(() -> {
      try {
        if (!dotProc.waitFor(2, TimeUnit.MINUTES)) {
          throw new CompletionException(new IllegalStateException(errors.toString()));
        }
      } catch (InterruptedException e) {
        throw new CompletionException(e);
      }
      System.out.println("waitForCompletion ended");
    }, processThreads);
  }

  private CompletableFuture<Void> processInputStream(Process dotProc, OutputStream outputStream) {
    System.out.println("processInputStream started");
    return CompletableFuture.runAsync(() -> {
      final byte[] buf = new byte[8192];
      try (final InputStream in = dotProc.getInputStream(); final OutputStream out = outputStream) {
        int len;
        while ((len = in.read(buf)) >= 0) {
          out.write(buf, 0, len);
        }
      } catch (IOException e) {
        throw new CompletionException(e);
      }
      System.out.println("processInputStream ended");
    }, processThreads);
  }

  private CompletableFuture<Void> processOutputStream(Process dotProc, String dot) {
    System.out.println("processOutputStream started");
    return CompletableFuture.runAsync(() -> {
      try (final Writer dotProcStream = new OutputStreamWriter(dotProc.getOutputStream(), StandardCharsets.UTF_8)) {
        dotProcStream.write(dot);
      } catch (IOException e) {
        throw new CompletionException(e);
      }
      System.out.println("processOutputStream ended");
    }, processThreads);
  }

  private CompletableFuture<Void> processErrorStream(Process dotProc, StringWriter errors) {
    System.out.println("processErrorStream started");
    return CompletableFuture.runAsync(() -> {
      final char[] buf = new char[8192];
      try (final Reader errorStream = new InputStreamReader(dotProc.getErrorStream())) {
        int len;
        while ((len = errorStream.read(buf)) >= 0) {
          errors.write(buf, 0, len);
        }
      } catch (IOException e) {
        throw new CompletionException(e);
      }
      System.out.println("processErrorStream ended");

    }, processThreads);
  }

}
