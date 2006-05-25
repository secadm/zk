/* AbstractComponent.java

{{IS_NOTE
	$Id: AbstractComponent.java,v 1.38 2006/05/25 04:22:41 tomyeh Exp $
	Purpose:
		
	Description:
		
	History:
		Mon May 30 21:49:42     2005, Created by tomyeh@potix.com
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package com.potix.zk.ui;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.List;
import java.util.AbstractSequentialList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.io.Writer;
import java.io.IOException;

import com.potix.lang.D;
import com.potix.lang.Objects;
import com.potix.lang.Strings;
import com.potix.util.CollectionsX;
import com.potix.util.logging.Log;

import com.potix.zk.mesg.MZk;
import com.potix.zk.ui.event.EventListener;
import com.potix.zk.ui.event.Events;
import com.potix.zk.ui.ext.RawId;
import com.potix.zk.ui.ext.Viewable;
import com.potix.zk.ui.sys.ExecutionCtrl;
import com.potix.zk.ui.sys.ExecutionsCtrl;
import com.potix.zk.ui.sys.ComponentCtrl;
import com.potix.zk.ui.sys.ComponentsCtrl;
import com.potix.zk.ui.sys.PageCtrl;
import com.potix.zk.ui.sys.DesktopCtrl;
import com.potix.zk.ui.sys.SessionCtrl;
import com.potix.zk.ui.sys.WebAppCtrl;
import com.potix.zk.ui.sys.UiEngine;
import com.potix.zk.ui.sys.Namespace;
import com.potix.zk.ui.sys.BshNamespace;
import com.potix.zk.ui.sys.Variables;
import com.potix.zk.ui.metainfo.ComponentDefinition;
import com.potix.zk.ui.metainfo.PageDefinition;
import com.potix.zk.ui.metainfo.LanguageDefinition;
import com.potix.zk.ui.metainfo.DefinitionNotFoundException;
import com.potix.zk.au.AuResponse;
import com.potix.zk.au.AuRemove;

/**
 * A skeletal implementation of {@link Component}. Though it is OK
 * to implement Component from scratch, this class simplifies some of
 * the chores.
 *
 * @author <a href="mailto:tomyeh@potix.com">tomyeh@potix.com</a>
 * @version $Revision: 1.38 $ $Date: 2006/05/25 04:22:41 $
 */
public class AbstractComponent implements Component, ComponentCtrl {
	private static final Log log = Log.lookup(AbstractComponent.class);
	private static int _globalId;
	private static synchronized int getNextGlobalId() {
		return _globalId++;
	}

	/* Note: if _page != null, then _desktop != null. */
	private Desktop _desktop;
	private /*final*/ Page _page;
	private String _id;
	private String _uuid;
	private ComponentDefinition _compdef;
	private Component _parent;
	/** The mold (default: "default"). */
	private String _mold = "default";
	private final List _children = new LinkedList();
	private final List _modChildren = new AbstractSequentialList() {
		public int size() {
			return _children.size();
		}
		public ListIterator listIterator(int index) {
			return new ChildIter(index);
		}
	};
	/** The info of the ID space, or null if IdSpace is NOT implemented. */
	private final SpaceInfo _spaceInfo;
	private final Map _attrs = new HashMap(3);
		//don't create it dynamically because _ip bind it at constructor
	/** A map of event listener: Map(evtnm, EventListener)). */
	private Map _listeners;
	/** A set of children being added. It is used only to speed up
	 * the performance when adding a new child. And, cleared after added.
	 * <p>To save footprint, we don't use Set (since it is rare to contain
	 * more than one)
	 */
	private transient final List _newChildren = new LinkedList();
	/** Used when user is modifying the children by Iterator.
	 */
	private transient boolean _modChildByIter;
	/** Whether this component is visible. */
	private boolean _visible = true;

