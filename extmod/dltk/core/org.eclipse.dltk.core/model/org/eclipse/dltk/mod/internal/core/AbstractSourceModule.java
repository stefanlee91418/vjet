/*******************************************************************************
 * Copyright (c) 2000-2011 IBM Corporation and others, eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     eBay Inc - modification
 *******************************************************************************/
package org.eclipse.dltk.mod.internal.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.dltk.mod.compiler.CharOperation;
import org.eclipse.dltk.mod.core.DLTKContentTypeManager;
import org.eclipse.dltk.mod.core.DLTKCore;
import org.eclipse.dltk.mod.core.DLTKLanguageManager;
import org.eclipse.dltk.mod.core.IBuffer;
import org.eclipse.dltk.mod.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.mod.core.IField;
import org.eclipse.dltk.mod.core.IMethod;
import org.eclipse.dltk.mod.core.IModelElement;
import org.eclipse.dltk.mod.core.IModelStatus;
import org.eclipse.dltk.mod.core.IModelStatusConstants;
import org.eclipse.dltk.mod.core.IPackageDeclaration;
import org.eclipse.dltk.mod.core.IProjectFragment;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.core.ISourceElementParser;
import org.eclipse.dltk.mod.core.ISourceElementParserExtension;
import org.eclipse.dltk.mod.core.ISourceModule;
import org.eclipse.dltk.mod.core.ISourceRange;
import org.eclipse.dltk.mod.core.IType;
import org.eclipse.dltk.mod.core.ModelException;
import org.eclipse.dltk.mod.core.SourceParserUtil;
import org.eclipse.dltk.mod.core.WorkingCopyOwner;
import org.eclipse.dltk.mod.internal.core.ModelManager.PerWorkingCopyInfo;
import org.eclipse.dltk.mod.internal.core.util.MementoTokenizer;
import org.eclipse.dltk.mod.internal.core.util.Messages;
import org.eclipse.dltk.mod.internal.core.util.Util;
import org.eclipse.dltk.mod.utils.CorePrinter;

