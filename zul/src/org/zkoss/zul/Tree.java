/* Tree.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Wed Jul  6 18:51:33     2005, Created by tomyeh
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zul;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;

import org.zkoss.lang.Exceptions;
import org.zkoss.lang.Objects;
import org.zkoss.util.logging.Log;
import org.zkoss.xml.HTMLs;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.ext.client.Selectable;
import org.zkoss.zk.ui.ext.render.ChildChangedAware;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

//import org.zkoss.zul.Listbox.Renderer;

import org.zkoss.zul.event.ListDataEvent;
import org.zkoss.zul.event.TreeDataEvent;
import org.zkoss.zul.event.TreeDataListener;
import org.zkoss.zul.impl.XulElement;

/**
 *  A container which can be used to hold a tabular
 * or hierarchical set of rows of elements.
 *
 * <p>Event:
 * <ol>
 * <li>org.zkoss.zk.ui.event.SelectEvent is sent when user changes
 * the selection.</li>
 * </ol>
 *
 * <p>Default {@link #getSclass}: tree.
 *
 * @author tomyeh
 */
public class Tree extends XulElement {
	
	private transient Treecols _treecols;
	private transient Treefoot _treefoot;
	private transient Treechildren _treechildren;
	/** A list of selected items. */
	private transient Set _selItems;
	/** The first selected item. */
	private transient Treeitem _sel;
	private int _rows = 0;
	/** The name. */
	private String _name;
	/** # of items per page. */
	private int _pgsz = 10;
	private boolean _multiple, _checkmark;
	private boolean _vflex;
	/** disable smartUpdate; usually caused by the client. */
	private transient boolean _noSmartUpdate;

	public Tree() {
		init();
		setSclass("tree");
	}
	private void init() {
		_selItems = new LinkedHashSet(5);
	}

	/** Returns the treecols that this tree owns (might null).
	 */
	public Treecols getTreecols() {
		return _treecols;
	}
	/** Returns the treefoot that this tree owns (might null).
	 */
	public Treefoot getTreefoot() {
		return _treefoot;
	}
	/** Returns the treechildren that this tree owns (might null).
	 */
	public Treechildren getTreechildren() {
		return _treechildren;
	}

	/** Returns the rows. Zero means no limitation.
	 * <p>Default: 0.
	 */
	public int getRows() {
		return _rows;
	}
	/** Sets the rows.
	 * <p>Note: if both {@link #setHeight} is specified with non-empty,
	 * {@link #setRows} is ignored
	 */
	public void setRows(int rows) throws WrongValueException {
		if (rows < 0)
			throw new WrongValueException("Illegal rows: "+rows);

		if (_rows != rows) {
			_rows = rows;
			smartUpdate("z.size", Integer.toString(_rows));
			initAtClient();
				//Don't use smartUpdate because client has to extra job
				//besides maintaining HTML DOM
		}
	}

	/** Returns the name of this component.
	 * <p>Default: null.
	 * <p>Don't use this method if your application is purely based
	 * on ZK's event-driven model.
	 * <p>The name is used only to work with "legacy" Web application that
	 * handles user's request by servlets.
	 * It works only with HTTP/HTML-based browsers. It doesn't work
	 * with other kind of clients.
	 */
	public String getName() {
		return _name;
	}
	/** Sets the name of this component.
	 * <p>Don't use this method if your application is purely based
	 * on ZK's event-driven model.
	 * <p>The name is used only to work with "legacy" Web application that
	 * handles user's request by servlets.
	 * It works only with HTTP/HTML-based browsers. It doesn't work
	 * with other kind of clients.
	 *
	 * @param name the name of this component.
	 */
	public void setName(String name) {
		if (name != null && name.length() == 0) name = null;
		if (!Objects.equals(_name, name)) {
			if (_name != null) smartUpdate("z.name", _name);
			else invalidate(); //1) generate _value; 2) add submit listener
			_name = name;
		}
	}

	/** Returns whether the check mark shall be displayed in front
	 * of each item.
	 * <p>Default: false.
	 */
	public final boolean isCheckmark() {
		return _checkmark;
	}
	/** Sets whether the check mark shall be displayed in front
	 * of each item.
	 * <p>The check mark is a checkbox if {@link #isMultiple} returns
	 * true. It is a radio button if {@link #isMultiple} returns false.
	 */
	public void setCheckmark(boolean checkmark) {
		if (_checkmark != checkmark) {
			_checkmark = checkmark;
			invalidate();
		}
	}

