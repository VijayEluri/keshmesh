/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.keshmesh.detector;

import java.util.Iterator;
import java.util.Map;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.BitVectorUnion;
import com.ibm.wala.dataflow.graph.BitVectorUnionVector;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixedpoint.impl.UnaryOperator;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.intset.BitVector;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import edu.illinois.keshmesh.detector.util.AnalysisUtils;

/**
 * 
 * @author Mohsen Vakilian
 * @author Stas Negara
 * 
 */
public class LCK06JTransferFunctionProvider implements ITransferFunctionProvider<CGNode, BitVectorVariable> {

	private final CallGraph callGraph;
	private final Map<CGNode, CGNodeInfo> cgNodeInfoMap;

	public LCK06JTransferFunctionProvider(CallGraph callGraph, Map<CGNode, CGNodeInfo> cgNodeInfoMap) {
		this.callGraph = callGraph;
		this.cgNodeInfoMap = cgNodeInfoMap;
	}

	@Override
	public AbstractMeetOperator<BitVectorVariable> getMeetOperator() {
		return BitVectorUnion.instance();
	}

	@Override
	public boolean hasEdgeTransferFunctions() {
		return true;
	}

	@Override
	public UnaryOperator<BitVectorVariable> getEdgeTransferFunction(CGNode src, CGNode dst) {
		CGNodeInfo srcNodeInfo = cgNodeInfoMap.get(src);
		CGNodeInfo dstNodeInfo = cgNodeInfoMap.get(dst);
		Iterator<CallSiteReference> callSitesIterator = callGraph.getPossibleSites(dst, src);
		IR dstIR = dst.getIR();
		while (callSitesIterator.hasNext()) {
			CallSiteReference callSiteReference = callSitesIterator.next();
			IntSet callInstructionIndices = dstIR.getCallInstructionIndices(callSiteReference);
			IntIterator instructionIndicesIterator = callInstructionIndices.intIterator();
			while (instructionIndicesIterator.hasNext()) {
				int invokeInstructionIndex = instructionIndicesIterator.next();
				InstructionInfo instructionInfo = new InstructionInfo(dst, invokeInstructionIndex);
				if (!AnalysisUtils.isProtectedByAnySynchronizedBlock(dstNodeInfo.getSafeSynchronizedBlocks(), instructionInfo)) {
					return new BitVectorUnionVector(srcNodeInfo.getBitVector());
				}
			}
		}
		return new BitVectorUnionVector(new BitVector());
	}

	@Override
	public boolean hasNodeTransferFunctions() {
		return false;
	}

	@Override
	public UnaryOperator<BitVectorVariable> getNodeTransferFunction(CGNode node) {
		return null;
	}

}