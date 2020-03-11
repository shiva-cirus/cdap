/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.runtime.spi.runtimejob;

import java.util.Objects;

/**
 * Program run information.
 */
public class ProgramRunInfo {
  private final String namespace;
  private final String application;
  private final String version;
  private final String program;
  private final String run;

  public ProgramRunInfo(String namespace, String application, String version, String program, String run) {
    this.namespace = namespace;
    this.application = application;
    this.version = version;
    this.program = program;
    this.run = run;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getApplication() {
    return application;
  }

  public String getVersion() {
    return version;
  }

  public String getProgram() {
    return program;
  }

  public String getRun() {
    return run;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProgramRunInfo that = (ProgramRunInfo) o;
    return namespace.equals(that.namespace) && application.equals(that.application) &&
      version.equals(that.version) && program.equals(that.program) && run.equals(that.run);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespace, application, version, program, run);
  }
}