	/** Constructs a component with auto-generated ID.
	 */
	protected AbstractComponent() {
		final Execution exec = Executions.getCurrent();
		final StringBuffer idsb = new StringBuffer(10)
			.append(ComponentsCtrl.AUTO_ID_PREFIX);
		if (exec != null) {
			_desktop = exec.getDesktop();
			Strings.encode(idsb, ((DesktopCtrl)_desktop).getNextId());
		} else {
			idsb.append('_');
			Strings.encode(idsb, getNextGlobalId());
		}
		_uuid = _id = idsb.toString();

		_spaceInfo = this instanceof IdSpace ? new SpaceInfo(_uuid): null;
		_compdef = getDefinitionFromCurrentPage(getClass());
			//we have to init it here because getCurrentPageDefinition
			//might changed later

		if (D.ON && log.debugable()) log.debug("Create comp: "+this);
	}
	private static final
	ComponentDefinition getDefinitionFromCurrentPage(Class cls) {
		final PageDefinition pgdef =
			ExecutionsCtrl.getCurrentCtrl().getCurrentPageDefinition(true);
		if (pgdef != null) {
			ComponentDefinition compdef = pgdef.getComponentDefinition(cls);
			if (compdef != null) return compdef;

			final LanguageDefinition langdef = pgdef.getLanguageDefinition();
			if (langdef != null)
				try {
					return langdef.getComponentDefinition(cls);
				} catch (DefinitionNotFoundException ex) {
				}
		}
		return null;
	}
	private static final ComponentDefinition getDefinitionFromAll(Class cls) {
		for (Iterator it = LanguageDefinition.getAll().iterator();
		it.hasNext();) {
			try {
				return ((LanguageDefinition)it.next())
					.getComponentDefinition(cls);
			} catch (DefinitionNotFoundException ex) {
			}
		}
		return null;
	}

	/** Adds to the ID spaces, if any, when ID is changed.
	 * Caller has to make sure the uniqueness.
	 */
	private static void addToIdSpaces(final Component comp) {
		if (comp instanceof IdSpace)
			((AbstractComponent)comp).bindToIdSpace(comp);

		final IdSpace is = getSpaceOwnerOfParent(comp);
		if (is instanceof Component)
			((AbstractComponent)is).bindToIdSpace(comp);
		else if (is != null)
			((PageCtrl)is).addFellow(comp);
	}
	private static final IdSpace getSpaceOwnerOfParent(Component comp) {
		final Component parent = comp.getParent();
		if (parent != null) return parent.getSpaceOwner();
		else return comp.getPage();
	}
	/** Removes from the ID spaces, if any, when ID is changed. */
	private static void removeFromIdSpaces(final Component comp) {
		final String compId = comp.getId();
		if (ComponentsCtrl.isAutoId(compId))
			return; //nothing to do

		if (comp instanceof IdSpace)
			((AbstractComponent)comp).unbindFromIdSpace(compId);

		final IdSpace is = getSpaceOwnerOfParent(comp);
		if (is instanceof Component)
			((AbstractComponent)is).unbindFromIdSpace(compId);
		else if (is != null)
			((PageCtrl)is).removeFellow(comp);
	}
	/** Checks the uniqueness in ID space when changing ID. */
	private static void checkIdSpaces(final Component comp, String newId) {
		if (comp instanceof IdSpace
		&& ((AbstractComponent)comp)._spaceInfo.fellows.containsKey(newId))
			throw new UiException("Not unique in the ID space of "+comp);

		final IdSpace is = getSpaceOwnerOfParent(comp);
		if (is instanceof Component) {
			if (((AbstractComponent)is)._spaceInfo.fellows.containsKey(newId))
				throw new UiException("Not unique in the ID space of "+is);
		} else if (is != null) {
			if (((PageCtrl)is).hasFellow(newId))
				throw new UiException("Not unique in the ID space of "+is);
		}
	}

	/** Adds its descendants to the ID space when parent or page is changed,
	 * excluding comp.
	 */
	private static void addToIdSpacesDown(Component comp) {
		final IdSpace is = getSpaceOwnerOfParent(comp);
		if (is instanceof Component)
			addToIdSpacesDown(comp, (Component)is);
		else if (is != null)
			addToIdSpacesDown(comp, (PageCtrl)is);
	}
	private static void addToIdSpacesDown(Component comp, Component owner) {
		if (!ComponentsCtrl.isAutoId(comp.getId()))
			((AbstractComponent)owner).bindToIdSpace(comp);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				addToIdSpacesDown((Component)it.next(), owner); //recursive
	}
	private static void addToIdSpacesDown(Component comp, PageCtrl owner) {
		if (!ComponentsCtrl.isAutoId(comp.getId()))
			owner.addFellow(comp);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				addToIdSpacesDown((Component)it.next(), owner); //recursive
	}

