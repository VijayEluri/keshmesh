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
import com.ibm.wala.util.intset.IntSet;

import edu.illinois.keshmesh.detector.util.AnalysisUtils;

public class LCK06JTransferFunctionProvider implements ITransferFunctionProvider<CGNode, BitVectorVariable> {

	private final CallGraph callGraph;
	private final Map<CGNode, CGNodeInfo> cgNodeInfoMap;

	public LCK06JTransferFunctionProvider(CallGraph callGraph, Map<CGNode, CGNodeInfo> cgNodeInfoMap) {
		this.callGraph = callGraph;
		this.cgNodeInfoMap = cgNodeInfoMap;
	}

	public AbstractMeetOperator<BitVectorVariable> getMeetOperator() {
		return BitVectorUnion.instance();
	}

	public boolean hasEdgeTransferFunctions() {
		return true;
	}

	public UnaryOperator<BitVectorVariable> getEdgeTransferFunction(CGNode src, CGNode dst) {
		CGNodeInfo srcNodeInfo = cgNodeInfoMap.get(src);
		CGNodeInfo dstNodeInfo = cgNodeInfoMap.get(dst);
		Iterator<CallSiteReference> callSitesIterator = callGraph.getPossibleSites(dst, src);
		BitVector resultBitVector = new BitVector();
		IR dstIR = dst.getIR();
		while (callSitesIterator.hasNext()) {
			CallSiteReference callSiteReference = callSitesIterator.next();
			IntSet callInstructionIndices = dstIR.getCallInstructionIndices(callSiteReference);
			if (callInstructionIndices.size() > 1) {
				throw new RuntimeException("Multiple invocations found for call site reference: " + callSiteReference);
			}
			int invokeInstructionIndex = callInstructionIndices.intIterator().next();
			InstructionInfo instructionInfo = new InstructionInfo(dst, invokeInstructionIndex);
			if (!AnalysisUtils.isProtectedByAnySynchronizedBlock(dstNodeInfo.getSafeSynchronizedBlocks(), instructionInfo)) {
				resultBitVector.or(srcNodeInfo.getBitVector());
				break;
			}
		}
		return new BitVectorUnionVector(resultBitVector);
	}

	public boolean hasNodeTransferFunctions() {
		return false;
	}

	public UnaryOperator<BitVectorVariable> getNodeTransferFunction(CGNode node) {
		return null;
	}

}
