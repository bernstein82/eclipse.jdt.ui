/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.core.refactoring.rename;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.core.codemanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.core.codemanipulation.TextBuffer;
import org.eclipse.jdt.internal.core.codemanipulation.TextEdit;
import org.eclipse.jdt.internal.core.codemanipulation.TextPosition;
import org.eclipse.jdt.internal.core.refactoring.Assert;
import org.eclipse.jdt.internal.core.refactoring.Checks;
import org.eclipse.jdt.internal.core.refactoring.CompositeChange;
import org.eclipse.jdt.internal.core.refactoring.JavaModelUtility;
import org.eclipse.jdt.internal.core.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.core.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.core.refactoring.SearchResult;
import org.eclipse.jdt.internal.core.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.core.refactoring.base.IChange;
import org.eclipse.jdt.internal.core.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.core.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.core.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.core.refactoring.changes.TextChange;
import org.eclipse.jdt.internal.core.refactoring.tagging.IReferenceUpdatingRefactoring;
import org.eclipse.jdt.internal.core.refactoring.tagging.IRenameRefactoring;

public abstract class RenameMethodRefactoring extends Refactoring implements IRenameRefactoring, IReferenceUpdatingRefactoring{
	
	private String fNewName;
	private SearchResultGroup[] fOccurrences;
	private boolean fUpdateReferences;
	private IMethod fMethod;
	
	RenameMethodRefactoring(IMethod method) {
		Assert.isNotNull(method);
		fMethod= method;
		fUpdateReferences= true;
	}
		
	/**
	 * Factory method to create appropriate instances
	 */
	public static RenameMethodRefactoring createInstance(IMethod method) throws JavaModelException{
		 int flags= method.getFlags();
		 if (Flags.isPrivate(flags))
		 	return new RenamePrivateMethodRefactoring(method);
		 else if (Flags.isStatic(flags))
		 	return new RenameStaticMethodRefactoring(method);
		 else if (method.getDeclaringType().isClass())	
		 	return new RenameVirtualMethodRefactoring(method);
		 else 	
		 	return new RenameMethodInInterfaceRefactoring(method);	
	}	
	
	/* non java-doc
	 * @see Refactoring#checkPreconditions(IProgressMonitor)
	 */
	public RefactoringStatus checkPreconditions(IProgressMonitor pm) throws JavaModelException{
		RefactoringStatus result= checkPreactivation();
		if (result.hasFatalError())
			return result;
		result.merge(super.checkPreconditions(pm));
		return result;
	}

	/* non java-doc
	 * @see IRenameRefactoring#setNewName
	 */
	public final void setNewName(String newName){
		Assert.isNotNull(newName);
		fNewName= newName;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#getCurrentName
	 */
	public final String getCurrentName(){
		return fMethod.getElementName();
	}
		
	/* non java-doc
	 * @see IRenameRefactoring#getNewName
	 */		
	public final String getNewName(){
		return fNewName;
	}
		
	/* non java-doc
	 * @see IRefactoring#getName
	 */
	public String getName(){
		return RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.name", //$NON-NLS-1$
															new String[]{fMethod.getElementName(), getNewName()});
	}
	
	public final IMethod getMethod(){
		return fMethod;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#canUpdateReferences()
	 */
	public boolean canEnableUpdateReferences() {
		return true;
	}

	/* non java-doc
	 * @see IRenameRefactoring#setUpdateReferences(boolean)
	 */
	public final void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}	
	
