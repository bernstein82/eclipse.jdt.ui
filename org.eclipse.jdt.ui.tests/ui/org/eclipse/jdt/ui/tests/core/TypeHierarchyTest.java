/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.core;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.IEditorPart;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeHierarchyChangedListener;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;

public class TypeHierarchyTest extends TestCase {
	
	private static final Class THIS= TypeHierarchyTest.class;
	
	private IJavaProject fJavaProject1;
	private IJavaProject fJavaProject2;
	
	public TypeHierarchyTest(String name) {
		super(name);
	}
			
	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}

	public static Test suite() {
		if (true) {
			return allTests();
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new AllTypesCacheTest("testHierarchyWithWorkingCopy"));
			return suite;
		}	
	}
	
	protected void setUp() throws Exception {
		fJavaProject1= ProjectTestSetup.getProject();
		fJavaProject2= JavaProjectHelper.createJavaProject("TestProject2", "bin");
	}

	protected void tearDown () throws Exception {
		JavaProjectHelper.clear(fJavaProject1, ProjectTestSetup.getDefaultClasspath());
		JavaProjectHelper.delete(fJavaProject2);		
	}
					
	public void test1() throws Exception {
		IPackageFragmentRoot root1= JavaProjectHelper.addSourceContainer(fJavaProject1, "src");
		IPackageFragment pack1= root1.createPackageFragment("pack1", true, null);
		
		ICompilationUnit cu1= pack1.getCompilationUnit("A.java");
		cu1.createType("public class A {\n}\n", null, true, null);
		
		JavaProjectHelper.addRTJar(fJavaProject2);
		JavaProjectHelper.addRequiredProject(fJavaProject2, fJavaProject1);
		IPackageFragmentRoot root2= JavaProjectHelper.addSourceContainer(fJavaProject2, "src");
		IPackageFragment pack2= root2.createPackageFragment("pack2", true, null);
		
		ICompilationUnit cu2= pack2.getCompilationUnit("B.java");
		IType type2= cu2.createType("public class B extends pack1.A {\n}\n", null, true, null);
		
		ITypeHierarchy hierarchy= type2.newSupertypeHierarchy(null);
		IType[] allTypes= hierarchy.getAllTypes();
		
		assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
		
		IType type= fJavaProject2.findType("pack1.A");
		assertTrue("Type not found", type != null);

	}
	
	
	public void testHierarchyWithWorkingCopy1() throws Exception {
		
		if (!JavaPlugin.USE_WORKING_COPY_OWNERS) {
			return;
		}
		
		IPackageFragmentRoot root1= JavaProjectHelper.addSourceContainer(fJavaProject1, "src");
		IPackageFragment pack1= root1.createPackageFragment("pack1", true, null);
		
		ICompilationUnit cu1= pack1.getCompilationUnit("A.java");
		cu1.createType("public class A {\n}\n", null, true, null);
		
		IPackageFragment pack2= root1.createPackageFragment("pack2", true, null);
		
		ICompilationUnit cu2= pack2.getCompilationUnit("B.java");
		IType type2= cu2.createType("public class B extends pack1.A {\n}\n", null, true, null);
		
		final int[] updateCount= new int[] {0};
		
		ITypeHierarchy hierarchy= type2.newSupertypeHierarchy(null);
		hierarchy.addTypeHierarchyChangedListener(new ITypeHierarchyChangedListener() {
			public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
				updateCount[0]++;
			}
		});
		
		IType[] allTypes= hierarchy.getAllTypes();
		
		assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
		assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
		
		IEditorPart part= EditorUtility.openInEditor(type2);
		try {
			IDocument document= JavaUI.getDocumentProvider().getDocument(part.getEditorInput());
			String superType= "pack1.A";
			
			int offset= document.search(0, superType, true, true, false);
			
			document.replace(offset, superType.length(), "Object");
			
			allTypes= hierarchy.getAllTypes();
		
			// no update of hierarchies on working copies
			assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
			assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
			
			part.doSave(null);
			
			allTypes= hierarchy.getAllTypes();
		
			// update after save
			assertTrue("Update count should be 1, is: " + updateCount[0], updateCount[0] == 1);
			hierarchy.refresh(null);
			assertTrue("Should contain 2 types, contains: " + allTypes.length, allTypes.length == 2);
			
		} finally {
			JavaPlugin.getActivePage().closeAllEditors(false);
		}
		
		allTypes= hierarchy.getAllTypes();
		
		// update after save
		assertTrue("Update count should be 1, is: " + updateCount[0], updateCount[0] == 1);
		assertTrue("Should contain 2 types, contains: " + allTypes.length, allTypes.length == 2);


	}
	
	public void testHierarchyWithWorkingCopy2() throws Exception {
		
		if (!JavaPlugin.USE_WORKING_COPY_OWNERS) {
			return;
		}
		
		IPackageFragmentRoot root1= JavaProjectHelper.addSourceContainer(fJavaProject1, "src");
		IPackageFragment pack1= root1.createPackageFragment("pack1", true, null);
		
		ICompilationUnit cu1= pack1.getCompilationUnit("A.java");
		cu1.createType("public class A {\n}\n", null, true, null);
		
		IPackageFragment pack2= root1.createPackageFragment("pack2", true, null);
		
		ICompilationUnit cu2= pack2.getCompilationUnit("B.java");
		IType type2= cu2.createType("public class B extends pack1.A {\n}\n", null, true, null);
			
		IEditorPart part= EditorUtility.openInEditor(type2);

		final int[] updateCount= new int[] {0};
		
		// create on type in working copy
		ITypeHierarchy hierarchy= type2.newSupertypeHierarchy(null);
		hierarchy.addTypeHierarchyChangedListener(new ITypeHierarchyChangedListener() {
			public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
				updateCount[0]++;
			}
		});
		
		IType[] allTypes= hierarchy.getAllTypes();
	
		assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
		assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
		
		try {
			
			IDocument document= JavaUI.getDocumentProvider().getDocument(part.getEditorInput());
			String superType= "pack1.A";
			
			int offset= document.search(0, superType, true, true, false);
			
			document.replace(offset, superType.length(), "Object");
			
			allTypes= hierarchy.getAllTypes();
		
			// no update of hierarchies on working copies
			assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
			assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
			
			part.doSave(null);
			
			allTypes= hierarchy.getAllTypes();
		
			// update after save
			assertTrue("Update count should be 1, is: " + updateCount[0], updateCount[0] == 1);
			hierarchy.refresh(null);
			assertTrue("Should contain 2 types, contains: " + allTypes.length, allTypes.length == 2);

		} finally {
			JavaPlugin.getActivePage().closeAllEditors(false);
		}
		
		allTypes= hierarchy.getAllTypes();
		
		// update after save
		assertTrue("Update count should be 1, is: " + updateCount[0], updateCount[0] == 1);
		assertTrue("Should contain 2 types, contains: " + allTypes.length, allTypes.length == 2);	

	}
	
	public void testHierarchyWithWorkingCopy3() throws Exception {
		
		if (!JavaPlugin.USE_WORKING_COPY_OWNERS) {
			return;
		}
		
		IPackageFragmentRoot root1= JavaProjectHelper.addSourceContainer(fJavaProject1, "src");
		IPackageFragment pack1= root1.createPackageFragment("pack1", true, null);
		
		ICompilationUnit cu1= pack1.getCompilationUnit("A.java");
		cu1.createType("public class A {\n}\n", null, true, null);
		
		IPackageFragment pack2= root1.createPackageFragment("pack2", true, null);
		
		ICompilationUnit cu2= pack2.getCompilationUnit("B.java");
		IType type2= cu2.createType("public class B extends pack1.A {\n}\n", null, true, null);
		
		// open editor -> working copy will be created
		IEditorPart part= EditorUtility.openInEditor(type2);

		final int[] updateCount= new int[] {0};
		
		// create on type in working copy
		ITypeHierarchy hierarchy= type2.newSupertypeHierarchy(null);
		hierarchy.addTypeHierarchyChangedListener(new ITypeHierarchyChangedListener() {
			public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
				updateCount[0]++;
			}
		});
		
		IType[] allTypes= hierarchy.getAllTypes();
	
		assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
		assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
		
		try {			
			IDocument document= JavaUI.getDocumentProvider().getDocument(part.getEditorInput());
			String superType= "pack1.A";
			
			int offset= document.search(0, superType, true, true, false);
			// modify source
			document.replace(offset, superType.length(), "Object");
			
			allTypes= hierarchy.getAllTypes();
		
			// no update of hierarchies on working copies
			assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
			assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);
			
			// no save
			
		} finally {
			JavaPlugin.getActivePage().closeAllEditors(false);
		}
		
		allTypes= hierarchy.getAllTypes();
		
		// update after save
		assertTrue("Should contain 3 types, contains: " + allTypes.length, allTypes.length == 3);
		assertTrue("Update count should be 0, is: " + updateCount[0], updateCount[0] == 0);

	}
	
	


}
