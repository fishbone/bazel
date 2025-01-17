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

package net.starlark.java.eval;

import com.google.common.collect.Maps;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;
import net.starlark.java.syntax.Location;

/**
 * The StarlarkCallable interface is implemented by all Starlark values that may be called from
 * Starlark like a function, including built-in functions and methods, Starlark functions, and
 * application-defined objects (such as rules, aspects, and providers in Bazel).
 *
 * <p>It defines two methods: {@code fastcall}, for performance, or {@code call} for convenience. By
 * default, {@code fastcall} delegates to {@code call}, and call throws an exception, so an
 * implementer may override either one.
 */
public interface StarlarkCallable extends StarlarkValue {

  /**
   * Defines the "convenient" implementation of function calling for a callable value.
   *
   * <p>Do not call this function directly. Use the {@link Starlark#call} function to make a call,
   * as it handles necessary book-keeping such as maintenance of the call stack, exception handling,
   * and so on.
   *
   * <p>The default implementation throws an EvalException.
   *
   * <p>See {@link Starlark#fastcall} for basic information about function calls.
   *
   * @param thread the StarlarkThread in which the function is called
   * @param args a tuple of the arguments passed by position
   * @param kwargs a new, mutable dict of the arguments passed by keyword. Iteration order is
   *     determined by keyword order in the call expression.
   */
  default Object call(StarlarkThread thread, Tuple args, Dict<String, Object> kwargs)
      throws EvalException, InterruptedException {
    throw Starlark.errorf("function %s not implemented", getName());
  }

  /**
   * Defines the "fast" implementation of function calling for a callable value.
   *
   * <p>Do not call this function directly. Use the {@link Starlark#call} or {@link
   * Starlark#fastcall} function to make a call, as it handles necessary book-keeping such as
   * maintenance of the call stack, exception handling, and so on.
   *
   * <p>The fastcall implementation takes ownership of the two arrays, and may retain them
   * indefinitely or modify them. The caller must not modify or even access the two arrays after
   * making the call.
   *
   * <p>This method defines the low-level or "fast" calling convention. A more convenient interface
   * is provided by the {@link #call} method, which provides a signature analogous to {@code def
   * f(*args, **kwargs)}, or possibly the "self-call" feature of the {@link StarlarkMethod#selfCall}
   * annotation mechanism.
   *
   * <p>The default implementation forwards the call to {@code call}, after rejecting any duplicate
   * named arguments. Other implementations of this method should similarly reject duplicates.
   *
   * <p>See {@link Starlark#fastcall} for basic information about function calls.
   *
   * @param thread the StarlarkThread in which the function is called
   * @param positional a list of positional arguments
   * @param named a list of named arguments, as alternating Strings/Objects. May contain dups.
   */
  default Object fastcall(StarlarkThread thread, Object[] positional, Object[] named)
      throws EvalException, InterruptedException {
    LinkedHashMap<String, Object> kwargs = Maps.newLinkedHashMapWithExpectedSize(named.length >> 1);
    for (int i = 0; i < named.length; i += 2) {
      if (kwargs.put((String) named[i], named[i + 1]) != null) {
        throw Starlark.errorf("%s got multiple values for parameter '%s'", this, named[i]);
      }
    }
    return call(thread, Tuple.of(positional), Dict.wrap(thread.mutability(), kwargs));
  }

  /**
   * Defines the "fast" implementation variant of function calling with only positional arguments.
   *
   * <p>Do not call this function directly. Use the {@link Starlark#easycall} function to make a
   * call, as it handles necessary book-keeping such as maintenance of the call stack, exception
   * handling, and so on.
   *
   * <p>The fastcall implementation takes ownership of the {@code positional} array, and may retain
   * it indefinitely or modify it. The caller must not modify or even access the array after making
   * the call.
   *
   * <p>The default implementation forwards the call to {@code call}.
   *
   * @param thread the StarlarkThread in which the function is called
   * @param positional a list of positional arguments
   */
  default Object positionalOnlyCall(StarlarkThread thread, Object... positional)
      throws EvalException, InterruptedException {
    return fastcall(thread, positional, new Object[] {});
  }

  /**
   * Defines a helper object for a new and hopefully faster way to invoke a StarlarkCallable.
   *
   * <p>An ArgumentProcessor implementation is returned by {@link #requestArgumentProcessor} if the
   * callable supports invocation via ArgumentProcessor. The ArgumentProcessor implementation must
   * then be used to first place the arguments, and then the {@link #call} is used to make the
   * invocation.
   */
  public interface ArgumentProcessor {
    void addPositionalArg(Object value) throws EvalException;

    void addNamedArg(String name, Object value) throws EvalException;

    StarlarkCallable getCallable();

    Object call(StarlarkThread thread) throws EvalException, InterruptedException;
  }

  /**
   * Returns a FasterCall implementation if the callable supports fasterCall invocations, else null.
   */
  @Nullable
  default ArgumentProcessor requestArgumentProcessor(StarlarkThread thread) {
    return null;
  }

  /** Returns the form this callable value should take in a stack trace. */
  String getName();

  /**
   * Returns the location of the definition of this callable value, or BUILTIN if it was not defined
   * in Starlark code.
   */
  default Location getLocation() {
    return Location.BUILTIN;
  }
}