public abstract class AbstractSourceModule extends Openable implements
		ISourceModule, org.eclipse.dltk.mod.compiler.env.ISourceModule {

	// ~ Static fields/initializers

	// ~ Static fields/initializers

	private static final boolean DEBUG_PRINT_MODEL = DLTKCore.DEBUG_PRINT_MODEL;

	// ~ Instance fields

	// ~ Instance fields

	private String name;

	private WorkingCopyOwner owner;
	private boolean readOnly;

	// ~ Constructors

	// ~ Constructors

	protected AbstractSourceModule(ModelElement parent, String name,
			WorkingCopyOwner owner) {
		this(parent, name, owner, false);
	}

	protected AbstractSourceModule(ModelElement parent, String name,
			WorkingCopyOwner owner, boolean readOnly) {
		super(parent);

		this.name = name;
		this.owner = owner;
		this.readOnly = readOnly;
	}

	// ~ Methods

	/*
	 * @see org.eclipse.dltk.mod.core.ICodeAssist#codeSelect(int, int)
	 */
	public IModelElement[] codeSelect(int offset, int length)
			throws ModelException {
		return codeSelect(offset, length, DefaultWorkingCopyOwner.PRIMARY);
	}

	/*
	 * @see org.eclipse.dltk.mod.core.ICodeAssist#codeSelect(int, int,
	 * org.eclipse.dltk.mod.core.WorkingCopyOwner)
	 */
	public IModelElement[] codeSelect(int offset, int length,
			WorkingCopyOwner owner) throws ModelException {
		return super.codeSelect(this, offset, length, owner);
	}

	public void copy(IModelElement container, IModelElement sibling,
			String rename, boolean replace, IProgressMonitor monitor)
			throws ModelException {
		if (container == null) {
			throw new IllegalArgumentException(Messages.operation_nullContainer);
		}

		IModelElement[] elements = new IModelElement[] { this };
		IModelElement[] containers = new IModelElement[] { container };
		String[] renamings = null;
		if (rename != null) {
			renamings = new String[] { rename };
		}

		getModel()
				.copy(elements, containers, null, renamings, replace, monitor);

	}

	// ~ Methods

	/*
	 * @see
	 * org.eclipse.dltk.mod.internal.core.ModelElement#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj instanceof AbstractSourceModule) {
			AbstractSourceModule other = (AbstractSourceModule) obj;
			return this.owner.equals(other.owner) && super.equals(obj);
		}
		return false;
	}

	public boolean exists() {
		// if not a working copy, it exists only if it is a primary compilation
		// unit
		try {
			return isPrimary() && validateSourceModule(getResource()).isOK();
		} catch (CoreException e) {
			return false;
		}
	}

	public IType[] getAllTypes() throws ModelException {
		IModelElement[] types = getTypes();
		int i;
		ArrayList allTypes = new ArrayList(types.length);
		ArrayList typesToTraverse = new ArrayList(types.length);
		for (i = 0; i < types.length; i++) {
			typesToTraverse.add(types[i]);
		}
		while (!typesToTraverse.isEmpty()) {
			IType type = (IType) typesToTraverse.get(0);
			typesToTraverse.remove(type);
			allTypes.add(type);
			types = type.getTypes();
			for (i = 0; i < types.length; i++) {
				typesToTraverse.add(types[i]);
			}
		}

		IType[] arrayOfAllTypes = new IType[allTypes.size()];
		allTypes.toArray(arrayOfAllTypes);
		return arrayOfAllTypes;
	}

	public IModelElement getElementAt(int position) throws ModelException {
		IModelElement e = getSourceElementAt(position);
		if (e == this) {
			return null;
		}

		return e;
	}

	/*
	 * @see org.eclipse.dltk.mod.internal.core.ModelElement#getElementName()
	 */
	public String getElementName() {
		return name;
	}

	public int getElementType() {
		return SOURCE_MODULE;
	}

	public IField getField(String fieldName) {
		return new SourceField(this, fieldName);
	}

	public IField[] getFields() throws ModelException {
		ArrayList list = getChildrenOfType(FIELD);
		IField[] array = new IField[list.size()];
		list.toArray(array);
		return array;
	}

	public IModelElement getHandleFromMemento(String token,
			MementoTokenizer memento, WorkingCopyOwner workingCopyOwner) {
		switch (token.charAt(0)) {
		case JEM_IMPORTDECLARATION: {
			if (DLTKCore.DEBUG) {
				System.err
						.println("Add import support in SourceModule getHandleFromMemento"); //$NON-NLS-1$
			}
			// ModelElement container = (ModelElement)getImportContainer();
			// return container.getHandleFromMemento(token, memento,
			// workingCopyOwner);
			break;
		}
		case JEM_PACKAGEDECLARATION: {
			if (!memento.hasMoreTokens()) {
				return this;
			}

			String pkgName = memento.nextToken();
			ModelElement pkgDecl = (ModelElement) getPackageDeclaration(pkgName);
			return pkgDecl.getHandleFromMemento(memento, workingCopyOwner);
		}
		case JEM_TYPE: {
			if (!memento.hasMoreTokens()) {
				return this;
			}

			String typeName = memento.nextToken();
			ModelElement type = (ModelElement) getType(typeName);
			return type.getHandleFromMemento(memento, workingCopyOwner);
		}
		case JEM_METHOD: {
			if (!memento.hasMoreTokens()) {
				return this;
			}

			String methodName = memento.nextToken();
			ModelElement method = (ModelElement) getMethod(methodName);
			return method.getHandleFromMemento(memento, workingCopyOwner);
		}
		case JEM_FIELD: {
			if (!memento.hasMoreTokens()) {
				return this;
			}

			String field = memento.nextToken();
			ModelElement fieldE = (ModelElement) getField(field);
			return fieldE.getHandleFromMemento(memento, workingCopyOwner);
		}
		}

		return null;
	}

	public IMethod getMethod(String selector) {
		return new SourceMethod(this, selector);
	}

	public IMethod[] getMethods() throws ModelException {
		ArrayList list = getChildrenOfType(METHOD);
		IMethod[] array = new IMethod[list.size()];
		list.toArray(array);
		return array;
	}

	public IModelElement getModelElement() {
		return this;
	}

	/*
	 * @see org.eclipse.dltk.mod.core.ISourceModule#getOwner()
	 */
	public WorkingCopyOwner getOwner() {
		return (isPrimary() || !isWorkingCopy()) ? null : this.owner;
	}

	public IPackageDeclaration getPackageDeclaration(String pkg) {
		return new PackageDeclaration(this, pkg);
	}

	public IPackageDeclaration[] getPackageDeclarations() throws ModelException {
		ArrayList list = getChildrenOfType(PACKAGE_DECLARATION);
		IPackageDeclaration[] array = new IPackageDeclaration[list.size()];
		list.toArray(array);
		return array;
	}

	public IPath getPath() {
		ProjectFragment root = this.getProjectFragment();
		// allow the root to be null for remote source
		if (root != null && root.isArchive()) {
			// eBay MOD to support js file in jar
			return this.getParent().getPath().append(this.getElementName());
			// eBay MOD
		}

		return this.getParent().getPath().append(this.getElementName());
	}

	public ISourceModule getPrimary() {
		return (ISourceModule) getPrimaryElement(true);
	}

	public IModelElement getPrimaryElement(boolean checkOwner) {

		if (checkOwner && isPrimary()) {
			return this;
		}

		return getOriginalSourceModule();
	}

	public IPath getScriptFolder() {
		return null;
	}

	public String getSource() throws ModelException {
		IBuffer buffer = getBufferNotOpen();
		if (buffer == null)
			return new String(getBufferContent());
		return buffer.getContents();
	}

	public char[] getSourceAsCharArray() throws ModelException {
		IBuffer buffer = getBufferNotOpen();
		if (buffer == null)
			return getBufferContent();
		return buffer.getContents().toCharArray();
		// return getSource().toCharArray();
	}

	public String getSourceContents() {
		try {
			return getSource();
		} catch (ModelException e) {
			if (DLTKCore.DEBUG) {
				e.printStackTrace();
			}
			return org.eclipse.dltk.mod.compiler.util.Util.EMPTY_STRING;
		}
	}

	/*
	 * @see
	 * org.eclipse.dltk.mod.compiler.env.ISourceModule#getContentsAsCharArray()
	 */
	public char[] getContentsAsCharArray() {
		try {
			return getSourceAsCharArray();
		} catch (ModelException e) {
			if (DLTKCore.DEBUG) {
				e.printStackTrace();
			}
			return CharOperation.NO_CHAR;
		}
	}

	public ISourceModule getSourceModule() {
		return this;
	}

	public ISourceRange getSourceRange() throws ModelException {
		return ((SourceModuleElementInfo) getElementInfo()).getSourceRange();
	}

	public IType getType(String typeName) {
		return new SourceType(this, typeName);
	}

	public IType[] getTypes() throws ModelException {
		ArrayList list = getChildrenOfType(TYPE);
		IType[] array = new IType[list.size()];
		list.toArray(array);
		return array;
	}

	public IResource getUnderlyingResource() throws ModelException {
		if (isWorkingCopy() && !isPrimary()) {
			return null;
		}

		return super.getUnderlyingResource();
	}

	public ISourceModule getWorkingCopy(IProgressMonitor monitor)
			throws ModelException {

		return getWorkingCopy(new WorkingCopyOwner() /*
													 * non shared working copy
													 */
		{
		}, null /* no problem requestor */, monitor);
	}

	public boolean isBuiltin() {
		return false;
	}

	public boolean isConsistent() {

		return !ModelManager.getModelManager()
				.getElementsOutOfSynchWithBuffers().contains(this);
	}

	/*
	 * @see org.eclipse.dltk.mod.core.ISourceModule#isPrimary()
	 */
	public boolean isPrimary() {

		return this.owner == DefaultWorkingCopyOwner.PRIMARY;
	}

	/*
	 * @see org.eclipse.dltk.mod.internal.core.ModelElement#isReadOnly()
	 */
	public boolean isReadOnly() {
		return readOnly;
	}

	public void printNode(CorePrinter output) {
		output.formatPrint(getModuleType() + getElementName());
		output.indent();
		try {
			IModelElement[] modelElements = this.getChildren();
			for (int i = 0; i < modelElements.length; ++i) {
				IModelElement element = modelElements[i];
				if (element instanceof ModelElement) {
					((ModelElement) element).printNode(output);
				} else {

					output.print("Unknown element:" + element); //$NON-NLS-1$
				}
			}
		} catch (ModelException ex) {
			output.formatPrint(ex.getLocalizedMessage());
		}

		output.dedent();
	}

	public boolean refreshSourceFields() throws ModelException {
		return false;
	}

	protected abstract char[] getBufferContent() throws ModelException;

	protected abstract String getModuleType();

	protected abstract String getNatureId() throws CoreException;

	protected abstract ISourceModule getOriginalSourceModule();

	protected ModelManager.PerWorkingCopyInfo getPerWorkingCopyInfo() {
		return null;
	}

	/**
	 * Returns {@link AccumulatingProblemReporter} or <code>null</code>
	 * 
	 * @return
	 */
	// eBay mod start
	// private AccumulatingProblemReporter getAccumulatingProblemReporter() {
	protected AccumulatingProblemReporter getAccumulatingProblemReporter() {
		// eBay mod end
		final PerWorkingCopyInfo perWorkingCopyInfo = getPerWorkingCopyInfo();
		if (perWorkingCopyInfo != null && perWorkingCopyInfo.isActive()) {
			final IScriptProject project = getScriptProject();
			if (project != null
					&& ScriptProject.hasScriptNature(project.getProject())) {
				return new AccumulatingProblemReporter(perWorkingCopyInfo);
			}
		}
		return null;
	}

	protected boolean buildStructure(OpenableElementInfo info,
			IProgressMonitor pm, Map newElements, IResource underlyingResource)
			throws ModelException {
		try {
			// check if this source module can be opened
			if (!isWorkingCopy()) {
				// no check is done on root kind or
				// exclusion pattern for working copies
				final IStatus status = validateSourceModule(underlyingResource);
				if (!status.isOK()) {
					throw newModelException(status);
				}
			}
			// prevents reopening of non-primary working copies (they are closed
			// when they are discarded and should not be reopened)
			if (preventReopen()) {
				throw newNotPresentException();
			}

			final SourceModuleElementInfo moduleInfo = (SourceModuleElementInfo) info;

			// ensure buffer is opened
			if (hasBuffer()) {
				final IBuffer buffer = getBufferManager().getBuffer(this);
				if (buffer == null) {
					openBuffer(pm, moduleInfo);
				}
			}

			// generate structure and compute syntax problems if needed
			final SourceModuleStructureRequestor requestor = new SourceModuleStructureRequestor(
					this, moduleInfo, newElements);

			// System.out.println("==> Parsing: " + resource.getName());
			final String natureId = getNatureId();
			if (natureId == null) {
				throw new ModelException(new ModelStatus(
						ModelStatus.INVALID_NAME));
			}

			final ISourceElementParser parser = getSourceElementParser(natureId);
			if (!isReadOnly()) {
				if (parser instanceof ISourceElementParserExtension) {
					((ISourceElementParserExtension) parser)
							.setScriptProject(this.getScriptProject());
				}
			}

			parser.setRequestor(requestor);

			final AccumulatingProblemReporter problemReporter = getAccumulatingProblemReporter();
			parser.setReporter(problemReporter);

			SourceParserUtil.parseSourceModule(this, parser);
			if (problemReporter != null) {
				if (!problemReporter.hasErrors()) {
					StructureBuilder.build(natureId, this, problemReporter);
				}
				problemReporter.reportToRequestor();
			}

			if (DEBUG_PRINT_MODEL) {
				System.out.println("Source Module Debug print:"); //$NON-NLS-1$

				CorePrinter printer = new CorePrinter(System.out);
				printNode(printer);
				printer.flush();
			}
			// update timestamp (might be IResource.NULL_STAMP if original does
			// not exist)
			if (underlyingResource == null) {
				underlyingResource = getResource();
			}
			// underlying resource is null in the case of a working copy out of
			// workspace
			if (underlyingResource != null) {
				moduleInfo.timestamp = ((IFile) underlyingResource)
						.getModificationStamp();
			}

			return moduleInfo.isStructureKnown();
		} catch (CoreException e) {
			throw new ModelException(e);
		}
	}

	protected Object createElementInfo() {
		return new SourceModuleElementInfo();
	}

	protected char getHandleMementoDelimiter() {
		return JEM_SOURCEMODULE;
	}

	protected ISourceElementParser getSourceElementParser(String natureId)
			throws CoreException {
		return DLTKLanguageManager.getSourceElementParser(natureId);
	}

	protected boolean hasBuffer() {
		return true;
	}

	protected final IDLTKLanguageToolkit lookupLanguageToolkit(Object object)
			throws CoreException {
		IDLTKLanguageToolkit toolkit = null;
		if (object instanceof IPath) {
			toolkit = DLTKLanguageManager.findToolkit((IPath) object);
		} else if (object instanceof IResource) {
			toolkit = DLTKLanguageManager.findToolkit(getParent(),
					(IResource) object, true);
		} else if (object instanceof IScriptProject) {
			toolkit = DLTKLanguageManager
					.getLanguageToolkit((IScriptProject) object);
		} else if (object instanceof IModelElement) {
			toolkit = DLTKLanguageManager
					.getLanguageToolkit((IModelElement) object);
		}

		return toolkit;
	}

	/*
	 * @see
	 * org.eclipse.dltk.mod.internal.core.Openable#openBuffer(org.eclipse.core
	 * .runtime .IProgressMonitor, java.lang.Object)
	 */
	protected IBuffer openBuffer(IProgressMonitor pm, Object info)
			throws ModelException {
		// create buffer
		final BufferManager bufManager = getBufferManager();
		final boolean isWorkingCopy = isWorkingCopy();
		IBuffer buffer = isWorkingCopy ? this.owner.createBuffer(this)
				: BufferManager.createBuffer(this);
		if (buffer == null) {
			return null;
		}

		/*
		 * synchronize to ensure that 2 threads are not putting 2 different
		 * buffers at the same time see
		 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=146331
		 */
		synchronized (bufManager) {
			final IBuffer existingBuffer = bufManager.getBuffer(this);
			if (existingBuffer != null)
				return existingBuffer;
			// set the buffer source
			char[] chars = buffer.getCharacters();
			if ((chars == null) || (chars.length == 0)) {
				if (isWorkingCopy) {
					ISourceModule original;
					if (!isPrimary()
							&& (original = getOriginalSourceModule()).isOpen()) {
						buffer.setContents(original.getSource());
					} else {
						// IFile file = (IFile) getResource();
						// if ((file == null) || ! file.exists())
						// {
						// // initialize buffer with empty contents
						// buffer.setContents(CharOperation.NO_CHAR);
						// }
						// else
						// {
						// buffer.setContents(Util.getResourceContentsAsCharArray
						// (file));
						// }
						char[] content = getBufferContent();
						buffer.setContents(content);
					}
				} else {
					char[] content = getBufferContent();
					buffer.setContents(content);
				}
			}

			// add buffer to buffer cache
			/*
			 * note this may cause existing buffers to be removed from the
			 * buffer cache, but only primary compilation unit's buffer can be
			 * closed, thus no call to a client's IBuffer#close() can be done in
			 * this synchronized block.
			 */
			bufManager.addBuffer(buffer);

			// listen to buffer changes
			buffer.addBufferChangedListener(this);
		}

		return buffer;
	}

	protected void openParent(Object childInfo, HashMap newElements,
			IProgressMonitor pm) throws ModelException {
		if (!isWorkingCopy()) {
			super.openParent(childInfo, newElements, pm);
		}
		// don't open parent for a working copy to speed up the first
		// becomeWorkingCopy
	}

	protected boolean preventReopen() {
		return !isPrimary();
	}

	protected IStatus validateSourceModule(IResource resource)
			throws CoreException {
		IProjectFragment root = getProjectFragment();
		try {
			if (root.getKind() != IProjectFragment.K_SOURCE) {
				return new ModelStatus(
						IModelStatusConstants.INVALID_ELEMENT_TYPES, root);
			}
		} catch (ModelException e) {
			return e.getModelStatus();
		}
		if (resource != null) {
			char[][] inclusionPatterns = ((ProjectFragment) root)
					.fullInclusionPatternChars();
			char[][] exclusionPatterns = ((ProjectFragment) root)
					.fullExclusionPatternChars();
			if (Util.isExcluded(resource, inclusionPatterns, exclusionPatterns))
				return new ModelStatus(
						IModelStatusConstants.ELEMENT_NOT_ON_BUILDPATH, this);
			if (!resource.isAccessible())
				return new ModelStatus(
						IModelStatusConstants.ELEMENT_DOES_NOT_EXIST, this);
		}

		IDLTKLanguageToolkit toolkit = null;
		if (!root.isArchive()) {
			toolkit = lookupLanguageToolkit(this);
		}

		IStatus status = validateSorceModule(toolkit, resource);
		if (status != null) {
			return status;
		}

		return new ModelStatus(IModelStatusConstants.INVALID_RESOURCE, root);

	}

	protected IStatus validateSorceModule(IDLTKLanguageToolkit toolkit,
			IResource resource) {
		if (toolkit == null) {
			toolkit = DLTKLanguageManager.findToolkit(getParent(), resource,
					true);
		}

		if (toolkit != null) {
			if (DLTKContentTypeManager.isValidResourceForContentType(toolkit,
					resource)) {
				return IModelStatus.VERIFIED_OK;
			}
		}

		return null;
	}
}
