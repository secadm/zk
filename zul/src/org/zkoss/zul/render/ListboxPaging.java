/* ListboxPaging.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Thu Sep 6 2007, Created by Jeff.Liu
}}IS_NOTE

Copyright (C) 2007 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zul.render;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import org.zkoss.zk.fn.ZkFns;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.ComponentRenderer;
import org.zkoss.zul.Listbox;

/**
 * {@link Listbox}'s paging mold.
 * @author Jeff Liu
 * @since 3.0.0
 */
public class ListboxPaging implements ComponentRenderer {

	public void render(Component comp, Writer out) throws IOException {
		final WriterHelper wh = new WriterHelper(out);
		final Listbox self = (Listbox)comp;
		final String uuid = self.getUuid();
		
		wh.write("<div id=\"").write(uuid).write("\" z.type=\"zul.sel.Libox\"");
		wh.write(self.getOuterAttrs()).write(self.getInnerAttrs()).writeln(">");
		
		wh.write("<div id=\"").write(uuid).write("!paging\" class=\"listbox-paging\">");
		wh.write("<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" class=\"listbox-btable\">");
		//header
		wh.write("<tbody>");
		ZkFns.redraw(self.getListhead(),out);
		wh.write("</tbody>");
		//body
		wh.write("<tbody id=\"").write(uuid).write("!cave\">");
		Iterator it = self.getItems().iterator();
		final int _beg = self.getVisibleBegin();
		final int _end = self.getVisibleEnd();
		for( int i = 0; ++i<_beg && it.hasNext();)
			it.next();
		for( int i = 0, cnt = _end - _beg + 1; it.hasNext() && --cnt >= 0; ++i) {
			final Component item = (Component)it.next();
			ZkFns.redraw(item, out);
		}
		wh.write("</tbody>");
		//Footer
		wh.write("<tbody class=\"grid-foot\">");
		ZkFns.redraw(self.getListfoot(),out);
		wh.write("</tbody>");
		wh.write("</table>");
		//Paging
		wh.write("<div id=\"").write(uuid).write("!pgi\" class=\"listbox-pgi\">");
		ZkFns.redraw(self.getPaging(), out);
		wh.write("</div></div></div>");
		
	
		
		/*
		<div id="${self.uuid}" z.type="zul.sel.Libox"${self.outerAttrs}${self.innerAttrs}>
			<div id="${self.uuid}!paging" class="listbox-paging">
			<table width="100%" border="0" cellpadding="0" cellspacing="0" class="listbox-btable">
			<tbody>
		${z:redraw(self.listhead, null)}
			</tbody>
	
			<tbody id="${self.uuid}!cave">
			<c:forEach var="item" items="${self.items}" begin="${self.visibleBegin}" end="${self.visibleEnd}">
		${z:redraw(item, null)}
			</c:forEach>
			</tbody>
	
			<tbody class="grid-foot">
		${z:redraw(self.listfoot, null)}
			</tbody>
			</table>
			<div id="${self.uuid}!pgi" class="listbox-pgi">
			${z:redraw(self.paging, null)}
			</div>
			</div>
		</div>
		*/		
		
		/*
		for (int j = 0; ++j <= _beg && it.hasNext();) //skip
		it.next();

		for (int j = 0, cnt = _end - _beg + 1; it.hasNext() && --cnt >= 0; ++j) {
			final Object val = it.next();
			if (_var != null) ac.setAttribute(_var, val, ac.PAGE_SCOPE);
			if (st != null) st.update(j, val);
			ac.renderFragment(out);
		} 
		 */
	}

}