	/** Returns whether to grow and shrink vertical to fit their given space,
	 * so called vertial flexibility.
	 *
	 * <p>Note: this attribute is ignored if {@link #setRows} is specified
	 *
	 * <p>Default: false.
	 */
	public final boolean isVflex() {
		return _vflex;
	}
	/** Sets whether to grow and shrink vertical to fit their given space,
	 * so called vertial flexibility.
	 *
	 * <p>Note: this attribute is ignored if {@link #setRows} is specified
	 */
	public void setVflex(boolean vflex) {
		if (_vflex != vflex) {
			_vflex = vflex;
			smartUpdate("z.flex", _vflex);
		}
	}

	/** Returns the seltype.
	 * <p>Default: "single".
	 */
	public String getSeltype() {
		return _multiple ? "multiple": "single";
	}
	/** Sets the seltype.
	 * Currently, only "single" is supported.
	 */
	public void setSeltype(String seltype) throws WrongValueException {
		if ("single".equals(seltype)) setMultiple(false);
		else if ("multiple".equals(seltype)) setMultiple(true);
		else throw new WrongValueException("Unknown seltype: "+seltype);
	}
	/** Returns whether multiple selections are allowed.
	 * <p>Default: false.
	 */
	public boolean isMultiple() {
		return _multiple;
	}
	/** Sets whether multiple selections are allowed.
	 */
	public void setMultiple(boolean multiple) {
		if (_multiple != multiple) {
			_multiple = multiple;
			if (!_multiple && _selItems.size() > 1) {
				final Treeitem item = getSelectedItem();
				_selItems.clear();
				if (item != null)
					_selItems.add(item);
				//No need to update z.selId because z.multiple will do the job
			}
			if (isCheckmark()) invalidate(); //change check mark
			else smartUpdate("z.multiple", _multiple);
		}
	}
	/** Returns the ID of the selected item (it is stored as the z.selId
	 * attribute of the tree).
	 */
	private String getSelectedId() {
		//NOTE: Treerow's uuid; not Treeitem's
		final Treerow tr = _sel != null ? _sel.getTreerow(): null;
		return tr != null ? tr.getUuid(): "zk_n_a";
	}

	/** Returns a readonly list of all descending {@link Treeitem}
	 * (children's children and so on).
	 *
	 * <p>Note: the performance of the size method of returned collection
	 * is no good.
	 */
	public Collection getItems() {
		return _treechildren != null ? _treechildren.getItems(): Collections.EMPTY_LIST;
	}
	/** Returns the number of child {@link Treeitem}.
	 * The same as {@link #getItems}.size().
	 * <p>Note: the performance of this method is no good.
	 */
	public int getItemCount() {
		return _treechildren != null ? _treechildren.getItemCount(): 0;
	}

	/**  Deselects all of the currently selected items and selects
	 * the given item.
	 * <p>It is the same as {@link #setSelectedItem}.
	 * @param item the item to select. If null, all items are deselected.
	 */
	public void selectItem(Treeitem item) {
		if (item == null) {
			clearSelection();
		} else {
			if (item.getTree() != this)
				throw new UiException("Not a child: "+item);

			if (_sel != item
			|| (_multiple && _selItems.size() > 1)) {
				for (Iterator it = _selItems.iterator(); it.hasNext();) {
					final Treeitem ti = (Treeitem)it.next();
					ti.setSelectedDirectly(false);
				}
				_selItems.clear();

				_sel = item;
				item.setSelectedDirectly(true);
				_selItems.add(item);

				final Treerow tr = item.getTreerow();
				if (tr != null)
					smartUpdate("select", tr.getUuid());
			}
		}
	}
	/** Selects the given item, without deselecting any other items
	 * that are already selected..
	 */
	public void addItemToSelection(Treeitem item) {
		if (item.getTree() != this)
			throw new UiException("Not a child: "+item);

		if (!item.isSelected()) {
			if (!_multiple) {
				selectItem(item);
			} else {
				item.setSelectedDirectly(true);
				_selItems.add(item);
				smartUpdateSelection();
				if (fixSelected())
					smartUpdate("z.selId", getSelectedId());
			}
		}
	}
	/**  Deselects the given item without deselecting other items.
	 */
	public void removeItemFromSelection(Treeitem item) {
		if (item.getTree() != this)
			throw new UiException("Not a child: "+item);

		if (item.isSelected()) {
			if (!_multiple) {
				clearSelection();
			} else {
				item.setSelectedDirectly(false);
				_selItems.remove(item);
				smartUpdateSelection();
				if (fixSelected())
					smartUpdate("z.selId", getSelectedId());
				//No need to use response because such info is carried on tags
			}
		}
	}
	/** Note: we have to update all selection at once, since addItemToSelection
	 * and removeItemFromSelection might be called interchangeably.
	 */
	private void smartUpdateSelection() {
		final StringBuffer sb = new StringBuffer(80);
		for (Iterator it = _selItems.iterator(); it.hasNext();) {
			final Treeitem item = (Treeitem)it.next();
			final Treerow tr = item.getTreerow();
			if (tr != null) {
				if (sb.length() > 0) sb.append(',');
				sb.append(tr.getUuid());
			}			
		}
		smartUpdate("chgSel", sb.toString());
	}
	/** If the specified item is selected, it is deselected.
	 * If it is not selected, it is selected. Other items in the tree
	 * that are selected are not affected, and retain their selected state.
	 */
	public void toggleItemSelection(Treeitem item) {
		if (item.isSelected()) removeItemFromSelection(item);
		else addItemToSelection(item);
	}
	/** Clears the selection.
	 */
	public void clearSelection() {
		if (!_selItems.isEmpty()) {
			for (Iterator it = _selItems.iterator(); it.hasNext();) {
				final Treeitem item = (Treeitem)it.next();
				item.setSelectedDirectly(false);
			}
			_selItems.clear();
			_sel = null;
			smartUpdate("select", "");
		}
	}
	/** Selects all items.
	 */
	public void selectAll() {
		if (!_multiple)
			throw new UiException("Appliable only to the multiple seltype: "+this);

		//we don't invoke getItemCount first because it is slow!
		boolean changed = false, first = true;
		for (Iterator it = getItems().iterator(); it.hasNext();) {
			final Treeitem item = (Treeitem)it.next();
			if (!item.isSelected()) {
				_selItems.add(item);
				item.setSelectedDirectly(true);
				changed = true;
			}
			if (first) {
				_sel = item;
				first = false;
			}
		}
		smartUpdate("selectAll", "true");
	}


