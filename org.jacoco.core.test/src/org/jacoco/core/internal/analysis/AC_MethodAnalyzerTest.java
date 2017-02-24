/*******************************************************************************
 * Copyright (c) 2009, 2017 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.util.ArrayList;

import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.internal.flow.IProbeIdGenerator;
import org.jacoco.core.internal.flow.LabelFlowAnalyzer;
import org.jacoco.core.internal.flow.MethodProbesAdapter;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.CheckMethodAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Unit tests for {@link MethodAnalyzer}.
 */
public class AC_MethodAnalyzerTest implements IProbeIdGenerator {

	private int nextProbeId;

	private boolean[] probes;

	private MethodNode method;

	private IMethodCoverage result;

	@Before
	public void setup() {
		nextProbeId = 0;
		method = new MethodNode();
		method.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
		probes = new boolean[32];
	}

	public int nextId() {
		return nextProbeId++;
	}

	// === Scenario: linear Sequence without branches ===

	private void createLinearSequence() {
		final Label l0 = new Label();
		method.visitLabel(l0);
		method.visitLineNumber(1001, l0);
		method.visitInsn(Opcodes.NOP);
		final Label l1 = new Label();
		method.visitLabel(l1);
		method.visitLineNumber(1002, l1);
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testLinearSequenceNotCovered1() {
		createLinearSequence();
		// printMethod(method);
		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);

		assertAcyclicPathCoverage(1, 0);
	}

	@Test
	public void testLinearSequenceNotCovered2() {
		createLinearSequence();
		probes = null;
		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);

		assertAcyclicPathCoverage(1, 0);
	}

	@Test
	public void testLinearSequenceCovered() {
		createLinearSequence();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 1, 0, 0);
		assertLine(1002, 0, 1, 0, 0);

		assertAcyclicPathCoverage(0, 1);
	}

	// === Scenario: simple if branch ===

	private void createIfBranch() {
		final Label l0 = new Label();
		method.visitLabel(l0);
		method.visitLineNumber(1001, l0);
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l1 = new Label();
		method.visitJumpInsn(Opcodes.IFEQ, l1);
		final Label l2 = new Label();
		method.visitLabel(l2);
		method.visitLineNumber(1002, l2);
		method.visitLdcInsn("a");
		method.visitInsn(Opcodes.ARETURN);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitLdcInsn("b");
		method.visitInsn(Opcodes.ARETURN);
	}

	@Test
	public void testIfBranchNotCovered() {
		createIfBranch();
		runMethodAnalzer();
		assertEquals(2, nextProbeId);

		assertLine(1001, 2, 0, 2, 0);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 2, 0, 0, 0);

		assertAcyclicPathCoverage(2, 0);
	}

	@Test
	public void testIfBranchCovered1() {
		createIfBranch();

		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 2, 0, 0, 0);

		assertAcyclicPathCoverage(1, 1);
	}

	@Test
	public void testIfBranchCovered2() {
		createIfBranch();
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 0, 2, 0, 0);

		assertAcyclicPathCoverage(1, 1);
	}

	@Test
	public void testIfBranchCovered3() {
		createIfBranch();
		// simulate two runs
		probes[0] = true;
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 0, 2);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 0, 2, 0, 0);

		assertAcyclicPathCoverage(0, 2);
	}

	// === Scenario: branch which merges back ===

	private void createIfBranchMerge() {
		final Label l0 = new Label();
		method.visitLabel(l0);
		method.visitLineNumber(1001, l0);
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l1 = new Label();
		method.visitJumpInsn(Opcodes.IFEQ, l1);
		final Label l2 = new Label();
		method.visitLabel(l2);
		method.visitLineNumber(1002, l2);
		method.visitInsn(Opcodes.NOP);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testIfBranchMergeNotCovered() {
		createIfBranchMerge();
		// printMethod(method);

		runMethodAnalzer();
		assertEquals(3, nextProbeId);

		assertLine(1001, 2, 0, 2, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	@Test
	public void testIfBranchMergeCovered1() {
		createIfBranchMerge();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	@Test
	public void testIfBranchMergeCovered2() {
		createIfBranchMerge();
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 1, 0, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	@Test
	public void testIfBranchMergeCovered3() {
		createIfBranchMerge();
		probes[0] = true;
		probes[1] = true;
		probes[2] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 0, 2);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 1, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	// === Scenario: branch which jump backwards ===

	private void createJumpBackwards() {
		final Label l0 = new Label();
		method.visitLabel(l0);
		method.visitLineNumber(1001, l0);
		final Label l1 = new Label();
		method.visitJumpInsn(Opcodes.GOTO, l1);
		final Label l2 = new Label();
		method.visitLabel(l2);
		method.visitLineNumber(1002, l2);
		method.visitInsn(Opcodes.RETURN);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitJumpInsn(Opcodes.GOTO, l2);
	}

	@Test
	public void testJumpBackwardsNotCovered() {
		createJumpBackwards();
		// printMethod(method);

		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	@Test
	public void testJumpBackwardsCovered() {
		createJumpBackwards();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 1, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 1, 0, 0);

		// TODO: add assertAcyclicPathCoverage with proper parameters and
		// make it pass.
	}

	// === Scenario: loops ===
	// TODO: construct at least one program that has a loop and test it
	// similarly to the other scenarios.

	private void createLoop() {
		// TODO: implement
	}

	private void runMethodAnalzer() {
		LabelFlowAnalyzer.markLabels(method);
		final MethodAnalyzer analyzer = new MethodAnalyzer("doit", "()V", null,
				probes);
		final MethodProbesAdapter probesAdapter = new MethodProbesAdapter(
				analyzer, this);
		// note that CheckMethodAdapter verifies that this test does not violate
		// contracts of ASM API
		method.accept(new CheckMethodAdapter(probesAdapter));
		result = analyzer.getCoverage();
	}

	// NOTE: You can add more parameters if needed
	private void assertAcyclicPathCoverage(int pathMissed, int pathCovered) {
		// TODO: implement
	}

	private void assertLine(int nr, int insnMissed, int insnCovered,
			int branchesMissed, int branchesCovered) {
		final ILine line = result.getLine(nr);
		assertEquals("Instructions in line " + nr,
				CounterImpl.getInstance(insnMissed, insnCovered),
				line.getInstructionCounter());
		assertEquals("Branches in line " + nr,
				CounterImpl.getInstance(branchesMissed, branchesCovered),
				line.getBranchCounter());
	}

	@SuppressWarnings("unused")
	private void printMethod(MethodNode m) {
		Textifier t = new Textifier();
		m.accept(new TraceMethodVisitor(t));

		PrintWriter writer = new PrintWriter(System.out);
		t.print(writer);
		writer.flush();
	}

	@SuppressWarnings("unused")
	private String branchExample(int i) {
		if (i != 0) {
			return "a";
		} else {
			return "b";
		}
	}
}
