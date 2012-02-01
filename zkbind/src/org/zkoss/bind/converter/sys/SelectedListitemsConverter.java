/* SelectedListitemConverter.java

	Purpose:
		
	Description:
		
	History:
		Aug 17, 2011 6:10:20 PM, Created by henrichen

Copyright (C) 2011 Potix Corporation. All Rights Reserved.
*/

package org.zkoss.bind.converter.sys;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.zkoss.bind.BindContext;
import org.zkoss.bind.Converter;
import org.zkoss.bind.impl.BinderImpl;
import org.zkoss.bind.sys.LoadPropertyBinding;
import org.zkoss.lang.Classes;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ext.Selectable;

/**
 * Convert listbox selected listitems to bean and vice versa.
 * @author dennis
 * @since 6.0.0
 */
public class SelectedListitemsConverter implements Converter, java.io.Serializable {
	private static final long serialVersionUID = 201108171811L;
	
	@SuppressWarnings("unchecked")
	public Object coerceToUi(Object val, Component comp, BindContext ctx) {
		Listbox lbx = (Listbox) comp;
		final ListModel<?> model = lbx.getModel();
		if(model !=null && !(model instanceof Selectable)){
			//model has to imple Selectable if binding to selectedItems
  			throw new UiException("model doesn't implement Selectable");
  		}
		
		//clean first.
		if(model!=null){
			((Selectable<?>)model).clearSelection();
		}
		
  		final Set<Listitem> items = new LinkedHashSet<Listitem>();
		Set<Object> vals = val == null ? null : (Set<Object>) Classes.coerce(LinkedHashSet.class, val);
		
	  	if (vals != null && vals.size()>0) {
	  		if(model!=null){
	  			for(Object obj:vals){
	  				((Selectable<Object>)model).addToSelection(obj);
	  			}
	  		}else{
	  			//no model case
			  	for (final Iterator<?> it = lbx.getItems().iterator(); it.hasNext();) {
			  		final Listitem li = (Listitem) it.next();
			  		Object bean = li.getValue();

			  		if (vals.contains(bean)) {
			  			items.add(li);
			  		}
			  	}
	  		}
	  	}
	  	return model == null ? items : LoadPropertyBinding.LOAD_IGNORED;
	}

	@SuppressWarnings("unchecked")
	public Object coerceToBean(Object val, Component comp, BindContext ctx) {
		Set<Object> vals = new LinkedHashSet<Object>();//the order
		if (val != null) {
			final Listbox lbx = (Listbox) comp;
	  		final ListModel<?> model = lbx.getModel();
	  		final Set<Listitem> items = (Set<Listitem>)Classes.coerce(LinkedHashSet.class, val);
	  		for(Listitem item : items){
		  		if(model != null){ //from model value
		  			vals.add(model.getElementAt(item.getIndex()));
		  		} else { //no binding
		  			vals.add(item.getValue());
		  		}
	  		}
	  		return vals;
	  	}
	 	return vals;
	}

}