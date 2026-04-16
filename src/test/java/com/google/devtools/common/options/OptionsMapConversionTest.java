// Copyright 2017 The Bazel Authors. All rights reserved.
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

package com.google.devtools.common.options;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for converting {@link OptionsBase} subclass instances to and from maps. */
@RunWith(JUnit4.class)
public class OptionsMapConversionTest {

  /** Dummy options base class. */
  @OptionsClass
  public abstract static class FooOptions extends OptionsBase {
    @Option(
        name = "foo",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "false")
    public abstract boolean getFoo();
  }

  /** Dummy options derived class. */
  @OptionsClass
  public abstract static class BazOptions extends FooOptions {
    @Option(
        name = "bar",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "true")
    public abstract boolean getBar();

    @Option(
        name = "baz",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "5")
    public abstract int getBaz();
  }

  @Test
  public void asMap_Basic() {
    FooOptions foo = Options.getDefaults(FooOptions.class);
    assertThat(foo.asMap()).containsExactly("foo", false);
  }

  @Test
  public void asMap_Inheritance() {
    // Static type is base class, dynamic type is derived. We still get the derived fields.
    FooOptions foo = Options.getDefaults(BazOptions.class);
    assertThat(foo.asMap()).containsExactly("foo", false, "bar", true, "baz", 5);
  }

  /**
   * Dummy options class for checking alphabetizing.
   *
   * <p>Note that field name order differs from option name order.
   */
  @OptionsClass
  public abstract static class AlphaOptions extends OptionsBase {

    @Option(
        name = "c",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "0")
    public abstract int getV();

    @Option(
        name = "d",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "0")
    public abstract int getW();

    @Option(
        name = "a",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "0")
    public abstract int getX();

    @Option(
        name = "e",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "0")
    public abstract int getY();

    @Option(
        name = "b",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.NO_OP},
        defaultValue = "0")
    public abstract int getZ();
  }

  @Test
  public void asMap_AlphabeticalOrder() {
    AlphaOptions alpha = Options.getDefaults(AlphaOptions.class);
    assertThat(alpha.asMap()).containsExactly("a", 0, "b", 0, "c", 0, "d", 0, "e", 0).inOrder();
  }
}