	/** Adds its descendants to the ID space when parent or page is changed,
	 * excluding comp.
	 */
	private static void removeFromIdSpacesDown(Component comp) {
		final IdSpace is = getSpaceOwnerOfParent(comp);
		if (is instanceof Component)
			removeFromIdSpacesDown(comp, (Component)is);
		else if (is != null)
			removeFromIdSpacesDown(comp, (PageCtrl)is);
	}
	private static void removeFromIdSpacesDown(Component comp, Component owner) {
		final String compId = comp.getId();
		if (!ComponentsCtrl.isAutoId(compId))
			((AbstractComponent)owner).unbindFromIdSpace(compId);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				removeFromIdSpacesDown((Component)it.next(), owner); //recursive
	}
	private static void removeFromIdSpacesDown(Component comp, PageCtrl owner) {
		if (!ComponentsCtrl.isAutoId(comp.getId()))
			owner.removeFellow(comp);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				removeFromIdSpacesDown((Component)it.next(), owner); //recursive
	}

	/** Checks the uniqueness in ID space when changing parent. */
	private static void checkIdSpacesDown(Component comp, Component newparent) {
		final IdSpace is = newparent.getSpaceOwner();
		if (is instanceof Component)
			checkIdSpacesDown(comp, ((AbstractComponent)is)._spaceInfo);
		else if (is != null)
			checkIdSpacesDown(comp, (PageCtrl)is);
	}
	/** Checks comp and its descendants for the specified SpaceInfo. */
	private static void checkIdSpacesDown(Component comp, SpaceInfo si) {
		final String compId = comp.getId();
		if (!ComponentsCtrl.isAutoId(compId) && si.fellows.containsKey(compId))
			throw new UiException("Not unique in the new ID space: "+compId);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				checkIdSpacesDown((Component)it.next(), si); //recursive
	}
	/** Checks comp and its descendants for the specified page. */
	private static void checkIdSpacesDown(Component comp, PageCtrl pageCtrl) {
		final String compId = comp.getId();
		if (!ComponentsCtrl.isAutoId(compId) && pageCtrl.hasFellow(compId))
			throw new UiException("Not unique in the ID space of "+pageCtrl+": "+compId);
		if (!(comp instanceof IdSpace))
			for (Iterator it = comp.getChildren().iterator(); it.hasNext();)
				checkIdSpacesDown((Component)it.next(), pageCtrl); //recursive
	}

	/** Bind this ID space. Called only if IdSpace is implemented. */
	private void bindToIdSpace(Component comp) {
		final String compId = comp.getId();
		assert D.OFF || !ComponentsCtrl.isAutoId(compId): "Auto ID shall be ignored: "+compId;
		_spaceInfo.fellows.put(compId, comp);
		if (Variables.isValid(compId))
			_spaceInfo.ns.setVariable(compId, comp, true);
	}
	/** Unbind this ID space. Called only if IdSpace is implemented. */
	private void unbindFromIdSpace(String compId) {
		_spaceInfo.fellows.remove(compId);
		if (Variables.isValid(compId))
			_spaceInfo.ns.unsetVariable(compId);
	}

