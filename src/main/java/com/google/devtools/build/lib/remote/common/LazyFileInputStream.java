// Copyright 2024 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.remote.common;

import com.google.devtools.build.lib.vfs.Path;
import java.io.IOException;
import java.io.InputStream;

/**
 * Creates an {@link InputStream} backed by a file that isn't actually opened until the first data
 * is read. This is useful to only have as many open file descriptors as necessary at a time to
 * avoid running into system limits.
 *
 * <p>The markSupported(), mark() and reset() methods need not be overridden, as they're unsupported
 * by the base implementation.
 */
public class LazyFileInputStream extends InputStream {

  private final Path path;
  private InputStream in;

  public LazyFileInputStream(Path path) {
    this.path = path;
  }

  @Override
  public int available() throws IOException {
    ensureOpen();
    return in.available();
  }

  @Override
  public int read() throws IOException {
    ensureOpen();
    return in.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    ensureOpen();
    return in.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    ensureOpen();
    return in.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    ensureOpen();
    return in.skip(n);
  }

  @Override
  public void close() throws IOException {
    if (in != null) {
      in.close();
    }
  }

  private void ensureOpen() throws IOException {
    if (in == null) {
      in = path.getInputStream();
    }
  }
}
