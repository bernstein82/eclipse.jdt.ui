/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.core.refactoring;

import org.eclipse.core.runtime.IProgressMonitor;import org.eclipse.jdt.core.ICompilationUnit;import org.eclipse.jdt.core.IJavaElement;import org.eclipse.jdt.core.IPackageFragment;import org.eclipse.jdt.core.JavaModelException;import org.eclipse.jdt.internal.core.refactoring.base.IChange;import org.eclipse.jdt.internal.core.refactoring.base.ChangeContext;

public class CreateCompilationUnitChange extends CompilationUnitChange {

	private IChange fUndoChange;
	
	public CreateCompilationUnitChange(IPackageFragment parent, String source, String name){
		super(parent, source, name);
	}
	
	public String getName(){
		return RefactoringCoreMessages.getFormattedString("CreateCompilationUnitChange.create_cu", new String[] { getCUName(), getPackageName() });	//$NON-NLS-1$
	}

	public IJavaElement getCorrespondingJavaElement(){
		IPackageFragment pack= getPackage();
		return pack.getCompilationUnit(getCUName());
	}
	
	public void perform(ChangeContext context, IProgressMonitor pm) throws JavaModelException{
		try {
			String msg= RefactoringCoreMessages.getFormattedString("CreateCompilationUnitChange.creating_resource", getCUName());	//$NON-NLS-1$
			pm.beginTask(msg, 1);
			if (!isActive()){
				fUndoChange= new NullChange();	
			} else{
				IPackageFragment pack= getPackage();
				ICompilationUnit cu= pack.getCompilationUnit(getCUName());
				if (cu.exists()){
					CompositeChange composite= new CompositeChange();
					composite.addChange(new DeleteCompilationUnitChange(cu));
					/* 
					 * once you delete the file it'll not be there, so
					 * there should not be an infinite loop here
					 */
					composite.addChange(new CreateCompilationUnitChange(pack, getSource(), getCUName()));
					composite.perform(context, pm);
					fUndoChange= composite.getUndoChange();
				} else {
					ICompilationUnit newCu= pack.createCompilationUnit(getCUName(), getSource(), true, pm);
					fUndoChange= new DeleteCompilationUnitChange(newCu);
				}
			}	
			pm.done();
		} catch (Exception e) {
			handleException(context, e);
			fUndoChange= new NullChange();
			setActive(false);
		}
	}

	public IChange getUndoChange() {
		return fUndoChange;
	}

}
