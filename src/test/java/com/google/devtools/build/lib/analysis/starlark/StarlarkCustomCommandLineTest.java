// Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.analysis.starlark;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.actions.ActionLookupData;
import com.google.devtools.build.lib.actions.ArgChunk;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifact;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifactType;
import com.google.devtools.build.lib.actions.Artifact.TreeFileArtifact;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.actions.ArtifactRoot.RootType;
import com.google.devtools.build.lib.actions.CommandLine;
import com.google.devtools.build.lib.actions.CommandLine.SimpleArgChunk;
import com.google.devtools.build.lib.actions.CommandLineExpansionException;
import com.google.devtools.build.lib.actions.FileArtifactValue;
import com.google.devtools.build.lib.actions.FilesetOutputSymlink;
import com.google.devtools.build.lib.actions.FilesetOutputTree;
import com.google.devtools.build.lib.actions.PathMapper;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.config.CoreOptions;
import com.google.devtools.build.lib.analysis.starlark.StarlarkCustomCommandLine.VectorArg;
import com.google.devtools.build.lib.cmdline.BazelModuleContext;
import com.google.devtools.build.lib.cmdline.BazelModuleKey;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.RepositoryMapping;
import com.google.devtools.build.lib.exec.util.FakeActionInputFileCache;
import com.google.devtools.build.lib.skyframe.TreeArtifactValue;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.vfs.DigestHashFunction;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.Tuple;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.ParserInput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link StarlarkCustomCommandLine}. */
@RunWith(JUnit4.class)
public final class StarlarkCustomCommandLineTest {

  private final ArtifactRoot derivedRoot =
      ArtifactRoot.asDerivedRoot(
          new InMemoryFileSystem(DigestHashFunction.SHA256).getPath("/execroot"),
          RootType.Output,
          "bin");

  private final StarlarkCustomCommandLine.Builder builder =
      new StarlarkCustomCommandLine.Builder(StarlarkSemantics.DEFAULT);