	//-- Extra utlities --//
	/** Returns the mold URI based on {@link #getMold}
	 * and {@link ComponentDefinition#getMoldURI}.
	 */
	protected String getMoldURI() {
		return getDefinition().getMoldURI(this, getMold());
	}
	/** Returns the initial parameter in int, or 0 if not found.
	 * In fact, it invokes {@link ComponentDefinition#getParameter},
	 * which evaluates the returned object if it is an EL expression.
	 */
	protected int getIntInitParam(String name) {
		final Integer v;
		final Object o = getDefinition().getParameter(this, name);
		if (o instanceof Integer) {
			v = (Integer)o;
		} else if (o != null) {
			v = Integer.valueOf(Objects.toString(o));
		} else {
			v = new Integer(0);
		}
		return v.intValue();
	}
	/** Returns the initial parameter, or null if not found.
	 * In fact, it invokes {@link ComponentDefinition#getParameter},
	 * which evaluates the returned object if it is an EL expression.
	 */
	protected String getInitParam(String name) {
		return Objects.toString(getDefinition().getParameter(this, name));
	}
	/** Returns URI that {@link com.potix.zk.au.http.DHtmlUpdateServlet}
	 * understands. Then, when DHtmlUpdateServlet serves the URI, it will
	 * invoke {@link Viewable#getView} to response.
	 *
	 * <p>Note: to use this method, {@link Viewable} must be implemented.
	 */
	protected String getViewURI(String pathInfo) {
		if (!(this instanceof Viewable))
			throw new UiException(Viewable.class+" not implemented by "+this);
		if (_desktop == null)
			throw new UiException("Not callable because this component doesn't belong to any desktop: "+this);

		final StringBuffer sb = new StringBuffer(32)
			.append("/view/").append(_desktop.getId())
			.append('/').append(getUuid());

		if (pathInfo != null && pathInfo.length() > 0) {
			if (!pathInfo.startsWith("/")) sb.append('/');
			sb.append(pathInfo);
		}
		return _desktop.getUpdateURI(sb.toString());
	}

	/** Returns the UI engine.
	 * Don't call this method when _desktop is null.
	 */
	private final UiEngine getUiEngine() {
		return ((WebAppCtrl)_desktop.getWebApp()).getUiEngine();
	}

	//-- Component --//
	public final Page getPage() {
		return _page;
	}
	public final Desktop getDesktop() {
		return _desktop;
	}

	/** Sets the page that this component belongs to. */
	public void setPage(Page page) {
		if (page == _page) return;

		if (_parent != null)
			throw new UiException("Only the parent of a root component can be changed: "+this);
		if (page != null) {
			if (page.getDesktop() != _desktop && _desktop != null)
				throw new UiException("The new page must be in the same desktop: "+page);
			checkIdSpacesDown(this, (PageCtrl)page);
		}

		if (_page != null) removeFromIdSpacesDown(this);

		addMoved(this, _page, page);
		setPage0(page);

		if (_page != null) addToIdSpacesDown(this);
	}
	/** Calling getUiEngine().addMoved().
	 */
	private static final
	void addMoved(Component comp, Page oldpg, Page newpg) {
		final Desktop dt;
		if (oldpg != null) dt = oldpg.getDesktop();
		else if (newpg != null) dt = newpg.getDesktop();
		else return;

		((WebAppCtrl)dt.getWebApp())
			.getUiEngine().addMoved(comp, oldpg == null);
	}

	/** Ses the page without fixing IdSpace
	 */
	private void setPage0(Page page) {
		if (page == _page)
			return; //nothing changed

		assert D.OFF || _parent == null || _parent.getPage() == page;

		if (_desktop == null && page != null) _desktop = page.getDesktop();

		//detach
		final DesktopCtrl dtctrl = (DesktopCtrl)_desktop;
		final boolean bRoot = _parent == null;
		if (_page != null) {
			if (bRoot) ((PageCtrl)_page).removeRoot(this);
			if (page == null) dtctrl.removeComponent(this);
		}

		final Page oldpage = _page;
		_page = page;

		//attach
		if (_page != null) {
			if (bRoot) ((PageCtrl)_page).addRoot(this);
			if (oldpage == null) dtctrl.addComponent(this);
		}
		if (_spaceInfo != null && _parent == null) {
			final PageCtrl pgctrl = (PageCtrl)page;
			_spaceInfo.ns.setParent(
				pgctrl != null ? pgctrl.getNamespace(): null);
		}

		//process all children recursively
		for (final Iterator it = _children.iterator(); it.hasNext();) {
			final Object child = it.next();
			((AbstractComponent)child).setPage0(page); //recursive
		}
	}