	/** Returns the selected item.
	 */
	public Treeitem getSelectedItem() {
		return _sel;
	}
	/**  Deselects all of the currently selected items and selects
	 * the given item.
	 * <p>It is the same as {@link #selectItem}.
	 */
	public void setSelectedItem(Treeitem item) {
		selectItem(item);
	}

	/** Returns all selected items.
	 */
	public Set getSelectedItems() {
		return Collections.unmodifiableSet(_selItems);
	}
	/** Returns the number of items being selected.
	 */
	public int getSelectedCount() {
		return _selItems.size();
	}

	/** Clears all child tree items ({@link Treeitem}.
	 * <p>Note: after clear, {@link #getTreechildren} won't be null, but
	 * it has no child
	 */
	public void clear() {
		if (_treechildren == null)
			return;

		final List l = _treechildren.getChildren();
		if (l.isEmpty())
			return; //nothing to do

		for (Iterator it = new ArrayList(l).iterator(); it.hasNext();)
			((Component)it.next()).detach();
	}

	/** Re-init the tree at the client.
	 */
	/*package*/ void initAtClient() {
		smartUpdate("z.init", true);
	}

	/** Returns the page size that is used by all {@link Treechildren}
	 * to display a portion of their child {@link Treeitem},
	 * or -1 if no limitation.
	 *
	 * <p>Default: 10.
	 *
	 * @since 2.4.1
	 */
	public int getPageSize() {
		return _pgsz;
	}
	/** Sets the page size that is used by all {@link Treechildren}
	 * to display a portion of their child {@link Treeitem}.
	 *
	 * @param size the page size. If non-positive, there won't be
	 * any limitation. In other wordss, all {@link Treeitem} are shown.
	 * Notice: since the browser's JavaScript engine is slow to
	 * handle huge trees, it is better not to set a non-positive size
	 * if your tree is huge.
	 * @since 2.4.1
	 */
	public void setPageSize(int size) throws WrongValueException {
		if (size <= 0) size = -1; //no limitation
		if (_pgsz != size) {
			_pgsz = size;
			invalidate();
				//FUTURE: trade-off: search and update only
				//necessary Treechildren is faster or not
		}
	}