  @Test
  public void add() throws Exception {
    CommandLine commandLine =
        builder
            .add("one")
            .add("two")
            .add("three")
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "one", "two", "three");
  }

  @Test
  public void addFormatted() throws Exception {
    CommandLine commandLine =
        builder
            .addFormatted("one", "--arg1=%s")
            .addFormatted("two", "--arg2=%s")
            .addFormatted("three", "--arg3=%s")
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "--arg1=one", "--arg2=two", "--arg3=three");
  }

  @Test
  public void argName() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setArgName("--arg"))
            .add(vectorArg("four").setArgName("--other_arg"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "--arg", "one", "two", "three", "--other_arg", "four");
  }

  @Test
  public void terminateWith() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setTerminateWith("end1"))
            .add(vectorArg("four").setTerminateWith("end2"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "one", "two", "three", "end1", "four", "end2");
  }

  @Test
  public void formatEach() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setFormatEach("--arg=%s"))
            .add(vectorArg("four").setFormatEach("--other_arg=%s"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "--arg=one", "--arg=two", "--arg=three", "--other_arg=four");
  }

  @Test
  public void beforeEach() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setBeforeEach("b4"))
            .add(vectorArg("four").setBeforeEach("and"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "b4", "one", "b4", "two", "b4", "three", "and", "four");
  }

  @Test
  public void joinWith() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setJoinWith("..."))
            .add(vectorArg("four").setJoinWith("n/a"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "one...two...three", "four");
  }

  @Test
  public void formatJoined() throws Exception {
    CommandLine commandLine =
        builder
            .add(vectorArg("one", "two", "three").setJoinWith("...").setFormatJoined("--arg=%s"))
            .add(vectorArg("four").setJoinWith("n/a").setFormatJoined("--other_arg=%s"))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "--arg=one...two...three", "--other_arg=four");
  }

  @Test
  public void emptyVectorArg_omit() throws Exception {
    CommandLine commandLine =
        builder
            .add("before")
            .add(vectorArg().omitIfEmpty(true).setJoinWith(",").setFormatJoined("--empty=%s"))
            .add("after")
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "before", "after");
  }

  @Test
  public void emptyVectorArg_noOmit() throws Exception {
    CommandLine commandLine =
        builder
            .add("before")
            .add(vectorArg().omitIfEmpty(false).setJoinWith(",").setFormatJoined("--empty=%s"))
            .add("after")
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(commandLine, "before", "--empty=", "after");
  }

  @Test
  public void flagPerLine() throws Exception {
    CommandLine commandLine =
        builder
            .recordArgStart()
            .add(vectorArg("is", "line", "one").setArgName("--this"))
            .recordArgStart()
            .add(vectorArg("this", "is", "line", "two").setArgName("--and"))
            .recordArgStart()
            .add("--line_three")
            .add("single_arg")
            .recordArgStart()
            .add(vectorArg("", "line", "four", "has", "no").setTerminateWith("flag"))
            .build(/* flagPerLine= */ true, RepositoryMapping.ALWAYS_FALLBACK);
    verifyCommandLine(
        commandLine,
        "--this=is line one",
        "--and=this is line two",
        "--line_three=single_arg",
        "line four has no flag");
  }

  @Test
  public void vectorArgAddToFingerprint_treeArtifactMissingExpansion_fails() {
    SpecialArtifact tree = createTreeArtifact("tree");
    CommandLine commandLine =
        builder
            .add(vectorArg(tree).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);

    CommandLineExpansionException e =
        assertThrows(
            CommandLineExpansionException.class,
            () -> commandLine.arguments(new FakeActionInputFileCache(), PathMapper.NOOP));
    assertThat(e).hasMessageThat().contains("Failed to expand directory <generated file tree>");
  }

  @Test
  public void vectorArgAddToFingerprint_expandFileset_includesInDigest() throws Exception {
    SpecialArtifact fileset = createFileset("fileset");
    CommandLine commandLine =
        builder
            .add(vectorArg(fileset).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    FilesetOutputSymlink symlink1 = createFilesetSymlink("file1");
    FilesetOutputSymlink symlink2 = createFilesetSymlink("file2");
    ActionKeyContext actionKeyContext = new ActionKeyContext();
    Fingerprint fingerprint = new Fingerprint();

    FakeActionInputFileCache fakeActionInputFileCache = new FakeActionInputFileCache();
    fakeActionInputFileCache.putFileset(
        fileset, FilesetOutputTree.create(ImmutableList.of(symlink1, symlink2)));
    commandLine.addToFingerprint(
        actionKeyContext, fakeActionInputFileCache, CoreOptions.OutputPathsMode.OFF, fingerprint);

    assertThat(fingerprint.digestAndReset()).isNotEmpty();
  }

  @Test
  public void vectorArgAddToFingerprint_expandTreeArtifact_includesInDigest() throws Exception {
    SpecialArtifact tree = createTreeArtifact("tree");
    TreeFileArtifact child = TreeFileArtifact.createTreeOutput(tree, "child");
    // The files won't be read so MISSING_FILE_MARKER will do
    TreeArtifactValue treeArtifactValue =
        TreeArtifactValue.newBuilder(tree)
            .putChild(child, FileArtifactValue.MISSING_FILE_MARKER)
            .build();

    CommandLine commandLine =
        builder
            .add(vectorArg(tree).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);

    ActionKeyContext actionKeyContext = new ActionKeyContext();
    FakeActionInputFileCache fakeActionInputFileCache = new FakeActionInputFileCache();
    fakeActionInputFileCache.putTreeArtifact(tree, treeArtifactValue);

    Fingerprint fingerprint = new Fingerprint();
    commandLine.addToFingerprint(
        actionKeyContext, fakeActionInputFileCache, CoreOptions.OutputPathsMode.OFF, fingerprint);
    assertThat(fingerprint.digestAndReset()).isNotEmpty();
  }

  @Test
  public void vectorArgAddToFingerprint_expandFilesetMissingExpansion_fails() {
    SpecialArtifact fileset = createFileset("fileset");
    CommandLine commandLine =
        builder
            .add(vectorArg(fileset).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    ActionKeyContext actionKeyContext = new ActionKeyContext();
    Fingerprint fingerprint = new Fingerprint();

    assertThrows(
        CommandLineExpansionException.class,
        () ->
            commandLine.addToFingerprint(
                actionKeyContext,
                new FakeActionInputFileCache(),
                CoreOptions.OutputPathsMode.OFF,
                fingerprint));
  }

  @Test
  public void vectorArgArguments_expandsTreeArtifact() throws Exception {
    SpecialArtifact tree = createTreeArtifact("tree");
    TreeFileArtifact child1 = TreeFileArtifact.createTreeOutput(tree, "child1");
    TreeFileArtifact child2 = TreeFileArtifact.createTreeOutput(tree, "child2");
    // The files won't be read so MISSING_FILE_MARKER will do
    TreeArtifactValue treeArtifactValue =
        TreeArtifactValue.newBuilder(tree)
            .putChild(child1, FileArtifactValue.MISSING_FILE_MARKER)
            .putChild(child2, FileArtifactValue.MISSING_FILE_MARKER)
            .build();

    FakeActionInputFileCache fakeActionInputFileCache = new FakeActionInputFileCache();
    fakeActionInputFileCache.putTreeArtifact(tree, treeArtifactValue);

    CommandLine commandLine =
        builder
            .add(vectorArg(tree).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    Iterable<String> arguments = commandLine.arguments(fakeActionInputFileCache, PathMapper.NOOP);
    assertThat(arguments).containsExactly("bin/tree/child1", "bin/tree/child2");
  }

  @Test
  public void vectorArgArguments_expandsFileset() throws Exception {
    SpecialArtifact fileset = createFileset("fileset");
    FilesetOutputSymlink symlink1 = createFilesetSymlink("file1");
    FilesetOutputSymlink symlink2 = createFilesetSymlink("file2");

    FakeActionInputFileCache fakeActionInputFileCache = new FakeActionInputFileCache();
    fakeActionInputFileCache.putFileset(
        fileset, FilesetOutputTree.create(ImmutableList.of(symlink1, symlink2)));

    CommandLine commandLine =
        builder
            .add(vectorArg(fileset).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);
    Iterable<String> arguments = commandLine.arguments(fakeActionInputFileCache, PathMapper.NOOP);

    assertThat(arguments).containsExactly("bin/fileset/file1", "bin/fileset/file2");
  }

  @Test
  public void vectorArgArguments_treeArtifactMissingExpansion_fails() {
    SpecialArtifact tree = createTreeArtifact("tree");
    CommandLine commandLine =
        builder
            .add(vectorArg(tree).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);

    assertThrows(
        CommandLineExpansionException.class,
        () -> commandLine.arguments(new FakeActionInputFileCache(), PathMapper.NOOP));
  }

  @Test
  public void vectorArgArguments_manuallyExpandedTreeArtifactMissingExpansion_fails()
      throws Exception {
    SpecialArtifact tree = createTreeArtifact("tree");
    CommandLine commandLine =
        builder
            .add(
                vectorArg(tree)
                    .setExpandDirectories(false)
                    .setMapEach(
                        (StarlarkFunction)
                            execStarlark(
                                """
                                def map_each(x, expander):
                                  expander.expand(x)
                                map_each
                                """)))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);

    CommandLineExpansionException e =
        assertThrows(
            CommandLineExpansionException.class,
            () -> commandLine.arguments(new FakeActionInputFileCache(), PathMapper.NOOP));
    assertThat(e).hasMessageThat().contains("Failed to expand directory <generated file tree>");
  }

  @Test
  public void vectorArgArguments_filesetMissingExpansion_fails() {
    SpecialArtifact fileset = createFileset("fileset");
    CommandLine commandLine =
        builder
            .add(vectorArg(fileset).setExpandDirectories(true))
            .build(/* flagPerLine= */ false, RepositoryMapping.ALWAYS_FALLBACK);

    assertThrows(
        CommandLineExpansionException.class,
        () -> commandLine.arguments(new FakeActionInputFileCache(), PathMapper.NOOP));
  }

  private static VectorArg.Builder vectorArg(Object... elems) {
    return new VectorArg.Builder(Tuple.of(elems)).setLocation(Location.BUILTIN);
  }

  private static void verifyCommandLine(CommandLine commandLine, String... expected)
      throws CommandLineExpansionException, InterruptedException {
    ArgChunk chunk = commandLine.expand(new FakeActionInputFileCache(), PathMapper.NOOP);
    assertThat(chunk.arguments()).containsExactlyElementsIn(expected).inOrder();
    // Check consistency of the total argument length calculation with SimpleArgChunk, which
    // materializes strings and adds up their lengths.
    assertThat(chunk.totalArgLength())
        .isEqualTo(new SimpleArgChunk(chunk.arguments()).totalArgLength());
  }

  private SpecialArtifact createFileset(String relativePath) {
    return createSpecialArtifact(relativePath, SpecialArtifactType.FILESET);
  }

  private FilesetOutputSymlink createFilesetSymlink(String relativePath) {
    return new FilesetOutputSymlink(
        PathFragment.create(relativePath),
        ActionsTestUtil.createArtifact(derivedRoot, "some/target"),
        FileArtifactValue.createForNormalFile(new byte[] {1}, null, 1));
  }

  private SpecialArtifact createTreeArtifact(String relativePath) {
    SpecialArtifact tree = createSpecialArtifact(relativePath, SpecialArtifactType.TREE);
    tree.setGeneratingActionKey(ActionLookupData.create(ActionsTestUtil.NULL_ARTIFACT_OWNER, 0));
    return tree;
  }

  private SpecialArtifact createSpecialArtifact(String relativePath, SpecialArtifactType type) {
    return SpecialArtifact.create(
        derivedRoot,
        derivedRoot.getExecPath().getRelative(relativePath),
        ActionsTestUtil.NULL_ARTIFACT_OWNER,
        type);
  }

  private static Object execStarlark(String code) throws Exception {
    try (Mutability mutability = Mutability.create("test")) {
      StarlarkThread thread = StarlarkThread.createTransient(mutability, StarlarkSemantics.DEFAULT);
      return Starlark.execFile(
          ParserInput.fromString(code, "test/label.bzl"),
          FileOptions.DEFAULT,
          Module.withPredeclaredAndData(
              StarlarkSemantics.DEFAULT,
              ImmutableMap.of(),
              BazelModuleContext.create(
                  BazelModuleKey.createFakeModuleKeyForTesting(
                      Label.parseCanonicalUnchecked("//test:label")),
                  RepositoryMapping.ALWAYS_FALLBACK,
                  "test/label.bzl",
                  /* loads= */ ImmutableList.of(),
                  /* bzlTransitiveDigest= */ new byte[0])),
          thread);
    }
  }
}