	public final String getId() {
		return _id;
	}
	public void setId(String id) {
		if (id == null || id.length() == 0)
			throw new UiException("ID cannot be empty");

		if (!_id.equals(id)) {
			if (Variables.isReserved(id) || ComponentsCtrl.isAutoId(id))
				throw new UiException("Invalid ID: "+id+". Cause: reserved words not allowed: "+Variables.getReservedNames());
			final boolean rawId = this instanceof RawId;
			if (rawId && _desktop.getComponentByUuidIfAny(id) != null)
				throw new UiException("Replicated ID is not allowed for "+getClass()+": "+id+"\nNote: HTML/WML tags, ID must be unique");
			checkIdSpaces(this, id);

			removeFromIdSpaces(this);
			if (rawId) { //we have to change UUID
				final DesktopCtrl dtctrl = (DesktopCtrl)_desktop;
				if (_page != null) {
					response(null, new AuRemove(_uuid));
					dtctrl.removeComponent(this);
				}

				_uuid = _id = id;
				if (_page != null) {
					dtctrl.addComponent(this);
					if (_parent != null && isTransparent()) _parent.invalidate(INNER);
					getUiEngine().addMoved(this, false);
				}
			} else {
				_id = id;
			}
			addToIdSpaces(this);
		}
	}
	public final String getUuid() {
		return _uuid;
	}

	public final IdSpace getSpaceOwner() {
		Component p = this;
		do {
			if (p instanceof IdSpace)
				return (IdSpace)p;
		} while ((p = p.getParent()) != null);
		return _page;
	}
	public final Component getFellow(String compId) {
		if (this instanceof IdSpace) {
			final Component comp = (Component)_spaceInfo.fellows.get(compId);
			if (comp == null)
				if (ComponentsCtrl.isAutoId(compId))
					throw new ComponentNotFoundException(MZk.AUTO_ID_NOT_LOCATABLE, compId);
				else
					throw new ComponentNotFoundException("Fellow component not found: "+compId);
			return comp;
		}

		final IdSpace idspace = getSpaceOwner();
		if (idspace == null)
			throw new ComponentNotFoundException("This component doesn't belong to any ID space: "+this);
		return idspace.getFellow(compId);
	}

	public void applyProperties() {
		final ComponentDefinition compdef = getDefinition();
		if (compdef != null) compdef.applyProperties(this);
	}

	public Map getAttributes(int scope) {
		switch (scope) {
		case SPACE_SCOPE:
			if (this instanceof IdSpace)
				return _spaceInfo.attrs;
			final IdSpace idspace = getSpaceOwner();
			return idspace instanceof Page ? ((Page)idspace).getAttributes():
				idspace == null ? Collections.EMPTY_MAP:
					((Component)idspace).getAttributes(SPACE_SCOPE);
		case PAGE_SCOPE:
			return _page != null ?
				_page.getAttributes(): Collections.EMPTY_MAP;
		case DESKTOP_SCOPE:
			return _desktop != null ?
				_desktop.getAttributes(): Collections.EMPTY_MAP;
		case SESSION_SCOPE:
			return _desktop != null ?
				_desktop.getSession().getAttributes(): Collections.EMPTY_MAP;
		case APPLICATION_SCOPE:
			return _desktop != null ?
				_desktop.getWebApp().getAttributes(): Collections.EMPTY_MAP;
		case COMPONENT_SCOPE:
			return _attrs;
		default:
			return Collections.EMPTY_MAP;
		}
	}
	public Object getAttribute(String name, int scope) {
		return getAttributes(scope).get(name);
	}
	public Object setAttribute(String name, Object value, int scope) {
		if (value != null) {
			final Map attrs = getAttributes(scope);
			if (attrs == Collections.EMPTY_MAP)
				throw new IllegalStateException("This component doesn't belong to any ID space: "+this);
			return attrs.put(name, value);
		} else {
			return removeAttribute(name, scope);
		}
	}
	public Object removeAttribute(String name, int scope) {
			final Map attrs = getAttributes(scope);
			if (attrs == Collections.EMPTY_MAP)
				throw new IllegalStateException("This component doesn't belong to any ID space: "+this);
		return attrs.remove(name);
	}

	public final Map getAttributes() {
		return _attrs;
	}
	public final Object getAttribute(String name) {
		return _attrs.get(name);
	}
	public final Object setAttribute(String name, Object value) {
		return value != null ? _attrs.put(name, value): _attrs.remove(name);
	}
	public final Object removeAttribute(String name) {
		return _attrs.remove(name);
	}

	public void setVariable(String name, Object val, boolean local) {
		getNamespace().setVariable(name, val, local);
	}
	public Object getVariable(String name, boolean local) {
		return getNamespace().getVariable(name, local);
	}
	public void unsetVariable(String name) {
		getNamespace().unsetVariable(name);
	}