	//-- Component --//
	public void setHeight(String height) {
		if (!Objects.equals(height, getHeight())) {
			super.setHeight(height);
			initAtClient();
		}
	}
	public void smartUpdate(String attr, String value) {
		if (!_noSmartUpdate) super.smartUpdate(attr, value);
	}
	public boolean insertBefore(Component child, Component refChild) {
		if (child instanceof Treecols) {
			if (_treecols != null && _treecols != child)
				throw new UiException("Only one treecols is allowed: "+this);
			if (!getChildren().isEmpty())
				refChild = (Component)getChildren().get(0);
				//always makes treecols as the first child
			_treecols = (Treecols)child;
			invalidate();
		} else if (child instanceof Treefoot) {
			if (_treefoot != null && _treefoot != child)
				throw new UiException("Only one treefoot is allowed: "+this);
			_treefoot = (Treefoot)child;
			refChild = null; //treefoot as the last
			invalidate();
		} else if (child instanceof Treechildren) {
			if (_treechildren != null && _treechildren != child)
				throw new UiException("Only one treechildren is allowed: "+this);
			if (refChild instanceof Treecols)
				throw new UiException("treecols must be the first child");
			if (refChild == null || refChild.getParent() != this)
				refChild = _treefoot; //treefoot as the last
			_treechildren = (Treechildren)child;
			invalidate();

			fixSelectedSet();
		} else {
			throw new UiException("Unsupported child for tree: "+child);
		}
		return super.insertBefore(child, refChild);
	}
	/** Called by {@link Treeitem} when is added to a tree. */
	/*package*/ void onTreeitemAdded(Treeitem item) {
		fixNewChild(item);
		onTreechildrenAdded(item.getTreechildren());
	}
	/** Called by {@link Treeitem} when is removed from a tree. */
	/*package*/ void onTreeitemRemoved(Treeitem item) {
		boolean fixSel = false;
		if (item.isSelected()) {
			_selItems.remove(item);
			fixSel = _sel == item;
			if (fixSel && !_multiple) {
				_sel = null;
				smartUpdate("z.selId", getSelectedId());
				assert _selItems.isEmpty();
			}
		}
		onTreechildrenRemoved(item.getTreechildren());
		if (fixSel) fixSelected();
	}
	/** Called by {@link Treechildren} when is added to a tree. */
	/*package*/ void onTreechildrenAdded(Treechildren tchs) {
		if (tchs == null || tchs.getParent() == this)
			return; //already being processed by insertBefore

		//main the selected status
		for (Iterator it = tchs.getItems().iterator(); it.hasNext();)
			fixNewChild((Treeitem)it.next());
	}
	/** Fixes the status of new added child. */
	private void fixNewChild(Treeitem item) {
		if (item.isSelected()) {
			if (_sel != null && !_multiple) {
				item.setSelectedDirectly(false);
				item.invalidate();
			} else {
				if (_sel == null)
					_sel = item;
				_selItems.add(item);
				smartUpdate("z.selId", getSelectedId());
			}
		}
	}
	/** Called by {@link Treechildren} when is removed from a tree. */
	/*package*/ void onTreechildrenRemoved(Treechildren tchs) {
		if (tchs == null || tchs.getParent() == this)
			return; //already being processed by onChildRemoved

		//main the selected status
		boolean fixSel = false;
		for (Iterator it = tchs.getItems().iterator(); it.hasNext();) {
			final Treeitem item = (Treeitem)it.next();
			if (item.isSelected()) {
				_selItems.remove(item);
				if (_sel == item) {
					if (!_multiple) {
						_sel = null;
						smartUpdate("z.selId", getSelectedId());
						assert _selItems.isEmpty();
						return; //done
					}
					fixSel = true;
				}
			}
		}
		if (fixSel) fixSelected();
	}

	public void onChildAdded(Component child) {
		super.onChildAdded(child);
		invalidate();
	}
	public void onChildRemoved(Component child) {
		if (child instanceof Treecols) {
			_treecols = null;
		} else if (child instanceof Treefoot) {
			_treefoot = null;
		} else if (child instanceof Treechildren) {
			_treechildren = null;
			_selItems.clear();
			_sel = null;
		}
		super.onChildRemoved(child);
		invalidate();
	}

	/** Fixes all info about the selected status. */
	private void fixSelectedSet() {
		_sel = null; _selItems.clear();
		for (Iterator it = getItems().iterator(); it.hasNext();) {
			final Treeitem item = (Treeitem)it.next();
			if (item.isSelected()) {
				if (_sel == null) {
					_sel = item;
				} else if (!_multiple) {
					item.setSelectedDirectly(false);
					continue;
				}
				_selItems.add(item);
			}
		}
	}
	/** Make _sel to be the first selected item. */
	private boolean fixSelected() {
		Treeitem sel = null;
		switch (_selItems.size()) {
		case 1:
			sel = (Treeitem)_selItems.iterator().next();
		case 0:
			break;
		default:
			for (Iterator it = getItems().iterator(); it.hasNext();) {
				final Treeitem item = (Treeitem)it.next();
				if (item.isSelected()) {
					sel = item;
					break;
				}
			}
		}

		if (sel != _sel) {
			_sel = sel;
			return true;
		}
		return false;
	}

