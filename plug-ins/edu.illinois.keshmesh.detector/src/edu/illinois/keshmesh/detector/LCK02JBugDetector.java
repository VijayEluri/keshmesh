/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.keshmesh.detector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.AbstractTypeInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.illinois.keshmesh.detector.bugs.BugInstance;
import edu.illinois.keshmesh.detector.bugs.BugInstances;
import edu.illinois.keshmesh.detector.bugs.BugPatterns;
import edu.illinois.keshmesh.detector.bugs.CodePosition;
import edu.illinois.keshmesh.detector.bugs.LCK02JFixInformation;
import edu.illinois.keshmesh.detector.util.AnalysisUtils;
import edu.illinois.keshmesh.util.Logger;
import edu.illinois.keshmesh.walaconfig.ReceiverStringContext;

/**
 * 
 * @author Mohsen Vakilian
 * @author Stas Negara
 * 
 */
public class LCK02JBugDetector extends BugPatternDetector {

	private static final String JAVA_LANG_CLASS = "Ljava/lang/Class"; //$NON-NLS-1$

	@Override
	public IntermediateResults getIntermediateResults() {
		return null;
	}

	@Override
	public BugInstances doPerformAnalysis(IJavaProject javaProject, BasicAnalysisData analysisData) {
		final BugInstances bugInstances = new BugInstances();
		Iterator<CGNode> cgNodesIterator = basicAnalysisData.callGraph.iterator();
		while (cgNodesIterator.hasNext()) {
			final CGNode cgNode = cgNodesIterator.next();
			IMethod method = cgNode.getMethod();
			if (AnalysisUtils.isJDKClass(method.getDeclaringClass()))
				continue;
			IR ir = cgNode.getIR();
			if (ir != null) {
				AnalysisUtils.collect(javaProject, new HashSet<InstructionInfo>(), cgNode, new InstructionFilter() {
					@Override
					public boolean accept(InstructionInfo instructionInfo) {
						SSAInstruction instruction = instructionInfo.getInstruction();
						if (AnalysisUtils.isMonitorEnter(instruction)) {
							Set<String> synchronizedClassTypeNames = getSynchronizedClassTypeNames((SSAMonitorInstruction) instruction, cgNode);
							if (!synchronizedClassTypeNames.isEmpty()) {
								CodePosition instructionPosition = instructionInfo.getPosition();
								Logger.log("Detected an instance of LCK02-J in class " + instructionPosition.getFullyQualifiedClassName() + ", line number=" + instructionPosition.getFirstLine()
										+ ", instructionIndex= " + instructionInfo.getInstructionIndex());
								bugInstances.add(new BugInstance(BugPatterns.LCK02J, instructionPosition, new LCK02JFixInformation(synchronizedClassTypeNames)));
							}
						}
						return false;
					}
				});
			}
		}
		return bugInstances;
	}

	private static boolean isReturnedByGetClass(NormalAllocationInNode normalAllocationInNode) {
		return normalAllocationInNode.getSite().getDeclaredType().getName().toString().equals(JAVA_LANG_CLASS) && AnalysisUtils.isObjectGetClass(normalAllocationInNode.getNode().getMethod());
	}

	private Set<String> getSynchronizedClassTypeNames(SSAMonitorInstruction monitorInstruction, CGNode cgNode) {
		Set<String> result = new HashSet<String>();
		int lockValueNumber = monitorInstruction.getRef();
		PointerKey lockPointer = getPointerForValueNumber(cgNode, lockValueNumber);
		OrdinalSet<InstanceKey> lockObjects = basicAnalysisData.pointerAnalysis.getPointsToSet(lockPointer);
		for (InstanceKey instanceKey : lockObjects) {
			Logger.log("An InstanceKey pointed to by the lock of the synchronized block:" + instanceKey);
			if (instanceKey instanceof NormalAllocationInNode) {
				NormalAllocationInNode normalAllocationInNode = (NormalAllocationInNode) instanceKey;
				if (isReturnedByGetClass(normalAllocationInNode)) {
					//					addSynchronizedClassTypeNames(result, normalAllocationInNode);
					result.add(getReceiverTypeName(normalAllocationInNode));
				}
			}
		}
		return result;
	}

	/*
	 * The method Object.getClass() has a normal allocation instruction. This
	 * method look for the predecessors of the CGNode containing the normal
	 * allocation instruction. These predecessors make calls to
	 * Object.getClass(). This method iterates over all such invocations and
	 * returns the type names of the receivers of all such method invocations.
	 * This method is an attempt to make the analysis independent of receiver
	 * instance context. But, we encountered several problems while using this
	 * method on contexts lighter than the receiver instance context. For
	 * example, cheaper contexts are less precise and thus report too many
	 * predecessors for a the CGNode of Object.getClass(). And, too many
	 * predecessors result in too many type names to be reported as potential
	 * receivers of a call to getClass. Specifically, several exception classes
	 * get reported as the type names of the receivers of the call to
	 * Object.getClass.
	 */
	@Deprecated
	private void addSynchronizedClassTypeNames(Set<String> result, NormalAllocationInNode normalAllocationInNode) {
		{
			CGNode normalAllocationCGNode = normalAllocationInNode.getNode();
			Iterator<CGNode> predNodesIterator = basicAnalysisData.callGraph.getPredNodes(normalAllocationCGNode);
			while (predNodesIterator.hasNext()) {
				CGNode predNode = predNodesIterator.next();
				Iterator<CallSiteReference> possibleSitesIterator = basicAnalysisData.callGraph.getPossibleSites(predNode, normalAllocationCGNode);
				while (possibleSitesIterator.hasNext()) {
					CallSiteReference possibleSite = possibleSitesIterator.next();
					SSAAbstractInvokeInstruction[] calls = predNode.getIR().getCalls(possibleSite);
					for (SSAAbstractInvokeInstruction invokeInstruction : calls) {
						int invocationReceiverValueNumber = invokeInstruction.getReceiver();
						PointerKey pointerKeyForReceiver = getPointerForValueNumber(predNode, invocationReceiverValueNumber);
						OrdinalSet<InstanceKey> receiverObjects = basicAnalysisData.pointerAnalysis.getPointsToSet(pointerKeyForReceiver);
						for (InstanceKey receiverInstanceKey : receiverObjects) {
							result.add(getJavaClassName(receiverInstanceKey.getConcreteType().getName()));
						}
					}
				}
			}
		}
	}

	private static String getReceiverTypeName(AbstractTypeInNode node) {
		TypeName typeName = ((ReceiverStringContext) (node.getNode().getContext())).getReceiver().getConcreteType().getName();
		return getJavaClassName(typeName);
	}

	private static String getJavaClassName(TypeName typeName) {
		return AnalysisUtils.walaTypeNameToJavaName(typeName) + ".class";
	}

}