	public Component getParent() {
		return _parent;
	}
	public void setParent(Component parent) {
		if (_parent == parent)
			return; //nothing changed

		final boolean idSpaceChanged;
		if (parent != null) {
			if (!(parent instanceof ComponentCtrl))
				throw new UnsupportedOperationException("Unknown parent: "+parent);
				//We don't support other type of parent yet
			if (Components.isAncestor(this, parent))
				throw new UiException("A child cannot be a parent of its ancestor: "+this);
			if (!parent.isChildable())
				throw new UiException(parent+" doesn't allow any child");
			final Page newpage = parent.getPage();
			if (newpage != null && newpage.getDesktop() != _desktop && _desktop != null)
				throw new UiException("The new parent must be in the same desktop: "+parent);

			idSpaceChanged = _parent == null
				|| parent.getSpaceOwner() != _parent.getSpaceOwner();
			if (idSpaceChanged) checkIdSpacesDown(this, parent);
		} else {
			idSpaceChanged = true;
		}

		if (_parent != null && isTransparent()) _parent.invalidate(INNER);

		if (idSpaceChanged) removeFromIdSpacesDown(this);
		if (_parent != null) {
			_parent.removeChild(this);
			_parent = null;
		} else {
			if (_page != null) ((PageCtrl)_page).removeRoot(this);
		}

		if (parent != null) {
			_parent = parent;
			//We could use _parent.getChildren().contains instead, but
			//the following statement is much faster if a lot of children
			if (!((AbstractComponent)_parent)._newChildren.contains(this))
				parent.appendChild(this);
		} //if parent == null, assume no page at all (so no addRoot)

		final Page newpg = _parent != null ? _parent.getPage(): null;
		addMoved(this, _page, newpg);
		setPage0(newpg);

		if (_spaceInfo != null) //ID space owner
			_spaceInfo.ns.setParent(_parent != null ?
				((ComponentCtrl)_parent).getNamespace(): null);
		if (idSpaceChanged) addToIdSpacesDown(this); //called after setPage
	}

	/** Default: return true (allows to have children).
	 */
	public boolean isChildable() {
		return true;
	}
	/** Default: false.
	 */
	public boolean isTransparent() {
		return false;
	}

	public boolean insertBefore(Component newChild, Component refChild) {
		if (newChild == null)
			throw new UiException("newChild is null");

		boolean found = false;
		if (_modChildByIter) {
			_modChildByIter = false; //avoid dead loop
		} else {
			boolean added = false;
			for (ListIterator it = _children.listIterator(); it.hasNext();) {
				final Object o = it.next();
				if (o == newChild) {
					if (!added) {
						if (!it.hasNext()) return false; //last
						if (it.next() == refChild) return false; //same position
						it.previous(); it.previous(); it.next(); //restore cursor
					}
					it.remove();
					found = true;
					if (added || refChild == null) break; //done
				} else if (o == refChild) {
					it.previous();
					it.add(newChild);
					it.next();
					added = true;
					if (found) break; //done
				}
			}

			if (!added) _children.add(newChild);
		}

		if (found) { //re-order
			if (newChild.isTransparent()) invalidate(INNER);
			addMoved(newChild, newChild.getPage(), _page);
		} else { //new added
			if (newChild.getParent() != this) { //avoid loop back
				_newChildren.add(newChild); //used by setParent to avoid loop back
				try {
					newChild.setParent(this); //call addMoved...
				} finally {
					_newChildren.remove(newChild);
				}
			}
			onChildAdded(newChild);
		}
		return true;
	}
	/** Appends a child to the end of all children.
	 * It calls {@link #insertBefore} with refChild to be null.
	 * Derives cannot override this method, and they shall override
	 * {@link #insertBefore} instead.
	 */
	public final boolean appendChild(Component child) { //Yes, final; see below
		return insertBefore(child, null); //NOTE: we must go thru insertBefore
			//such that deriving is easy to override
	}
	public boolean removeChild(Component child) {
		if (child == null)
			throw new UiException("child must be specified");

		if (_modChildByIter || _children.remove(child)) {
			_modChildByIter = false; //avoid dead loop

			if (child.getParent() != null) //avoid loop back
				child.setParent(null);
			onChildRemoved(child);
				//to invalidate itself if necessary
			return true;
		} else {
			return false;
		}
	}