	//-- super --//
	public String getOuterAttrs() {
		final StringBuffer sb = new StringBuffer(64)
			.append(super.getOuterAttrs());
		HTMLs.appendAttribute(sb, "z.name", _name);
		HTMLs.appendAttribute(sb, "z.size",  getRows());
		HTMLs.appendAttribute(sb, "z.selId", getSelectedId());
		if (_multiple)
			HTMLs.appendAttribute(sb, "z.multiple", true);
		//if (_checkmark)
		//	HTMLs.appendAttribute(sb, "z.checkmark",  _checkmark);
		if (_vflex)
			HTMLs.appendAttribute(sb, "z.vflex", true);
		appendAsapAttr(sb, Events.ON_SELECT);

		final Treechildren tc = getTreechildren();
		if (tc != null) {
			final int pgcnt = tc.getPageCount();
			if (pgcnt > 1) {
				HTMLs.appendAttribute(sb, "z.tchsib", tc.getUuid());
				HTMLs.appendAttribute(sb, "z.pgc", pgcnt);
				HTMLs.appendAttribute(sb, "z.pgi", tc.getActivePage());
				HTMLs.appendAttribute(sb, "z.pgsz", tc.getPageSize());
			}
		}
		return sb.toString();
	}

	//Cloneable//
	public Object clone() {
		int cntSel = _selItems.size();

		final Tree clone = (Tree)super.clone();
		clone.init();

		int cnt = 0;
		if (_treecols != null) ++cnt;
		if (_treefoot != null) ++cnt;
		if (_treechildren != null) ++cnt;
		if (cnt > 0 || cntSel > 0) clone.afterUnmarshal(cnt, cntSel);

		return clone;
	}
	/** @param cnt # of children that need special handling (used for optimization).
	 * -1 means process all of them
	 * @param cntSel # of selected items
	 */
	private void afterUnmarshal(int cnt, int cntSel) {
		if (cnt != 0) {
			for (Iterator it = getChildren().iterator(); it.hasNext();) {
				final Object child = it.next();
				if (child instanceof Treecols) {
					_treecols = (Treecols)child;
					if (--cnt == 0) break;
				} else if (child instanceof Treefoot) {
					_treefoot = (Treefoot)child;
					if (--cnt == 0) break;
				} else if (child instanceof Treechildren) {
					_treechildren = (Treechildren)child;
					if (--cnt == 0) break;
				}
			}
		}

		_sel = null;
		_selItems.clear();
		if (cntSel != 0) {
			for (Iterator it = getItems().iterator(); it.hasNext();) {
				final Treeitem ti = (Treeitem)it.next();
				if (ti.isSelected()) {
					if (_sel == null) _sel = ti;
					_selItems.add(ti);
					if (--cntSel == 0) break;
				}
			}
		}
	}

	//-- Serializable --//
	private synchronized void readObject(java.io.ObjectInputStream s)
	throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();

		init();

