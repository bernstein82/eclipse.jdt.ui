/*******************************************************************************
 * Copyright (c) 2000, 2004 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.participants.xml;

import org.eclipse.core.runtime.CoreException;

/**
 * A property tester can be used to add additional properties to test to an 
 * existing type.
 * <p>
 * This interface is not intended to be implemented by clients. Clients
 * should subclass type <code>PropertyTester</code>.
 * </p>
 * 
 * @since 3.0
 */
/* package */ interface IPropertyTester {

	/**
	 * Returns whether the property tester can handle the given
	 * property or not.
	 * 
	 * @param namespace the name space to be considered
	 * @param property the property to test
	 * @return <code>true</code> if the tester provides an implementation
	 *  for the given property; otherwise <code>false</code> is returned
	 */
	public boolean handles(String namespace, String property);
	
	/**
	 * Returns whether the implementation class for this property tester is
	 * loaded or not.
	 * 
	 * @return <code>true</code>if the implementation class is loaded; 
	 *  <code>false</code> otherwise
	 */
	public boolean isLoaded();
	
	/**
	 * Returns <code>true</code> if the implementation class of this property
	 * tester can be loaded. This is the case if the plug-in providing
	 * the implementation class is loaded. Returns <code>false</code> otherwise.
	 * 
	 * @return whether the implementation class can be loaded or not
	 */
	public boolean canLoad();
	
	/**
	 * Loads the implementation class for this property tester and returns an
	 * instance of this class.
	 * 
	 * @return an instance of the implementation class for this property tester
	 * 
	 * @throws CoreException if the implementation class cannot be loaded
	 */
	public IPropertyTester load() throws CoreException;
	
	/**
	 * Executes the property test determined by the parameter <code>property</code>. 
	 * 
	 * @param receiver the receiver of the property test
	 * @param property the property to test
	 * @param args additional arguments to resolve the property. If no arguments
	 *  are specified in the <code>test</code> expression an array of length 0
	 *  is passed
	 * @param expectedValue the expected value of the property. The value is either 
	 *  of type <code>java.lang.String</code> or a boxed base type
	 * 
	 * @return returns <code>true<code> if the property is equal to the expected value; 
	 *  otherwise <code>false</code> is returned
	 */
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue);
}