	public List getChildren() {
		return _modChildren;
	}
	/** Returns the root of the specified component.
	 */
	public Component getRoot() {
		for (Component comp = this;;) {
			final Component parent = comp.getParent();
			if (parent == null)
				return comp;
			comp = parent;
		}
	}


	public boolean isVisible() {
		return _visible;
	}
	public boolean setVisible(boolean visible) {
		final boolean old = _visible;
		if (old != visible) {
			_visible = visible;
			if (!isTransparent())
				smartUpdate("visibility", _visible);
		}
		return old;
	}

	public final void invalidate() {
		invalidate(OUTER);
	}
	public void invalidate(Range range) {
		if (_page != null) getUiEngine().addInvalidate(this, range);
		if (_parent != null && isTransparent()) _parent.invalidate(INNER);
			//Note: UiEngine will handle transparent, but we still
			//handle it here to simplify codes that handles transparent
			//in AbstractComponent
	}
	public void response(String key, AuResponse response) {
		//if response depends on nothing, it must be generated
		if (_page != null
		|| (_desktop != null && response.getDepends() == null))
			 getUiEngine().addResponse(key, response);
	}
	public void smartUpdate(String attr, String value) {
		if (_parent != null && isTransparent())
			throw new IllegalStateException("A transparent component cannot use smartUpdate");
		if (_page != null) getUiEngine().addSmartUpdate(this, attr, value);
	}
	/** A special smart-update that update a value in int.
	 */
	public void smartUpdate(String attr, int value) {
		smartUpdate(attr, Integer.toString(value));
	}
	/** A special smart-update that update a value in boolean.
	 */
	public void smartUpdate(String attr, boolean value) {
		smartUpdate(attr, Boolean.toString(value));
	}

	public void detach() {
		if (getParent() != null) setParent(null);
		else setPage(null);
	}

	/** Default: does nothing.
	 */
	public void onChildAdded(Component child) {
	}
	/** Default: does nothing.
	 */
	public void onChildRemoved(Component child) {
	}

	/** Default: null (no propagation at all).
	 */
	public Component getPropagatee(String evtnm) {
		return null;
	}

	/**
	 * Default: "default"
	 */
	public final String getMold() {
		return _mold;
	}
	public void setMold(String mold) {
		if (mold == null || mold.length() == 0)
			mold = "default";
		if (!Objects.equals(_mold, mold)) {
			if (!getDefinition().hasMold(mold))
				throw new UiException("Unknown mold: "+mold
					+", while allowed include "+getDefinition().getMoldNames());
			_mold = mold;
			invalidate(OUTER);
		}
	}

	public ComponentDefinition getDefinition() {
		if (_compdef == null)
			_compdef = getDefinitionFromAll(getClass());
		return _compdef;
	}

	//-- in the redrawing phase --//
	/** Includes the page returned by {@link #getMoldURI} and
	 * set the self attribute to be this component.
	 */
	public void redraw(Writer out) throws IOException {
		final String mold = getMoldURI();
		if (D.ON && log.finerable()) log.finer("Redraw comp: "+this+" with "+mold);

		final Map attrs = new HashMap(3);
		attrs.put("self", this);
		_desktop.getExecution()
			.include(out, mold, attrs, Execution.PASS_THRU_ATTR);
	}
	/* Default: does nothing.
	 */
	public void onDrawNewChild(Component child, StringBuffer out)
	throws IOException {
	}

	public boolean addEventListener(String evtnm, EventListener listener) {
		if (evtnm == null || listener == null)
			throw new IllegalArgumentException("null");
		if (!Events.isValid(evtnm))
			throw new IllegalArgumentException("Invalid event name: "+evtnm);

		if (_listeners == null)
			_listeners = new HashMap(3);

		List l = (List)_listeners.get(evtnm);
		if (l != null) {
			for (Iterator it = l.iterator(); it.hasNext();) {
				final EventListener li = (EventListener)it.next();
				if (listener.equals(li))
					return false;
			}
		} else {
			_listeners.put(evtnm, l = new LinkedList());
		}
		l.add(listener);
		return true;
	}
	public boolean removeEventListener(String evtnm, EventListener listener) {
		if (evtnm == null || listener == null)
			throw new NullPointerException();

		if (_listeners != null) {
			final List l = (List)_listeners.get(evtnm);
			if (l != null) {
				for (Iterator it = l.iterator(); it.hasNext();) {
					final EventListener li = (EventListener)it.next();
					if (listener.equals(li)) {
						if (l.size() == 1)
							_listeners.remove(evtnm);
						else
							it.remove();
						return true;
					}
				}
			}
		}
		return false;
	}