	/* non java-doc
	 * @see IRenameRefactoring#getUpdateReferences()
	 */	
	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}	
	
	//----------- preconditions ------------------
	
	/*
	 * non java-doc
	 * @see IPreactivatedRefactoring#checkPreactivation
	 */
	public RefactoringStatus checkPreactivation() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		result.merge(checkAvailability(fMethod));
		if (isSpecialCase(fMethod))
			result.addError(RefactoringCoreMessages.getString("RenameMethodRefactoring.special_case")); //$NON-NLS-1$
		if (fMethod.isConstructor())
			result.addFatalError(RefactoringCoreMessages.getString("RenameMethodRefactoring.no_constructors"));	 //$NON-NLS-1$
		return result;
	}

	/*
	 * non java-doc
	 * @see Refactoring#checkActivation
	 */		
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException{
		IMethod orig= (IMethod)WorkingCopyUtil.getOriginal(fMethod);
		if (orig == null || ! orig.exists())
			return RefactoringStatus.createFatalErrorStatus("Please save the compilation unit '" + fMethod.getCompilationUnit().getElementName()+ "' before performing this refactoring.");
		fMethod= orig;
		
		RefactoringStatus result= Checks.checkIfCuBroken(fMethod);
		if (Flags.isNative(fMethod.getFlags()))
			result.addError(RefactoringCoreMessages.getString("RenameMethodRefactoring.no_native")); //$NON-NLS-1$
		return result;
	}
	
	/* non java-doc
	 * @see IRenameRefactoring#checkNewName
	 */
	public final RefactoringStatus checkNewName() {
		RefactoringStatus result= new RefactoringStatus();
		result.merge(Checks.checkMethodName(fNewName));
		result.merge(checkIfConstructorName(fMethod));
					
		if (Checks.isAlreadyNamed(fMethod, getNewName()))
			result.addFatalError(RefactoringCoreMessages.getString("RenameMethodRefactoring.same_name")); //$NON-NLS-1$
		return result;
	}
	
	/*
	 * non java-doc
	 * @see Refactoring#checkInput
	 */	
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException{
		try{
			RefactoringStatus result= new RefactoringStatus();
			pm.beginTask("", 6); //$NON-NLS-1$
			result.merge(Checks.checkIfCuBroken(fMethod));
			if (result.hasFatalError())
				return result;
			pm.subTask(RefactoringCoreMessages.getString("RenameMethodRefactoring.checking_name")); //$NON-NLS-1$	
			result.merge(checkNewName());
			pm.worked(1);
			
			if (!fUpdateReferences)
				return result;
				
			result.merge(Checks.checkAffectedResourcesAvailability(getOccurrences(new SubProgressMonitor(pm, 4))));
			pm.subTask(RefactoringCoreMessages.getString("RenameMethodRefactoring.analyzing_hierarchy")); //$NON-NLS-1$
			result.merge(checkRelatedMethods(new SubProgressMonitor(pm, 1)));
			
			result.merge(analyzeCompilationUnits(new SubProgressMonitor(pm, 3)));	
			
			return result;
		} finally{
			pm.done();
		}	
	}
	
	private IJavaSearchScope createRefactoringScope() throws JavaModelException{
		return RefactoringScope.create(fMethod);
	}
	
	ISearchPattern createSearchPattern(IProgressMonitor pm) throws JavaModelException{
		pm.beginTask("", 4); //$NON-NLS-1$
		Set methods= methodsToRename(fMethod, new SubProgressMonitor(pm, 3));
		Iterator iter= methods.iterator();
		ISearchPattern pattern= SearchEngine.createSearchPattern((IMethod)iter.next(), IJavaSearchConstants.ALL_OCCURRENCES);
		
		while (iter.hasNext()){
			ISearchPattern methodPattern= SearchEngine.createSearchPattern((IMethod)iter.next(), IJavaSearchConstants.ALL_OCCURRENCES);	
			pattern= SearchEngine.createOrSearchPattern(pattern, methodPattern);
		}
		pm.done();
		return pattern;
	}
	
	Set getMethodsToRename(IMethod method, IProgressMonitor pm) throws JavaModelException{
		//method has been added in the caller	
		pm.done();
		return new HashSet(0);
	}
	
	private final Set methodsToRename(IMethod method, IProgressMonitor pm) throws JavaModelException{
		HashSet methods= new HashSet();
		pm.beginTask("", 1); //$NON-NLS-1$
		methods.add(method);
		methods.addAll(getMethodsToRename(method, new SubProgressMonitor(pm, 1)));
		pm.done();
		return methods;
	}
	
	SearchResultGroup[] getOccurrences(IProgressMonitor pm) throws JavaModelException{
		if (fOccurrences != null)
			return fOccurrences;

		if (pm == null)
			pm= new NullProgressMonitor();
		pm.beginTask("", 2);	 //$NON-NLS-1$
		pm.subTask(RefactoringCoreMessages.getString("RenameMethodRefactoring.creating_pattern")); //$NON-NLS-1$
		ISearchPattern pattern= createSearchPattern(new SubProgressMonitor(pm, 1));
		pm.subTask(RefactoringCoreMessages.getString("RenameMethodRefactoring.searching")); //$NON-NLS-1$
		fOccurrences= RefactoringSearchEngine.search(new SubProgressMonitor(pm, 1), createRefactoringScope(), pattern);	
		pm.done();
		return fOccurrences;
	}

	private static boolean isSpecialCase(IMethod method) throws JavaModelException{
		if (  method.getElementName().equals("toString") //$NON-NLS-1$
			&& (method.getNumberOfParameters() == 0)
			&& (method.getReturnType().equals("Ljava.lang.String;")  //$NON-NLS-1$
				|| method.getReturnType().equals("QString;") //$NON-NLS-1$
				|| method.getReturnType().equals("Qjava.lang.String;"))) //$NON-NLS-1$
			return true;		
		else return (JavaModelUtility.isMainMethod(method));
	}
	
	private RefactoringStatus checkIfConstructorName(IMethod method){
		return Checks.checkIfConstructorName(method, fNewName, method.getDeclaringType().getElementName());
	}
	
	private RefactoringStatus checkRelatedMethods(IProgressMonitor pm) throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		for (Iterator iter= getMethodsToRename(fMethod, pm).iterator(); iter.hasNext(); ){
			IMethod method= (IMethod)iter.next();
			
			result.merge(checkIfConstructorName(method));
			
			String[] msgData= new String[]{method.getElementName(), method.getDeclaringType().getFullyQualifiedName()};
			if (! method.exists()){
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.not_in_model", msgData)); //$NON-NLS-1$ 
				continue;
			}
			if (method.isBinary())
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_binary", msgData)); //$NON-NLS-1$
			if (method.isReadOnly())
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_read_only", msgData));//$NON-NLS-1$
			if (Flags.isNative(method.getFlags()))
				result.addError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_native_1", msgData));//$NON-NLS-1$
		}
		return result;	
	}
	
	private RefactoringStatus analyzeCompilationUnits(IProgressMonitor pm) throws JavaModelException{
		if (fOccurrences.length == 0)
			return null;
			
		RefactoringStatus result= new RefactoringStatus();
		fOccurrences= Checks.excludeCompilationUnits(fOccurrences, getUnsavedFiles(), result);
		if (result.hasFatalError())
			return result;
		
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fOccurrences));	
			
		RenameMethodASTAnalyzer analyzer= new RenameMethodASTAnalyzer(getNewName(), fMethod);
		for (int i= 0; i < fOccurrences.length; i++){	
			if (pm.isCanceled())
				throw new OperationCanceledException();
			
			ICompilationUnit cu= (ICompilationUnit)JavaCore.create(fOccurrences[i].getResource());
			pm.subTask(RefactoringCoreMessages.getFormattedString("RenameVirtualMethodRefactoring.analyzing", cu.getElementName())); //$NON-NLS-1$
			result.merge(analyzer.analyze(fOccurrences[i].getSearchResults(), cu));
		}
		return result;
	}
	
	private boolean classesDeclareMethodName(ITypeHierarchy hier, List classes, IMethod method, String newName)  throws JavaModelException  {

		IType type= method.getDeclaringType();
		List subtypes= Arrays.asList(hier.getAllSubtypes(type));
		
		int parameterCount= method.getParameterTypes().length;
		boolean isMethodPrivate= Flags.isPrivate(method.getFlags());
		
		for (Iterator iter= classes.iterator(); iter.hasNext(); ){
			IType clazz= (IType) iter.next();
			IMethod[] methods= clazz.getMethods();
			boolean isSubclass= subtypes.contains(clazz);
			for (int j= 0; j < methods.length; j++) {
				if (null == Checks.findMethod(newName, parameterCount, false, new IMethod[] {methods[j]}))
					continue;
				if (isSubclass || type.equals(clazz))
					return true;
				if ((! isMethodPrivate) && (! Flags.isPrivate(methods[j].getFlags())))
					return true;
			}
		}
		return false;
	}
	
	boolean hierarchyDeclaresMethodName(IProgressMonitor pm, IMethod method, String newName) throws JavaModelException{
		IType type= method.getDeclaringType();
		ITypeHierarchy hier= type.newTypeHierarchy(pm);
		if (null != Checks.findMethod(newName, method.getParameterTypes().length, false, type)) {
			return true;
		}
		IType[] implementingClasses= hier.getImplementingClasses(type);
		return classesDeclareMethodName(hier, Arrays.asList(hier.getAllClasses()), method, newName) 
			|| classesDeclareMethodName(hier, Arrays.asList(implementingClasses), method, newName);
	}
				
	//-------- changes -----
	
	public final IChange createChange(IProgressMonitor pm) throws JavaModelException {
		try {
			pm.beginTask("", 1);
			pm.subTask(RefactoringCoreMessages.getString("RenameMethodRefactoring.creating_change")); //$NON-NLS-1$
			CompositeChange builder= new CompositeChange();
			
			/* don't really want to add declaration and references separetely in this refactoring 
			* (declarations of methods are different than declarations of anything else)
			 */
			if (! fUpdateReferences)
				addDeclarationUpdate(builder);
			else	
				addOccurrences(new SubProgressMonitor(pm, 1), builder);

			return builder;
		} catch (CoreException e){
			throw new JavaModelException(e);	
		} finally{
			pm.done();
		}	
	}
	
	void addOccurrences(IProgressMonitor pm, CompositeChange builder) throws CoreException{
		pm.beginTask("", fOccurrences.length);
		for (int i= 0; i < fOccurrences.length; i++){
			IJavaElement element= JavaCore.create(fOccurrences[i].getResource());
			if (!(element instanceof ICompilationUnit))
				continue;
			String name= RefactoringCoreMessages.getString("RenameMethodRefactoring.update_references");
			ICompilationUnit cu= WorkingCopyUtil.getWorkingCopyIfExists((ICompilationUnit)element);
			TextChange change= new CompilationUnitChange(name, cu);
			SearchResult[] results= fOccurrences[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				String editName= RefactoringCoreMessages.getString("RenameMethodRefactoring.update_reference");
				change.addTextEdit(editName, createTextChange(results[j]));
			}
			builder.addChange(change);
			pm.worked(1);
		}
	}
	
	private void addDeclarationUpdate(CompositeChange builder) throws CoreException{
		String name= RefactoringCoreMessages.getString("RenameMethodRefactoring.update_references");
		ICompilationUnit cu= WorkingCopyUtil.getWorkingCopyIfExists(fMethod.getCompilationUnit());
		TextChange change= new CompilationUnitChange(name, cu);
		addDeclarationUpdate(change);
		builder.addChange(change);
	}
	
	void addDeclarationUpdate(TextChange change) throws JavaModelException{
		change.addTextEdit("declaration update", SimpleTextEdit.createReplace(fMethod.getNameRange().getOffset(), fMethod.getNameRange().getLength(), fNewName)); 
	}
	
	TextEdit createTextChange(SearchResult searchResult) {
		return new UpdateMethodReferenceEdit(searchResult.getStart(), searchResult.getEnd() - searchResult.getStart(), fNewName, fMethod.getElementName());		
	}
	
	//----
	private static class UpdateMethodReferenceEdit extends SimpleTextEdit{

		private String fOldName;
		
		UpdateMethodReferenceEdit(int offset, int length, String newName, String oldName) {
			super(offset, length, newName);
			Assert.isNotNull(oldName);
			fOldName= oldName;			
		}
		
		private UpdateMethodReferenceEdit(TextPosition position, String newName, String oldName) {
			super(position, newName);
			Assert.isNotNull(oldName);
			fOldName= oldName;			
		}

		/* non Java-doc
		 * @see TextEdit#copy
		 */
		public TextEdit copy() {
			return new UpdateMethodReferenceEdit(getTextPosition().copy(), getText(), fOldName);
		}

		/* non Java-doc
		 * @see TextEdit#connect(TextBuffer)
		 */
		public void connect(TextBuffer buffer) throws CoreException {
			TextPosition pos= getTextPosition();
			String oldText= buffer.getContent(pos.getOffset(), pos.getLength());
			String oldMethodName= fOldName;
			int leftBracketIndex= oldText.indexOf("("); //$NON-NLS-1$
			if (leftBracketIndex == -1)
				return; 
			int offset= pos.getOffset();
			int length= leftBracketIndex;
			oldText= oldText.substring(0, leftBracketIndex);
			int theDotIndex= oldText.lastIndexOf("."); //$NON-NLS-1$
			if (theDotIndex == -1) {
				setText(getText() + oldText.substring(oldMethodName.length()));
			} else {
				String subText= oldText.substring(theDotIndex);
				int oldNameIndex= subText.indexOf(oldMethodName) + theDotIndex;
				String ending= oldText.substring(theDotIndex, oldNameIndex) + getText();
				oldText= oldText.substring(0, oldNameIndex + oldMethodName.length());
				length= oldNameIndex + oldMethodName.length();
				setText(oldText.substring(0, theDotIndex) + ending);
			}			
			setPosition(new TextPosition(offset, length));
		}
	}
}	
