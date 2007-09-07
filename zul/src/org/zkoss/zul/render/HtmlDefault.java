/* HtmlDefault.java

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

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.ComponentRenderer;
import org.zkoss.zul.Html;

/**
 * {@link Html}'s default mold.
 * @author Jeff Liu
 * @since 3.0.0
 */
public class HtmlDefault implements ComponentRenderer {

	public void render(Component comp, Writer out) throws IOException {
		final WriterHelper wh = new WriterHelper(out);
		final Html self = (Html)comp;
		wh.write("<span id=\"").write(self.getUuid()).write("\"");
		wh.write(self.getOuterAttrs()).write(self.getInnerAttrs()).writeln(">");
		wh.write(self.getContent());
		wh.writeln("</span>");
		/*
		<span id="${self.uuid}"${self.outerAttrs}${self.innerAttrs}>
		${self.content}<%-- don't escape --%>
		</span>
		*/

	}

}