		afterUnmarshal(-1, -1);
	}

	//-- ComponentCtrl --//
	protected Object newExtraCtrl() {
		return new ExtraCtrl();
	}
	
	// TODO AREA JEFF ADDED
	
	private static final Log log = Log.lookup(Tree.class);
	
	private TreeModel _model;
	
	private TreeitemRenderer _renderer;
	
	private TreeDataListener _dataListener;
	
	private EventListener _treeitemOpenListener = new EventListener() {
		public void onEvent(Event event) throws Exception {
			if (event.getName().equals(Events.ON_OPEN)) {
				Treeitem _item = (Treeitem) event.getTarget();
				if(!_item.isLoaded()){
					Tree t = _item.getTree();
			    _item.getTreechildren().getChildren().clear();
					t.renderItem(_item);
				}
			}
		}
	};
	
	/*
	 * Handles when the tree model's content changed 
	 */
	private void onTreeDataChange(TreeDataEvent event) {	
			List l = getPath(event.getParent());
			int[] indexes =event.getIndexes();
			Treeitem ti = getTreeitemByPath(l);
			Object data = event.getParent();
			//Loop through indexes array
			for(int i=0;i<indexes.length;i++)
			{
				int index = indexes[i];
				switch (event.getType()) {
				case TreeDataEvent.NODE_ADDED:
					newInsertIndexHelper(indexes,i);
					onTreeDataInsert(ti,indexes[i],data);
					break;
				case TreeDataEvent.NODE_REMOVED:
					onTreeDataRemoved(ti,index);
					break;
				case TreeDataEvent.CONTENTS_CHANGED:
					onTreeDataContentChanged(ti,index,data);
					break;
				}
			}
	}
	
	/*
	 * Handle Treedata insertion
	 */
	private void onTreeDataInsert(Treeitem parent, int index, Object data){
		
		/* 	Find the sibling to insertBefore;
		 * 	if there is no sibling or new item is inserted at end.
		 */
		Treeitem newTi = new Treeitem();
		Treechildren ch= null;
		renderItem(newTi,_model.getChild(data,index));
		if(parent.getTreechildren()!=null){
			ch = parent.getTreechildren();
		}
		else{
			ch= new Treechildren();
		}
		List siblings = getTreeitems(ch);
		//if there is no sibling or new item is inserted at end.
		if(siblings.size()==0 || index == siblings.size() ){
			ch.insertBefore(newTi, null);
		}else{
			ch.insertBefore(newTi, (Treeitem)siblings.get(index));
		}
		ch.setParent(parent);
		parent.setOpen(true);
	}
	
	/*
	 * Helper method to calculate the new index after modification that
	 * caused by period indexes
	 */
	private void newInsertIndexHelper(int[] indexes, int cur)
	{
		for(int i=cur;i<indexes.length;i++){
			if(indexes[cur] >indexes[i]){
				indexes[cur]++;
			}
		}
	}
		
	/*
	 * Handle event that child is removed
	 */
	private void onTreeDataRemoved(Treeitem parent, int index){
		List items = getTreeitems((Treechildren)parent.getTreechildren());
		
		if(items.size()>1){
			((Treeitem)items.get(index)).detach();
		}else{
			((Treechildren)parent.getTreechildren()).detach();
			renderItem(parent);
		}
		parent.setOpen(true);
	}
	
	/*
	 * Handle event that child's content is changed
	 */
	private void onTreeDataContentChanged(Treeitem parent, int index, Object data){
		List l = getPath(data);
		l.add(new Integer(index));
		Treeitem ti = getTreeitemByPath(l);
		renderItem(ti,_model.getChild(data,index));
		ti.setOpen(true);
	}
	
	/*
	 * Return the Treeitem by a given tree path
	 */
	private Treeitem getTreeitemByPath(List path)
	{
		Iterator itr = path.iterator();
		Treeitem ti = (Treeitem)getTreeitems(this.getTreechildren()).get(0);
		while (itr.hasNext( )) { //GO THROU PATH
			ti = getTreeitemByPathHelper(ti,Integer.parseInt(itr.next().toString()));	
		} 
		return ti;
	}

	/*
	 * Helper function for getTreeitemByPath
	 */
	private Treeitem getTreeitemByPathHelper(Treeitem parent, int index)
	{
		List l = getTreeitems(parent.getTreechildren());
		return (Treeitem)l.get(index);
	}
	
	/*
	 * return Treeitems from a Treechildren
	 */
	private List getTreeitems(Treechildren parent)
	{	
		List li = parent.getChildren();
		List l = new ArrayList();
		for(int i=0; i< li.size();i++){
			if(li.get(i) instanceof Treeitem){
				l.add(li.get(i));
			}
		}
		return l;
	}
	
	/*
	 * Initial Tree data listener
	 */
	private void initDataListener() {
		if (_dataListener == null)
			_dataListener = new TreeDataListener() {
				public void onChange(TreeDataEvent event) {
					onTreeDataChange(event);
				}
			};

		_model.addTreeDataListener(_dataListener);
	}
	
	/** Sets the tree model associated with this tree. 
	 *
	 * @param model the tree model to associate, or null to dis-associate
	 * any previous model.
	 * @exception UiException if failed to initialize with the model
	 */
	public void setModel(TreeModel model) throws Exception
	{
		_model = model;
		syncModel();
		initDataListener();
	}
	