	//-- ComponentCtrl --//
	public Namespace getNamespace() {
		if (this instanceof IdSpace)
			return _spaceInfo.ns;

		final IdSpace idspace = getSpaceOwner();
		return idspace instanceof Page ? ((PageCtrl)idspace).getNamespace():
			idspace == null ? null: ((ComponentCtrl)idspace).getNamespace();
	}

	public void setDefinition(ComponentDefinition compdef) {
		if (compdef == null) throw new IllegalArgumentException("null");
		_compdef = compdef;
	}

	public boolean isListenerAvailable(String evtnm, boolean asap) {
		if (_listeners != null) {
			final List l = (List)_listeners.get(evtnm);
			if (l != null) {
				if (!asap)
					return !l.isEmpty();

				for (Iterator it = l.iterator(); it.hasNext();) {
					final EventListener li = (EventListener)it.next();
					if (li.isAsap())
						return true;
				}
			}
		}
		return false;
	}
	public Iterator getListenerIterator(String evtnm) {
		if (_listeners != null) {
			final List l = (List)_listeners.get(evtnm);
			if (l != null)
				return l.iterator();
		}
		return CollectionsX.EMPTY_ITERATOR;
	}

	//-- Object --//
	public String toString() {
		final String clsnm = getClass().getName();
		final int j = clsnm.lastIndexOf('.');
		return "["+clsnm.substring(j+1)+' '+_id+']';
	}
	public final boolean equals(Object o) { //no more override
		return this == o;
	}

	/** Holds info shared of the same ID space. */
	private static class SpaceInfo {
		private final Map attrs = new HashMap(7);
			//don't create it dynamically because _ip bind it at constructor
		private final Namespace ns;
		/** A map of ((String id, Component fellow). */
		private final Map fellows = new HashMap(23);
		private SpaceInfo(String id) {
			ns = new BshNamespace(id);
			ns.setVariable("spaceScope", attrs, true);
		}
	}
	private class ChildIter implements ListIterator  {
		private final ListIterator _it;
		private Object _last;
		private boolean _bNxt;
		private ChildIter(int index) {
			_it = _children.listIterator(index);
		}
		public void add(Object o) {
			final Component comp = (Component)o;
			if (comp.getParent() == AbstractComponent.this)
				throw new UnsupportedOperationException("Unable to add component with the same parent: "+o);
				//1. it is confusing to allow adding (with replace)
				//2. the code is complicated

			_it.add(o);

			//Note: we must go thru insertBefore because spec
			//(such that component need only to override removeChhild
			_modChildByIter = true;
			try {
				final Component ref;
				if (_bNxt) {
					if (_it.hasNext()) {
						ref = (Component)_it.next();
						_it.previous();
					} else
						ref = null;
				} else
					ref = (Component)_last;

				insertBefore(comp, ref);
			} finally {
				_modChildByIter = false;
			}
		}
		public boolean hasNext() {
			return _it.hasNext();
		}
		public boolean hasPrevious() {
			return _it.hasPrevious();
		}
		public Object next() {
			_bNxt = true;
			return _last = _it.next();
		}
		public Object previous() {
			_bNxt = false;
			return _last = _it.previous();
		}
		public int nextIndex() {
			return _it.nextIndex();
		}
		public int previousIndex() {
			return _it.previousIndex();
		}
		public void remove() {
			_it.remove();

			//Note: we must go thru removeChild because spec
			//(such that component need only to override removeChhild
			_modChildByIter = true;
			try {
				removeChild((Component)_last);
			} finally {
				_modChildByIter = false;
			}
		}
		public void set(Object o) {
			throw new UnsupportedOperationException("set");
				//Possible to implement this but confusing to developers
				//if o has the same parent (since we have to move)
		}
	}
}
