// Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.java;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction;
import com.google.devtools.build.lib.analysis.platform.ConstraintSettingInfo;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.analysis.util.AnalysisMock;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.util.MockToolsConfig;
import com.google.devtools.build.lib.testutil.Scratch;
import com.google.devtools.build.lib.testutil.TestConstants;
import java.util.Objects;

public class JavaTestUtil {

  private JavaTestUtil() {}

  public static void setupPlatform(
      AnalysisMock analysisMock,
      MockToolsConfig mockToolsConfig,
      Scratch scratch,
      String packagePath,
      String platform,
      String os,
      String cpu)
      throws Exception {
    scratch.overwriteFile(
        packagePath + "/BUILD",
        "platform(",
        "  name = '" + platform + "',",
        "  constraint_values = [",
        "    '" + TestConstants.CONSTRAINTS_PACKAGE_ROOT + "os:" + os + "',",
        "    '" + TestConstants.CONSTRAINTS_PACKAGE_ROOT + "cpu:" + cpu + "',",
        "  ]",
        ")");
    analysisMock.ccSupport().setupCcToolchainConfigForCpu(mockToolsConfig, cpu);
  }

  public static String getJvmFlagsForJavaBinaryExecutable(RuleContext ruleContext, Action action)
      throws Exception {
    if (ruleContext.targetPlatformHasConstraint(getWindowsConstraintValue())) {
      return ((SpawnAction) action)
          .getArguments().stream()
              .filter(a -> a.startsWith("jvm_flags="))
              .flatMap(a -> stream(a.substring("jvm_flags=".length()).split("\t")))
              .collect(joining(" "));
    } else {
      return ((TemplateExpansionAction) action)
          .getSubstitutions().stream()
              .filter(s -> Objects.equals(s.getKey(), "%jvm_flags%"))
              .collect(onlyElement())
              .getValue();
    }
  }

  private static ConstraintValueInfo getWindowsConstraintValue() throws Exception {
    String osConstraintsPackage = TestConstants.CONSTRAINTS_PACKAGE_ROOT + "os";
    return ConstraintValueInfo.create(
        ConstraintSettingInfo.create(Label.create(osConstraintsPackage, "os")),
        Label.create(osConstraintsPackage, "windows"));
  }
}
