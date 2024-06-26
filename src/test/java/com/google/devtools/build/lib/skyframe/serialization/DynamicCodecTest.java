// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skyframe.serialization;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skyframe.serialization.DynamicCodec.FieldHandler;
import com.google.devtools.build.lib.skyframe.serialization.testutils.SerializationTester;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DynamicCodec}. */
@RunWith(JUnit4.class)
public final class DynamicCodecTest {

  private static class SimpleExample {
    private final String elt;
    private final String elt2;
    private final int x;

    private SimpleExample(String elt, String elt2, int x) {
      this.elt = elt;
      this.elt2 = elt2;
      this.x = x;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SimpleExample that)) {
        return false;
      }
      return Objects.equals(elt, that.elt) && Objects.equals(elt2, that.elt2) && x == that.x;
    }
  }

  @Test
  public void testExample() throws Exception {
    new SerializationTester(new SimpleExample("a", "b", -5), new SimpleExample("a", null, 10))
        .addCodec(new DynamicCodec(SimpleExample.class))
        .makeMemoizing()
        .runTests();
  }

  private static class ExampleSubclass extends SimpleExample {
    private final String elt; // duplicate name with superclass

    private ExampleSubclass(String elt1, String elt2, String elt3, int x) {
      super(elt1, elt2, x);
      this.elt = elt3;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ExampleSubclass that)) {
        return false;
      }
      if (!super.equals(other)) {
        return false;
      }
      return Objects.equals(elt, that.elt);
    }
  }

  @Test
  public void testExampleSubclass() throws Exception {
    new SerializationTester(
            new ExampleSubclass("a", "b", "c", 0), new ExampleSubclass("a", null, null, 15))
        .addCodec(new DynamicCodec(ExampleSubclass.class))
        .makeMemoizing()
        .runTests();
  }

  private static class ExampleSmallPrimitives {
    private final Void v;
    private final boolean bit;
    private final byte b;
    private final short s;
    private final char c;

    private ExampleSmallPrimitives(boolean bit, byte b, short s, char c) {
      this.v = null;
      this.bit = bit;
      this.b = b;
      this.s = s;
      this.c = c;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ExampleSmallPrimitives that)) {
        return false;
      }
      return v == that.v && bit == that.bit && b == that.b && s == that.s && c == that.c;
    }
  }

  @Test
  public void testExampleSmallPrimitives() throws Exception {
    new SerializationTester(
            new ExampleSmallPrimitives(false, (byte) 0, (short) 0, 'a'),
            new ExampleSmallPrimitives(false, (byte) 120, (short) 18000, 'x'),
            new ExampleSmallPrimitives(true, Byte.MIN_VALUE, Short.MIN_VALUE, Character.MIN_VALUE),
            new ExampleSmallPrimitives(true, Byte.MAX_VALUE, Short.MAX_VALUE, Character.MAX_VALUE))
        .addCodec(new DynamicCodec(ExampleSmallPrimitives.class))
        .makeMemoizing()
        .runTests();
  }

  private static class ExampleMediumPrimitives {
    private final int i;
    private final float f;

    private ExampleMediumPrimitives(int i, float f) {
      this.i = i;
      this.f = f;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ExampleMediumPrimitives that)) {
        return false;
      }
      return i == that.i && f == that.f;
    }
  }

  @Test
  public void testExampleMediumPrimitives() throws Exception {
    new SerializationTester(
            new ExampleMediumPrimitives(12345, 1e12f),
            new ExampleMediumPrimitives(67890, -6e9f),
            new ExampleMediumPrimitives(Integer.MIN_VALUE, Float.MIN_VALUE),
            new ExampleMediumPrimitives(Integer.MAX_VALUE, Float.MAX_VALUE))
        .addCodec(new DynamicCodec(ExampleMediumPrimitives.class))
        .makeMemoizing()
        .runTests();
  }

  private static class ExampleLargePrimitives {
    private final long l;
    private final double d;

    private ExampleLargePrimitives(long l, double d) {
      this.l = l;
      this.d = d;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ExampleLargePrimitives that)) {
        return false;
      }
      return l == that.l && d == that.d;
    }
  }

  @Test
  public void testExampleLargePrimitives() throws Exception {
    new SerializationTester(
            new ExampleLargePrimitives(12345346523453L, 1e300),
            new ExampleLargePrimitives(678900093045L, -9e180),
            new ExampleLargePrimitives(Long.MIN_VALUE, Double.MIN_VALUE),
            new ExampleLargePrimitives(Long.MAX_VALUE, Double.MAX_VALUE))
        .addCodec(new DynamicCodec(ExampleLargePrimitives.class))
        .makeMemoizing()
        .runTests();
  }

  private static class ArrayExample {
    String[] text;
    byte[] numbers;
    char[] chars;
    long[] longs;

    private ArrayExample(String[] text, byte[] numbers, char[] chars, long[] longs) {
      this.text = text;
      this.numbers = numbers;
      this.chars = chars;
      this.longs = longs;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ArrayExample that)) {
        return false;
      }
      return Arrays.equals(text, that.text)
          && Arrays.equals(numbers, that.numbers)
          && Arrays.equals(chars, that.chars)
          && Arrays.equals(longs, that.longs);
    }
  }

  @Test
  public void testArray() throws Exception {
    new SerializationTester(
            new ArrayExample(null, null, null, null),
            new ArrayExample(new String[] {}, new byte[] {}, new char[] {}, new long[] {}),
            new ArrayExample(
                new String[] {"a", "b", "cde"},
                new byte[] {-1, 0, 1},
                new char[] {'a', 'b', 'c', 'x', 'y', 'z'},
                new long[] {Long.MAX_VALUE, Long.MIN_VALUE, 27983741982341L, 52893748523495834L}))
        .addCodec(new DynamicCodec(ArrayExample.class))
        .runTests();
  }

  private static class NestedArrayExample {
    int[][] numbers;

    private NestedArrayExample(int[][] numbers) {
      this.numbers = numbers;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof NestedArrayExample that)) {
        return false;
      }
      return Arrays.deepEquals(numbers, that.numbers);
    }
  }

  @Test
  public void testNestedArray() throws Exception {
    new SerializationTester(
            new NestedArrayExample(null),
            new NestedArrayExample(
                new int[][] {
                  {1, 2, 3},
                  {4, 5, 6, 9},
                  {7}
                }),
            new NestedArrayExample(new int[][] {{1, 2, 3}, null, {7}}))
        .addCodec(new DynamicCodec(NestedArrayExample.class))
        .runTests();
  }

  private static class CycleA {
    private final int value;
    private CycleB b;

    private CycleA(int value) {
      this.value = value;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      // Integrity check. Not really part of equals.
      assertThat(b.a).isEqualTo(this);
      if (!(other instanceof CycleA that)) {
        return false;
      }
      // Consistency check. Not really part of equals.
      assertThat(that.b.a).isEqualTo(that);
      return value == that.value && b.value() == that.b.value;
    }
  }

  private static class CycleB {
    private final int value;
    private CycleA a;

    private CycleB(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }

  private static CycleA createCycle(int valueA, int valueB) {
    CycleA a = new CycleA(valueA);
    a.b = new CycleB(valueB);
    a.b.a = a;
    return a;
  }

  @Test
  public void testCyclic() throws Exception {
    new SerializationTester(createCycle(1, 2), createCycle(3, 4))
        .addCodec(new DynamicCodec(CycleA.class))
        .addCodec(new DynamicCodec(CycleB.class))
        .makeMemoizing()
        .runTests();
  }

  enum EnumExample {
    ZERO,
    ONE,
    TWO,
    THREE
  }

  static class PrimitiveExample {

    private final boolean booleanValue;
    private final int intValue;
    private final double doubleValue;
    private final EnumExample enumValue;
    private final String stringValue;

    PrimitiveExample(
        boolean booleanValue,
        int intValue,
        double doubleValue,
        EnumExample enumValue,
        String stringValue) {
      this.booleanValue = booleanValue;
      this.intValue = intValue;
      this.doubleValue = doubleValue;
      this.enumValue = enumValue;
      this.stringValue = stringValue;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object object) {
      if (!(object instanceof PrimitiveExample that)) {
        return false;
      }
      return booleanValue == that.booleanValue
          && intValue == that.intValue
          && doubleValue == that.doubleValue
          && Objects.equals(enumValue, that.enumValue)
          && Objects.equals(stringValue, that.stringValue);
    }
  }

  @Test
  public void testPrimitiveExample() throws Exception {
    new SerializationTester(
            new PrimitiveExample(true, 1, 1.1, EnumExample.ZERO, "foo"),
            new PrimitiveExample(false, -1, -5.5, EnumExample.ONE, "bar"),
            new PrimitiveExample(true, 5, 20.0, EnumExample.THREE, null),
            new PrimitiveExample(true, 100, 100, null, "hello"))
        .addCodec(new DynamicCodec(PrimitiveExample.class))
        .addCodec(new EnumCodec<>(EnumExample.class))
        .setRepetitions(100000)
        .runTests();
  }

  private static class NoCodecExample2 {
    @SuppressWarnings("unused")
    private final BufferedInputStream noCodec = new BufferedInputStream(null);
  }

  private static class NoCodecExample1 {
    @SuppressWarnings("unused")
    private final NoCodecExample2 noCodec = new NoCodecExample2();
  }

  @Test
  public void testNoCodecExample() {
    ObjectCodecs codecs = new ObjectCodecs(AutoRegistry.get(), ImmutableClassToInstanceMap.of());
    SerializationException.NoCodecException expected =
        assertThrows(
            SerializationException.NoCodecException.class,
            () -> codecs.serializeMemoized(new NoCodecExample1()));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "java.io.BufferedInputStream ["
                + "java.io.BufferedInputStream, "
                + "com.google.devtools.build.lib.skyframe.serialization."
                + "DynamicCodecTest$NoCodecExample2, "
                + "com.google.devtools.build.lib.skyframe.serialization."
                + "DynamicCodecTest$NoCodecExample1]");
  }

  private static class SpecificObject {}

  private static class SpecificObjectWrapper {
    @SuppressWarnings("unused")
    private final SpecificObject field;

    SpecificObjectWrapper(SpecificObject field) {
      this.field = field;
    }
  }

  @Test
  public void overGeneralCodec() throws Exception {
    // Class must be hidden from other tests.
    class OverGeneralCodec implements ObjectCodec<Object> {
      @Override
      public Class<?> getEncodedClass() {
        return Object.class;
      }

      @Override
      public void serialize(SerializationContext context, Object obj, CodedOutputStream codedOut) {}

      @Override
      public Object deserialize(DeserializationContext context, CodedInputStream codedIn) {
        return new Object();
      }
    }
    ObjectCodecRegistry registry =
        ObjectCodecRegistry.newBuilder()
            .add(new DynamicCodec(SpecificObjectWrapper.class))
            .add(new OverGeneralCodec())
            .build();
    ObjectCodecs codecs = new ObjectCodecs(registry);
    ByteString bytes = codecs.serializeMemoized(new SpecificObjectWrapper(new SpecificObject()));
    SerializationException expected =
        assertThrows(SerializationException.class, () -> codecs.deserializeMemoized(bytes));
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "was not instance of class "
                + "com.google.devtools.build.lib.skyframe.serialization."
                + "DynamicCodecTest$SpecificObject");
  }

  @Test
  public void overGeneralCodecOkWhenNull() throws Exception {
    // Class must be hidden from other tests.
    class OverGeneralCodec implements ObjectCodec<Object> {
      @Override
      public Class<?> getEncodedClass() {
        return Object.class;
      }

      @Override
      public void serialize(SerializationContext context, Object obj, CodedOutputStream codedOut) {}

      @Override
      public Object deserialize(DeserializationContext context, CodedInputStream codedIn) {
        return new Object();
      }
    }
    ObjectCodecRegistry registry =
        ObjectCodecRegistry.newBuilder()
            .add(new DynamicCodec(SpecificObjectWrapper.class))
            .add(new OverGeneralCodec())
            .build();
    ObjectCodecs codecs = new ObjectCodecs(registry);
    ByteString bytes = codecs.serializeMemoized(new SpecificObjectWrapper(null));
    Object deserialized = codecs.deserializeMemoized(bytes);
    assertThat(deserialized).isInstanceOf(SpecificObjectWrapper.class);
    assertThat(((SpecificObjectWrapper) deserialized).field).isNull();
  }

  private static class CustomHandlerExample {
    private final String text;
    private Object tricky;

    private CustomHandlerExample(String text, Object tricky) {
      this.text = text;
      this.tricky = tricky;
    }

    @SuppressWarnings("EqualsHashCode") // Testing
    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CustomHandlerExample that)) {
        return false;
      }
      return Objects.equals(text, that.text) && Objects.equals(tricky, that.tricky);
    }
  }

  /** An object for testing that can't be serialized. */
  private static final Object NOT_SERIALIZABLE =
      new Object() {
        @Override
        public String toString() {
          return "not serializable";
        }
      };

  @Test
  public void customFieldHandler_counterfactual() throws Exception {
    // Verifies that a naive DynamicCodec instance cannot serialize the `NOT_SERIALIZABLE` object.
    ObjectCodecs codecs =
        new ObjectCodecs(
            ObjectCodecRegistry.newBuilder()
                .add(new DynamicCodec(CustomHandlerExample.class))
                .build());
    SerializationException expected =
        assertThrows(
            SerializationException.class,
            () -> codecs.serialize(new CustomHandlerExample("hello", NOT_SERIALIZABLE)));
    assertThat(expected).hasMessageThat().contains("No default codec available");
  }

  @Test
  public void customFieldHandler() throws Exception {
    // Overrides the handler for the field "tricky".
    DynamicCodec customCodec =
        DynamicCodec.createWithOverrides(
            CustomHandlerExample.class,
            ImmutableMap.of(
                CustomHandlerExample.class.getDeclaredField("tricky"),
                new FieldHandler() {
                  @Override
                  public void serialize(
                      SerializationContext context, CodedOutputStream codedOut, Object obj)
                      throws IOException {
                    CustomHandlerExample subject = (CustomHandlerExample) obj;
                    codedOut.writeBoolNoTag(subject.tricky != null);
                  }

                  @Override
                  public void deserialize(
                      AsyncDeserializationContext context, CodedInputStream codedIn, Object obj)
                      throws IOException {
                    if (codedIn.readBool()) {
                      ((CustomHandlerExample) obj).tricky = NOT_SERIALIZABLE;
                    }
                  }
                }));

    // The NOT_SERIALIZABLE object round-trips successfully with the custom handler.
    new SerializationTester(
            new CustomHandlerExample("a", null), new CustomHandlerExample("b ", NOT_SERIALIZABLE))
        .addCodec(customCodec)
        .runTests();
  }
}