//	-- ListModel dependent codes --//
	/** Returns the list model associated with this tree, or null
	 * if this tree is not associated with any tree data model.
	 */
	public TreeModel getModel()
	{
		return _model;
	}
	
	/** Synchronizes the tree to be consistent with the specified model.
	 */
	private void syncModel() throws Exception
	{
		if (_renderer == null)
			_renderer = getRealRenderer();
		renderTree();
	}
	
	/** Sets the renderer which is used to render each item
	 * if {@link #getModel} is not null.
	 *
	 * <p>Note: changing a render will not cause the tree to re-render.
	 * If you want it to re-render, you could assign the same model again 
	 * (i.e., setModel(getModel())), or fire an {@link TreeDataEvent} event.
	 *
	 * @param renderer the renderer, or null to use the default.
	 * @exception UiException if failed to initialize with the model
	 */
	public void setTreeitemRenderer(TreeitemRenderer renderer)
	{
		_renderer = renderer;
	}
	
	/** Returns the renderer to render each item, or null if the default
	 * renderer is used.
	 * @return the renderer to render each item, or null if the default
	 */
	public TreeitemRenderer getTreeitemRenderer()
	{
		return _renderer;
	}
	
	/*
	 * Render the root of tree
	 */
	private void renderTree() throws Exception
	{
		_treechildren = null;
		Treechildren children = new Treechildren();
		Treeitem ti = new Treeitem();
		ti.setParent(children);
		children.setParent(this);
		this.renderItem(ti);
	}
	
	private static final TreeitemRenderer getDefaultItemRenderer() {
		return _defRend;
	}
	private static final TreeitemRenderer _defRend = new TreeitemRenderer() {
		public void render(Treeitem ti, Object data)
		{
				
				Treecell tc = new Treecell(data.toString());
				Treerow tr = null;
				if(ti.getTreerow()==null){
					tr = new Treerow();
					tr.setParent(ti);
				}else{
					tr = ti.getTreerow(); 
					tr.getChildren().clear();
				}			
				tc.setParent(tr);
				ti.setOpen(false);
		}
	};
	/** Returns the renderer used to render items.
	 */
	private TreeitemRenderer getRealRenderer() {
		return _renderer != null ? _renderer: getDefaultItemRenderer();
	}

	/** Used to render treeitem if _model is specified. */
	private class Renderer implements java.io.Serializable {
		private final TreeitemRenderer _renderer;
		private boolean _rendered, _ctrled;

		private Renderer() {
			_renderer = getRealRenderer();
		}
		
		private void render(Treeitem item) throws Throwable {
			
			if (!item.isOpen())
				return; //nothing to do

			if (!_rendered && (_renderer instanceof RendererCtrl)) {
				((RendererCtrl)_renderer).doTry();
				_ctrled = true;
			}
			
			try {
				Object node = getAssocitedNode(item, Tree.this);
				_renderer.render(item, node);
			} catch (Throwable ex) {
				try {
					item.setLabel(Exceptions.getMessage(ex));
				} catch (Throwable t) {
					log.error(t);
				}
				item.setOpen(true);
				throw ex;
			}

			item.setOpen(true);
			_rendered = true;
		}
		
		private void doCatch(Throwable ex) {
			if (_ctrled) {
				try {
					((RendererCtrl)_renderer).doCatch(ex);
				} catch (Throwable t) {
					throw UiException.Aide.wrap(t);
				}
			} else {
				throw UiException.Aide.wrap(ex);
			}
		}
		private void doFinally() {
			if (_rendered)
				initAtClient();
					//reason: after rendering, the column width might change
					//Also: Mozilla remembers scrollTop when user's pressing
					//RELOAD, it makes init more desirable.
			if (_ctrled)
				((RendererCtrl)_renderer).doFinally();
		}
	}
	
	/** Renders the specified {@link Treeitem} if not loaded yet,
	 * with {@link #getTreeitemRenderer}.
	 *
	 * <p>It does nothing if {@link #getModel} returns null.
	 *
	 * @see #renderItems
	 */
	public void renderItem(Treeitem item){
		if(_model ==null) return;
		final Renderer renderer = new Renderer();
		try {
			renderItem(item,getAssocitedNode(item,this));
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}	
	}
	
	/** Renders the specified {@link Treeitem} with data if not loaded yet,
	 * with {@link #getTreeitemRenderer}.
	 *
	 * <p>It does nothing if {@link #getModel} returns null.
	 *
	 * @see #renderItems
	 */
	public void renderItem(Treeitem item, Object data){
		if(_model ==null) return;
		final Renderer renderer = new Renderer();
		try {
			dfRenderItem(data,item);
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}
	
	/*
	 * Render the treetiem with given node
	 */
	private void  dfRenderItem(Object node, Treeitem item) throws Exception
	{
		Treechildren children = null;
		
		if(item.getTreechildren()!=null){
			children = item.getTreechildren();
			/* 
			 * When the treeitem is rendered after 1st time, dropped all
			 * the descending treeitems first.
			*/
			if(children.getItemCount()>0)
				children.getChildren().clear();
		}
		else{
			children = new Treechildren();
			_renderer.render(item, node);
		}
		/*
		 * After modified the node in tree model, if node is leaf, 
		 * its treechildren is needed to be dropped.
		 */
		if(_model.isLeaf(node)){
			_renderer.render(item, node);
			if(item.getTreechildren()!=null)
				item.getTreechildren().detach();
		}
		else
		{
			for(int i=0; i< _model.getChildCount(node);i++ ){
				Treeitem ti = new Treeitem();
				Object data = _model.getChild(node, i);
				_renderer.render(ti, data);
				if(!_model.isLeaf(data)){
					ti.addEventListener(Events.ON_OPEN, _treeitemOpenListener);	
					Treechildren ch = new Treechildren();
					ch.setParent(ti);
				}
				ti.setParent(children);
			}
			children.setParent(item);
		}
		//After the treeitem is loaded with data, set treeitem to be loaded
		item.setLoaded(true);
	}
	
	/** Renders the specified {@link Treeitem}s with data if not loaded yet,
	 * with {@link #getTreeitemRenderer}.
	 *
	 * <p>It does nothing if {@link #getModel} returns null.
	 *
	 * @see #renderItem
	 */
	public void renderItems(Set items) {
		if (_model == null) return;

		if (items.isEmpty())
			return; //nothing to do

		final Renderer renderer = new Renderer();
		try {
			for (Iterator it = items.iterator(); it.hasNext();){
				Treeitem item = (Treeitem)it.next();
				Object data = getAssocitedNode(item,this);
				dfRenderItem(data,item);
			}
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}
	
	/*
	 * Remove from TreeModel Aug 13 07
	 */
	
	/*
	 * Return a node which is an associated Treeitem ti in a Tree tree 
	 */
	private Object getAssocitedNode(Treeitem ti, Tree t){
		return getNodeByPath(getTreePath(t,ti),_model.getRoot());
	}
	
	/**
	 * return the path which is from ZK Component root to ZK Component lastNode 
	 * @param root
	 * @param lastNode
	 * @return the path which is from ZK Component root to ZK Component lastNode 
	 */
	private List getTreePath(Component root, Component lastNode){
		List al = new ArrayList();
		Component curNode = lastNode;
		while(!root.equals(curNode)){
			if(curNode instanceof Treeitem){
				al.add(new Integer(((Treeitem)curNode).indexOf()));
			}
			curNode = curNode.getParent();
		}
		return al;
	}
	
	/**
	 * Constructs a new TreePath, which is the path identified by root ending in node.
	 * @param node - The destination of path
	 * @return The tree path
	 */
	private List getPath(Object node){
		return getTreePath(_model.getRoot(),node);
	}
	
	/**
	 * return the tree path which is from root to lastNode
	 * @param root
	 * @param lastNode
	 * @return  the tree path which is from root to lastNode
	 */
	private List getTreePath(Object root, Object lastNode){
		List l = new ArrayList();
		dfSearch(l, root, lastNode);
		return l;
	}
	
	/**
	 * Depth first search to find the path which is from node to target
	 * @param al path
	 * @param node origin
	 * @param target destination
	 * @return whether the target is found or not
	 */
	private boolean dfSearch(List l, Object node, Object target){
			if(node.equals(target)){
				return true;
			}
			else{
				int size = _model.getChildCount(node);
				for(int i=0; i< size; i++){
					boolean flag = dfSearch(l,_model.getChild(node,i),target);
					if(flag){
						l.add(0,new Integer(i));
						return true;
					}
				}
			}
			return false;
	}
	
	/**
	 * Get the node from tree by given path
	 * @param path
	 * @param root
	 * @return the node from tree by given path
	 */
	private Object getNodeByPath(List path, Object root)
	{
		Object node = root;
		for(int i=path.size()-2; i >= 0; i--){
			node = _model.getChild(node, Integer.parseInt(path.get(i).toString()));
		}
		return node;
	}
	

	//TODO AREA JEFF ADDED END
	
	/** A utility class to implement {@link #getExtraCtrl}.
	 * It is used only by component developers.
	 */
	
	protected class ExtraCtrl extends XulElement.ExtraCtrl
	implements Selectable, ChildChangedAware {
		//-- Selectable --//
		public void selectItemsByClient(Set selItems) {
			_noSmartUpdate = true;
			try {
				if (!_multiple || selItems == null || selItems.size() <= 1) {
					final Treeitem item =
						selItems != null && selItems.size() > 0 ?
							(Treeitem)selItems.iterator().next(): null;
					selectItem(item);
				} else {
					for (Iterator it = new ArrayList(_selItems).iterator(); it.hasNext();) {
						final Treeitem item = (Treeitem)it.next();
						if (!selItems.remove(item))
							removeItemFromSelection(item);
					}
					for (Iterator it = selItems.iterator(); it.hasNext();)
						addItemToSelection((Treeitem)it.next());
				}
			} finally {
				_noSmartUpdate = false;
			}
		}
		//ChildChangedAware//
		public boolean isChildChangedAware() {
			return true;
		}
	}
}
